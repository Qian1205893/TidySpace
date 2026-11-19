package com.example.tidyspace.pc5853b2a

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.tidyspace.pc5853b2a.domain.model.*
import com.example.tidyspace.pc5853b2a.platform.LaunchActivity
import com.example.tidyspace.pc5853b2a.ui.sorting.RoomViewModel
import com.example.tidyspace.pc5853b2a.ui.sorting.scene.*
import com.pico.spatial.core.ecs.Entity
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Isolated scene and reducer; never writes to the user's preferences. */
@RunWith(AndroidJUnit4::class)
class ExpandedCleaningNativeRuntimeTest {
    @Test fun movableHollowBinCapturesNearbyTrashAndMopReachesStudy(): Unit=runBlocking {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync { instrumentation.targetContext.startActivity(
            Intent(instrumentation.targetContext,LaunchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        delay(2000)
        withContext(Dispatchers.Main) {
            val parent=node()
            val targets=mutableMapOf<Entity,String>()
            val resources=SceneResources()
            val scene=RoomRenovation(targets)
            val vm=RoomViewModel(RoomGame(cleaning=CleaningState())) {}
            fun act(a: RoomAction) { vm.onEvent(a);scene.applyCleaning(vm.currentGame) }
            fun move(p: Point3)=act(RoomAction.Transform(GrabDelta(p-vm.currentGame.cleaning!!.position,Rotation())))
            try {
                scene.build(parent,resources)
                scene.synchronize(vm.currentGame)
                val bin=checkNotNull(parent.findEntity("clean_bin"))
                val bounds=bin.getVisualBounds(bin,true,true)
                assertEquals(-.295f,bounds.min.y,.001f)
                assertEquals(.245f,bounds.max.y,.002f)
                assertTrue(bounds.max.x-bounds.min.x<.28f)
                assertNotNull(bin.findEntity("bin_lid"))
                assertTrue(targets.containsValue("@clean:Bin"))
                assertTrue(targets.containsValue("@clean:Mop"))
                val mop=checkNotNull(parent.findEntity("clean_Mop"))
                assertEquals(.39f,mop.getVisualBounds(mop,true,true).max.y,.002f)
                act(RoomAction.Begin("@clean:Bin"));move(Point3(-.7f,.05f,1.42f))
                assertEquals(-.7f,bin.transform.position.x,.001f)
                act(RoomAction.End)
                assertEquals(-.025f,bin.transform.position.y,.001f)
                act(RoomAction.Begin("@trash:trash-3"));move(vm.currentGame.cleaning!!.binPosition+Point3(.17f,.11f,0f))
                assertTrue(vm.currentGame.cleaning!!.binReady)
                assertTrue(parent.findEntity("bin_mouth_halo")!!.enabled)
                assertEquals(0f,vm.currentGame.cleaning!!.amount("trash-3"),0f)
                act(RoomAction.End)
                assertEquals(1f,vm.currentGame.cleaning!!.amount("trash-3"),0f)
                scene.animate(.5)
                val trash=parent.findEntity("trash-3")!!
                assertEquals("@clean:Bin",targets[trash])
                assertTrue(trash.transform.position.y<bin.transform.position.y)
                assertTrue((Point3(trash.transform.position.x,trash.transform.position.y,trash.transform.position.z)-
                    vm.currentGame.cleaning!!.binPosition).length()<.28f)
                act(RoomAction.Begin("@clean:Mop"))
                val mess=cleaningMess().first { it.id=="spill-0" }
                move(CleaningGrid.centre(mess,10))
                repeat(4) { act(RoomAction.CleanTick(.075f)) }
                assertEquals(0f,vm.currentGame.cleaning!!.amount("spill-0"),0f)
                move(vm.currentGame.cleaning!!.position+Point3(.07f,0f,0f))
                assertTrue(vm.currentGame.cleaning!!.amount("spill-0")>0f)
                scene.animate(.516)
                assertEquals(.74f,parent.findEntity("mop_head")!!.transform.scaleVector.y,.001f)
                act(RoomAction.Cancel)
                assertEquals(0f,vm.currentGame.cleaning!!.amount("spill-0"),0f)
            } finally { parent.destroy(true);resources.close() }
        }
    }
}
