package com.example.tidyspace.pc5853b2a.domain.model

import kotlin.math.atan2
import kotlin.math.sqrt

enum class ContactPhase { Down, Move, Up, Lost }

/** Hit poses belong to roomRoot; hand positions belong to the unrotated fittedRoot. */
data class RoomContact(
    val id: Long,
    val phase: ContactPhase,
    val target: String?=null,
    val objectPose: HandPose?=null,
    val handPosition: Point3?=null,
    val hitPose: HandPose?=null,
    val source: String?=null,
    val pairPose: HandPose?=objectPose,
)

/**
 * The second hand locks to the first hand's movable object, even on a ray miss.
 * Two background pinches turn the house. A house turn ends at the first release,
 * and both hands must release before another gesture can begin.
 */
class RoomGestureInput(
    private val begin: (String)->Unit,
    private val transform: (GrabDelta)->Unit,
    private val end: ()->Unit,
    private val cancelled: ()->Unit,
    private val beginAt: ((String,HandPose?)->Unit)?=null,
    private val movableRoot: (String)->String?={ id -> id.takeUnless { it.startsWith("@house:") } },
    private val immediateTap: (String)->Boolean={ false },
) {
    private val contacts=linkedMapOf<Long,RoomContact>()
    private val tapped=mutableSetOf<Long>()
    private val objects=GrabTracker(uprightPair=true)
    private var selected: String?=null
    private var direction: Point3?=null
    private var waitingForRelease=false
    private var pendingDrag=Point3(0f,0f,0f)
    private var needsBaseline=false

    /** Resume the same transaction; the next pinch establishes a fresh tracking baseline. */
    fun resumeSelection(id: String?) {
        contacts.clear();objects.clear();tapped.clear()
        selected=id;direction=null;waitingForRelease=false;needsBaseline=true
    }
    fun rebaseline() { needsBaseline=true }
    private fun trackingPose(sample: RoomContact)=
        if(contacts.size>=2) sample.pairPose else sample.objectPose

    fun events(events: List<RoomContact>,seconds: Float=1f/60): Boolean {
        var tappedEvent=false
        val gestures=events.filter { sample ->
            if(sample.id in tapped) {
                if(sample.phase==ContactPhase.Up || sample.phase==ContactPhase.Lost) tapped.remove(sample.id)
                tappedEvent=true;false
            } else if(sample.phase==ContactPhase.Down && sample.target?.let(immediateTap)==true) {
                tapped+=sample.id;tappedEvent=true;false
            } else true
        }
        if(tappedEvent && gestures.isEmpty()) return true
        if(waitingForRelease) {
            drain(gestures)
            return true
        }
        val lost=gestures.any { it.id in contacts && it.phase==ContactPhase.Lost }
        val roomReleased=selected=="@room" &&
            gestures.any { it.id in contacts && it.phase==ContactPhase.Up }
        if(lost || roomReleased) {
            val captured=selected!=null
            if(captured) {
                if(lost) cancelled() else end()
                selected=null;objects.clear();direction=null
            }
            waitingForRelease=captured
            drain(gestures)
            return captured
        }

        var consumed=selected!=null || tappedEvent
        // A release/join/source switch owns this frame. Refresh then rebaseline,
        // never apply a final single-hand move while the second hand is joining.
        val changed=needsBaseline || gestures.any { sample ->
            sample.phase==ContactPhase.Down && sample.id !in contacts ||
                sample.phase==ContactPhase.Up && sample.id in contacts ||
                contacts[sample.id]?.let { it.source!=sample.source }==true
        }
        needsBaseline=false
        gestures.forEach { sample ->
            contacts[sample.id]?.let { original ->
                contacts[sample.id]=sample.copy(target=original.target)
            }
        }
        if(!changed && selected=="@room") {
            val next=horizontalDirection()
            val previous=direction
            if(previous!=null && next!=null) {
                val degrees=Math.toDegrees(atan2(
                    previous.z*next.x-previous.x*next.z,
                    previous.x*next.x+previous.z*next.z,
                ).toDouble()).toFloat()
                transform(GrabDelta(Point3(0f,0f,0f),Rotation.yaw(degrees),seconds))
            }
            // Close/vertical hands or invalid tracking discard the baseline, preventing a jump.
            direction=next
        } else if(!changed && selected!=null && objects.count==contacts.size) {
            // Keep an explicit invalid sample: omitting it would reuse the stale
            // hand while the other hand turns, followed by a jump on recovery.
            objects.update(contacts.mapValues { (_,sample) ->
                trackingPose(sample) ?: HandPose(Point3(Float.NaN,0f,0f),Rotation())
            })?.let { measured ->
                var delta=measured.copy(seconds=seconds)
                val root=selected?.let(movableRoot)
                if(objects.count==1 && root!=null &&
                    (root==selected || selected?.startsWith("@unpack:")==true)) {
                    val extracted=selected?.startsWith("@unpack:")==true
                    if(root!=selected && !extracted) {
                        pendingDrag+=delta.translation
                        if(pendingDrag.length()>=.008f) {
                            cancelled();selected=root;begin(root)
                            delta=delta.copy(translation=pendingDrag)
                            pendingDrag=Point3(0f,0f,0f)
                        }
                    }
                    if(root==selected || extracted) delta=delta.copy(rotation=Rotation(),spanDelta=0f)
                }
                transform(delta)
            }
        }

        gestures.filter { it.phase==ContactPhase.Up }.forEach {
            contacts.remove(it.id)
            objects.up(it.id)
            if(selected!=null && contacts.isEmpty()) {
                end();selected=null;pendingDrag=Point3(0f,0f,0f);consumed=true
            }
        }
        gestures.filter { it.phase==ContactPhase.Down }.forEach { sample ->
            if(sample.id in contacts || contacts.size>=2) return@forEach
            // @room is deliberately never an object hit target.
            val contact=sample.copy(target=sample.target?.takeUnless { it=="@room" })
            contacts[sample.id]=contact
            if(contacts.size==1) {
                val pose=contact.objectPose
                if(selected!=null) {
                    if(pose!=null) objects.down(contact.id,pose)
                    consumed=true
                    return@forEach
                }
                if(contact.target!=null && pose!=null && pose.position.finite() && pose.rotation.finite) {
                    selected=contact.target;objects.down(contact.id,pose);pendingDrag=Point3(0f,0f,0f)
                    if(beginAt!=null) beginAt.invoke(contact.target,contact.hitPose ?: pose) else begin(contact.target)
                    consumed=true
                }
            } else {
                val pair=contacts.values.toList()
                // An unpacked preview selects the newly extracted item, not its
                // source carton. Keep that active target on second-hand capture.
                val playing=selected?.startsWith("@play:")==true
                val objectId=if(playing) selected else selected?.let(movableRoot) ?:
                    pair.firstNotNullOfOrNull { it.target?.let(movableRoot) }
                if(objectId!=null) {
                    if(selected!=objectId && selected?.startsWith("@unpack:")!=true) {
                        if(selected!=null) cancelled()
                        objects.clear();selected=objectId
                        begin(objectId)
                        pair.forEach { p -> trackingPose(p)?.let { objects.down(p.id,it) } }
                    } else trackingPose(contact)?.let { objects.down(contact.id,it) }
                } else {
                    // Wall controls retain their own interaction; only empty space
                    // starts a house turn.
                    if(pair.any { it.target!=null }) return@forEach
                    if(selected!=null) cancelled()
                    objects.clear();selected="@room";direction=horizontalDirection()
                    begin("@room")
                }
                consumed=true
            }
        }
        if(selected!=null && (changed || objects.count!=contacts.size)) {
            objects.clear()
            contacts.forEach { (id,sample) -> trackingPose(sample)?.let { objects.down(id,it) } }
            direction=if(selected=="@room") horizontalDirection() else null
            pendingDrag=Point3(0f,0f,0f)
        }
        return consumed
    }

    private fun horizontalDirection(): Point3? {
        val pair=contacts.values.take(2).map { it.handPosition }
        if(pair.size!=2 || pair.any { it==null || !it.finite() }) return null
        val span=pair[1]!!-pair[0]!!
        val length=sqrt(span.x*span.x+span.z*span.z)
        if(length<.05f) return null
        return Point3(span.x/length,0f,span.z/length)
    }

    private fun drain(events: List<RoomContact>) {
        events.forEach {
            when(it.phase) {
                ContactPhase.Up,ContactPhase.Lost -> contacts.remove(it.id)
                ContactPhase.Down -> contacts[it.id]=it
                ContactPhase.Move -> Unit
            }
        }
        if(contacts.isEmpty()) waitingForRelease=false
    }

    fun cancel() {
        if(selected!=null) cancelled()
        contacts.clear();tapped.clear();objects.clear();selected=null;direction=null;waitingForRelease=false
        pendingDrag=Point3(0f,0f,0f)
        needsBaseline=false
    }
}
