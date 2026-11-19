package com.example.tidyspace.pc5853b2a

import android.content.Intent
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.tidyspace.pc5853b2a.data.repository.RoomPersistence
import com.example.tidyspace.pc5853b2a.domain.model.*
import com.example.tidyspace.pc5853b2a.domain.usecase.MovingHome
import com.example.tidyspace.pc5853b2a.platform.LaunchActivity
import com.example.tidyspace.pc5853b2a.ui.sorting.RoomViewModel
import com.example.tidyspace.pc5853b2a.ui.sorting.scene.*
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.foundation.gesture.SpatialTransformValue
import com.pico.spatial.ui.foundation.gesture.data.InteractionKind
import com.pico.spatial.ui.foundation.geometry.NormalizedPoint3D
import com.pico.spatial.ui.foundation.geometry.Rotation3D
import com.pico.spatial.ui.geometry.Offset3D
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Public SDK frames aimed at actual body meshes, with isolated save-free VMs. */
@RunWith(AndroidJUnit4::class)
class CleaningPlayNativeRuntimeTest {
    private fun start() {
        val i=InstrumentationRegistry.getInstrumentation()
        Log.i("TidyCleanPlayNative","stage=launch")
        i.targetContext.startActivity(Intent(i.targetContext,LaunchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        Log.i("TidyCleanPlayNative","stage=launched")
    }
    private fun nodes(e: Entity): List<Entity> = listOf(e)+e.getChildren().flatMap(::nodes)
    @Test fun jellyMochiAndTumblerBodyMeshesMoveAndKeepTheirPlay(): Unit=runBlocking {
        start();delay(8000)
        withContext(Dispatchers.Main) {
                val ids=listOf("jelly","mochi","tumbler")
                val initial=MovingHome.prepare(RoomGame()).let { s->s.copy(
                    roomYaw=35f,items=s.items.map { item->if(item.spec.id in ids)
                        item.copy(packed=false,support=null,position=Point3((ids.indexOf(item.spec.id)-1)*.42f,.18f,.2f),rotation=Rotation.yaw(110f))
                        else item.copy(packed=true,support=null) },
                    boxStock=s.boxStock.mapValues { (_,stock)->stock.filterNot { it in ids } }) }
                val vm=RoomViewModel(RoomPersistence.encode(initial))
                val scene=RoomScene()
                fun send(a: RoomAction) { vm.onEvent(a);scene.applyInput(vm.currentGame) }
                val grab=RoomNativeGesture({ send(RoomAction.Begin(it,scene.viewerFacing(it))) },
                    { send(scene.dragFrame(it)) },{ send(RoomAction.End) },{ send(RoomAction.Cancel) },
                    captureImmediately=scene::bodyPlay,allowCompression=scene::softBody)
                try {
                    Log.i("TidyCleanPlayNative","stage=build")
                    scene.bindState { vm.currentGame };scene.build(vm.currentGame)
                    Log.i("TidyCleanPlayNative","stage=built")
                    scene.volume.fittedRoot.transform.scaleVector=Vector3(.4f)
                    for(id in ids) {
                    val body=checkNotNull(scene.volume.roomRoot.findEntity(id))
                    val mesh=nodes(body).first { it.components[ModelComponent::class.java]!=null &&
                        scene.resolveTarget(it)=="@play:$id" }
                    assertEquals("Visible BODY routes to native movement",id,scene.nativeTarget(mesh))
                    val original=vm.currentGame.item(id)
                    fun frame(dx: Float=0f,dy: Float=0f,scale: Float=1f,hands: Int=1) {
                        val sdk=SpatialTransformValue(NormalizedPoint3D.Center,Offset3D(dx,dy,0f),scale,
                            Rotation3D.identity(),InteractionKind.GazePinch,InteractionKind.RayBasedPinch,mesh)
                        grab.frame(scene.nativeTarget(mesh),scene.nativeDelta(sdk,.0004f,hands),scene.resolveTarget(mesh))
                    }
                    grab.start();frame()
                    assertEquals(id,vm.currentGame.selectedId)
                    scene.animate(1.0)
                    if(id!="tumbler") {
                        frame(scale=.8f,hands=2) // contact transition baseline
                        repeat(12) { frame(dx=300f,dy=-300f,scale=.92f,hands=2) }
                        assertEquals(original.position,vm.currentGame.item(id).position)
                        assertTrue(vm.currentGame.toyGesture.compression>0f)
                        scene.animate(1.10)
                        val soft=checkNotNull(body.findEntity("${id}_soft"))
                        assertTrue("Native soft CHILD compresses",soft.transform.scaleVector.y<.98f)
                        assertEquals(Vector3(1f),body.transform.scaleVector)
                    } else {
                        frame(dx=12f)
                        scene.animate(1.10)
                        val tilted=checkNotNull(body.findEntity("tumbler_body")).transform.quaternion
                        assertTrue(kotlin.math.abs(tilted.z)>.001f)
                    }
                    grab.finish()
                    assertEquals("Short play restores whole-body pose",original,vm.currentGame.item(id))
                    assertTrue((vm.currentGame.playCounts[id] ?: 0)>0)
                    scene.animate(1.2);scene.animate(5.0)
                    val playCount=vm.currentGame.playCounts[id]
                    grab.start()
                    repeat(60) { n ->
                        frame(dx=3f,dy=-2f)
                        val expected=original.position+Rotation.yaw(-35f).rotate(Point3((n+1)*.003f,(n+1)*.002f,0f))
                        assertTrue("Actual body moves immediately on frame $n: $id",
                            (Point3(body.transform.position.x,body.transform.position.y,body.transform.position.z)-expected).length()<.0001f)
                        val q=body.transform.quaternion
                        assertEquals(-35f,Rotation(q.x,q.y,q.z,q.w).yaw(),.001f)
                    }
                    val pivot=body.transform.position
                    frame(dx=500f,dy=-500f,scale=.8f,hands=2)
                    repeat(10) { frame(dx=500f,dy=-500f,scale=.95f,hands=2) }
                    assertEquals(pivot,body.transform.position)
                    frame(dx=500f,dy=-500f,hands=1)
                    assertEquals("Releasing one hand cannot jump",pivot,body.transform.position)
                    grab.finish()
                    assertNull(vm.currentGame.selectedId)
                    assertEquals(playCount,vm.currentGame.playCounts[id])
                    assertNotEquals(original.position,vm.currentGame.item(id).position)
                    val released=vm.currentGame.items
                    grab.start();frame(dx=10f,dy=-10f);grab.cancel()
                    assertEquals(released,vm.currentGame.items)
                    val smallOrigin=body.transform.position
                    grab.start();frame(dx=30f);grab.finish()
                    val smallShift=Rotation.yaw(-35f).rotate(Point3(.03f,0f,0f))
                    assertEquals("Small body placement must remain placed: $id",
                        smallOrigin.x+smallShift.x,body.transform.position.x,.0001f)
                    assertEquals(smallOrigin.z+smallShift.z,body.transform.position.z,.0001f)
                    assertEquals(playCount,vm.currentGame.playCounts[id])
                    Log.i("TidyCleanPlayNative","BODY_PASS $id actual @play body mesh→native SDK→60 immediate moves; play+pair+caption+release+cancel+3cm placement")
                    }
                } finally { grab.cancel();scene.destroy() }
        }
    }
}
