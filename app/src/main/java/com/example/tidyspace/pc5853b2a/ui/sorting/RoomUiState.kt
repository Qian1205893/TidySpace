package com.example.tidyspace.pc5853b2a.ui.sorting

import com.example.tidyspace.pc5853b2a.domain.model.RoomAction
import com.example.tidyspace.pc5853b2a.domain.model.RoomGame

/** Render the same immutable snapshot for the room, guide, catalogue and achievements. */
data class RoomUiState(val game: RoomGame=RoomGame())
sealed interface RoomEvent {
    data class Act(val action: RoomAction): RoomEvent
}
