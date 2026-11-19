package com.example.tidyspace.pc5853b2a.domain.usecase

import com.example.tidyspace.pc5853b2a.domain.model.*

/** A drop marker belongs to a visible held object, never to an idle guide step. */
internal object RoomPlacementCue {
    data class Cue(val point: Point3,val valid: Boolean)
    fun visible(game: RoomGame,item: RoomItem)=game.decorating &&
        if(item.spec.kind==Furnishing.MovingBox) game.stock(item.spec.id).isNotEmpty()
        else !item.packed || (!game.movingHome && game.crateOpen)

    fun resolve(game: RoomGame): Cue? {
        val held=game.selected?.takeIf { visible(game,it) } ?: return null
        val placement=RoomUseCase().placement(game)
        val item=placement.item ?: held
        return Cue(item.position-Point3(0f,RoomPhysics.half(item).y,0f),placement.valid)
    }
}
