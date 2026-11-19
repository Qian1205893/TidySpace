package com.example.tidyspace.pc5853b2a.ui.sorting

import com.example.tidyspace.pc5853b2a.domain.model.SortingGame
import com.example.tidyspace.pc5853b2a.domain.model.SortingAction

enum class SceneStatus { Loading, Ready, Failed }
data class SortingUiState(
    val game: SortingGame = SortingGame(),
    val sceneStatus: SceneStatus = SceneStatus.Loading,
    val sceneRevision: Int = 0,
)
sealed interface SortingEvent {
    data class Act(val action: SortingAction): SortingEvent
    data object SceneReady: SortingEvent
    data object SceneFailed: SortingEvent
    data object RetryScene: SortingEvent
}
