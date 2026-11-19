package com.example.tidyspace.pc5853b2a

import android.content.Intent
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.tidyspace.pc5853b2a.data.repository.RoomPersistence
import com.example.tidyspace.pc5853b2a.domain.model.*
import com.example.tidyspace.pc5853b2a.domain.usecase.*
import com.example.tidyspace.pc5853b2a.platform.LaunchActivity
import com.example.tidyspace.pc5853b2a.ui.sorting.RoomViewModel
import com.example.tidyspace.pc5853b2a.ui.sorting.scene.*
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.math.Vector3
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.hypot

/** Isolated native models and VMs. No test fixture is written to user preferences. */
@RunWith(AndroidJUnit4::class)
class CartonPlacementNativeRuntimeTest {
    private fun start() {
        val i=InstrumentationRegistry.getInstrumentation()
        i.runOnMainSync {
            i.targetContext.startActivity(Intent(i.targetContext,LaunchActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
    private fun rotation(e: Entity)=e.transform.quaternion.let { Rotation(it.x,it.y,it.z,it.w) }
    private fun privateEntity(scene: RoomScene,name: String)=RoomScene::class.java
        .getDeclaredField(name).apply { isAccessible=true }.get(scene) as Entity
    private fun nodes(e: Entity): List<Entity> = listOf(e)+e.getChildren().flatMap(::nodes)

    @Test fun placementRingsHideImmediatelyOnReleaseCancelAndHiddenTargets(): Unit=runBlocking {
        start();delay(2000)
        withContext(Dispatchers.Main) {
            val initial=RoomGame().let { game -> game.copy(items=game.items.map { item ->
                if(item.spec.id=="chair") item.copy(position=Point3(0f,.3f,.2f),support=null,packed=false)
                else item.copy(support=null,packed=true) },guideVisible=true,achievements=setOf("guide-1")) }
            val vm=RoomViewModel(RoomPersistence.encode(initial))
            val scene=RoomScene()
            fun dispatch(a: RoomAction) { vm.onEvent(a);scene.applyInput(vm.currentGame) }
            try {
                scene.bindState { vm.currentGame };scene.build(initial)
                assertNotNull("The idle guide must have a destination to reproduce the reported bug",
                    RoomProgress.current(vm.currentGame)?.destination)
                val green=privateEntity(scene,"destination")
                val red=privateEntity(scene,"invalid")
                assertFalse("No idle guide destination",green.enabled);assertFalse(red.enabled)
                dispatch(RoomAction.Begin("chair"))
                assertTrue(green.enabled);assertFalse(red.enabled)
                dispatch(RoomAction.Transform(GrabDelta(Point3(.025f,.02f,0f),Rotation())))
                val cue=checkNotNull(RoomPlacementCue.resolve(vm.currentGame))
                assertEquals(cue.point.x,green.transform.position.x,.0001f)
                assertEquals(cue.point.z,green.transform.position.z,.0001f)
                dispatch(RoomAction.End)
                assertFalse("Release hides without synchronize",green.enabled);assertFalse(red.enabled)
                dispatch(RoomAction.Begin("chair"));assertTrue(green.enabled)
                dispatch(RoomAction.Cancel)
                assertFalse("Cancel hides without synchronize",green.enabled);assertFalse(red.enabled)
                // Invalid placement must use only the red marker and disappear on cancel.
                val obstructed=initial.copy(items=initial.items.map { it.copy(position=Point3(10f,.3f,.2f)) },
                    selectedId="chair",origin=initial.items)
                scene.applyInput(obstructed)
                assertTrue("Out-of-room drop shows red",red.enabled);assertFalse(green.enabled)
                for(hidden in listOf(initial.copy(selectedId="@room"),
                    initial.copy(selectedId="@play:chair"),
                    initial.copy(selectedId="chair",items=initial.items.map { it.copy(packed=true) }),
                    initial.copy(cleaning=CleaningState(),selectedId="chair"))) {
                    scene.applyInput(hidden)
                    assertFalse(green.enabled);assertFalse(red.enabled)
                }
                Log.i("TidyCartonNative","PASS native drop rings: idle, held, immediate motion, release, cancel, blocked, hidden")
            } finally { scene.destroy() }
        }
    }

    @Test fun nativeCartonsFaceCaptionCenterAndPreviewExtractsWithoutYawJump(): Unit=runBlocking {
        start();delay(2000)
        withContext(Dispatchers.Main) {
            val initial=MovingHome.prepare(RoomGame()).let { game ->
                val boxes=movingBoxes().mapIndexed { index,spec ->
                    RoomItem(spec,Point3(if(index%2==0) -.35f else .35f,-.10f,
                        if(index<2) -.35f else .35f),rotation=Rotation.yaw(143f),packed=false)
                }
                game.copy(items=game.items.filter { it.spec.kind!=Furnishing.MovingBox }+boxes)
            }
            var vm=RoomViewModel(RoomPersistence.encode(initial))
            val scene=RoomScene()
            fun dispatch(a: RoomAction) {
                vm.onEvent(RoomAction.SyncCartonFacing(scene.cartonFacing()))
                vm.onEvent(a);scene.applyInput(vm.currentGame)
            }
            try {
                scene.bindState { vm.currentGame };scene.build(initial)
                // Isolated scene fixture uses the manifest's 6x4.5x6m window,
                // with a nontrivial fit, so native owner→room conversion is exercised.
                SharedVolume::class.java.getDeclaredField("half").apply { isAccessible=true }
                    .set(scene.volume,Vector3(3f,2.25f,3f))
                scene.volume.fittedRoot.transform.scaleVector=Vector3(1.3f)
                scene.volume.fittedRoot.transform.position=Vector3(.17f,.05f,-.3f)
                for(yaw in listOf(0f,68f,-125f)) {
                    vm=RoomViewModel(RoomPersistence.encode(initial.copy(roomYaw=yaw)))
                    scene.applyInput(vm.currentGame)
                    val reference=checkNotNull(scene.volume.captionPosition())
                    val angles=mutableListOf<Float>()
                    for(spec in movingBoxes()) {
                        val entity=checkNotNull(scene.volume.roomRoot.findEntity(spec.assetId))
                        val item=vm.currentGame.item(spec.id)
                        val toward=reference-item.position
                        val length=hypot(toward.x,toward.z)
                        val front=rotation(entity).rotate(Point3(0f,0f,1f))
                        assertEquals("Actual +Z toward caption center ${spec.id}",toward.x/length,front.x,.001f)
                        assertEquals(toward.z/length,front.z,.001f)
                        assertEquals(0f,front.y,.001f)
                        assertEquals(1f,rotation(entity).rotate(Point3(0f,1f,0f)).y,.001f)
                        assertTrue("Body hit follows the real root",nodes(entity).any {
                            scene.nativeTarget(it)==spec.id && it.components[CollisionComponent::class.java]!=null })
                        angles+=rotation(entity).yaw()
                        Log.i("TidyCartonNative","CAPTION_PASS id=${spec.id} room-yaw=$yaw box-yaw=${rotation(entity).yaw()} reference=$reference")
                    }
                    assertTrue("Off-center cartons face independently",angles.maxOrNull()!!-angles.minOrNull()!!>2f)
                    dispatch(RoomAction.Add(vm.currentGame.stock("box-toys").first()))
                    scene.synchronize(vm.currentGame)
                    val box=checkNotNull(scene.volume.roomRoot.findEntity("movingbox-toys"))
                    val preview=scene.volume.roomRoot.getChildren().first { scene.nativeTarget(it)=="@unpack:box-toys" }
                    assertEquals(rotation(box).yaw(),rotation(preview).yaw(),.001f)
                    val before=vm.currentGame.items
                    dispatch(RoomAction.Begin("@unpack:box-toys",scene.viewerFacing("@unpack:box-toys")))
                    assertEquals(rotation(box).yaw(),checkNotNull(vm.currentGame.selected).rotation.yaw(),.001f)
                    assertEquals(rotation(box).yaw(),rotation(preview).yaw(),.001f)
                    assertEquals(vm.currentGame.selectedId,scene.nativeTarget(preview))
                    dispatch(RoomAction.Cancel)
                    assertEquals(before,vm.currentGame.items)
                    dispatch(RoomAction.Begin("box-toys",scene.viewerFacing("box-toys")))
                    val held=rotation(box).yaw()
                    dispatch(RoomAction.Transform(GrabDelta(Point3(0f,0f,0f),Rotation.yaw(17f),pointerCount=2)))
                    assertEquals(17f,(rotation(box)*Rotation.yaw(held).inverse()).yaw(),.01f)
                    dispatch(RoomAction.Cancel)
                    assertEquals(scene.viewerFacing("box-toys")!!.yaw(),rotation(box).yaw(),.001f)
                }
                Log.i("TidyCartonNative","PASS all four native carton faces at 3 room yaws, fit conversion, upright +Z, body hits, preview, extraction, cancel and pair rotation")
            } finally { scene.destroy() }
        }
    }
}
