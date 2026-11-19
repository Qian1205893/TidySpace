package com.example.tidyspace.pc5853b2a

import android.content.Intent
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.tidyspace.pc5853b2a.data.repository.RoomPersistence
import com.example.tidyspace.pc5853b2a.domain.model.*
import com.example.tidyspace.pc5853b2a.domain.usecase.*
import com.example.tidyspace.pc5853b2a.ui.sorting.RoomViewModel
import com.example.tidyspace.pc5853b2a.ui.sorting.scene.*
import com.example.tidyspace.pc5853b2a.platform.LaunchActivity
import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.Entity
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Detached native scenes and VMs; never replace the player's preferences. */
@RunWith(AndroidJUnit4::class)
class StudyNativeRuntimeTest {
    private suspend fun start() {
        val i=InstrumentationRegistry.getInstrumentation()
        i.runOnMainSync { i.targetContext.startActivity(Intent(i.targetContext,LaunchActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        delay(2000)
    }
    private fun nodes(e: Entity): List<Entity> = listOf(e)+e.getChildren().flatMap(::nodes)
    private fun state()=StudyRenovation.prepare(MovingHome.prepare(RoomGame()))
    @Test fun fourteenNativeModelsHaveExactBoundsAndIndependentBodyTargets(): Unit=runBlocking {
        start()
        withContext(Dispatchers.Main) {
            val parent=node();val targets=mutableMapOf<Entity,String>()
            val library=RoomModelLibrary();val furniture=RoomFurniture(targets,library)
            try {
                coroutineScope {
                    val first=async { library.load("tidy-study") }
                    val second=async { library.load("tidy-study") }
                    assertSame("Concurrent requests must share one native source",first.await(),second.await())
                }
                furniture.load(parent,emptyList())
                for(item in StudyLayout.equipment()) {
                    val root=furniture.reconcile(listOf(item)).getValue(item.spec.id)
                    val b=root.getVisualBounds(root,true,true)
                    assertFalse(item.spec.id,b.isEmpty())
                    assertEquals(item.spec.id,item.spec.size.x,b.max.x-b.min.x,.001f)
                    assertEquals(item.spec.id,item.spec.size.y,b.max.y-b.min.y,.001f)
                    assertEquals(item.spec.id,item.spec.size.z,b.max.z-b.min.z,.001f)
                    assertTrue(item.spec.id,nodes(root).any {
                        targets[it]==item.spec.id && it.components[CollisionComponent::class.java]!=null })
                    Log.i("TidyStudyNative","MODEL_PASS id=${item.spec.id} bounds=$b")
                }
            } finally { furniture.close();library.close();parent.destroy(true) }
        }
    }
    @Test fun studyLoadsWithContinuousFloorAndNoBathroomGeometry(): Unit=runBlocking {
        start()
        withContext(Dispatchers.Main) {
            val scene=RoomScene();val library=RoomModelLibrary()
            try {
                scene.build(state())
                val root=scene.volume.roomRoot
                for(name in listOf("bathroom_house","bathroom_open_passage","bathroom_short_glass",
                    "bathroom_open_sliding_door","entryway")) assertNull(name,root.findEntity(name))
                assertNotNull(root.findEntity("study_continuous_floor"))
                assertNull(root.findEntity("study_open_passage"))
                assertNull(root.findEntity("gaming_doorless_partitions"))
                assertNull(root.findEntity("gaming_architectural_lighting"))
                assertNotNull(root.findEntity("gaming_continuous_floor"))
                val home=library.load("tidy-home-expansion")
                for(id in StudyLayout.retiredAssets)assertNull(id,home.findEntity(id))
                val b=checkNotNull(root.findEntity("study_house")).getVisualBounds(root,true,true)
                assertTrue(b.max.z>=2.259f);assertTrue(RoomFloor.point(0f,1.2f))
                val loaded=checkNotNull(RoomPersistence.decode(RoomPersistence.encode(state())))
                assertEquals(1,loaded.studyVersion);assertTrue(loaded.items.none { StudyLayout.retired(it.spec) })
                Log.i("TidyStudyNative","PASS study architecture, no retired native meshes, v14")
            } finally { library.close();scene.destroy() }
        }
    }
    @Test fun actualDeskCarriesObjectsAndBookMovesReleasesAndReloads(): Unit=runBlocking {
        start()
        withContext(Dispatchers.Main) {
            val initial=state();val vm=RoomViewModel(RoomPersistence.encode(initial));val scene=RoomScene()
            try {
                scene.bindState { vm.currentGame };scene.build(vm.currentGame)
                val desk=checkNotNull(scene.volume.roomRoot.findEntity("studydesk"))
                val book=checkNotNull(scene.volume.roomRoot.findEntity("studybook"))
                fun send(a: RoomAction) { vm.onEvent(a);scene.applyInput(vm.currentGame) }
                send(RoomAction.Begin("studydesk",Rotation()))
                send(RoomAction.Transform(GrabDelta(Point3(.02f,.03f,.01f),Rotation())))
                assertEquals(vm.currentGame.item("studybook").position.x,book.transform.position.x,.001f)
                val pivot=vm.currentGame.item("studydesk").position
                send(RoomAction.Transform(GrabDelta(Point3(.7f,.6f,.4f),Rotation.yaw(2f),pointerCount=2)))
                assertEquals(pivot,vm.currentGame.item("studydesk").position)
                send(RoomAction.Cancel)
                assertEquals(-.72f,desk.transform.position.x,.001f)
                assertEquals(1.105f,desk.transform.position.z,.001f)
                send(RoomAction.Begin("studybook",Rotation()))
                send(RoomAction.Transform(GrabDelta(Point3(.012f,.025f,0f),Rotation())))
                send(RoomAction.End)
                assertEquals(-.708f,book.transform.position.x,.001f)
                assertEquals("studydesk",vm.currentGame.item("studybook").support)
                val loaded=checkNotNull(RoomPersistence.decode(RoomPersistence.encode(vm.currentGame)))
                assertEquals(vm.currentGame.item("studybook"),loaded.item("studybook"))
                val again=RoomUseCase().reduce(loaded,RoomAction.CleanAgain)
                assertTrue(again.items.none { StudyLayout.retired(it.spec) })
                Log.i("TidyStudyNative","PASS native family move, pivot, cancel, book release/reload, no resurrection")
            } finally { scene.destroy() }
        }
    }
}
