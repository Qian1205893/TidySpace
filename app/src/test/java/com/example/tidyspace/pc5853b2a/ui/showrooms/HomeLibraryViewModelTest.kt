package com.example.tidyspace.pc5853b2a.ui.showrooms

import com.example.tidyspace.pc5853b2a.data.repository.HomeRepository
import com.example.tidyspace.pc5853b2a.domain.model.*
import com.example.tidyspace.pc5853b2a.domain.usecase.*
import com.example.tidyspace.pc5853b2a.ui.sorting.RoomViewModel
import org.junit.Assert.*
import org.junit.Test

class HomeLibraryViewModelTest {
    private class Store: HomeRepository {
        var saved: HomeLibrary?=null
        var fail=false
        override fun load()=saved
        override fun save(library: HomeLibrary) { check(!fail);saved=library }
    }
    private fun models(store: Store): Pair<RoomViewModel,ShowroomViewModel> {
        val game=StudyRenovation.prepare(MovingHome.prepare(RoomGame(cleaning=CleaningState().completed()),fresh=true))
        val homes=HomeUseCase(store)
        homes.initialize(game,ShowroomSession())
        val room=RoomViewModel(homes.activeGame(),homes.library.active.session,null,{},homes)
        return room to ShowroomViewModel(emptyList(),room) { _,_,_,_->"" }
    }
    @Test fun personalEntryLoadsLibraryAndCreationUpdatesLiveRoom() {
        val (room,vm)=models(Store())
        vm.onEvent(ShowroomEvent.MyHomes)
        assertEquals(ShowroomPage.Homes,vm.state.value.page)
        vm.onEvent(ShowroomEvent.NewHome)
        vm.onEvent(ShowroomEvent.HomeName("我的留白"))
        vm.onEvent(ShowroomEvent.CreateBlankHome)
        assertEquals(ShowroomPage.Applied,vm.state.value.page)
        assertEquals(2,room.homes.value?.homes?.size)
        assertEquals("我的留白",room.homes.value?.active?.name)
    }
    @Test fun reopeningAfterCreationSelectsEntryBeforeWindowCanObserveApplied() {
        val (room,vm)=models(Store())
        vm.onEvent(ShowroomEvent.NewHome);vm.onEvent(ShowroomEvent.CreateBlankHome)
        assertEquals(ShowroomPage.Applied,vm.state.value.page)
        val saved=room.homes.value
        vm.open(personal=true)
        assertEquals(ShowroomPage.Homes,vm.state.value.page)
        vm.open(personal=false)
        assertEquals(ShowroomPage.Gallery,vm.state.value.page)
        assertEquals(saved,room.homes.value)
    }
    @Test fun openingAfterFailedOperationClearsStaleErrorWithoutRetryingIt() {
        val store=Store();val (room,vm)=models(store)
        store.fail=true
        vm.onEvent(ShowroomEvent.NewHome);vm.onEvent(ShowroomEvent.CreateBlankHome)
        assertEquals(ShowroomPage.SaveError,vm.state.value.page)
        store.fail=false
        vm.open(personal=true)
        assertEquals(ShowroomPage.Homes,vm.state.value.page)
        assertEquals("",vm.state.value.message)
        assertEquals(1,room.homes.value!!.homes.size)
    }
    @Test fun switchingClearsCrossHomeUndoAndPreservesSourceLayout() {
        val (room,vm)=models(Store())
        val id=room.homes.value!!.activeId
        room.onEvent(RoomAction.Mood(RoomMood.Evening))
        assertTrue(room.currentGame.canUndo)
        vm.onEvent(ShowroomEvent.ManageHome(id));vm.onEvent(ShowroomEvent.CopyHome)
        assertFalse(room.currentGame.canUndo)
        room.onEvent(RoomAction.Brightness(.2f))
        val copy=room.homes.value!!.activeId
        vm.onEvent(ShowroomEvent.EnterHome(id))
        assertEquals(RoomMood.Evening,room.currentGame.mood)
        assertFalse(room.currentGame.canUndo)
        assertEquals(.2f,room.homes.value!!.homes.first { it.id==copy }.game.indoorBrightness,0f)
    }
    @Test fun failedSaveKeepsInMemoryEditsAndRetryCommitsThem() {
        val store=Store();val (room,vm)=models(store)
        val before=store.saved
        store.fail=true;room.onEvent(RoomAction.Brightness(.12f))
        assertTrue(room.currentGame.saveError)
        assertEquals(before,store.saved)
        assertEquals(.12f,room.currentGame.indoorBrightness,0f)
        store.fail=false;vm.onEvent(ShowroomEvent.Retry)
        assertFalse(room.currentGame.saveError)
        assertEquals(.12f,store.saved!!.active.game.indoorBrightness,0f)
    }
    @Test fun invalidNamesKeepFormAndFailedCreationOffersRetry() {
        val store=Store();val (room,vm)=models(store)
        vm.onEvent(ShowroomEvent.NewHome);vm.onEvent(ShowroomEvent.HomeName(" "))
        vm.onEvent(ShowroomEvent.CreateBlankHome)
        assertEquals(ShowroomPage.NewHome,vm.state.value.page)
        assertEquals(1,room.homes.value!!.homes.size)
        vm.onEvent(ShowroomEvent.HomeName("小屋"));store.fail=true
        vm.onEvent(ShowroomEvent.CreateBlankHome)
        assertEquals(ShowroomPage.SaveError,vm.state.value.page)
        assertEquals(1,room.homes.value!!.homes.size)
        store.fail=false;vm.onEvent(ShowroomEvent.Retry)
        assertEquals(2,room.homes.value!!.homes.size)
    }
    @Test fun deleteRequiresConfirmationAndUndoRestoresOriginal() {
        val (room,vm)=models(Store())
        val original=room.homes.value!!.activeId
        vm.onEvent(ShowroomEvent.ManageHome(original));vm.onEvent(ShowroomEvent.CopyHome)
        vm.onEvent(ShowroomEvent.ManageHome(original));vm.onEvent(ShowroomEvent.DeleteHome)
        assertEquals(ShowroomPage.DeleteConfirm,vm.state.value.page)
        assertEquals(2,room.homes.value!!.homes.size)
        vm.onEvent(ShowroomEvent.CancelDeleteHome)
        assertEquals(ShowroomPage.Manage,vm.state.value.page)
        vm.onEvent(ShowroomEvent.DeleteHome);vm.onEvent(ShowroomEvent.ConfirmDeleteHome)
        assertEquals(1,room.homes.value!!.homes.size)
        vm.onEvent(ShowroomEvent.UndoDeleteHome)
        assertTrue(room.homes.value!!.homes.any { it.id==original })
    }
}
