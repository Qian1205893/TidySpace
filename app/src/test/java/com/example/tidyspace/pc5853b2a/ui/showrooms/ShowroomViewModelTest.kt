package com.example.tidyspace.pc5853b2a.ui.showrooms

import com.example.tidyspace.pc5853b2a.domain.model.*
import com.example.tidyspace.pc5853b2a.ui.sorting.RoomViewModel
import org.junit.Assert.*
import org.junit.Test

class ShowroomViewModelTest {
    private fun initial()=RoomGame(items=availableCatalogue(RoomGame()).map { RoomItem(it,packed=true) },studyVersion=1)
    private fun reference()=Showroom("test","绿意","治愈系","入门","植物小屋","植物","挪一件",
        RoomMood.Morning,9f,.7f,listOf(ShowroomTarget("plant",Point3(0f,-.24f,0f),0f,null,"会客")),
        listOf(ShowroomStep("第一步","放植物",listOf("plant")),ShowroomStep("完成","完成",emptyList())),emptyList(),emptyList())
    private fun model(room: RoomViewModel)=ShowroomViewModel(listOf(reference()),room) { _,_,_,_->"export.zip" }
    @Test fun restoredPersonalEntryStartsOnHomesWithoutADeferredCorrection() {
        var writes=0
        val room=RoomViewModel(initial()) { writes++ }
        val vm=ShowroomViewModel(listOf(reference()),room,ShowroomPage.Homes) { _,_,_,_->"" }
        assertEquals(ShowroomPage.Homes,vm.state.value.page)
        assertEquals(0,writes)
    }
    @Test fun browsingAndCancelNeverSaveRoom() {
        var writes=0
        val room=RoomViewModel(initial()) { writes++ }
        val vm=model(room)
        assertEquals(ShowroomPage.Gallery,vm.state.value.page)
        vm.onEvent(ShowroomEvent.Select("test"))
        vm.onEvent(ShowroomEvent.Preflight)
        vm.onEvent(ShowroomEvent.Detail)
        assertEquals(0,writes)
        assertTrue(room.currentGame.items.all { it.packed })
    }
    @Test fun applyAndDedicatedUndoUseAtomicSnapshot() {
        var writes=0
        val room=RoomViewModel(initial(),ShowroomSession(),{ _,_->writes++ }) {}
        val vm=model(room)
        vm.onEvent(ShowroomEvent.Select("test"));vm.onEvent(ShowroomEvent.Apply)
        assertEquals(ShowroomPage.Applied,vm.state.value.page)
        assertNotNull(room.showroomSession.value.undo)
        vm.onEvent(ShowroomEvent.Undo)
        assertEquals(2,writes)
        assertTrue(room.currentGame.items.all { it.packed })
        assertNull(room.showroomSession.value.undo)
    }
    @Test fun failedSaveLeavesGameAndMetadataUntouched() {
        val initial=initial()
        val room=RoomViewModel(initial,ShowroomSession(),{ _,_->error("disk") }) {}
        val before=room.currentGame
        val vm=model(room)
        vm.onEvent(ShowroomEvent.Select("test"));vm.onEvent(ShowroomEvent.Apply)
        assertEquals(before,room.currentGame)
        assertNull(room.showroomSession.value.undo)
        assertTrue(vm.state.value.message.contains("保存失败"))
    }
    @Test fun stepsDoNotAcceptPackedOrMisplacedObjectsAndResumeProgress() {
        val room=RoomViewModel(initial()) {}
        val vm=model(room)
        vm.onEvent(ShowroomEvent.Select("test"));vm.onEvent(ShowroomEvent.Steps);vm.onEvent(ShowroomEvent.Next)
        assertEquals(0,room.showroomSession.value.step)
        assertTrue(vm.state.value.message.contains("还"))
        room.applyShowroom(reference())
        vm.onEvent(ShowroomEvent.Steps);vm.onEvent(ShowroomEvent.Next)
        assertEquals(1,room.showroomSession.value.step)
        val resumed=model(room)
        resumed.onEvent(ShowroomEvent.Steps)
        assertEquals(ShowroomPage.Steps,resumed.state.value.page)
        assertEquals(1,room.showroomSession.value.step)
    }
    @Test fun filtersAndDraftInputsHandleEmptyAndLengthBounds() {
        val vm=model(RoomViewModel(initial()) {})
        vm.onEvent(ShowroomEvent.Search("x".repeat(200)))
        assertEquals(60,vm.state.value.query.length)
        vm.onEvent(ShowroomEvent.Title("a".repeat(100)))
        assertEquals(40,vm.state.value.title.length)
        vm.onEvent(ShowroomEvent.Select("missing"))
        assertNull(vm.state.value.selected)
        vm.onEvent(ShowroomEvent.Style("童趣风"))
        assertEquals("童趣风",vm.state.value.style)
    }
}
