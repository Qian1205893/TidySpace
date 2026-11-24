package com.example.tidyspace.pc5853b2a.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tidyspace.pc5853b2a.domain.model.SpaceMode
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.geometry.NormalizedPoint3D
import com.pico.spatial.ui.foundation.window.Augment
import com.pico.spatial.ui.foundation.window.AugmentContentAlignment
import com.pico.spatial.ui.foundation.window.WindowSizeBehaviors
import kotlinx.coroutines.delay

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

@Composable
fun SpaceModeControl() {
    val session=rememberSpaceSession()
    val state by session.modes.state.collectAsState()
    LaunchedEffect(session) {
        while(true) {
            session.modes.observeSystemMode()
            delay(500)
        }
    }
    Augment(anchor=NormalizedPoint3D(1f,0f,1f),alignment=AugmentContentAlignment.Center,
        windowSizeBehaviors=WindowSizeBehaviors.Adaptive) {
        PicoTheme {
            Column(Modifier.widthIn(max=440.dp).testTag("space-mode-controls")) {
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Button(onClick={ session.modes.request(SpaceMode.Shared) },
                        enabled=!state.recoveringWindow || state.error!=null,
                        modifier=Modifier.testTag("space-shared")) {
                        Text(if(state.mode==SpaceMode.Shared) "共享空间 · 当前" else "共享空间")
                    }
                    Button(onClick={ session.modes.request(SpaceMode.Full) },
                        enabled=!state.recoveringWindow,
                        modifier=Modifier.testTag("space-full")) {
                        Text(if(state.mode==SpaceMode.Full) "全屏空间 · 当前" else "全屏空间")
                    }
                }
                if(state.switching) Text("正在切换…",color=PicoTheme.colorScheme.labelSecondary)
                state.error?.let { Text(it,color=PicoTheme.colorScheme.labelPrimary) }
            }
        }
    }
}
