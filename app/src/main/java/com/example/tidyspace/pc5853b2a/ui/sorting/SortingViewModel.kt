package com.example.tidyspace.pc5853b2a.ui.sorting

import androidx.lifecycle.ViewModel
import com.example.tidyspace.pc5853b2a.domain.usecase.SortingUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class SortingViewModel(private val rules: SortingUseCase): ViewModel() {
    private val _state = MutableStateFlow(SortingUiState(rules.initial()))
    val state: StateFlow<SortingUiState> = _state.asStateFlow()
    fun onEvent(event: SortingEvent) {
        when(event) {
            is SortingEvent.Act -> _state.update {
                if(it.sceneStatus == SceneStatus.Ready) {
                    val next=rules.reduce(it.game,event.action)
                    val changed=next.levelIndex!=it.game.levelIndex
                    it.copy(game=next,sceneStatus=if(changed) SceneStatus.Loading else it.sceneStatus)
                } else it
            }
            SortingEvent.SceneReady -> _state.update { it.copy(sceneStatus=SceneStatus.Ready) }
            SortingEvent.SceneFailed -> _state.update { it.copy(sceneStatus=SceneStatus.Failed) }
            SortingEvent.RetryScene -> _state.update {
                it.copy(game=rules.initial(it.game.levelIndex),sceneStatus=SceneStatus.Loading,
                    sceneRevision=it.sceneRevision+1)
            }
        }
    }
}
