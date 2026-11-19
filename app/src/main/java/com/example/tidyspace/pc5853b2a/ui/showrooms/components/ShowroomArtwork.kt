package com.example.tidyspace.pc5853b2a.ui.showrooms.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tidyspace.pc5853b2a.R
import com.example.tidyspace.pc5853b2a.ui.theme.ShowroomColors
import com.pico.spatial.ui.design.*

@Composable
internal fun referenceFieldColors()=TextFieldDefaults.textFieldColors(
    textColor=ShowroomColors.roomInk,backgroundColor=ShowroomColors.roomPaper,
    focusedColor=ShowroomColors.roomAccent,placeholderColor=ShowroomColors.roomMuted)
@Composable
internal fun referenceSegmentColors()=SegmentControlDefaults.colors(
    itemBackgroundColor=Color.Transparent,itemContentColor=ShowroomColors.roomInk,
    selectedItemBackgroundColor=ShowroomColors.roomSoft,selectedItemContentColor=ShowroomColors.roomInk)
@Composable
internal fun referenceButtonColors(primary: Boolean=false)=ButtonDefaults.buttonColors(
    containerColor=if(primary) ShowroomColors.roomAccent else ShowroomColors.roomPaper,
    contentColor=ShowroomColors.roomInk)
@Composable
internal fun referenceIconColors()=IconButtonDefaults.iconButtonColors(
    containerColor=ShowroomColors.roomPaper,contentColor=ShowroomColors.roomInk)
@Composable
internal fun referenceTitle()=PicoTheme.typography.titleLarge.copy(fontSize=26.sp,lineHeight=34.sp)
@Composable
internal fun referenceBody()=PicoTheme.typography.bodyLarge.copy(fontSize=20.sp,lineHeight=28.sp)
@Composable
internal fun referenceCaption()=PicoTheme.typography.bodyMedium.copy(fontSize=18.sp,lineHeight=26.sp)

/** Shipped-model reference renders keep their original resolution and alpha. */
@Composable
internal fun Artwork(image: String,description: String,modifier: Modifier) {
    val resource=when(image.replace('_','-')) {
        "green-cover" -> R.drawable.showroom_green_cover
        "retro-cover" -> R.drawable.showroom_retro_cover
        "workshop-cover" -> R.drawable.showroom_workshop_cover
        "minimal-cover" -> R.drawable.showroom_minimal_cover
        "play-cover" -> R.drawable.showroom_play_cover
        "cinema-cover" -> R.drawable.showroom_cinema_cover
        "tea-cover" -> R.drawable.showroom_tea_cover
        "player-cover" -> R.drawable.showroom_player_cover
        "bakery-cover" -> R.drawable.showroom_bakery_cover
        "fashion-cover" -> R.drawable.showroom_fashion_cover
        "camp-cover" -> R.drawable.showroom_camp_cover
        "artist-cover" -> R.drawable.showroom_artist_cover
        "bakery-panorama" -> R.drawable.showroom_bakery_panorama
        "fashion-panorama" -> R.drawable.showroom_fashion_panorama
        "camp-panorama" -> R.drawable.showroom_camp_panorama
        "artist-panorama" -> R.drawable.showroom_artist_panorama
        "green-panorama" -> R.drawable.showroom_green_panorama
        "green-detail-1" -> R.drawable.showroom_green_detail_1
        "green-detail-2" -> R.drawable.showroom_green_detail_2
        "retro-panorama" -> R.drawable.showroom_retro_panorama
        "retro-detail-1" -> R.drawable.showroom_retro_detail_1
        "retro-detail-2" -> R.drawable.showroom_retro_detail_2
        "workshop-panorama" -> R.drawable.showroom_workshop_panorama
        "workshop-detail-1" -> R.drawable.showroom_workshop_detail_1
        "workshop-detail-2" -> R.drawable.showroom_workshop_detail_2
        "minimal-panorama" -> R.drawable.showroom_minimal_panorama
        "minimal-detail-1" -> R.drawable.showroom_minimal_detail_1
        "minimal-detail-2" -> R.drawable.showroom_minimal_detail_2
        "play-panorama" -> R.drawable.showroom_play_panorama
        "play-detail-1" -> R.drawable.showroom_play_detail_1
        "play-detail-2" -> R.drawable.showroom_play_detail_2
        "cinema-panorama" -> R.drawable.showroom_cinema_panorama
        "cinema-detail-1" -> R.drawable.showroom_cinema_detail_1
        "cinema-detail-2" -> R.drawable.showroom_cinema_detail_2
        "tea-panorama" -> R.drawable.showroom_tea_panorama
        "tea-detail-1" -> R.drawable.showroom_tea_detail_1
        "tea-detail-2" -> R.drawable.showroom_tea_detail_2
        "player-panorama" -> R.drawable.showroom_player_panorama
        "player-detail-1" -> R.drawable.showroom_player_detail_1
        "player-detail-2" -> R.drawable.showroom_player_detail_2
        else -> error("Unknown shipped showroom artwork: $image")
    }
    Image(painterResource(resource),contentDescription=description,contentScale=ContentScale.Fit,
        modifier=modifier.clip(RoundedCornerShape(16.dp)))
}
