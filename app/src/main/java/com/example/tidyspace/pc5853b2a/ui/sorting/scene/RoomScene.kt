package com.example.tidyspace.pc5853b2a.ui.sorting.scene

import android.util.Log
import com.example.tidyspace.pc5853b2a.domain.model.*
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.OpacityControllerComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.foundation.gesture.SpatialTransformValue
import com.pico.spatial.ui.foundation.gesture.SpatialPointerInfo
import com.example.tidyspace.pc5853b2a.domain.usecase.RoomProgress
import com.example.tidyspace.pc5853b2a.domain.usecase.RoomTime
import com.example.tidyspace.pc5853b2a.domain.usecase.BodyPlayRules
import com.example.tidyspace.pc5853b2a.domain.usecase.RoomPlacementCue
import com.example.tidyspace.pc5853b2a.domain.usecase.RoomCardDrop
import com.pico.spatial.core.ecs.ViewCoordinateSpace
import kotlin.math.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class RoomScene {
    val dragDiagnostics=DragDiagnostics()
    val volume=SharedVolume()
    private val resources=SceneResources()
    private val library=RoomModelLibrary()
    private val updates=SceneUpdates()
    private val targets=mutableMapOf<Entity,String>()
    private val objects=mutableMapOf<String,Entity>()
    private val furniture=RoomFurniture(targets,library)
    private val playthings=PlaythingAnimator()
    private val architecture=RoomRenovation(targets,library,includeCleaning=false)
    private val architectureRoot=node().also { volume.roomRoot.addChild(it) }
    private lateinit var beacon: Entity
    private lateinit var destination: Entity
    private lateinit var invalid: Entity
    private var state=RoomGame()
    private var readGame: (() -> RoomGame)?=null
    private var inputRevision=0L
    private var lastInput: RoomGame?=null
    private var synchronizedGame: RoomGame?=null
    private var cue: Entity?=null
    private var cueKey: Triple<String,String?,Boolean>?=null
    private var cueStarted=0.0
    private val cueAnchor=node().also { volume.roomRoot.addChild(it) }
    private var clock=0.0
    private var ready=false
    private var prepared=false
    internal val isPrepared get()=prepared
    internal var buildCount=0
        private set
    /** Build a detached hierarchy; publish it to SpatialView only after preparation. */
    internal var presented: Boolean=false
        private set
    fun present() {
        check(prepared) { "Room cannot be shown before model loading and fitting finish" }
        volume.owner.enabled=true
        volume.content?.addEntity(volume.owner)
        presented=true
        Log.i("TidyLoading","presented furnishings=${objects.size} ready=$prepared")
    }
    fun conceal() { volume.owner.enabled=false;presented=false }
    /** Detach only the host-owned panels. Keep all models/resources in the session. */
    fun detachHost() {
        listOfNotNull(catalogue,catalogueActions,journal,cue).forEach {
            updates.forget(it);it.removeFromParent()
        }
        labels.values.forEach { updates.forget(it);it.removeFromParent() }
        catalogue=null;catalogueActions=null;journal=null;cue=null;labels.clear()
        volume.content?.removeEntity(volume.owner)
        volume.content=null
        presented=false
    }
    private val buildLock=Mutex()
    private var drawerBase: Vector3?=null
    private data class Echo(val event: PlayEvent,val start: Double)
    private val echoes=arrayOfNulls<Echo>(1)
    private val labels=mutableMapOf<Int,Entity>()
    private val ripples=mutableListOf<Entity>()
    private var seenPlay=0L
    private var catalogue: Entity?=null
    private var catalogueActions: Entity?=null
    private var journal: Entity?=null
    private var journalWidth=1f
    private var catalogueWidth=1f
    private var catalogueActionsWidth=1f
    private var lastCardPoint: Vector3?=null
    private var placementFrame: RoomGame?=null
    private var placementSelection: String?=null
    private var placementNanos=0L
    private var cataloguePixels=Point3(0f,0f,0f)
    fun catalogueCenter(): Vector3?=catalogue?.let {
        volume.owner.convertPositionFrom(Vector3.ZERO,it)
    }
    internal fun catalogueClearance(): Float? {
        val panel=catalogue ?: return null
        val bounds=volume.turntable.getVisualBounds(volume.owner,true,true)
        if(bounds.isEmpty()) return null
        val bottom=volume.owner.convertPositionFrom(Vector3(0f,-catalogueWidth*302f/1440f/2,0f),panel)
        return bottom.y-bounds.max.y
    }
    fun catalogueLayout(width: Float,height: Float) {
        cataloguePixels=Point3(width,height,0f)
    }
    fun attachCatalogue(entity: Entity,width: Float) {
        if(catalogue!=entity) { volume.owner.addChild(entity);catalogue=entity }
        catalogueWidth=width
        positionCatalogue()
    }
    fun attachCatalogueActions(entity: Entity,width: Float) {
        if(catalogueActions!=entity) { volume.owner.addChild(entity);catalogueActions=entity }
        catalogueActionsWidth=width
        positionCatalogue()
    }
    fun attachJournal(entity: Entity,width: Float) {
        if(journal!=entity) { volume.owner.addChild(entity);journal=entity }
        journalWidth=width
        positionCatalogue()
    }
    private fun positionCatalogue() {
        val h=volume.windowHalf
        val width=1.65f*h.x
        val height=width*302f/1440f
        val ceiling=h.y-.035f-height/2
        val centerY=volume.roomTop?.let { min(ceiling,it+.14f+height/2) } ?: ceiling
        // A reserved band, shared with fitting at every yaw, separates the
        // catalogue from the roof. The bounded plane remains parallel to +Z.
        volume.catalogueBand=true
        catalogue?.let {
            updates.position(it,Vector3(0f,centerY,0f))
            updates.scale(it,Vector3(width/catalogueWidth))
            updates.enabled(it,(readGame?.invoke() ?: state).controlsVisible)
        }
        catalogueActions?.let {
            // Align with the unused left header margin of the catalogue.
            updates.position(it,Vector3(-width/2+width*104f/1440f,
                centerY+height/2-width*23f/1440f,.004f))
            updates.scale(it,Vector3(width*144f/1440f/catalogueActionsWidth))
        }
        journal?.let {
            updates.position(it,Vector3(0f,0f,.045f))
            updates.scale(it,Vector3(.85f*h.x/journalWidth))
            updates.enabled(it,(readGame?.invoke() ?: state).journalOpen)
        }
    }
    /** Local pointer XYZ becomes a free held pose; support is solved only on release. */
    fun cardPoint(pixels: Point3,metersPerPixel: Float): Point3? {
        val game=readGame?.invoke() ?: state
        val spec=game.cardDrag?.let { drag -> game.items.find { it.spec.id==drag.id }?.spec } ?: return null
        val panel=catalogue ?: return null
        val pixelLength=if(cataloguePixels.x>0f) catalogueWidth/cataloguePixels.x else metersPerPixel
        val point=Vector3(pixels.x*pixelLength-catalogueWidth/2,
            catalogueWidth*302f/1440f/2-pixels.y*pixelLength,pixels.z*pixelLength)
        val ownerPoint=volume.owner.convertPositionFrom(point,panel)
        lastCardPoint=ownerPoint
        // Returning inside the tray is always a cancel, even if a floor is behind it.
        val inside=pixels.y*pixelLength in 0f..catalogueWidth*302f/1440f &&
            pixels.x*pixelLength in 0f..catalogueWidth &&
            abs(pixels.z*pixelLength*panel.transform.scaleVector.z)<.03f
        if(inside) return null
        return freeCardPosition(ownerPoint,spec)
    }
    private fun freeCardPosition(point: Vector3,spec: RoomSpec): Point3 {
        val fit=volume.fittedRoot.transform.scaleVector.x
        val halfXZ=max(spec.size.x,spec.size.z)*fit/2
        val halfY=spec.size.y*fit/2
        // Keep the selected mesh just in front of its card; retain height and
        // depth movement rather than projecting it down onto a support.
        val p=boundWindowPoint(point+Vector3(0f,0f,halfXZ+.055f),Vector3(halfXZ,halfY,halfXZ))
        return volume.roomRoot.convertPositionFrom(p,volume.owner).let { Point3(it.x,it.y,it.z) }
    }
    /** Cards and already placed objects share only the outer window envelope. */
    private fun boundWindowPoint(point: Vector3,extent: Vector3): Vector3 {
        val h=volume.windowHalf
        fun bound(value: Float,limit: Float,radius: Float): Float {
            val range=(limit-radius-.035f).coerceAtLeast(0f)
            return value.coerceIn(-range,range)
        }
        return Vector3(bound(point.x,h.x,extent.x),bound(point.y,h.y,extent.y),
            bound(point.z,h.z,extent.z))
    }
    internal fun freeDragDelta(game: RoomGame,delta: GrabDelta): GrabDelta {
        if(delta.pointerCount!=1 || !delta.translation.finite() || delta.translation.length()<1e-7f) return delta
        val item=game.selected
        val cleaning=game.cleaning?.takeIf { it.tool!=null || it.heldTrash!=null || it.heldBin }
        val position=item?.position ?: cleaning?.position ?: return delta
        val fit=volume.fittedRoot.transform.scaleVector.x
        val size=item?.spec?.size ?: cleaning?.let {
            val (lo,hi)=com.example.tidyspace.pc5853b2a.domain.usecase.CleaningRules.heldBounds(it)
            // Conservative root-centred bounds include asymmetric tool handles.
            Point3(max(abs(lo.x),abs(hi.x))*2,max(abs(lo.y),abs(hi.y))*2,max(abs(lo.z),abs(hi.z))*2)
        } ?: return delta
        val extent=Vector3(max(size.x,size.z)*fit/2,size.y*fit/2,max(size.x,size.z)*fit/2)
        val point=volume.owner.convertPositionFrom((position+delta.translation).vector(),volume.roomRoot)
        val bounded=volume.roomRoot.convertPositionFrom(boundWindowPoint(point,extent),volume.owner)
        return delta.copy(translation=Point3(bounded.x,bounded.y,bounded.z)-position)
    }
    suspend fun preloadCard(spec: RoomSpec) {
        library.source(spec)
        if(ready) applyCardModel(readGame?.invoke() ?: state)
    }
    private fun applyCardModel(game: RoomGame) {
        furniture.finishCardModel(game)?.let { root ->
            objects[root.getName()]=root
        }
        val floating=lastCardPoint?.let { point ->
            game.cardDrag?.let { drag -> game.items.find { it.spec.id==drag.id }?.spec }
                ?.let { freeCardPosition(point,it) }
        }
        furniture.updateCardModel(game,floating)
        if(game.cardDrag==null) lastCardPoint=null
    }
    fun attachEcho(index: Int,entity: Entity,width: Float) {
        val transform=entity.components[TransformComponent::class.java] ?: return
        if(labels[index]==entity) return
        volume.roomRoot.addChild(entity)
        transform.scaleVector=Vector3(.55f/width)
        // Keep the attachment consumer alive. Disabling it while Compose submits
        // a feedback texture makes HWUI wait for a spatial buffer release.
        entity.components.set(OpacityControllerComponent(0f))
        entity.enabled=true
        labels[index]=entity
    }
    fun attachCue(entity: Entity,width: Float) {
        val transform=entity.components[TransformComponent::class.java] ?: return
        if(cue==entity) return
        cueAnchor.addChild(entity)
        transform.scaleVector=Vector3(.95f/width)
        entity.components.set(OpacityControllerComponent(1f))
        entity.enabled=(readGame?.invoke() ?: state).cueVisible
        cue=entity
    }
    internal fun resolveTarget(entity: Entity?): String? {
        var current=entity
        repeat(24) {
            targets[current]?.let { id ->
                return id.takeUnless {
                    state.items.any { item -> item.spec.id==id && item.spec.kind==Furnishing.MovingBox && state.stock(id).isEmpty() }
                }
            }
            current=current?.getParent() ?: return null
        }
        return null
    }
    private fun movableRoot(id: String): String? {
        val root=when {
            id.startsWith("@play:")->id.removePrefix("@play:").substringBefore(':')
            id.startsWith("@unpack:")->if(state.extractionBox==id.removePrefix("@unpack:"))
                state.selectedId ?: return null else state.stock(id.removePrefix("@unpack:")).firstOrNull() ?: return null
            id=="crate:lid"->"crate"
            id=="desk:drawer"->"desk"
            id=="lamp:switch"->"lamp"
            id.startsWith("@house:") || id.startsWith("@angle:")->return null
            id.startsWith("@clean:") || id.startsWith("@trash:")->return id
            else->id
        }
        return root.takeIf { key -> state.items.any { it.spec.id==key } }
    }
    internal fun nativeTarget(entity: Entity?): String? = resolveTarget(entity)?.let(::nativeId)
    internal fun nativeId(id: String): String? {
        // Mechanisms need wrist rotation, continuous hold and span changes.
        // The sustained furniture recognizer intentionally removes those signals.
        if(id.startsWith("@play:")) {
            val root=playId(id) ?: return null
            return root.takeIf { key ->
                (readGame?.invoke() ?: state).items.any { it.spec.id==key && BodyPlayRules.accepts(it.spec.kind) }
            }
        }
        if(id.startsWith("@unpack:")) return id
        if(id.startsWith("@clean:") || id.startsWith("@trash:"))
            return movableRoot(id)
        return id.takeIf { key -> state.items.any { it.spec.id==key } }
    }
    fun bindState(reader: ()->RoomGame) { readGame=reader }
    internal fun bodyPlay(id: String)=(readGame?.invoke() ?: state).items.any {
        it.spec.id==id && BodyPlayRules.accepts(it.spec.kind)
    }
    internal fun softBody(id: String)=(readGame?.invoke() ?: state).items.any {
        it.spec.id==id && BodyPlayRules.soft(it.spec.kind)
    }
    internal fun viewerFacing(id: String): Rotation? {
        val game=readGame?.invoke() ?: state
        val box=if(id.startsWith("@unpack:")) id.removePrefix("@unpack:")
            else id.takeIf { key -> game.items.any { it.spec.id==key && it.spec.kind==Furnishing.MovingBox } }
        val item=box?.let { key -> game.items.find { it.spec.id==key } }
        return item?.let { volume.captionPosition()?.let { reference ->
            SharedVolume.facingToward(it.position,reference)
        } } ?: SharedVolume.captionFacing(game.roomYaw)
    }
    internal fun cartonFacing(): Map<String,Rotation> {
        val game=readGame?.invoke() ?: state
        val reference=volume.captionPosition()
        return game.items.filter { it.spec.kind==Furnishing.MovingBox &&
            RoomPlacementCue.visible(game,it) && it.spec.id!=game.selectedId }
            .associate { item -> item.spec.id to (reference?.let {
                SharedVolume.facingToward(item.position,it)
            } ?: SharedVolume.captionFacing(game.roomYaw)) }
    }
    internal fun dragFrame(delta: GrabDelta): RoomAction.Transform {
        val game=readGame?.invoke() ?: state
        val facing=if(delta.pointerCount==1 && game.dragFacing!=null)
            game.selectedId?.let(::viewerFacing) else null
        return RoomAction.Transform(freeDragDelta(game,delta),facing)
    }
    internal fun inputPosition(): Vector3? {
        val game=readGame?.invoke() ?: state
        return objects[game.selectedId]?.transform?.position ?: architecture.heldPosition(game)
    }
    fun nativeDelta(frame: SpatialTransformValue,metersPerPixel: Float,pressedCount: Int): GrabDelta {
        // InteractionKind describes a source; it does not say whether that hand
        // is pinching. Count the actual down/up contacts observed before capture.
        val hands=pressedCount.coerceIn(1,2)
        val q=frame.rotation.toQuaternion()
        val p=frame.dragAmount
        val game=readGame?.invoke() ?: state
        val id=game.selectedId ?: frame.targetEntity?.let(::nativeTarget)
        val soft=game.items.find { it.spec.id==id && BodyPlayRules.soft(it.spec.kind) }
        val span=if(hands==2 && soft!=null && frame.scaleValue.isFinite())
            (frame.scaleValue-1f).coerceIn(-.5f,.5f)*soft.spec.size.x else 0f
        return GrabDelta(RoomNativeGesture.roomDelta(Point3(p.x,p.y,p.z),metersPerPixel,
            volume.fittedRoot.transform.scaleVector.x,game.roomYaw),
            Rotation(q.x,q.y,q.z,q.w),spanDelta=span,pointerCount=hands)
    }
    /** No coroutine, model loading or Compose roundtrip on a gesture frame. */
    fun applyInput(game: RoomGame) {
        dragTrace("Tidy.applyInput") {
        state=game;inputRevision++
        if(!ready) { positionCatalogue();return }
        lastInput=game
        observePlay(game)
        // Convert the held UI mesh through this frame's room transform.
        volume.rotate(game.roomYaw)
        positionCatalogue()
        applyCardModel(game)
        if(game.extractionBox!=null) game.selected?.let { item ->
            furniture.promotePreview(item)?.let { objects[item.spec.id]=it }
        }
        if(game.selectedId==null) furniture.finishPromotion()
        dragTrace("Tidy.poses") { applyPoses(game) }
        dragTrace("Tidy.cleaning") { architecture.applyCleaning(game) }
        positionCue(game)
        dragTrace("Tidy.placementCue") { updatePlacementCue(game) }
        }
    }
    private fun applyPoses(game: RoomGame) {
        val reference=volume.captionPosition()
        game.items.forEach { item -> objects[item.spec.id]?.let { entity ->
            val visible=RoomPlacementCue.visible(game,item)
            updates.enabled(entity,visible)
            updates.position(entity,item.position.vector())
            val facing=if(item.spec.kind==Furnishing.MovingBox && visible && game.selectedId!=item.spec.id)
                reference?.let { SharedVolume.facingToward(item.position,it) }
                    ?: SharedVolume.captionFacing(game.roomYaw) else item.rotation
            updates.rotation(entity,facing)
        } }
        furniture.updatePreviewFacing(game)
    }
    private fun updatePlacementCue(game: RoomGame) {
        // Input already solved this exact state before Compose reconciliation.
        // A second pass must not repeat gravity/contact search on the hand frame.
        if(placementFrame===game) return
        placementFrame=game
        if(game.cardDrag!=null) {
            val preview=RoomCardDrop.preview(game)
            val point=preview?.item?.position ?: game.cardDrag.point
            updates.enabled(destination,point!=null && preview?.valid==true)
            updates.enabled(invalid,point!=null && preview?.valid!=true)
            val item=preview?.item ?: game.items.find { it.spec.id==game.cardDrag.id }
            val footprint=item?.let { com.example.tidyspace.pc5853b2a.domain.usecase.RoomPhysics.half(it) }
            val scale=Vector3(((footprint?.x ?: .10f)/.16f).coerceAtLeast(.25f),1f,
                ((footprint?.z ?: .10f)/.16f).coerceAtLeast(.25f))
            updates.scale(destination,scale);updates.scale(invalid,scale)
            point?.let {
                val half=preview?.item?.let { item -> com.example.tidyspace.pc5853b2a.domain.usecase.RoomPhysics.half(item).y }
                    ?: (game.items.find { item -> item.spec.id==game.cardDrag.id }?.spec?.size?.y ?: 0f)/2
                val p=Vector3(it.x,it.y-half+.003f,it.z)
                updates.position(destination,p);updates.position(invalid,p)
            }
            return
        }
        // A cluttered room can try many gravity/support candidates. Keep that
        // preview work off most hand frames; applyPoses still moves the held
        // model on every event. Selection/release always refresh immediately.
        val now=System.nanoTime()
        if(game.selectedId!=null && placementSelection==game.selectedId &&
            now-placementNanos<50_000_000L) return
        placementSelection=game.selectedId
        placementNanos=now
        updates.scale(destination,Vector3(1f));updates.scale(invalid,Vector3(1f))
        val cue=RoomPlacementCue.resolve(game)
        updates.enabled(destination,cue?.valid==true)
        updates.enabled(invalid,cue?.valid==false)
        cue?.point?.let { point ->
            val position=(point+Point3(0f,.002f,0f)).vector()
            updates.position(destination,position)
            updates.position(invalid,position)
        }
    }
    internal fun positionCue(game: RoomGame) {
        val key=if(game.cueVisible) Triple(game.message,game.selectedId,game.saveError) else null
        if(key!=cueKey) {
            cueKey=key
            cueStarted=android.os.SystemClock.uptimeMillis()/1000.0
            cue?.takeIf { it.components[TransformComponent::class.java]!=null }
                ?.components?.get(OpacityControllerComponent::class.java)?.opacity=1f
        }
        cue?.takeIf { it.components[TransformComponent::class.java]!=null }
            ?.let { updates.enabled(it,game.cueVisible) }
        val point=when {
            game.selectedId?.startsWith("@house:")==true->RoomTime.clock+Point3(-.06f,.22f,.025f)
            game.cleaning?.tool!=null || game.cleaning?.heldTrash!=null || game.cleaning?.heldBin==true->game.cleaning.position+Point3(0f,.28f,.07f)
            game.previewBox!=null->game.items.first { it.spec.id==game.previewBox }.position+Point3(-.10f,.48f,.04f)
            game.selected!=null->game.selected!!.position+Point3(0f,game.selected!!.spec.size.y/2+.10f,.05f)
            else->RoomLayout.point(Point3(.61f,.45f,1.02f))
        }
        updates.position(cueAnchor,Point3(point.x.coerceIn(-.75f*RoomLayout.scale,1.14f*RoomLayout.scale),point.y.coerceIn(-.1f,.75f),
            point.z.coerceIn(RoomLayout.back,1.18f*RoomLayout.scale)).vector())
    }
    fun input(send: (RoomAction)->Unit,
              pose: (SpatialPointerInfo,String)->HandPose?=volume::pose)=SharedSpatialInput(
        target={ entity -> resolveTarget(entity)?.let { nativeId(it) ?: it } },pose=pose,
        begin={
            val facing=viewerFacing(it)
            Log.i("TidyGrab","begin=$it viewer-yaw=${facing?.yaw()}")
            send(RoomAction.Begin(it,facing))
        },transform={ delta ->
            val id=(readGame?.invoke() ?: state).selectedId
            val native=id?.let(::nativeId)!=null
            send(dragFrame(if(native && delta.pointerCount==2) delta.copy(
                rotation=RoomNativeGesture.responsiveTurn(delta.rotation),
                spanDelta=if(id?.let(::softBody)==true) delta.spanDelta else 0f) else delta))
        },
        end={ send(RoomAction.End) },cancelled={ send(RoomAction.Cancel) },
        twoHandRoom=true,
        beginAt={ id,pose -> send(RoomAction.Begin(id,viewerFacing(id),pose?.position)) },
        hitPose=volume::hitPose,movableRoot=::movableRoot,
    )
    suspend fun build(game: RoomGame,onProgress: (RoomLoadPhase)->Unit={})=buildLock.withLock {
        if(ready) {
            synchronize(game)
            Log.i("TidyRoom","scene-reattached reused=true furnishings=${objects.size} targets=${targets.size}")
            return@withLock
        }
        check(!presented)
        buildCount++
        onProgress(RoomLoadPhase.Building)
        architecture.build(architectureRoot,resources)
        onProgress(RoomLoadPhase.Furnishing)
        objects.putAll(furniture.load(volume.roomRoot,visibleItems(game)))
        onProgress(RoomLoadPhase.Finishing)
        beacon=resources.part(volume.roomRoot,resources.ring(.11f,.102f,.003f),"lampGlow")
        destination=resources.part(volume.roomRoot,resources.ring(.16f,.155f,.002f),"leafLight")
        destination.enabled=false
        invalid=resources.part(volume.roomRoot,resources.ring(.16f,.153f,.003f),"terracotta")
        invalid.enabled=false
        repeat(1) {
            ripples+=resources.part(volume.roomRoot,resources.ring(.075f,.073f,.001f),"goldObject").apply { enabled=false }
        }
        seenPlay=game.playSequence
        ready=true
        synchronize(game)
        // Detached scene preparation can precede a SpatialView attachment.
        if(volume.content!=null) {
            cue?.enabled=false
            volume.fit(VolumeEnvelope(radiusX=2.32f,radiusZ=2.66f,lowY=-.571f,highY=.94f,
                minX=-1.78f,maxX=2.32f,minZ=-1.58f,maxZ=2.46f),currentYawFit=true)
            applyPoses(readGame?.invoke() ?: state)
            positionCatalogue()
            cue?.enabled=game.cueVisible
        }
        Log.i("TidyRoom","ready furnishings=${objects.size} targets=${targets.size} shared=true rotation=two-hand-pinch floor-wall-rotation=false moving-home=${game.movingHome} stock=${game.remainingGoods} immersive-clock=true")
        Log.i("TidyGrab","viewer-facing-ready yaw=${volume.viewerFacing()?.yaw()} room-yaw=${game.roomYaw}")
        prepared=true
        onProgress(RoomLoadPhase.Prepared)
    }
    private fun visibleItems(game: RoomGame)=if(!game.decorating) emptyList() else game.items.filter {
        if(it.spec.kind==Furnishing.MovingBox) !it.packed && game.stock(it.spec.id).isNotEmpty()
        else !it.packed || (!game.movingHome && game.crateOpen)
    }
    suspend fun synchronize(snapshot: RoomGame) {
        if(!ready) return
        var latest=readGame?.invoke() ?: snapshot
        val previous=synchronizedGame
        if(latest===lastInput && previous!=null && latest.roomYaw!=previous.roomYaw &&
            latest.copy(roomYaw=previous.roomYaw)==previous) {
            // The synchronous input path already applied this turn; object
            // membership, inventory, lights and mechanisms are unchanged.
            synchronizedGame=latest
            return
        }
        while(true) {
            val revision=inputRevision
            val current=furniture.reconcile(visibleItems(latest)) { item ->
                visibleItems(readGame?.invoke() ?: if(revision!=inputRevision) state else latest)
                    .any { it.spec.id==item.spec.id }
            }
            objects.clear();objects.putAll(current)
            furniture.inventory(latest)
            // A load can suspend across many hand frames. Never write its old pose back.
            val fresh=readGame?.invoke() ?: if(revision!=inputRevision) state else latest
            if(fresh==latest) break
            latest=fresh
        }
        val game=latest
        state=game
        applyCardModel(game)
        architecture.synchronize(game)
        updates.retain(objects.values+furniture.parts.values+ripples+labels.values+
            listOfNotNull(beacon,destination,invalid,cueAnchor,catalogue,catalogueActions,journal,cue))
        volume.rotate(game.roomYaw)
        positionCatalogue()
        applyPoses(game)
        furniture.parts["crate:lid"]?.let { updates.angles(it,EulerAngles(
            pitch=if(game.selectedId=="crate:lid") -(if(game.crateOpen) 105f else 0f)-
                game.actionTravel*400f else if(game.crateOpen) -105f else 0f)) }
        furniture.parts["desk:drawer"]?.let {
            if(drawerBase==null) drawerBase=it.transform.position
            updates.position(it,checkNotNull(drawerBase)+Vector3(0f,0f,game.drawerOpen*.10f))
        }
        positionCue(game)
        val step=if(game.guideVisible && game.decorating) RoomProgress.current(game) else null
        val targetId=step?.target?.substringBefore(':')
        val target=game.items.find { it.spec.id==targetId }
        val focus=game.items.find { it.spec.id==game.focusId && it.spec.id!=it.spec.assetId }
        val highlight=(target ?: focus)?.takeIf { RoomPlacementCue.visible(game,it) }
        beacon.enabled=game.decorating && highlight!=null && !highlight.packed
        beacon.transform.eulerAngles=EulerAngles()
        highlight?.let { beacon.transform.position=Vector3(it.position.x,-.313f,it.position.z) }
        if(!game.decorating) {
            val c=checkNotNull(game.cleaning)
            val order=listOf(MessKind.Trash,MessKind.Debris,MessKind.Dust,MessKind.Spill,MessKind.Wall)
            val mess=order.firstNotNullOfOrNull { kind -> cleaningMess().firstOrNull { it.kind==kind && c.amount(it.id)<1f } }
            beacon.enabled=mess!=null
            mess?.let {
                beacon.transform.position=Vector3(it.position.x,if(it.kind==MessKind.Wall) it.position.y else -.311f,it.position.z+.008f)
                if(it.kind==MessKind.Wall) beacon.transform.eulerAngles=EulerAngles(pitch=90f)
            }
        }
        updatePlacementCue(game)
        observePlay(game)
        synchronizedGame=game
    }
    private fun observePlay(game: RoomGame) {
        if(game.playSequence==seenPlay) return
        seenPlay=game.playSequence
        game.playEvent?.let {
            echoes[0]=Echo(it,android.os.SystemClock.uptimeMillis()/1000.0)
            labels[0]?.components?.get(OpacityControllerComponent::class.java)?.opacity=1f
        }
    }
    fun animate(seconds: Double) {
        if(!ready) return
        if(cueKey!=null) cue?.takeIf { it.components[TransformComponent::class.java]!=null }?.let { panel ->
            val opacity=(1.0-(seconds-cueStarted-3.8)/.8).toFloat().coerceIn(0f,1f)
            panel.components[OpacityControllerComponent::class.java]?.let {
                if(it.opacity!=opacity) it.opacity=opacity
            }
        }
        clock=seconds
        furniture.updatePreviewFacing(state)
        architecture.animate(seconds)
        playthings.frame(state,furniture.parts,seconds)
        val echo=echoes[0]
        val echoAge=echo?.let { seconds-it.start } ?: Double.POSITIVE_INFINITY
        val echoId=echo?.event?.id?.takeIf { echoAge in 0.0..1.0 }
        val held=state.selectedId?.takeIf { it.startsWith("@play:") }?.substringAfter(':')
        // Touch only the current targets and reset those that just stopped.
        lastEchoId?.takeIf { it!=echoId && it!=held }?.let { animateMechanism(it,0f,0f) }
        lastHeldId?.takeIf { it!=held && it!=echoId && it!=lastEchoId }?.let { animateMechanism(it,0f,0f) }
        echoId?.let {
            val hit=if(echoAge<=.28) sin(echoAge/.28*PI).toFloat() else 0f
            val turn=if(held==it) state.crankRadians*180f/PI.toFloat()
                else if(echoAge<=.6) (sin(echoAge/.6*PI/2)*90).toFloat() else 0f
            animateMechanism(it,hit,turn)
        }
        held?.takeIf { it!=echoId }?.let { animateMechanism(it,0f,state.crankRadians*180f/PI.toFloat()) }
        lastEchoId=echoId
        lastHeldId=held
        echoes.forEachIndexed { index,echo ->
            val age=echo?.let { seconds-it.start } ?: 10.0
            val item=echo?.takeIf { age in 0.0..1.0 }?.let { active ->
                state.items.find { it.spec.id==active.event.id }
            }
            val active=item!=null && age in 0.0..1.0
            labels[index]?.takeIf { it.components[TransformComponent::class.java]!=null }?.let { label ->
                val opacity=if(active) (if(age<=.25) 1f else ((1.0-age)/.75).toFloat()).coerceIn(0f,1f) else 0f
                label.components[OpacityControllerComponent::class.java]?.let {
                    if(it.opacity!=opacity) it.opacity=opacity
                }
                if(active) {
                    val p=checkNotNull(item).position
                    label.transform.position=Vector3(p.x,p.y+item.spec.size.y/2+.055f+(age*.07).toFloat(),p.z+.012f)
                    // Fixed to room coordinates, matching the rear board's orientation.
                    label.transform.eulerAngles=EulerAngles()
                }
            }
            ripples.getOrNull(index)?.let { ring ->
                updates.enabled(ring,active)
                if(active) {
                    val p=checkNotNull(item).position
                    ring.transform.position=Vector3(p.x,p.y+item.spec.size.y/2+.008f,p.z)
                    val radius=(.65+age*1.4).toFloat()
                    ring.transform.scaleVector=Vector3(radius,(1-age).toFloat(),radius)
                }
            }
        }
        if(beacon.enabled) updates.scale(beacon,Vector3((1.0+.09*sin(seconds*3.8)).toFloat(),1f,(1.0+.09*sin(seconds*3.8)).toFloat()))
    }
    private var lastEchoId: String?=null
    private var lastHeldId: String?=null
    private fun animateMechanism(id: String,hit: Float,turn: Float) {
        furniture.parts["$id:woodfish_hammer"]?.let { updates.angles(it,EulerAngles(pitch=-hit*24f)) }
        furniture.parts["$id:bowl_striker"]?.let { updates.angles(it,EulerAngles(pitch=-hit*18f)) }
        furniture.parts["$id:musicbox_crank"]?.let { updates.angles(it,EulerAngles(pitch=turn)) }
        furniture.parts["$id:musicbox_rotor"]?.let { updates.angles(it,EulerAngles(yaw=turn)) }
    }
    fun validate()=volume.validate()
    fun destroy() {
        conceal()
        prepared=false
        ready=false
        furniture.close()
        volume.destroy()
        library.close();updates.close()
        objects.clear();targets.clear()
        lastInput=null;synchronizedGame=null
        resources.close();playthings.close()
        labels.clear();ripples.clear();echoes.fill(null)
    }
}
