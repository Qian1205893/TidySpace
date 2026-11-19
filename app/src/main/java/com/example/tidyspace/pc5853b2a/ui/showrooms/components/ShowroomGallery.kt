package com.example.tidyspace.pc5853b2a.ui.showrooms.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.tidyspace.pc5853b2a.domain.model.Showroom
import com.example.tidyspace.pc5853b2a.ui.showrooms.*
import com.example.tidyspace.pc5853b2a.ui.theme.CottageColors
import com.pico.spatial.ui.design.*

@Composable
internal fun ShowroomGallery(s: ShowroomUiState,
    send: (ShowroomEvent)->Unit,modifier: Modifier=Modifier) {
    Column(modifier.fillMaxWidth().testTag("showroom-gallery"),
        verticalArrangement=Arrangement.spacedBy(16.dp,Alignment.CenterVertically)) {
        s.rooms.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                row.forEach { room ->
                    ReferenceRoomCard(room,{ send(ShowroomEvent.Select(room.id)) },Modifier.weight(1f))
                }
                repeat(4-row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ReferenceRoomCard(r: Showroom,onClick: ()->Unit,modifier: Modifier) {
    Button(onClick=onClick,modifier=modifier.height(184.dp).testTag("showroom-${r.id}"),
        shape=RoundedCornerShape(26.dp),contentPadding=PaddingValues(0.dp),
        colors=ButtonDefaults.buttonColors(containerColor=Color.Transparent,contentColor=CottageColors.ink)) {
        CottageCard(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(12.dp),horizontalAlignment=Alignment.CenterHorizontally,
                verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Artwork("${r.id}-cover",r.title,Modifier.weight(1f).fillMaxWidth())
                Text(r.title,style=homeTitle(),maxLines=1)
            }
        }
    }
}
