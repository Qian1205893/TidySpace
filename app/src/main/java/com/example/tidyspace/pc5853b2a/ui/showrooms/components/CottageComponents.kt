package com.example.tidyspace.pc5853b2a.ui.showrooms.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.tidyspace.pc5853b2a.ui.showrooms.ShowroomEvent
import com.example.tidyspace.pc5853b2a.ui.theme.CottageColors
import com.pico.spatial.ui.design.*
import com.pico.spatial.ui.foundation.layout.offset
import com.pico.spatial.ui.foundation.layout.padding3D

/** Bounded card artwork reproduces the supplied picture's two soft rims. */
@Composable
internal fun CottageCard(modifier: Modifier=Modifier,selected: Boolean=false,
    content: @Composable BoxScope.()->Unit) {
    // A clipping/background parent is not a depth container: native hover can
    // split its descendants into compositor layers at the same Z as that fill.
    Box(modifier) {
        Canvas(Modifier.matchParentSize().offset(z=(-4).dp)) {
            drawRoundRect(if(selected) CottageColors.pinkPaper else CottageColors.paper,
                cornerRadius=CornerRadius(26.dp.toPx()))
            val inset=2.dp.toPx()
            drawRoundRect(if(selected) CottageColors.pink else CottageColors.rim,
                Offset(inset,inset),Size(size.width-2*inset,size.height-2*inset),
                CornerRadius(24.dp.toPx()),style=Stroke(if(selected) 3.dp.toPx() else 1.5.dp.toPx()))
            val inner=7.dp.toPx()
            drawRoundRect(CottageColors.gold,Offset(inner,inner),
                Size(size.width-2*inner,size.height-2*inner),CornerRadius(19.dp.toPx()),
                style=Stroke(1.dp.toPx()))
        }
        content()
    }
}

/** Separate the opaque backplate from system-promoted hover/pressed layers. */
@Composable
internal fun CottageSurface(modifier: Modifier=Modifier,content: @Composable BoxScope.()->Unit) {
    Box(modifier.padding3D(back=32.dp,front=16.dp)) {
        // design-style: opaque-root reference cream backplate; Augment glass is disabled
        Box(Modifier.matchParentSize().offset(z=(-24).dp)
            .background(CottageColors.shell,RoundedCornerShape(40.dp)))
        content()
    }
}

@Composable
internal fun CottagePanel(modifier: Modifier,tag: String,content: @Composable ColumnScope.()->Unit) {
    CottageSurface(Modifier.size(1104.dp,800.dp).then(modifier).testTag(tag)) {
        Column(Modifier.fillMaxSize().padding(28.dp),
            verticalArrangement=Arrangement.spacedBy(16.dp),content=content)
    }
}

@Composable
internal fun CottageHeader(title: String,personal: Boolean,send: (ShowroomEvent)->Unit,
    closeTag: String,onClose: ()->Unit,newAction: Boolean=false) {
    Row(Modifier.fillMaxWidth().height(72.dp),verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(title,style=homeDisplay(),color=CottageColors.ink,modifier=Modifier.weight(1f),maxLines=1)
        HomeButton("我的小屋","collection-homes",selected=personal) { send(ShowroomEvent.MyHomes) }
        HomeButton("样板间","collection-templates",selected=!personal) { send(ShowroomEvent.Home) }
        if(newAction) HomeButton("新建小屋","home-new",primary=true) { send(ShowroomEvent.NewHome) }
        HomeButton(if(closeTag=="home-close") "返回" else "关闭",closeTag,onClick=onClose)
    }
}
