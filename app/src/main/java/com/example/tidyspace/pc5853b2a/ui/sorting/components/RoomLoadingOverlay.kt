package com.example.tidyspace.pc5853b2a.ui.sorting.components

import android.util.Log
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tidyspace.pc5853b2a.R
import com.example.tidyspace.pc5853b2a.domain.model.RoomLoadPhase
import com.example.tidyspace.pc5853b2a.ui.navigation.FrontWindowPlacement
import com.example.tidyspace.pc5853b2a.ui.showrooms.components.CottageSurface
import com.example.tidyspace.pc5853b2a.ui.theme.CottageColors
import com.pico.spatial.ui.design.*
import com.pico.spatial.ui.foundation.window.Augment
import com.pico.spatial.ui.foundation.window.AugmentContentAlignment

@Composable
internal fun RoomLoadingOverlay(phase: RoomLoadPhase,failed: Boolean,retry: ()->Unit) {
    val offset=FrontWindowPlacement.offset()
    LaunchedEffect(offset) {
        Log.i("TidyLoading","window-offset=$offset setback-meters=${FrontWindowPlacement.setbackMeters}")
    }
    Augment(anchor=FrontWindowPlacement.anchor,alignment=AugmentContentAlignment.Center,
        offset=offset,enableMaterialBackground=false) {
        PicoTheme {
            RoomLoadingCard(phase,failed,retry)
        }
    }
}

@Composable
internal fun RoomLoadingCard(phase: RoomLoadPhase,failed: Boolean,retry: ()->Unit) {
    val motion=rememberInfiniteTransition(label="settling-furniture")
    val settle by motion.animateFloat(0f,1f,
        infiniteRepeatable(tween(1100,easing=FastOutSlowInEasing),RepeatMode.Reverse),
        label="loading-breath")
    CottageSurface(Modifier.size(520.dp,380.dp).testTag(if(failed) "room-load-error" else "room-loading-overlay")) {
        Column(Modifier.fillMaxSize().padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally,
            verticalArrangement=Arrangement.spacedBy(12.dp,Alignment.CenterVertically)) {
            Image(painterResource(R.drawable.furniture_sofa),contentDescription=null,
                modifier=Modifier.size(180.dp,132.dp).offset(y=if(failed) 0.dp else (-6*settle).dp)
                    .alpha(if(failed) 1f else .82f+.18f*settle))
            Text(if(failed) "小屋暂时没能打开" else "正在打开小屋",
                color=CottageColors.ink,style=PicoTheme.typography.titleLarge.copy(fontSize=28.sp))
            Text(if(failed) "你的布置已保留，请重试。" else when(phase) {
                RoomLoadPhase.Building -> "准备房屋与灯光…"
                RoomLoadPhase.Furnishing -> "把家具一件件摆好…"
                RoomLoadPhase.Finishing,RoomLoadPhase.Prepared -> "整理好，马上见。"
            },color=CottageColors.muted,style=PicoTheme.typography.bodyLarge.copy(fontSize=20.sp))
            if(failed) Button(onClick=retry,modifier=Modifier.testTag("room-load-retry"),
                colors=ButtonDefaults.buttonColors(containerColor=CottageColors.green,
                    contentColor=CottageColors.ink)) { Text("重新打开") }
            else CircularProgressIndicator(modifier=Modifier.size(32.dp).testTag("room-load-animation"),
                color=CottageColors.muted)
        }
    }
}
