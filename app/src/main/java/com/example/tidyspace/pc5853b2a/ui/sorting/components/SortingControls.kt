package com.example.tidyspace.pc5853b2a.ui.sorting.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.example.tidyspace.pc5853b2a.domain.model.SortingAction
import com.example.tidyspace.pc5853b2a.ui.sorting.SortingEvent
import com.example.tidyspace.pc5853b2a.ui.sorting.SortingUiState
import com.example.tidyspace.pc5853b2a.ui.sorting.SceneStatus
import com.example.tidyspace.pc5853b2a.ui.theme.SortingPalette

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun SortingControls(state: SortingUiState, onEvent: (SortingEvent) -> Unit,onRoom: ()->Unit={}) {
    val game = state.game
    Column(Modifier.size(1088.dp, 168.dp).padding(horizontal=24.dp,vertical=12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("拾间 · ${game.levelName}", style = PicoTheme.typography.titleLarge,
                color = PicoTheme.colorScheme.labelPrimary)
            Text(when(state.sceneStatus) {
                SceneStatus.Loading -> "布置房间中"
                SceneStatus.Failed -> "暂时无法整理"
                SceneStatus.Ready -> "${game.levelIndex+1} / ${game.levelCount} 关 · 已归位 ${game.placedCount} / ${game.items.size}"
            },
                style = PicoTheme.typography.bodyLarge, color = PicoTheme.colorScheme.labelPrimary)
        }
        Text(when(state.sceneStatus) {
            SceneStatus.Loading -> "正在准备地板、墙面与小物…"
            SceneStatus.Failed -> "收纳场景暂时无法载入，请重新整理。"
            SceneStatus.Ready -> if(game.completed) "给每件小物，一个刚好的位置。" else game.message
        },
            style = PicoTheme.typography.bodyLarge, color = PicoTheme.colorScheme.labelPrimary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick=onRoom) { Text("布置小屋") }
            Button(onClick = { onEvent(SortingEvent.Act(SortingAction.Hint)) },
                enabled = state.sceneStatus == SceneStatus.Ready && !game.completed) { Text("提示") }
            Button(onClick = {
                if(state.sceneStatus == SceneStatus.Failed) onEvent(SortingEvent.RetryScene)
                else onEvent(SortingEvent.Act(SortingAction.Restart))
            }) {
                Text(if(game.completed) "再整理一次" else "重新整理")
            }
            Button(onClick={ onEvent(SortingEvent.Act(SortingAction.PreviousLevel)) },
                enabled=state.sceneStatus==SceneStatus.Ready) { Text("上一关") }
            Button(onClick={ onEvent(SortingEvent.Act(SortingAction.NextLevel)) },
                enabled=state.sceneStatus==SceneStatus.Ready) { Text("下一关") }
        }
    }
}
