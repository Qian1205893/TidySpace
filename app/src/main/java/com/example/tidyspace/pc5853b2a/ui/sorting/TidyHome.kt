package com.example.tidyspace.pc5853b2a.ui.sorting

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tidyspace.pc5853b2a.domain.model.SortingAction

@Composable
fun TidyHome() {
    var decorating by rememberSaveable { mutableStateOf(true) }
    // Activity-owned models survive switching between the two experiences.
    val sorting: SortingViewModel=viewModel(factory=SortingViewModelFactory())
    val state by sorting.state.collectAsStateWithLifecycle()
    if(decorating) RoomScreen { decorating=false }
    else SortingContent(state,sorting::onEvent) {
        sorting.onEvent(SortingEvent.Act(SortingAction.CancelGrab));decorating=true
    }
}
