package com.example.tidyspace.pc5853b2a.ui.navigation

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tidyspace.pc5853b2a.domain.model.SpaceMode
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.Text

/** Entry on the real catalogue tray, within the player's existing interaction area. */
@Composable
fun SpaceModeToggle() {
    val session=rememberSpaceSession()
    val state by session.modes.state.collectAsStateWithLifecycle()
    Button(onClick={
        session.modes.request(if(state.mode==SpaceMode.Full) SpaceMode.Shared else SpaceMode.Full)
    },enabled=!state.switching && !state.recoveringWindow,
        modifier=Modifier.testTag("room-space-mode")) {
        Text(when {
            state.switching -> "正在切换空间…"
            state.mode==SpaceMode.Full -> "返回共享空间"
            else -> "进入全屏空间"
        })
    }
}
