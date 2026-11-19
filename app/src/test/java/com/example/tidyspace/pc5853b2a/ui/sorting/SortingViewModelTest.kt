package com.example.tidyspace.pc5853b2a.ui.sorting

import com.example.tidyspace.pc5853b2a.data.repository.LocalSortingRepository
import com.example.tidyspace.pc5853b2a.data.repository.SortingRepository
import com.example.tidyspace.pc5853b2a.domain.model.*
import com.example.tidyspace.pc5853b2a.domain.usecase.SortingUseCase
import org.junit.Assert.*
import org.junit.Test

class SortingViewModelTest {
    private fun vm() = SortingViewModel(SortingUseCase(LocalSortingRepository())).apply {
        onEvent(SortingEvent.SceneReady)
    }
    @Test fun startsWithSixUnplacedObjects() {
        val state = vm().state.value.game
        assertEquals(6, state.items.size)
        assertEquals(0, state.placedCount)
    }
    @Test fun selectionRotationAndPlacementUpdateObservableState() {
        val vm = vm()
        vm.onEvent(SortingEvent.Act(SortingAction.Pick("pencil")))
        vm.onEvent(SortingEvent.Act(SortingAction.Rotate))
        vm.onEvent(SortingEvent.Act(SortingAction.Place("pencil")))
        assertEquals(1, vm.state.value.game.placedCount)
        assertNull(vm.state.value.game.selectedId)
    }
    @Test fun unknownSelectionIsIgnored() {
        val vm = vm()
        val before = vm.state.value
        vm.onEvent(SortingEvent.Act(SortingAction.Pick("unknown")))
        assertEquals(before, vm.state.value)
    }
    @Test fun emptyRepositoryCannotCompleteOrHintMissingObjects() {
        val vm = SortingViewModel(SortingUseCase(object: SortingRepository {
            override fun items(): List<ItemSpec> = emptyList()
        }))
        vm.onEvent(SortingEvent.Act(SortingAction.Hint))
        assertFalse(vm.state.value.game.completed)
        assertNull(vm.state.value.game.hintId)
    }
    @Test fun recenterPreservesProgress() {
        val vm = vm()
        vm.onEvent(SortingEvent.Act(SortingAction.Pick("tape")))
        vm.onEvent(SortingEvent.Act(SortingAction.Place("tape")))
        vm.onEvent(SortingEvent.Act(SortingAction.Recenter))
        assertEquals(1, vm.state.value.game.recenterRevision)
        assertEquals(1, vm.state.value.game.placedCount)
    }
    @Test fun loadingRejectsInputUntilSceneIsReady() {
        val vm=SortingViewModel(SortingUseCase(LocalSortingRepository()))
        vm.onEvent(SortingEvent.Act(SortingAction.Pick("tape")))
        assertNull(vm.state.value.game.selectedId)
        vm.onEvent(SortingEvent.SceneReady)
        vm.onEvent(SortingEvent.Act(SortingAction.Pick("tape")))
        assertEquals("tape",vm.state.value.game.selectedId)
    }
    @Test fun failedSceneCanBeRetriedWithoutKeepingHeldObjects() {
        val vm=vm()
        vm.onEvent(SortingEvent.Act(SortingAction.Pick("key")))
        vm.onEvent(SortingEvent.SceneFailed)
        vm.onEvent(SortingEvent.RetryScene)
        assertEquals(SceneStatus.Loading,vm.state.value.sceneStatus)
        assertNull(vm.state.value.game.selectedId)
        assertEquals(1,vm.state.value.sceneRevision)
    }
    @Test fun levelChangeWaitsForNewSceneAndRetryKeepsCurrentLevel() {
        val vm=vm()
        vm.onEvent(SortingEvent.Act(SortingAction.NextLevel))
        assertEquals(1,vm.state.value.game.levelIndex)
        assertEquals(SceneStatus.Loading,vm.state.value.sceneStatus)
        vm.onEvent(SortingEvent.Act(SortingAction.NextLevel))
        assertEquals(1,vm.state.value.game.levelIndex)
        vm.onEvent(SortingEvent.SceneFailed)
        vm.onEvent(SortingEvent.RetryScene)
        assertEquals(1,vm.state.value.game.levelIndex)
    }
}
