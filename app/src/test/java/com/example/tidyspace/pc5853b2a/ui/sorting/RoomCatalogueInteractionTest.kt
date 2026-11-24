package com.example.tidyspace.pc5853b2a.ui.sorting

import com.example.tidyspace.pc5853b2a.domain.model.*
import com.example.tidyspace.pc5853b2a.domain.usecase.RoomUseCase
import org.junit.Assert.*
import org.junit.Test

class RoomCatalogueInteractionTest {
    private val rules=RoomUseCase()
    private fun move(game: RoomGame,translation: Point3=Point3(0f,0f,0f),yaw: Float=0f,pointers: Int=1)=
        rules.reduce(game,RoomAction.Transform(GrabDelta(translation,Rotation.yaw(yaw),pointerCount=pointers)))

    @Test fun pressingEveryPlacedBodyAndMechanismLeavesCatalogueAvailable() {
        val initial=RoomGame(items=RoomGame().items.map { it.copy(packed=false) })
        for(id in initial.items.map { it.spec.id }+listOf("@room","crate:lid","lamp:switch",
            "desk:drawer","@house:clock","@house:dimmer","@play:woodfish","@play:bowl")) {
            val pressed=rules.reduce(initial,RoomAction.Begin(id))
            assertFalse("Press must not disable catalogue: $id",RoomCatalogueInteraction.blocks(pressed))
            val wobble=move(pressed,Point3(.001f,0f,0f))
            assertFalse("Small hand movement must not disable catalogue: $id",RoomCatalogueInteraction.blocks(wobble))
        }
    }
    @Test fun actualDraggingBlocksUntilReleaseOrCancelEvenAfterReturningToOrigin() {
        for(id in listOf("chair","@room","crate:lid","desk:drawer")) {
            val pressed=rules.reduce(RoomGame(),RoomAction.Begin(id))
            val moved=if(id=="@room") move(pressed,yaw=8f,pointers=2)
                else move(pressed,Point3(0f,.02f,.02f))
            assertTrue(id,RoomCatalogueInteraction.blocks(moved))
            val returned=if(id=="@room") move(moved,yaw=-8f,pointers=2)
                else move(moved,Point3(0f,-.02f,-.02f))
            assertTrue("A drag remains busy until release: $id",RoomCatalogueInteraction.blocks(returned))
            assertFalse(RoomCatalogueInteraction.blocks(rules.reduce(returned,RoomAction.Cancel)))
            assertFalse(RoomCatalogueInteraction.blocks(rules.reduce(returned,RoomAction.End)))
        }
    }
    @Test fun dialTapIgnoresPreviousRoomTurnAndCardExtractionStillBlocks() {
        val turned=move(rules.reduce(RoomGame(),RoomAction.Begin("@room")),yaw=12f,pointers=2)
        val ended=rules.reduce(turned,RoomAction.End)
        val dial=rules.reduce(ended,RoomAction.Begin("@house:dimmer"))
        assertFalse(RoomCatalogueInteraction.blocks(dial))
        assertTrue(RoomCatalogueInteraction.blocks(RoomGame(cardDrag=CardDrag(1,"chair",null,Rotation()))))
    }
}
