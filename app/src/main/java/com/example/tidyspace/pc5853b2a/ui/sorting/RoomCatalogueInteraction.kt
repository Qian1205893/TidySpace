package com.example.tidyspace.pc5853b2a.ui.sorting

import com.example.tidyspace.pc5853b2a.domain.model.RoomGame
import kotlin.math.abs

/** Pressing selects a target; the catalogue is busy only after actual manipulation. */
internal object RoomCatalogueInteraction {
    fun blocks(game: RoomGame): Boolean {
        if(game.cardDrag!=null) return true
        val target=game.selectedId ?: return false
        game.ritual?.let { return it.travel>=.016f || it.turn>=.07f }
        if(game.gestureDistance>=.016f || abs(game.gestureTurn)>=3f) return true
        if(target.startsWith("@play:")) return abs(game.toyGesture.turn)>=3f
        return when(target) {
            "crate:lid" -> abs(game.actionTravel)>=.016f
            "desk:drawer" -> game.mechanismOrigin?.let { abs(game.drawerOpen-it.drawerOpen)*.10f>=.016f } ?: false
            "@room" -> game.mechanismOrigin?.let {
                abs(((game.roomYaw-it.roomYaw+540f)%360f)-180f)>=3f
            } ?: false
            else -> false
        }
    }
}
