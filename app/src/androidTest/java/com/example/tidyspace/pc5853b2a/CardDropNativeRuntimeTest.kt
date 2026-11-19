package com.example.tidyspace.pc5853b2a

import android.content.Intent
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.tidyspace.pc5853b2a.platform.LaunchActivity
import com.example.tidyspace.pc5853b2a.domain.model.*
import com.example.tidyspace.pc5853b2a.domain.usecase.*
import com.example.tidyspace.pc5853b2a.ui.sorting.RoomViewModel
import com.example.tidyspace.pc5853b2a.ui.sorting.scene.*
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.math.Vector3
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real runtime geometry with isolated state; never persists fixtures to the user's prefs. */
@RunWith(AndroidJUnit4::class)
class CardDropNativeRuntimeTest {
    @Test fun placedModelsMatchCardFreePathAcrossRoomSurfaces(): Unit=runBlocking {
        start();delay(2000)
        withContext(Dispatchers.Main) {
            val initial=MovingHome.packAll(RoomGame()).let { s ->
                s.copy(items=s.items.map { it.copy(packed=true,support=null) },guideVisible=false) }
            var vm=RoomViewModel(initial) {}
            val scene=RoomScene();val panel=node()
            fun send(a: RoomAction) { vm.onEvent(a);scene.applyInput(vm.currentGame) }
            try {
                scene.bindState { vm.currentGame };scene.build(vm.currentGame)
                scene.attachCatalogue(panel,1.44f);scene.catalogueLayout(1440f,302f)
                for(id in listOf("chair","desk","mug","jelly","mochi","tumbler"))
                    for(yaw in listOf(0f,60f)) for(fit in listOf(.6f,1.2f)) {
                    vm=RoomViewModel(initial.copy(roomYaw=yaw)) {}
                    scene.synchronize(vm.currentGame)
                    scene.volume.fittedRoot.transform.scaleVector=Vector3(fit)
                    scene.volume.fittedRoot.transform.position=Vector3(.12f,.08f,-.17f)
                    val spec=vm.currentGame.item(id).spec
                    scene.preloadCard(spec)
                    send(RoomAction.CardBegin(id,1,Rotation()))
                    val path=listOf(Point3(0f,.3f,.1f),Point3(-1.7f,.55f,-1.4f),
                        Point3(.5f,-.65f,.3f),Point3(1.5f,.9f,.6f),Point3(.2f,.6f,.35f))
                    val cardPoses=path.map { desired ->
                        val owner=scene.volume.owner.convertPositionFrom(desired.vector(),scene.volume.roomRoot)
                        val offset=maxOf(spec.size.x,spec.size.z)*fit/2+.055f
                        val local=panel.convertPositionFrom(owner-Vector3(0f,0f,offset),scene.volume.owner)
                        val pixels=Point3((local.x+.72f)/.001f,(.151f-local.y)/.001f,local.z/.001f)
                        val point=checkNotNull(scene.cardPoint(pixels,.001f))
                        send(RoomAction.CardMove(1,point))
                        val model=checkNotNull(scene.volume.roomRoot.findEntity("@card-model:$id"))
                        val actual=model.transform.position
                        assertTrue((Point3(actual.x,actual.y,actual.z)-point).length()<.0001f)
                        point
                    }
                    send(RoomAction.CardCancel(1))
                    val placed=initial.copy(roomYaw=yaw,items=initial.items.map {
                        if(it.spec.id==id) it.copy(packed=false,support=null,position=path.first(),rotation=Rotation()) else it
                    },boxStock=initial.boxStock.mapValues { (_,ids)->ids.filterNot { it==id } })
                    vm=RoomViewModel(placed) {}
                    scene.synchronize(vm.currentGame)
                    send(RoomAction.Begin(id,Rotation()))
                    val model=checkNotNull(scene.volume.roomRoot.findEntity(id))
                    for((index,desired) in path.withIndex()) {
                        send(scene.dragFrame(GrabDelta(desired-vm.currentGame.item(id).position,Rotation())))
                        val actual=model.transform.position
                        assertEquals("$id yaw=$yaw fit=$fit x",cardPoses[index].x,actual.x,.0002f)
                        assertEquals("$id yaw=$yaw fit=$fit y",cardPoses[index].y,actual.y,.0002f)
                        assertEquals("$id yaw=$yaw fit=$fit z",cardPoses[index].z,actual.z,.0002f)
                    }
                    send(RoomAction.End)
                    assertNull(vm.currentGame.selectedId)
                    assertTrue(RoomPhysics.supported(vm.currentGame,vm.currentGame.item(id)))
                    val saved=vm.currentGame.items
                    send(RoomAction.Begin(id,Rotation()))
                    send(scene.dragFrame(GrabDelta(Point3(-1f,-.6f,-1f),Rotation())))
                    send(RoomAction.Cancel)
                    assertEquals(saved,vm.currentGame.items)
                    Log.i("TidyUnifiedDrag","PASS id=$id fit=$fit yaw=$yaw card/placed XYZ identical; release supported; cancel restored")
                }
            } finally { scene.destroy() }
        }
    }

    private fun start() {
        val i=InstrumentationRegistry.getInstrumentation()
        i.runOnMainSync { i.targetContext.startActivity(Intent(i.targetContext,LaunchActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
    @Test fun realModelsAppearOnSelectionFollowDragAndPromoteOnRelease(): Unit=runBlocking {
        start();delay(2000)
        withContext(Dispatchers.Main) {
            val initial=MovingHome.packAll(RoomGame()).let { s -> s.copy(
                items=s.items.map { it.copy(packed=true,support=null) },guideVisible=false) }
            val vm=RoomViewModel(initial) {}
            val scene=RoomScene()
            fun send(a: RoomAction) { vm.onEvent(a);scene.applyInput(vm.currentGame) }
            try {
                scene.bindState { vm.currentGame };scene.build(vm.currentGame)
                for(id in listOf("chair","jelly","mug")) {
                    val stock=vm.currentGame.boxStock
                    val serial=vm.currentGame.nextSerial
                    assertNull(scene.volume.roomRoot.findEntity(id))
                    scene.preloadCard(vm.currentGame.item(id).spec)
                    val token=vm.currentGame.cardSequence+1
                    send(RoomAction.CardBegin(id,token,Rotation.yaw(18f)))
                    val held=checkNotNull(scene.volume.roomRoot.findEntity("@card-model:$id"))
                    assertTrue("Selection immediately exposes the actual mesh",held.enabled)
                    assertNull("Inventory stays packed until a valid release",scene.volume.roomRoot.findEntity(id))
                    send(RoomAction.CardMove(token,Point3(if(id=="chair") -.45f else .35f,.35f,if(id=="mug") .45f else 0f)))
                    scene.synchronize(vm.currentGame)
                    assertSame(held,scene.volume.roomRoot.findEntity("@card-model:$id"))
                    assertTrue(held.getChildren().isNotEmpty())
                    val preview=checkNotNull(RoomCardDrop.preview(vm.currentGame)?.item)
                    assertEquals(vm.currentGame.cardDrag!!.point!!.vector(),held.transform.position)
                    assertTrue("Held furniture must stay above its predicted support",
                        held.transform.position.y>preview.position.y+.10f)
                    assertEquals(stock,vm.currentGame.boxStock);assertEquals(serial,vm.currentGame.nextSerial)
                    send(RoomAction.CardRelease(token));scene.synchronize(vm.currentGame)
                    val model=checkNotNull(scene.volume.roomRoot.findEntity(id))
                    assertSame("Release preserves the held mesh",held,model)
                    assertTrue(model.enabled)
                    assertNull(scene.volume.roomRoot.findEntity("@card-model:$id"))
                    assertEquals(vm.currentGame.item(id).position.vector(),model.transform.position)
                    assertEquals(id,scene.nativeTarget(model))
                    val q=model.transform.quaternion
                    assertEquals(18f,Rotation(q.x,q.y,q.z,q.w).yaw(),.01f)
                    send(RoomAction.CardRelease(token))
                    assertEquals(serial+1,vm.currentGame.nextSerial)
                    send(RoomAction.Undo)
                    assertFalse("Undo hides without waiting for async sync",model.enabled)
                    assertEquals(stock,vm.currentGame.boxStock)
                    scene.synchronize(vm.currentGame)
                    assertNull(scene.volume.roomRoot.findEntity(id))
                    send(RoomAction.Redo);scene.synchronize(vm.currentGame)
                    assertTrue(checkNotNull(scene.volume.roomRoot.findEntity(id)).enabled)
                }
                val stock=vm.currentGame.boxStock
                val token=vm.currentGame.cardSequence+1
                scene.preloadCard(vm.currentGame.item("plant").spec)
                send(RoomAction.CardBegin("plant",token,Rotation()))
                send(RoomAction.CardMove(token,Point3(9f,.35f,0f)))
                assertNotNull(scene.volume.roomRoot.findEntity("@card-model:plant"))
                send(RoomAction.CardCancel(token))
                assertNull(scene.volume.roomRoot.findEntity("@card-model:plant"))
                assertEquals(stock,vm.currentGame.boxStock)
                assertNull(scene.volume.roomRoot.findEntity("plant"))
                // A late source load cannot resurrect a cancelled preview.
                send(RoomAction.CardBegin("plant",token+1,Rotation()))
                send(RoomAction.CardCancel(token+1))
                scene.preloadCard(vm.currentGame.item("plant").spec)
                assertNull(scene.volume.roomRoot.findEntity("@card-model:plant"))
                Log.i("TidyCardNative","PASS immediate actual meshes, continuous poses, same-entity release, caption facing, hits, cancel, late load, undo and redo")
            } finally { scene.destroy() }
        }
    }
    @Test fun cardPixelsFollowActualPanelFitAndRotatedRoomCoordinates(): Unit=runBlocking {
        start();delay(2000)
        withContext(Dispatchers.Main) {
            val initial=MovingHome.packAll(RoomGame()).copy(guideVisible=false)
            var game=initial
            val scene=RoomScene();val panel=node()
            try {
                scene.bindState { game };scene.build(game)
                scene.attachCatalogue(panel,1.44f)
                assertTrue(panel.enabled)
                game=game.copy(controlsVisible=false);scene.applyInput(game)
                assertFalse("Hiding controls disables the actual in-window entity",panel.enabled)
                game=game.copy(controlsVisible=true);scene.applyInput(game)
                assertTrue(panel.enabled)
                for(h in listOf(Vector3(3f,2.25f,3f),Vector3(1.08f))) {
                    SharedVolume::class.java.getDeclaredField("half").apply { isAccessible=true }
                        .set(scene.volume,h)
                    scene.attachCatalogue(panel,1.44f)
                    for(fit in listOf(.75f,1.3f)) for(yaw in listOf(0f,60f,-120f)) {
                    game=game.copy(roomYaw=yaw)
                    scene.applyInput(game)
                    scene.volume.fittedRoot.transform.scaleVector=Vector3(fit)
                    scene.volume.fittedRoot.transform.position=Vector3(.17f,.05f,-.3f)
                    val token=game.cardSequence+1
                    game=RoomCardDrop.begin(game,"mug",token,Rotation.yaw(-yaw))
                    scene.applyInput(game)
                    assertEquals("The scenario exercises its declared fit",fit,
                        scene.volume.fittedRoot.transform.scaleVector.x,.001f)
                    assertNull("Release back in tray is never floor placement",
                        scene.cardPoint(Point3(720f,150f,0f),.001f))
                    // Free XYZ must pass through the actual panel and rotated
                    // room transforms without falling onto the floor.
                    val target=Vector3(0f,.35f,0f)
                    val targetInOwner=scene.volume.owner.convertPositionFrom(target,scene.volume.roomRoot)
                    val panelCenter=scene.volume.owner.convertPositionFrom(Vector3.ZERO,panel)
                    val spec=game.item("mug").spec
                    val offset=maxOf(spec.size.x,spec.size.z)*fit/2+.055f
                    val through=targetInOwner-Vector3(0f,0f,offset)
                    val local=panel.convertPositionFrom(through,scene.volume.owner)
                    // A changed compositor pixel density must not change the landing.
                    val pixelWidth=if(yaw==60f) 2160f else 1440f
                    scene.catalogueLayout(pixelWidth,pixelWidth*302f/1440f)
                    val pixelLength=1.44f/pixelWidth
                    val pixels=Point3((local.x+.72f)/pixelLength,
                        (1.44f*302f/1440f/2-local.y)/pixelLength,local.z/pixelLength)
                    val heldPoint=checkNotNull(scene.cardPoint(pixels,.001f))
                    assertEquals(0f,heldPoint.x,.001f);assertEquals(0f,heldPoint.z,.001f)
                    assertEquals(.35f,heldPoint.y,.001f)
                    assertNull(scene.cardPoint(Point3(pixelWidth/2,pixelWidth*150f/1440f,0f),.001f))
                    scene.preloadCard(game.item("mug").spec)
                    scene.applyInput(game)
                    val held=checkNotNull(scene.volume.roomRoot.findEntity("@card-model:mug"))
                    val heldInOwner=scene.volume.owner.convertPositionFrom(held.transform.position,scene.volume.roomRoot)
                    assertTrue(held.enabled)
                    assertTrue("Held mesh stays in front of the actual catalogue: held=$heldInOwner panel=$panelCenter",
                        heldInOwner.z>panelCenter.z)
                    assertTrue("Mesh clears the panel along depth",heldInOwner.z>panelCenter.z+.04f)
                    game=RoomCardDrop.cancel(game,token)
                    scene.applyInput(game)
                    Log.i("TidyCardNative","PIXEL_PASS half=$h fit=$fit yaw=$yaw pixels=$pixels free=$heldPoint")
                    }
                }
            } finally { scene.destroy() }
        }
    }
}
