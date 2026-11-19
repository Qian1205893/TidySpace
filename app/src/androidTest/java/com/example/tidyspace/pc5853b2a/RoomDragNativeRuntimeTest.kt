package com.example.tidyspace.pc5853b2a

import android.content.Intent
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.tidyspace.pc5853b2a.data.repository.RoomPersistence
import com.example.tidyspace.pc5853b2a.domain.model.*
import com.example.tidyspace.pc5853b2a.domain.usecase.MovingHome
import com.example.tidyspace.pc5853b2a.domain.usecase.RoomPhysics
import com.example.tidyspace.pc5853b2a.domain.usecase.RoomUseCase
import com.example.tidyspace.pc5853b2a.platform.LaunchActivity
import com.example.tidyspace.pc5853b2a.ui.sorting.RoomViewModel
import com.example.tidyspace.pc5853b2a.ui.sorting.scene.*
import com.pico.spatial.ui.foundation.gesture.SpatialTransformValue
import com.pico.spatial.ui.foundation.gesture.SpatialPointerInfo
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ViewCoordinateSpace
import com.pico.spatial.ui.foundation.gesture.data.InteractionKind
import com.pico.spatial.ui.foundation.geometry.NormalizedPoint3D
import com.pico.spatial.ui.foundation.geometry.Rotation3D
import com.pico.spatial.ui.foundation.geometry.RotationAxis3D
import com.pico.spatial.ui.geometry.Offset3D
import com.pico.spatial.core.math.Vector3
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real ECS poses and resource handoff; isolated VM, never writes the user's prefs. */
@RunWith(AndroidJUnit4::class)
class RoomDragNativeRuntimeTest {
    @Test fun freeDragFramesCrossRoomSurfacesAndFollowActualModel(): Unit=runBlocking {
        val i=InstrumentationRegistry.getInstrumentation()
        i.runOnMainSync { i.targetContext.startActivity(Intent(i.targetContext,LaunchActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        delay(2000)
        withContext(Dispatchers.Main) {
            val base=MovingHome.prepare(RoomGame())
            var vm=RoomViewModel(base) {}
            val scene=RoomScene()
            fun dispatch(a: RoomAction) { vm.onEvent(a);scene.applyInput(vm.currentGame) }
            try {
                scene.bindState { vm.currentGame };scene.build(vm.currentGame)
                for(id in listOf("chair","mug","jelly","mochi","tumbler","desk")) {
                    val item=base.item(id)
                    val floor=-.32f+RoomPhysics.half(item.copy(rotation=Rotation())).y
                    val initial=base.copy(items=base.items.map {
                        it.copy(packed=it.spec.id!=id,support=null,
                            position=Point3(-.25f,floor,0f),rotation=Rotation())
                    },boxStock=base.boxStock.mapValues { (_,ids) -> ids.filterNot { it==id } })
                    vm=RoomViewModel(initial) {}
                    scene.synchronize(vm.currentGame)
                    val model=checkNotNull(scene.volume.roomRoot.findEntity(id))
                    dispatch(RoomAction.Begin(id,Rotation()))
                    val times=mutableListOf<Long>()
                    repeat(240) { frame ->
                        val started=System.nanoTime()
                        dispatch(RoomAction.Transform(GrabDelta(
                            Point3(.001f,if(frame%2==0) -.0004f else -.0008f,.0003f),Rotation())))
                        times+=System.nanoTime()-started
                        assertEquals(id,vm.currentGame.selectedId)
                        assertEquals(-.25f+(frame+1)*.001f,model.transform.position.x,.00004f)
                        assertEquals((frame+1)*.0003f,model.transform.position.z,.00004f)
                        val down=(frame/2+1)*.0004f+((frame+1)/2)*.0008f
                        assertEquals(floor-down,model.transform.position.y,.00002f)
                    }
                    val sorted=times.sorted()
                    Log.i("TidyFloorFrames","id=$id frames=240 p50us=${sorted[120]/1000} p95us=${sorted[228]/1000} maxus=${sorted.last()/1000}")
                    dispatch(RoomAction.Transform(GrabDelta(Point3(0f,.05f,0f),Rotation())))
                    assertEquals(floor-.144f+.05f,model.transform.position.y,.00002f)
                    dispatch(RoomAction.End)
                    assertNull(vm.currentGame.selectedId)
                    assertEquals(vm.currentGame.item(id).position.vector(),model.transform.position)
                    dispatch(RoomAction.Begin(id,Rotation()))
                    dispatch(RoomAction.Transform(GrabDelta(Point3(.025f,.03f,0f),Rotation())))
                    dispatch(RoomAction.Cancel)
                    assertEquals(vm.currentGame.item(id).position.vector(),model.transform.position)
                }
                Log.i("TidyFloorFrames","PASS six bodies × 240 free XYZ frames across floor; lift/release/cancel; isolated save")
            } finally { scene.destroy() }
        }
    }
    @Test fun rawPointerDownCapturesAllModelBodiesAndRetainsTargetOnMiss(): Unit=runBlocking {
        val i=InstrumentationRegistry.getInstrumentation()
        i.runOnMainSync { i.targetContext.startActivity(Intent(i.targetContext,LaunchActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        delay(2000)
        withContext(Dispatchers.Main) {
            val base=MovingHome.prepare(RoomGame())
            var vm=RoomViewModel(base) {}
            val scene=RoomScene()
            fun dispatch(a: RoomAction) { vm.onEvent(a);scene.applyInput(vm.currentGame) }
            try {
                scene.bindState { vm.currentGame };scene.build(vm.currentGame)
                scene.volume.fittedRoot.transform.scaleVector=Vector3(.6f)
                scene.volume.fittedRoot.transform.position=Vector3(.1f,0f,-.2f)
                val input=scene.input(::dispatch,pose={ event,key ->
                    // Use actual native room transforms, with the documented
                    // view-pixel convention. Test the production identity-pose
                    // fallback instead of supplying a fictitious tracked hand.
                    val (source,space)=scene.volume.pointerSource(event,key)
                    assertEquals(ViewCoordinateSpace.Local,space)
                    assertEquals(event.x,source.x,0f)
                    assertEquals(event.y,source.y,0f)
                    val p=Vector3((source.x-500f)*.0004f,(500f-source.y)*.0004f,source.z*.0004f)
                    val target=if(key=="@room") scene.volume.fittedRoot else scene.volume.roomRoot
                    val native=target.convertPositionFrom(p,scene.volume.owner)
                    HandPose(Point3(native.x,native.y,native.z),Rotation())
                })
                fun event(n: Int,pressed: Boolean,previous: Boolean,target: Entity?): SpatialPointerInfo {
                    val change=PointerInputChange(id=PointerId(1),uptimeMillis=1000L+n*16,
                        position=Offset(500f+n*2f,500f-n),pressed=pressed,
                        previousUptimeMillis=984L+n*16,previousPosition=Offset(500f+(n-1)*2f,501f-n),
                        previousPressed=previous,type=PointerType.Mouse,pressure=1f,
                        isInitiallyConsumed=false,scrollDelta=Offset.Zero)
                    // The documented public constructor references SpacePointerInfo,
                    // supplied by PICO OS but absent from the compile stubs.
                    return SpatialPointerInfo::class.java.constructors.first { it.parameterCount==3 }
                        .newInstance(change,null,target) as SpatialPointerInfo
                }
                for(id in listOf("chair","mug","jelly","mochi","tumbler","musicbox")) {
                    val initial=base.copy(items=base.items.map {
                        it.copy(packed=it.spec.id!=id,support=null,position=Point3(0f,.22f,0f))
                    },boxStock=base.boxStock.mapValues { (_,ids) -> ids.filterNot { it==id } })
                    vm=RoomViewModel(initial) {}
                    scene.synchronize(vm.currentGame)
                    val model=checkNotNull(scene.volume.roomRoot.findEntity(id))
                    fun findMesh(entity: Entity): Entity? =
                        if(entity.components[com.pico.spatial.core.ecs.ModelComponent::class.java]!=null &&
                            scene.nativeTarget(entity)==id) entity
                        else entity.getChildren().firstNotNullOfOrNull(::findMesh)
                    val mesh=checkNotNull(findMesh(model))
                    assertTrue("Model down must enter the same scene input: $id",
                        input.events(listOf(event(0,true,false,mesh))))
                    assertEquals(id,vm.currentGame.selectedId)
                    repeat(40) { frame ->
                        // A ray miss cannot release the selected native body.
                        assertTrue(input.events(listOf(event(frame+1,true,true,null))))
                        assertEquals(id,vm.currentGame.selectedId)
                        assertEquals((frame+1)*.0008f/.6f,model.transform.position.x,.00002f)
                        assertEquals(.22f+(frame+1)*.0004f/.6f,model.transform.position.y,.00002f)
                    }
                    assertTrue(input.events(listOf(event(41,false,true,null))))
                    assertNull(vm.currentGame.selectedId)
                    assertEquals(vm.currentGame.item(id).position.vector(),model.transform.position)
                    val placed=vm.currentGame.items
                    input.events(listOf(event(0,true,false,mesh)))
                    input.events(listOf(event(12,true,true,null)))
                    input.cancel()
                    assertEquals("Lost input rolls back instead of committing",placed,vm.currentGame.items)
                    assertNull(vm.currentGame.selectedId)
                }
                val cleaning=MovingHome.prepare(RoomGame(cleaning=CleaningState()))
                for(tool in CleanTool.entries) {
                    vm=RoomViewModel(cleaning) {}
                    scene.synchronize(vm.currentGame)
                    val model=checkNotNull(scene.volume.roomRoot.findEntity("clean_${tool.name}"))
                    assertTrue(input.events(listOf(event(0,true,false,model))))
                    assertEquals("@clean:${tool.name}",vm.currentGame.selectedId)
                    val before=model.transform.position
                    repeat(10) { frame ->
                        assertTrue(input.events(listOf(event(frame+1,true,true,null))))
                        assertEquals(before.x+(frame+1)*.0008f/.6f,model.transform.position.x,.00002f)
                        assertEquals(before.y+(frame+1)*.0004f/.6f,model.transform.position.y,.00002f)
                    }
                    input.cancel()
                    assertNull(vm.currentGame.selectedId)
                    assertEquals(before,model.transform.position)
                }
                Log.i("TidyRawNative","PASS six furniture/toy bodies × 40 pointer frames; all four cleaning tools × 10 frames; model/mesh down; identity fallback; ray miss retained; release and cancel")
            } finally { scene.destroy() }
        }
    }
    @Test fun repeatedDragsFaceViewerWallPlacementAndCompletionCueStayNative(): Unit=runBlocking {
        val i=InstrumentationRegistry.getInstrumentation()
        i.startActivitySync(Intent(i.targetContext,LaunchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        delay(2000)
        withContext(Dispatchers.Main) {
            val initial=MovingHome.prepare(RoomGame()).let { s ->
                s.copy(items=s.items.map {
                    if(it.spec.id=="chair") it.copy(packed=false,support=null,position=Point3(0f,-.175f,.2f))
                    else it.copy(packed=true,support=null)
                },boxStock=s.boxStock.mapValues { (_,stock)->stock.filterNot { it=="chair" } })
            }
            val vm=RoomViewModel(RoomPersistence.encode(initial))
            val scene=RoomScene()
            fun dispatch(a: RoomAction) { vm.onEvent(a);scene.applyInput(vm.currentGame) }
            try {
                scene.bindState { vm.currentGame }
                scene.build(initial)
                val chair=checkNotNull(scene.volume.roomRoot.findEntity("chair"))
                val cue=node()
                scene.attachCue(cue,.8f)
                assertFalse("Idle attachment must not cover the user",cue.enabled)
                for(yaw in listOf(-37f,108f,42f)) {
                    dispatch(RoomAction.Begin("chair",Rotation.yaw(yaw)))
                    dispatch(RoomAction.Transform(GrabDelta(Point3(0f,.04f,0f),Rotation())))
                    val q=chair.transform.quaternion
                    assertEquals(yaw,Rotation(q.x,q.y,q.z,q.w).yaw(),.001f)
                    assertTrue(cue.enabled)
                    dispatch(RoomAction.End)
                    assertFalse("Release must disable the native cue, not only fade its text",cue.enabled)
                }
                dispatch(RoomAction.Begin("chair",Rotation.yaw(42f)))
                val target=Point3(.45f,-.13f,RoomLayout.back+.20f)
                dispatch(RoomAction.Transform(GrabDelta(target-vm.currentGame.item("chair").position,Rotation())))
                val predicted=RoomUseCase().placement(vm.currentGame)
                assertTrue(predicted.reason,predicted.valid)
                dispatch(RoomAction.End)
                assertEquals(predicted.item!!.position.vector(),chair.transform.position)
                val q=chair.transform.quaternion
                assertEquals(0f,Rotation(q.x,q.y,q.z,q.w).yaw(),.001f)
                assertTrue(RoomPhysics.heldClear(vm.currentGame,setOf("chair")))
                assertFalse(cue.enabled)
                scene.synchronize(vm.currentGame.copy(celebration=vm.currentGame.celebration+1))
                scene.animate(10.0)
                assertFalse("Completion cannot revive the attachment",cue.enabled)
                Log.i("TidyDragNative","PASS repeated viewer-facing ECS drags; legal broad-back wall snap; completion cue disabled")
            } finally { scene.destroy() }
        }
    }
    @Test fun firstLevelToolFramesMoveActualModel(): Unit=runBlocking {
        val i=InstrumentationRegistry.getInstrumentation()
        i.targetContext.startActivity(Intent(i.targetContext,LaunchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        delay(2000)
        withContext(Dispatchers.Main) {
            val initial=MovingHome.prepare(RoomGame(cleaning=CleaningState()))
            var vm=RoomViewModel(RoomPersistence.encode(initial))
            val scene=RoomScene()
            fun dispatch(a: RoomAction) { vm.onEvent(a);scene.applyInput(vm.currentGame) }
            try {
                scene.bindState { vm.currentGame }
                scene.build(initial)
                scene.volume.fittedRoot.transform.scaleVector=Vector3(.4f)
                for(tool in CleanTool.entries) {
                    vm=RoomViewModel(RoomPersistence.encode(initial))
                    scene.synchronize(vm.currentGame)
                    val model=checkNotNull(scene.volume.roomRoot.findEntity("clean_${tool.name}"))
                    assertEquals("@clean:${tool.name}",scene.nativeTarget(model))
                    val gesture=RoomNativeGesture({ dispatch(RoomAction.Begin(it,Rotation.yaw(12f))) },
                        { dispatch(RoomAction.Transform(it)) },{ dispatch(RoomAction.End) },
                        { dispatch(RoomAction.Cancel) })
                    gesture.start()
                    val frame=SpatialTransformValue(NormalizedPoint3D.Center,Offset3D(1f,-1f,0f),1f,
                        Rotation3D.identity(),InteractionKind.GazePinch,InteractionKind.RayBasedPinch,model)
                    repeat(60) { n ->
                        gesture.frame(scene.nativeTarget(model),scene.nativeDelta(frame,.0004f,1))
                        assertEquals(tool.rack.x+(n+1)*.001f,model.transform.position.x,.00001f)
                        assertEquals(vm.currentGame.cleaning!!.position.vector(),scene.inputPosition())
                        val q=model.transform.quaternion
                        assertEquals(12f,Rotation(q.x,q.y,q.z,q.w).yaw(),.001f)
                    }
                    val pivot=model.transform.position
                    gesture.frame(scene.nativeTarget(model),GrabDelta(Point3(.4f,.4f,.4f),Rotation(),pointerCount=2))
                    repeat(20) {
                        gesture.frame(null,GrabDelta(Point3(.3f,.3f,.3f),Rotation.yaw(.2f),
                            spanDelta=.2f,pointerCount=2))
                        assertEquals(pivot,model.transform.position)
                    }
                    val q=model.transform.quaternion
                    assertEquals(12f+4f*RoomNativeGesture.TURN_GAIN,Rotation(q.x,q.y,q.z,q.w).yaw(),.001f)
                    gesture.finish()
                    assertNull(vm.currentGame.selectedId)
                }
                Log.i("TidyDragNative","PASS first-level Vacuum/Broom/Brush: 60 immediate actual-model moves each")
            } finally { scene.destroy() }
        }
    }
    @Test fun continuousFramesImmediatelyMoveActualEntityAndRejectStaleSnapshot(): Unit=runBlocking {
        val i=InstrumentationRegistry.getInstrumentation()
        i.startActivitySync(Intent(i.targetContext,LaunchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        delay(2000)
        withContext(Dispatchers.Main) {
            val start=MovingHome.prepare(RoomGame()).let { s ->
                s.copy(items=s.items.map {
                    if(it.spec.id=="chair") it.copy(packed=false,support=null,position=Point3(0f,-.175f,0f))
                    else it.copy(packed=true,support=null)
                },boxStock=s.boxStock.mapValues { (_,v)->v.filterNot { it=="chair" } })
            }
            val vm=RoomViewModel(RoomPersistence.encode(start))
            val scene=RoomScene()
            fun dispatch(a: RoomAction) { vm.onEvent(a);scene.applyInput(vm.currentGame) }
            val gesture=RoomNativeGesture({ dispatch(RoomAction.Begin(it,Rotation.yaw(42f))) },
                { dispatch(RoomAction.Transform(it)) },{ dispatch(RoomAction.End) },{ dispatch(RoomAction.Cancel) })
            try {
                scene.bindState { vm.currentGame }
                scene.build(vm.currentGame)
                for(window in listOf("back","side")) for(phase in listOf("day","sunset","night")) {
                    assertNull(scene.volume.roomRoot.findEntity("window_${window}_$phase"))
                }
                for(hour in listOf(9f,17f,21f)) scene.synchronize(vm.currentGame.copy(dayHour=hour))
                val balcony=checkNotNull(scene.volume.roomRoot.findEntity("renovation_balcony"))
                val bounds=balcony.getVisualBounds(balcony,true,true)
                assertFalse("The extended balcony must contain native geometry",bounds.isEmpty())
                assertTrue(bounds.min.z<=RoomLayout.back)
                assertTrue(bounds.max.z>=RoomLayout.front)
                assertTrue(bounds.max.z-bounds.min.z>2.43f)
                val chair=checkNotNull(scene.volume.roomRoot.findEntity("chair"))
                assertEquals("Model roots must be movable, not only collision proxies","chair",scene.nativeTarget(chair))
                val mesh=checkNotNull(chair.getChildren().firstOrNull {
                    it.components[com.pico.spatial.core.ecs.ModelComponent::class.java]!=null
                })
                assertEquals("Mesh hits must resolve through their stable model root","chair",scene.nativeTarget(mesh))
                fun hit(e: com.pico.spatial.core.ecs.Entity): com.pico.spatial.core.ecs.Entity? =
                    if(scene.nativeTarget(e)=="chair") e else e.getChildren().firstNotNullOfOrNull(::hit)
                val target=checkNotNull(hit(chair))
                scene.volume.fittedRoot.transform.scaleVector=Vector3(.4f)
                fun sdkFrame(hands: Int,yaw: Float=90f)=SpatialTransformValue(
                    NormalizedPoint3D.Center,Offset3D(2f,-1f,1f),1.8f,
                    Rotation3D(yaw,RotationAxis3D.Y),InteractionKind.GazePinch,
                    InteractionKind.RayBasedPinch,target)
                assertEquals("Available left/right input sources do not imply two pressed hands",
                    1,scene.nativeDelta(sdkFrame(1),.0004f,1).pointerCount)
                val old=vm.currentGame
                gesture.start()
                repeat(90) { index ->
                    val frame=sdkFrame(1)
                    gesture.frame(scene.nativeTarget(frame.targetEntity),scene.nativeDelta(frame,.0004f,1))
                    // No scene.synchronize or Compose frame between these assertions.
                    val pose=chair.transform.position
                    assertEquals((index+1)*.002f,pose.x,.00001f)
                    assertEquals(vm.currentGame.item("chair").position.y,pose.y,.00001f)
                    val q=chair.transform.quaternion
                    assertEquals(42f,Rotation(q.x,q.y,q.z,q.w).yaw(),.001f)
                }
                val current=chair.transform.position
                scene.synchronize(old)
                assertEquals(current,chair.transform.position)
                repeat(2) {
                    val frame=sdkFrame(2,30f)
                    gesture.frame(scene.nativeTarget(frame.targetEntity),scene.nativeDelta(frame,.0004f,2))
                }
                assertEquals(current,chair.transform.position)
                val q=chair.transform.quaternion
                assertEquals(42f+30f*RoomNativeGesture.TURN_GAIN,Rotation(q.x,q.y,q.z,q.w).yaw(),.001f)
                repeat(20) {
                    val frame=sdkFrame(2,.2f)
                    gesture.frame(null,scene.nativeDelta(frame,.0004f,2))
                    assertEquals(current,chair.transform.position)
                }
                val fine=chair.transform.quaternion
                assertEquals(42f+34f*RoomNativeGesture.TURN_GAIN,
                    Rotation(fine.x,fine.y,fine.z,fine.w).yaw(),.001f)
                gesture.finish()
                scene.synchronize(vm.currentGame)
                assertNull(vm.currentGame.selectedId)
                Log.i("TidyDragNative","PASS 90 immediate ECS drag frames; stale snapshot rejected; pair yaw fixed pivot; release; balcony bounds=$bounds")
            } finally { gesture.cancel();scene.destroy() }
        }
    }
    @Test fun extractingPreviewPreservesCapturedNativeEntity(): Unit=runBlocking {
        withContext(Dispatchers.Main) {
            val initial=MovingHome.prepare(RoomGame(),fresh=true).let {
                it.copy(boxStock=it.boxStock+("box-home" to listOf("mug")))
            }
            val vm=RoomViewModel(RoomPersistence.encode(initial))
            val parent=node()
            val targets=mutableMapOf<com.pico.spatial.core.ecs.Entity,String>()
            val furniture=RoomFurniture(targets)
            try {
                furniture.load(parent,vm.currentGame.items.filter { !it.packed })
                vm.onEvent(RoomAction.Add("mug"));furniture.inventory(vm.currentGame)
                val hit=targets.entries.first {
                    it.value=="@unpack:box-home" &&
                        it.key.components[com.pico.spatial.core.ecs.CollisionComponent::class.java]!=null
                }.key
                val preview=hit.getParent()
                assertEquals("The preview root and its collision child must both resolve",
                    "@unpack:box-home",targets[preview])
                vm.onEvent(RoomAction.Begin("@unpack:box-home"))
                val promoted=furniture.promotePreview(vm.currentGame.selected!!)
                assertEquals("The captured preview must not be destroyed/reloaded",preview,promoted)
                assertEquals("mug",targets[hit])
                val reconciled=furniture.reconcile(vm.currentGame.items.filter { !it.packed })
                assertEquals(promoted,reconciled["mug"])
                furniture.inventory(vm.currentGame)
                assertFalse(targets.containsValue("@unpack:box-home"))
                furniture.finishPromotion()
                assertFalse(targets.containsKey(hit))
                assertTrue(targets.containsValue("mug"))
                Log.i("TidyDragNative","PASS captured preview promoted in place; collider retained until release")
            } finally { furniture.close();parent.destroy(true) }
        }
    }
}
