package com.example.tidyspace.pc5853b2a

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.tidyspace.pc5853b2a.data.repository.RoomPersistence
import com.example.tidyspace.pc5853b2a.domain.model.*
import com.example.tidyspace.pc5853b2a.domain.usecase.MovingHome
import com.example.tidyspace.pc5853b2a.platform.LaunchActivity
import com.example.tidyspace.pc5853b2a.ui.sorting.RoomViewModel
import com.example.tidyspace.pc5853b2a.ui.sorting.scene.*
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** The removed stage remains readable, but its native tools and grime cannot appear on entry. */
@RunWith(AndroidJUnit4::class)
class CleaningNativeRuntimeTest {
    private fun verifyEntry(old: RoomGame): Unit=runBlocking {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        instrumentation.runOnMainSync {
            context.startActivity(Intent(context,LaunchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        delay(2000)
        withContext(Dispatchers.Main) {
            val parent=node();val resources=SceneResources()
            val renovation=RoomRenovation(mutableMapOf())
            val vm=RoomViewModel(RoomPersistence.encode(old))
            try {
                renovation.build(parent,resources)
                renovation.synchronize(vm.currentGame)
                assertTrue(vm.currentGame.decorating)
                assertEquals(old.items,vm.currentGame.items)
                assertEquals(old.boxStock,vm.currentGame.boxStock)
                assertEquals(old.playCounts,vm.currentGame.playCounts)
                for(name in listOf("renovation_house","renovation_fireplace","renovation_base")) {
                    val entity=checkNotNull(parent.findEntity(name))
                    assertFalse(entity.getVisualBounds(entity,true,true).isEmpty())
                }
                val hidden=listOf("clean_bin","clean_stand","clean_stand_extra")+
                    CleanTool.entries.map { "clean_${it.name}" }+cleaningMess().map { it.id }
                hidden.forEach { assertFalse("Retired cleaning content visible: $it",checkNotNull(parent.findEntity(it)).enabled) }
                for(mess in cleaningMess().filter { it.kind in setOf(MessKind.Dust,MessKind.Wall,MessKind.Spill) }) {
                    val mesh=checkNotNull(parent.findEntity("${mess.id}_mesh"))
                    mesh.components[ModelComponent::class.java]!!.materials.forEach {
                        assertEquals(0f,(it as PhysicallyBasedMaterial).getOpacity(),0f)
                    }
                }
                val before=vm.currentGame
                val writes=renovation.grimeWrites
                vm.onEvent(RoomAction.CleanAgain)
                repeat(120) { renovation.applyCleaning(vm.currentGame) }
                assertEquals(before,vm.currentGame)
                assertEquals(writes,renovation.grimeWrites)
                hidden.forEach { assertFalse(checkNotNull(parent.findEntity(it)).enabled) }
                assertTrue(checkNotNull(RoomPersistence.decode(RoomPersistence.encode(vm.currentGame))).decorating)
            } finally { parent.destroy(true);resources.close() }
        }
    }

    @Test fun dirtySavedHouseEntersFurnishingWithNoToolsOrGrime() =
        verifyEntry(MovingHome.prepare(RoomGame(cleaning=CleaningState(progress=mapOf("dust-0" to .3f)))))

    @Test fun finishedSavedHouseKeepsLayoutAndCannotRestartCleaning() =
        verifyEntry(MovingHome.prepare(RoomGame(cleaning=CleaningState().completed(),
            playCounts=mapOf("musicbox" to 12L))).let { s ->
            s.copy(items=s.items.map { if(it.spec.id=="chair") it.copy(packed=false) else it },
                boxStock=s.boxStock.mapValues { (_,ids)->ids.filterNot { it=="chair" } })
        })
}
