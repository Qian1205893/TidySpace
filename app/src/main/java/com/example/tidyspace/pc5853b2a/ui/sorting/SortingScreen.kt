package com.example.tidyspace.pc5853b2a.ui.sorting

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.foundation.gesture.TargetEntity
import com.example.tidyspace.pc5853b2a.ui.sorting.scene.SortingSpatialInput
import com.example.tidyspace.pc5853b2a.ui.sorting.components.SortingControls
import com.example.tidyspace.pc5853b2a.ui.sorting.scene.SortingScene
import com.example.tidyspace.pc5853b2a.ui.theme.SortingPalette
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import com.pico.spatial.ui.foundation.gesture.detectSpatialPointerEvent

@Composable
fun SortingScreen() {
    val vm: SortingViewModel = viewModel(factory = SortingViewModelFactory())
    val state by vm.state.collectAsStateWithLifecycle()
    SortingContent(state, vm::onEvent)
}

@Composable
internal fun SortingContent(state: SortingUiState,onEvent: (SortingEvent)->Unit,onRoom: ()->Unit={}) {
    key(state.sceneRevision,state.game.levelIndex) { SortingSceneContent(state,onEvent,onRoom) }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun SortingSceneContent(state: SortingUiState,onEvent: (SortingEvent)->Unit,onRoom: ()->Unit) {
    val scene = remember { SortingScene() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val currentEvent by rememberUpdatedState(onEvent)
    val input=remember(scene) { SortingSpatialInput(scene) {
        currentEvent(SortingEvent.Act(it))
    } }
    DisposableEffect(scene) { onDispose { input.cancel();scene.destroy() } }
    SpatialView(
        modifier = Modifier.fillMaxSize().pointerInput(scene) {
            try {
                detectSpatialPointerEvent(context,targetedToEntity=TargetEntity.any(),onEvent=input::events)
            } finally { input.cancel() }
        },
        attachments = {
            AttachmentPanel("sorting-controls") { PicoTheme { SortingControls(state,onEvent,onRoom) } }
        },
        initial = { content,attachments ->
            content.addEntity(scene.placementOwner)
            scene.bind(content)
            attachments.entity("sorting-controls")?.let {
                scene.attach(it,Vector3(0f,.91f,-.10f))
            }
            scope.launch {
                try {
                    scene.build(state.game)
                    currentEvent(SortingEvent.SceneReady)
                } catch(e: CancellationException) { throw e }
                catch(e: Exception) {
                    Log.e("TidyScene","scene-load-failed",e)
                    currentEvent(SortingEvent.SceneFailed)
                }
            }
        },
    )
    LaunchedEffect(state.game,state.sceneStatus) {
        if(state.sceneStatus == SceneStatus.Ready) scene.synchronize(state.game)
    }
}
