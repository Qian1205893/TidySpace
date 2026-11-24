package com.example.tidyspace.pc5853b2a.ui.sorting

import com.example.tidyspace.pc5853b2a.data.repository.HomeRepository
import com.example.tidyspace.pc5853b2a.domain.model.*
import com.example.tidyspace.pc5853b2a.domain.usecase.*
import org.junit.Assert.*
import org.junit.Test

class RoomInteractiveSaveTest {
    private class DeferredStore: HomeRepository {
        var disk: HomeLibrary?=null
        val pending=mutableListOf<Pair<HomeLibrary,(Result<Unit>)->Unit>>()
        override fun load()=disk
        override fun save(library: HomeLibrary) { disk=library }
        override fun saveInteractive(library: HomeLibrary,complete: (Result<Unit>)->Unit) {
            pending+=library to complete
        }
    }
    private fun room(store: DeferredStore): RoomViewModel {
        val homes=HomeUseCase(store)
        homes.initialize(RoomGame(),ShowroomSession())
        return RoomViewModel(homes.activeGame(),homes.library.active.session,null,{},homes)
    }
    @Test fun newInputPublishesBeforeStorageAndOldCompletionDoesNotOverwriteIt() {
        val store=DeferredStore();val vm=room(store)
        vm.onEvent(RoomAction.Brightness(.2f))
        vm.onEvent(RoomAction.Brightness(.8f))
        assertEquals(.8f,vm.currentGame.indoorBrightness,0f)
        assertEquals(.8f,store.pending.last().first.active.game.indoorBrightness,0f)
        store.pending.last().second(Result.failure(IllegalStateException("disk full")))
        assertTrue(vm.currentGame.saveError)
        store.pending.first().second(Result.success(Unit))
        assertTrue("Stale success cannot hide latest failure",vm.currentGame.saveError)
        assertNull(vm.retryHomeSave())
        assertFalse(vm.currentGame.saveError)
        assertEquals(.8f,store.disk!!.active.game.indoorBrightness,0f)
    }
    @Test fun lateFailureCannotMutateNewHomeOrUndoNewInteraction() {
        val store=DeferredStore();val vm=room(store)
        vm.onEvent(RoomAction.Brightness(.4f))
        val old=store.pending.single()
        assertNull(vm.createHome("另一间"))
        old.second(Result.failure(IllegalStateException("old save")))
        assertFalse(vm.currentGame.saveError)
        assertEquals("另一间",vm.homes.value!!.active.name)
        vm.onEvent(RoomAction.Begin("@room"))
        vm.onEvent(RoomAction.Transform(GrabDelta(Point3(0f,0f,0f),Rotation.yaw(20f),pointerCount=2)))
        val moving=vm.currentGame
        old.second(Result.success(Unit))
        assertEquals(moving,vm.currentGame)
    }
    @Test fun rejectedHomeOperationDoesNotHideAnOutstandingSaveFailure() {
        val store=DeferredStore();val vm=room(store)
        vm.onEvent(RoomAction.Brightness(.4f))
        assertTrue(vm.saveInFlight)
        assertNotNull(vm.createHome(""))
        store.pending.single().second(Result.failure(IllegalStateException("disk full")))
        assertFalse(vm.saveInFlight)
        assertTrue(vm.currentGame.saveError)
    }
}
