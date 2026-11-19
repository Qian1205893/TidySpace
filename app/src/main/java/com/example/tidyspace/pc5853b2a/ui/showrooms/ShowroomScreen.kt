package com.example.tidyspace.pc5853b2a.ui.showrooms

import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tidyspace.pc5853b2a.ui.sorting.RoomViewModel
import com.example.tidyspace.pc5853b2a.ui.showrooms.components.ShowroomContent
import com.example.tidyspace.pc5853b2a.ui.navigation.FrontWindowPlacement
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.window.Augment
import com.pico.spatial.ui.foundation.window.AugmentContentAlignment
import com.pico.spatial.ui.foundation.window.WindowSizeBehaviors
import com.pico.spatial.ui.foundation.effect3d.scale3D

/** SDK normalized anchors use 0..1, with the caption/user side at z=1. */
internal object ShowroomPlacement {
    val anchor=FrontWindowPlacement.anchor
    const val readingScale=1.1f
    const val materialBackground=false
}

@Composable
fun ShowroomScreen(room: RoomViewModel,factory: ShowroomViewModelFactory,onClose: ()->Unit) {
    // RoomScreen initializes this shared instance before publishing the opening flag.
    val model: ShowroomViewModel=viewModel(key="room-showrooms",factory=factory)
    val state by model.state.collectAsStateWithLifecycle()
    val game by room.state.collectAsStateWithLifecycle()
    val session by room.showroomSession.collectAsStateWithLifecycle()
    val library by room.homes.collectAsStateWithLifecycle()
    val offset=FrontWindowPlacement.offset()
    LaunchedEffect(offset) {
        Log.i("TidyShowrooms","open page=${state.page} anchor=${ShowroomPlacement.anchor} alignment=center offset=$offset setback-meters=${FrontWindowPlacement.setbackMeters} reading-scale=${ShowroomPlacement.readingScale} material-background=${ShowroomPlacement.materialBackground}")
    }
    LaunchedEffect(state.page) {
        if(state.page==ShowroomPage.Applied && library!=null) onClose()
    }
    // A front-centered reference is immediately visible from the room's caption
    // side. Do not anchor an opening action outside the normalized window bounds.
    Augment(anchor=ShowroomPlacement.anchor,alignment=AugmentContentAlignment.Center,
        offset=offset,windowSizeBehaviors=WindowSizeBehaviors.Adaptive,
        enableMaterialBackground=ShowroomPlacement.materialBackground) {
        PicoTheme {
            ShowroomContent(state,game.game,session,model::onEvent,onClose,
                Modifier.scale3D(scaleX=ShowroomPlacement.readingScale,
                    scaleY=ShowroomPlacement.readingScale,scaleZ=1f),library,room.deletedHomeName)
        }
    }
}
