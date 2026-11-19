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
import com.pico.spatial.core.ecs.PointLightComponent
import com.pico.spatial.core.ecs.DirectionalLightComponent
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Isolated VM and detached native Entities; never clears or edits the user's save. */
@RunWith(AndroidJUnit4::class)
class MovingHomeNativeRuntimeTest {
    @Test fun sceneReattachmentDoesNotDuplicateHouseLightsOrHitRegions(): Unit=runBlocking {
        val i=InstrumentationRegistry.getInstrumentation()
        i.startActivitySync(Intent(i.targetContext,LaunchActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        delay(2000)
        withContext(Dispatchers.Main) {
            val scene=RoomScene()
            try {
                val game=MovingHome.prepare(RoomGame(),fresh=true)
                scene.build(game)
                fun nodes(e: Entity): Int=1+e.getChildren().sumOf(::nodes)
                val count=nodes(scene.volume.roomRoot)
                scene.build(game)
                assertEquals("Reattachment must reuse the existing native house",count,nodes(scene.volume.roomRoot))
                Log.i("TidyMovingNative","PASS scene-reattach stable-nodes=$count")
            } finally { scene.destroy() }
        }
    }
    @Test fun unpackPreviewEmptyBoxAndActualLights(): Unit = runBlocking {
        val i=InstrumentationRegistry.getInstrumentation()
        i.startActivitySync(Intent(i.targetContext,LaunchActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        delay(5000)
        withContext(Dispatchers.Main) {
            val fresh=MovingHome.prepare(RoomGame(),fresh=true).let {
                it.copy(boxStock=it.boxStock+("box-home" to listOf("mug")))
            }
            val vm=RoomViewModel(RoomPersistence.encode(fresh))
            fun game()=vm.state.value.game
            fun act(a: RoomAction)=vm.onEvent(a)
            val targets=mutableMapOf<Entity,String>()
            val parent=node()
            val furniture=RoomFurniture(targets)
            val building=RoomRenovation(targets)
            val resources=SceneResources()
            try {
                building.build(parent,resources)
                val models=furniture.load(parent,game().items.filter { !it.packed })
                assertEquals(4,models.size)
                assertFalse(targets.containsValue("desk"))
                assertFalse(targets.containsValue("sofa"))
                movingBoxes().forEach {
                    assertTrue(targets.containsValue(it.id))
                    assertNotNull(furniture.parts["${it.id}:fill"])
                }
                act(RoomAction.Add("mug"));furniture.inventory(game())
                assertTrue(targets.containsValue("@unpack:box-home"))
                act(RoomAction.Begin("@unpack:box-home"))
                furniture.reconcile(game().items.filter { !it.packed });furniture.inventory(game())
                assertFalse(targets.containsValue("@unpack:box-home"))
                act(RoomAction.Cancel);furniture.reconcile(game().items.filter { !it.packed });furniture.inventory(game())
                assertTrue(targets.containsValue("@unpack:box-home"))
                assertEquals(listOf("mug"),game().stock("box-home"))
                act(RoomAction.Begin("@unpack:box-home"))
                act(RoomAction.Transform(GrabDelta(Point3(.10f,.20f,.35f)-game().selected!!.position,Rotation())))
                act(RoomAction.End)
                furniture.reconcile(game().items.filter { !it.packed });furniture.inventory(game())
                assertTrue(game().stock("box-home").isEmpty())
                assertFalse(models.getValue("box-home").enabled)
                assertFalse(targets.containsValue("@unpack:box-home"))
                act(RoomAction.Undo);furniture.reconcile(game().items.filter { !it.packed });furniture.inventory(game())
                assertTrue(models.getValue("box-home").enabled)
                assertEquals(listOf("mug"),game().stock("box-home"))

                // New goods all load through the same catalogue and receive independent targets.
                val homeItems=homeCatalogue().map(::RoomItem)
                val all=furniture.reconcile((game().items.filter { !it.packed }+homeItems+RoomGame().item("sofa")).distinctBy { it.spec.id })
                homeItems.forEach { item ->
                    val root=all.getValue(item.spec.id)
                    assertFalse(item.spec.id,root.getVisualBounds(root,true,true).isEmpty())
                    assertTrue(targets.containsValue(item.spec.id))
                }
                fun descendants(e: Entity): List<Entity> = listOf(e)+e.getChildren().flatMap(::descendants)
                val sun=descendants(parent).first {
                    it.components[DirectionalLightComponent::class.java]?.castsShadowEnabled==true
                }.components[DirectionalLightComponent::class.java]!!
                building.mood(RoomMood.Morning);val day=sun.intensity
                building.mood(RoomMood.Evening);assertTrue(sun.intensity<day/30f)
                building.synchronize(game().copy(indoorBrightness=0f))
                val points=descendants(parent).mapNotNull { it.components[PointLightComponent::class.java] }
                assertTrue(points.any { it.intensity==0f })
                val fire=checkNotNull(parent.findEntity("hearth_flame_0"))
                building.animate(1.0);val first=fire.transform.scaleVector.y
                building.animate(1.4);assertNotEquals(first,fire.transform.scaleVector.y)
                assertTrue(points.any { it.intensity in 2.7f..3.7f })
                val entry=checkNotNull(parent.findEntity("entryway"))
                val door=checkNotNull(entry.findEntity("Entry open door"))
                assertEquals(1.074f*RoomLayout.scale,door.transform.position.x,.0001f)
                assertEquals(.002f,door.transform.position.y,.0001f)
                assertEquals(.909f*RoomLayout.scale+.168f,door.transform.position.z,.0001f)
                val q=door.transform.quaternion
                assertEquals(-68f,Rotation(q.x,q.y,q.z,q.w).yaw(),.01f)
                val doorBounds=door.getVisualBounds(entry,true,true)
                assertFalse(doorBounds.isEmpty())
                assertEquals(-.315f,doorBounds.min.y,.001f)
                assertEquals(.319f,doorBounds.max.y,.001f)
                // Actual native sofa follows the same viewer-facing rule as extraction.
                val rules=com.example.tidyspace.pc5853b2a.domain.usecase.RoomUseCase()
                val heldSofa=rules.reduce(rules.reduce(RoomGame(),RoomAction.Begin("sofa",Rotation.yaw(42f))),
                    RoomAction.Transform(GrabDelta(Point3(0f,.05f,0f),Rotation())))
                val sofa=all.getValue("sofa")
                sofa.transform.quaternion=heldSofa.item("sofa").rotation.quat()
                val facing=sofa.transform.quaternion
                assertEquals(42f,Rotation(facing.x,facing.y,facing.z,facing.w).yaw(),.001f)
                assertEquals(game().boxStock,RoomPersistence.decode(RoomPersistence.encode(game()))!!.boxStock)
                Log.i("TidyMovingNative","PASS lazy-packed=4; preview/cancel/empty/undo; 6 home assets; actual day/night and dimmer; animated hearth; entry")
                Unit
            } finally {
                furniture.close();parent.destroy(true);resources.close()
            }
        }
    }
}
