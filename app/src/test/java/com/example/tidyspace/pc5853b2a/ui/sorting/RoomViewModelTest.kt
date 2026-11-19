package com.example.tidyspace.pc5853b2a.ui.sorting

import com.example.tidyspace.pc5853b2a.domain.model.*
import com.example.tidyspace.pc5853b2a.data.repository.RoomPersistence
import org.junit.Assert.*
import org.junit.Test

class RoomViewModelTest {
    @Test fun restoredExistingProgressIsRecognizedImmediately() {
        val snapshot=RoomPersistence.encode(RoomGame(crateOpen=true))
        val vm=RoomViewModel(snapshot)
        assertTrue("guide-1" in vm.state.value.game.achievements)
        assertEquals(0,vm.state.value.game.celebration)
    }
    @Test fun undoRedoRetainAchievementsAndDoNotReuseInstanceIds() {
        val vm=RoomViewModel(RoomPersistence.encode(RoomGame()))
        vm.onEvent(RoomAction.Add("chair"))
        val first=vm.state.value.game.focusId
        val xp=vm.state.value.game.xp
        vm.onEvent(RoomAction.Undo)
        assertEquals(13,vm.state.value.game.items.size)
        assertEquals(xp,vm.state.value.game.xp)
        assertTrue(vm.state.value.game.canRedo)
        vm.onEvent(RoomAction.Redo)
        assertEquals(first,vm.state.value.game.focusId)
        vm.onEvent(RoomAction.Undo)
        vm.onEvent(RoomAction.Add("chair"))
        assertNotEquals(first,vm.state.value.game.focusId)
        assertFalse(vm.state.value.game.canRedo)
    }
    @Test fun cancelledGrabDoesNotReplaceUndoHistory() {
        val vm=RoomViewModel(RoomPersistence.encode(RoomGame()))
        vm.onEvent(RoomAction.Mood(RoomMood.Evening))
        vm.onEvent(RoomAction.Begin("desk"))
        vm.onEvent(RoomAction.Transform(GrabDelta(Point3(.2f,.1f,0f),Rotation())))
        vm.onEvent(RoomAction.Cancel)
        vm.onEvent(RoomAction.Undo)
        assertEquals(RoomMood.Morning,vm.state.value.game.mood)
        assertEquals(RoomGame().items,vm.state.value.game.items)
        assertTrue("guide-10" in vm.state.value.game.achievements)
    }
    @Test fun stateSurvivesRecreationAndOnlySettledChangesAreSaved() {
        var saved: String?=null
        val vm=RoomViewModel(RoomPersistence.encode(RoomGame())) { saved=it }
        vm.onEvent(RoomAction.Begin("crate:lid"))
        assertNull(saved)
        vm.onEvent(RoomAction.End)
        assertTrue(RoomViewModel(saved).state.value.game.crateOpen)
    }
    @Test fun failedPersistenceIsVisibleAndRetriedOnNextRelease() {
        var fail=true
        val vm=RoomViewModel(RoomPersistence.encode(RoomGame())) { if(fail) error("disk") }
        vm.onEvent(RoomAction.Begin("lamp:switch"));vm.onEvent(RoomAction.End)
        assertTrue(vm.state.value.game.saveError)
        fail=false
        vm.onEvent(RoomAction.Begin("lamp:switch"));vm.onEvent(RoomAction.End)
        assertFalse(vm.state.value.game.saveError)
    }
}
