package com.example.tidyspace.pc5853b2a.domain.usecase

import com.example.tidyspace.pc5853b2a.data.repository.HomeRepository
import com.example.tidyspace.pc5853b2a.data.repository.RoomPersistence
import com.example.tidyspace.pc5853b2a.domain.model.*
import com.example.tidyspace.pc5853b2a.ui.sorting.RoomViewModel
import org.junit.Assert.*
import org.junit.Test

class RoomEntryTest {
    private fun dirty()=MovingHome.prepare(RoomGame()).let { game ->
        game.copy(cleaning=CleaningState(progress=mapOf("dust-0" to .3f)),
            roomYaw=38f,playCounts=mapOf("musicbox" to 19L),achievements=setOf("zen"),
            boxStock=game.boxStock.mapValues { it.value.reversed() },nextSerial=24,
            indoorBrightness=.32f,toyStates=mapOf("popboard" to ToyState(bubbles=3)))
    }

    @Test fun migrationOnlyRemovesCleaningAndPreservesAllPlayerContent() {
        val old=dirty()
        val ready=RoomEntry.prepare(old)
        assertTrue(ready.decorating);assertNull(ready.cleaning)
        assertEquals(old.copy(cleaning=null,message=ready.message),ready)
        assertSame(ready,RoomEntry.prepare(ready))
        val reloaded=checkNotNull(RoomPersistence.decode(RoomPersistence.encode(ready)))
        assertTrue(reloaded.decorating)
        assertEquals(old.items,reloaded.items);assertEquals(old.playCounts,reloaded.playCounts)
    }
    @Test fun completedAndCleanExistingHomesAreNotRewritten() {
        for(game in listOf(RoomGame(),dirty().copy(cleaning=CleaningState().completed())))
            assertSame(game,RoomEntry.prepare(game))
    }
    @Test fun abandonedCleaningGrabIsClearedWhileFurnitureDraftIsRetained() {
        val held=dirty().copy(selectedId="@clean:Vacuum",origin=dirty().items,
            cleaning=CleaningState(tool=CleanTool.Vacuum))
        val ready=RoomEntry.prepare(held)
        assertNull(ready.selectedId);assertNull(ready.origin);assertEquals(held.items,ready.items)
        val furniture=dirty().copy(selectedId="chair",origin=dirty().items)
        assertEquals(furniture.selectedId,RoomEntry.prepare(furniture).selectedId)
        assertEquals(furniture.origin,RoomEntry.prepare(furniture).origin)
    }
    @Test fun inactiveHomesAndTemplateUndoMigrateTogetherWithoutIdentityChanges() {
        val a=SavedHome("a","甲",dirty(),ShowroomSession(undo=dirty(),note="保留笔记"),123L,8)
        val b=SavedHome("b","乙",dirty().copy(roomYaw=81f),savedAt=456L,revision=9)
        val old=HomeLibrary(listOf(a,b),"b",setOf("favorite"),mapOf("musicbox" to 19L),24)
        val next=RoomEntry.prepare(old)
        assertEquals("b",next.activeId);assertTrue(next.tutorialComplete)
        assertTrue(next.homes.all { it.game.decorating })
        assertTrue(next.homes.first().session.undo!!.decorating)
        assertEquals(old.homes.map { Triple(it.id,it.savedAt,it.revision) },
            next.homes.map { Triple(it.id,it.savedAt,it.revision) })
        assertEquals(old.homes.map { it.game.items },next.homes.map { it.game.items })
        assertEquals(old.achievements,next.achievements)
        assertEquals(next,RoomEntry.prepare(next))
    }
    @Test fun failedMigrationSaveKeepsOriginalAndRetryPublishesPreparedHome() {
        val original=HomeLibrary(listOf(SavedHome("a","小屋",dirty())),"a")
        var stored=original;var fail=true;var writes=0
        val repository=object: HomeRepository {
            override fun load()=stored
            override fun save(library: HomeLibrary) {
                check(!fail) { "disk" };stored=library;writes++
            }
        }
        val homes=HomeUseCase(repository)
        homes.initialize(RoomGame(),ShowroomSession())
        assertTrue(homes.migrationError);assertEquals(original,stored)
        assertTrue(homes.activeGame().decorating)
        fail=false;homes.save(homes.activeGame(),homes.library.active.session)
        assertFalse(homes.migrationError);assertTrue(stored.active.game.decorating)
        val reread=HomeUseCase(repository);reread.initialize(RoomGame(),ShowroomSession())
        assertEquals(1,writes);assertEquals(stored,reread.library)
    }
    @Test fun retiredCleanAgainDoesNotChangeLayoutUndoOrWriteSave() {
        var writes=0
        val vm=RoomViewModel(dirty()) { writes++ }
        val before=vm.currentGame
        vm.onEvent(RoomAction.CleanAgain)
        assertEquals(before,vm.currentGame);assertEquals(0,writes)
        vm.onEvent(RoomAction.Mood(RoomMood.Evening))
        assertTrue(vm.currentGame.canUndo)
        vm.onEvent(RoomAction.Undo)
        assertTrue(vm.currentGame.decorating)
        assertEquals(before.items,vm.currentGame.items)
    }
}
