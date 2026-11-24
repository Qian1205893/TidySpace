package com.example.tidyspace.pc5853b2a.domain.usecase

import com.example.tidyspace.pc5853b2a.domain.model.*

/** Retire the cleaning gate without repacking, moving, or awarding furniture/progress. */
object RoomEntry {
    fun prepare(game: RoomGame): RoomGame {
        if(game.decorating) return game
        val cleanSelection=game.selectedId?.let {
            it.startsWith("@clean:") || it.startsWith("@trash:")
        }==true
        val ready=game.copy(cleaning=null,
            message="挑选喜欢的家具，直接开始布置小屋。")
        return if(cleanSelection) ready.copy(selectedId=null,origin=null,mechanismOrigin=null,
            dragFacing=null,twoHandTurning=false,gestureDistance=0f,gestureTurn=0f) else ready
    }

    /** Every home, including inactive homes and old template undo snapshots, can be opened. */
    fun prepare(library: HomeLibrary): HomeLibrary = library.copy(
        tutorialComplete=true,
        homes=library.homes.map { home ->
            home.copy(game=prepare(home.game),session=home.session.copy(
                undo=home.session.undo?.let(::prepare)))
        })
}
