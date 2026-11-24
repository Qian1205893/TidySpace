package com.example.tidyspace.pc5853b2a.ui.sorting.scene

import android.util.Log
import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.BoundingBox
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.HoverEffectComponent
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.ViewCoordinateSpace
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.foundation.gesture.SpatialPointerInfo
import com.pico.spatial.ui.foundation.gesture.data.InteractionKind
import com.example.tidyspace.pc5853b2a.domain.model.*
import com.example.tidyspace.pc5853b2a.domain.model.Rotation
import kotlin.math.*
import kotlinx.coroutines.delay

/** Only fittedRoot owns fitting; roomRoot owns gameplay meters. No camera anchor. */
internal class SharedVolume {
    val owner=node()
    val fittedRoot=node().also { owner.addChild(it) }
    val turntable=node().also { fittedRoot.addChild(it) }
    val roomRoot=node().also {
        turntable.addChild(it)
        it.transform.position=Vector3(0f,-.18f,.12f)
    }
    var content: SpatialViewContent?=null
    private var yaw=0f
    private var half=Vector3(0f)
    private var panelMin: Vector3?=null
    private var panelMax: Vector3?=null
    private var wallPanel: Entity?=null
    private var wallPanelSize: Vector3?=null
    private var fitCorners: List<Point3>?=null
    internal var catalogueBand=false
    internal val reservedTop get()=if(catalogueBand) windowHalf.x*1.65f*302f/1440f+.14f else 0f
    internal val roomTop: Float? get()=fitCorners?.maxOf { it.y }?.let {
        it*fittedRoot.transform.scaleVector.y+fittedRoot.transform.position.y
    }
    internal val windowHalf get()=half.takeIf { it.x>.001f } ?: Vector3(3f,2.25f,3f)
    fun startTracking() {
        Log.i("TidyGrab","facing-reference=captionbar local-front=+Z room-yaw=$yaw")
    }
    fun stopTracking() = Unit
    /** Window +Z is the captionbar/user side. Invert only the turntable, not a global HMD pose. */
    fun viewerFacing(): Rotation = captionFacing(yaw)
    /** Measured front/caption center in room metres, including fit and room turn. */
    internal fun captionPosition(): Point3? {
        if(min(half.x,min(half.y,half.z))<=.001f) return null
        val p=roomRoot.convertPositionFrom(Vector3(0f,-half.y,half.z),owner)
        return Point3(p.x,p.y,p.z).takeIf { it.finite() }
    }
    companion object {
        fun captionFacing(roomYaw: Float)=Rotation.yaw(-roomYaw)
        fun facingToward(position: Point3,viewer: Point3): Rotation? {
            val direction=viewer-position
            if(!direction.finite() || hypot(direction.x,direction.z)<.001f) return null
            return Rotation.yaw(Math.toDegrees(atan2(direction.x,direction.z).toDouble()).toFloat())
        }
    }
    fun attach(panel: Entity,largeRoom: Boolean=false,panelSize: Vector3?=null) {
        if(largeRoom) {
            roomRoot.addChild(panel)
            val size=checkNotNull(panelSize)
            val scale=.75f/size.x
            panel.transform.position=Vector3(.64f,.507f,-.904f)
            panel.transform.scaleVector=Vector3(scale)
            panel.transform.eulerAngles=EulerAngles()
            wallPanel=panel
            wallPanelSize=size*scale
            Log.i("TidyVolume","guide=rear-wall center=${panel.transform.position} size=${size*scale}")
            return
        }
        fittedRoot.addChild(panel)
        panel.transform.position=Vector3(0f,if(largeRoom) 1.03f else .79f,0f)
        val scale=if(largeRoom) 1.7f else 1.6f
        panel.transform.scaleVector=Vector3(scale)
        panelSize?.let {
            panelMin=panel.transform.position-it*(scale*.5f)
            panelMax=panel.transform.position+it*(scale*.5f)
        }
    }
    fun rotate(degrees: Float) {
        if(yaw==degrees) return
        yaw=degrees;turntable.transform.eulerAngles=EulerAngles(yaw=degrees)
        refitYaw()
    }
    fun turn(delta: Rotation)=rotate(yaw+delta.yaw())
    fun pose(event: SpatialPointerInfo,key: String): HandPose? {
        val c=content ?: return null
        val target=if(key=="@room") fittedRoot else roomRoot
        val (source,space)=pointerSource(event,key)
        if(!source.x.isFinite() || !source.y.isFinite() || !source.z.isFinite()) return null
        val p=c.convertPosition(source,space,target)
        val q=c.convertRotation(event.inputDevicePose.rawRotation.toQuaternion(),ViewCoordinateSpace.Global,target)
        return HandPose(Point3(p.x,p.y,p.z),Rotation(q.x,q.y,q.z,q.w))
    }
    internal fun pointerSource(event: SpatialPointerInfo,key: String): Pair<Vector3,ViewCoordinateSpace> {
        val hand=event.inputDevicePose.rawPosition
        // Mouse/touch events have no tracked hand. An identity raw pose must
        // never turn every movement into the same global origin.
        val tracked=event.kind!=InteractionKind.Pointer && event.kind!=InteractionKind.Unknown &&
            (abs(hand.x)+abs(hand.y)+abs(hand.z)>.00001f)
        // Single-hand movement follows the same mapped XYZ as catalogue cards.
        // Physical hand separation is used only for two-hand turning/squeezing.
        val rawHand=(key=="@room" || key=="@pair") && tracked
        val source=if(rawHand) Vector3(hand.x,hand.y,hand.z) else Vector3(event.x,event.y,event.z)
        return source to if(rawHand) ViewCoordinateSpace.Global else ViewCoordinateSpace.Local
    }
    fun hitPose(event: SpatialPointerInfo): HandPose? {
        val c=content ?: return null
        val p=c.convertPosition(Vector3(event.x,event.y,event.z),ViewCoordinateSpace.Local,roomRoot)
        return Point3(p.x,p.y,p.z).takeIf { it.finite() }?.let { HandPose(it,Rotation()) }
    }
    /** Resource bounds exist before a hidden/detached hierarchy is rendered.
     * Measure enabled meshes in the requested space without briefly revealing
     * the owner to populate the renderer's visual-bounds cache.
     */
    internal fun measuredBounds(root: Entity,relativeTo: Entity): BoundingBox {
        if(owner.enabled) {
            val rendered=root.getVisualBounds(relativeTo,true,true)
            if(!rendered.isEmpty()) return rendered
        }
        var low: Vector3?=null
        var high: Vector3?=null
        fun visit(entity: Entity) {
            if(!entity.enabled) return
            val bounds=entity.components[ModelComponent::class.java]?.mesh?.getBoundingBox()
            if(bounds!=null && !bounds.isEmpty())
                for(x in listOf(bounds.min.x,bounds.max.x))
                    for(y in listOf(bounds.min.y,bounds.max.y))
                        for(z in listOf(bounds.min.z,bounds.max.z)) {
                            val p=entity.convertPositionTo(Vector3(x,y,z),relativeTo)
                            val lo=low;val hi=high
                            low=if(lo==null) p else Vector3(min(lo.x,p.x),min(lo.y,p.y),min(lo.z,p.z))
                            high=if(hi==null) p else Vector3(max(hi.x,p.x),max(hi.y,p.y),max(hi.z,p.z))
                        }
            entity.getChildren().forEach(::visit)
        }
        visit(root)
        val lo=low ?: return root.getVisualBounds(relativeTo,true,true)
        val hi=checkNotNull(high)
        return BoundingBox((lo+hi)*.5f,(hi-lo)*.5f)
    }
    suspend fun fit(fallback: VolumeEnvelope,currentYawFit: Boolean=false) {
        val c=checkNotNull(content)
        var ready=false
        for(frame in 0 until 90) {
            delay(16)
            val corner=c.convertPosition(Vector3.ZERO,ViewCoordinateSpace.Local,c.localSpatialCoordinateSpace)
            half=Vector3(abs(corner.x),abs(corner.y),abs(corner.z))
            if(min(half.x,min(half.y,half.z))>.001f) { ready=true;break }
        }
        check(ready) { "Volumetric window bounds unavailable" }
        // Measure before yaw: an already-rotated AABB would inflate the cylinder,
        // shrinking a restored room according to its saved viewing angle.
        var room=measuredBounds(turntable,turntable)
        var whole=measuredBounds(fittedRoot,fittedRoot)
        var boundsAttempt=0
        while((room.isEmpty() || whole.isEmpty()) && boundsAttempt++<10) {
            delay(16)
            room=measuredBounds(turntable,turntable)
            whole=measuredBounds(fittedRoot,fittedRoot)
        }
        val nativeReady=!room.isEmpty() && !whole.isEmpty()
        if(currentYawFit) {
            val lo=if(nativeReady) Point3(room.min.x-.04f,min(room.min.y,fallback.lowY),room.min.z-.04f)
                else Point3(fallback.minX,fallback.lowY,fallback.minZ)
            val hi=if(nativeReady) Point3(room.max.x+.04f,max(room.max.y,fallback.highY),room.max.z+.04f)
                else Point3(fallback.maxX,fallback.highY,fallback.maxZ)
            fitCorners=listOf(lo.x,hi.x).flatMap { x -> listOf(lo.y,hi.y).flatMap { y ->
                listOf(lo.z,hi.z).map { z -> Point3(x,y,z) }
            } }
            refitYaw()
            validate()
            Log.i("TidyVolume","shared=true mode=fixed fit=current-yaw bounds=${if(nativeReady) "native" else "designed"} " +
                "half=$half authoredMin=$lo authoredMax=$hi scale=${fittedRoot.transform.scaleVector.x} position=${fittedRoot.transform.position}")
            return
        }
        // Ancestor visual bounds can be unavailable before the renderer presents a frame.
        // In that case use the dimension-driven scene envelope supplied by its owner.
        val rx=if(nativeReady) max(abs(room.min.x),abs(room.max.x)) else fallback.radiusX
        val rz=if(nativeReady) max(abs(room.min.z),abs(room.max.z)) else fallback.radiusZ
        val radius=if(nativeReady) sqrt(rx*rx+rz*rz)+.10f else max(rx,rz)
        val x=if(nativeReady) max(radius,max(max(abs(whole.min.x),abs(whole.max.x)),
            max(abs(panelMin?.x ?: 0f),abs(panelMax?.x ?: 0f)))) else fallback.radiusX
        val z=if(nativeReady) max(radius,max(abs(whole.min.z),abs(whole.max.z))) else fallback.radiusZ
        val lowY=if(nativeReady) min(whole.min.y,panelMin?.y ?: whole.min.y) else fallback.lowY
        val highY=if(nativeReady) max(whole.max.y,panelMax?.y ?: whole.max.y) else fallback.highY
        val placement=VolumePlacement.solve(half.x,half.y,half.z,x,z,lowY,highY)
        fittedRoot.transform.scaleVector=Vector3(placement.scale)
        fittedRoot.transform.position=Vector3(0f,placement.y,placement.z)
        validate()
        Log.i("TidyVolume","shared=true mode=fixed anchor=base+caption bounds=${if(nativeReady) "native" else "designed"} " +
            "half=$half scale=${placement.scale} " +
            "position=${fittedRoot.transform.position} yawSafeRadius=$radius panelMin=$panelMin panelMax=$panelMax")
    }
    private fun refitYaw() {
        val corners=fitCorners ?: return
        val rotated=corners.map { Rotation.yaw(yaw).rotate(it) }
        val placement=VolumePlacement.solveBounds(half.x,half.y,half.z,
            rotated.minOf { it.x },rotated.maxOf { it.x },
            rotated.minOf { it.y },rotated.maxOf { it.y },
            rotated.minOf { it.z },rotated.maxOf { it.z },reservedTop)
        fittedRoot.transform.scaleVector=Vector3(placement.scale)
        fittedRoot.transform.position=Vector3(placement.x,placement.y,placement.z)
    }
    fun validate() {
        val b=owner.getVisualBounds(owner,true,true)
        if(b.isEmpty()) {
            Log.i("TidyVolume","nativeVisualBounds=unavailable mode=fixed designedEnvelope=true")
            return
        }
        val pass=!b.isEmpty() && b.min.x>=-half.x && b.max.x<=half.x &&
            b.min.y>=-half.y && b.max.y<=half.y && b.min.z>=-half.z && b.max.z<=half.z
        Log.i("TidyVolume","actualVisualBounds min=${b.min} max=${b.max} fits=$pass")
        if(panelMin!=null && panelMax!=null) {
            val lo=panelMin!!*fittedRoot.transform.scaleVector.x+fittedRoot.transform.position
            val hi=panelMax!!*fittedRoot.transform.scaleVector.x+fittedRoot.transform.position
            val panelFits=lo.x>=-half.x && hi.x<=half.x && lo.y>=-half.y && hi.y<=half.y && lo.z>=-half.z && hi.z<=half.z
            Log.i("TidyVolume","panelBounds min=$lo max=$hi fits=$panelFits")
        }
        wallPanel?.let { panel ->
            val size=checkNotNull(wallPanelSize)
            val corners=listOf(-1f,1f).flatMap { x -> listOf(-1f,1f).map { y ->
                val local=panel.transform.position+Vector3(x*size.x/2,y*size.y/2,0f)+roomRoot.transform.position
                val p=Rotation.yaw(yaw).rotate(Point3(local.x,local.y,local.z))
                p.vector()*fittedRoot.transform.scaleVector.x+fittedRoot.transform.position
            } }
            val fits=corners.all { abs(it.x)<=half.x && abs(it.y)<=half.y && abs(it.z)<=half.z }
            Log.i("TidyVolume","wallGuide yaw=$yaw attachedToRoom=true fits=$fits")
        }
    }
    fun destroy() { stopTracking();content=null;owner.destroy(true) }
}
internal fun node()=Entity().apply {
    if(components[TransformComponent::class.java]==null) components.set(TransformComponent())
}
internal val Entity.transform: TransformComponent get()=components[TransformComponent::class.java]!!
internal fun Rotation.quat()=Quat(x,y,z,w)
internal fun interactive(entity: Entity,size: Vector3) {
    entity.components.set(CollisionComponent(listOf(ShapeResource.createBox(size)),PhysicsMaterialResource()))
    entity.components.set(InteractableComponent())
    entity.components.set(HoverEffectComponent())
}

/** A single capture path for gaze pinch, direct pinch, ray pinch and controllers. */
internal class SharedSpatialInput(
    private val target: (Entity?)->String?,
    private val pose: (SpatialPointerInfo,String)->HandPose?,
    private val begin: (String)->Unit,
    private val transform: (GrabDelta)->Unit,
    private val end: ()->Unit,
    private val cancelled: ()->Unit,
    twoHandRoom: Boolean=false,
    beginAt: ((String,HandPose?)->Unit)?=null,
    hitPose: ((SpatialPointerInfo)->HandPose?)?=null,
    movableRoot: (String)->String?={ it },
    immediateTap: (String)->Boolean={ false },
) {
    private val hitPose=hitPose
    private val roomInput=if(twoHandRoom) RoomGestureInput(begin,transform,end,cancelled,beginAt,movableRoot,immediateTap) else null
    private val tracker=GrabTracker()
    private var selected: String?=null
    private var sampleNanos=0L
    fun resumeSelection(id: String?) {
        if(roomInput!=null) roomInput.resumeSelection(id)
        else { selected=id;tracker.clear() }
    }
    fun rebaseline() { roomInput?.rebaseline();tracker.rebaseline();sampleNanos=0L }
    fun events(events: List<SpatialPointerInfo>): Boolean {
        roomInput?.let { input ->
            val now=System.nanoTime()
            val seconds=if(sampleNanos==0L) 1f/60 else ((now-sampleNanos)/1e9).toFloat().coerceIn(0f,.1f)
            sampleNanos=now
            return input.events(events.map { e ->
                val contact=RoomContact(
                    id=e.pointerId.value,
                    phase=when {
                        e.isDownEvent()->ContactPhase.Down
                        e.isUpEvent()->ContactPhase.Up
                        !e.pressed->ContactPhase.Lost
                        else->ContactPhase.Move
                    },
                    target=target(e.targetedEntity),
                    objectPose=pose(e,"@object"),
                    pairPose=pose(e,"@pair"),
                    handPosition=pose(e,"@room")?.position,
                    hitPose=hitPose?.invoke(e),
                    source=e.kind.name,
                )
                if(e.isDownEvent()) Log.i("TidyGrab","contact=${contact.id} kind=${e.kind} " +
                    "target=${contact.target} raw=${e.inputDevicePose.rawPosition} room=${contact.objectPose?.position}")
                contact
            },seconds)
        }
        // Losing a captured contact is not a completed release. Cancel before
        // applying its last pose so a lost hand cannot place an item or award a hit.
        if(events.any { tracker.contains(it.pointerId.value) && !it.pressed && !it.isUpEvent() }) {
            cancel()
            return true
        }
        var consumed=false
        // First apply samples for existing contacts together, before adding/removing a hand.
        val updates=events.filter { tracker.contains(it.pointerId.value) }.mapNotNull { e ->
            selected?.let { key->pose(e,key)?.let { e.pointerId.value to it } }
        }.toMap()
        if(updates.isNotEmpty()) {
            val now=System.nanoTime()
            tracker.update(updates)?.let { transform(it.copy(seconds=((now-sampleNanos)/1e9).toFloat().coerceIn(0f,.1f))) }
            sampleNanos=now;consumed=true
        }
        for(e in events) {
            val id=e.pointerId.value
            if(e.isDownEvent()) {
                val key=target(e.targetedEntity) ?: continue
                if(selected!=null && key!=selected) continue
                val p=pose(e,key) ?: continue
                if(selected==null) { selected=key;sampleNanos=System.nanoTime();begin(key) }
                tracker.down(id,p);consumed=true
            } else if(tracker.contains(id) && e.isUpEvent()) {
                tracker.up(id);consumed=true
                if(tracker.count==0) { end();selected=null }
            }
        }
        return consumed
    }
    fun cancel() {
        roomInput?.let { it.cancel();sampleNanos=0L;return }
        if(selected!=null) cancelled()
        tracker.clear();selected=null
    }
}
