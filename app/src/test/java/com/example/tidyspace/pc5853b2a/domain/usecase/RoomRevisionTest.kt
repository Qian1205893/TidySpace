package com.example.tidyspace.pc5853b2a.domain.usecase

import com.example.tidyspace.pc5853b2a.domain.model.*
import com.example.tidyspace.pc5853b2a.data.repository.RoomPersistence
import com.example.tidyspace.pc5853b2a.ui.sorting.RoomViewModel
import org.junit.Assert.*
import org.junit.Test

class RoomRevisionTest {
    private val rules=RoomUseCase()
    private fun placed(): RoomGame {
        val s=MovingHome.prepare(RoomGame())
        return s.copy(items=s.items.map {
            when(it.spec.id) {
                "desk"->it.copy(position=Point3(0f,-.15f,0f),packed=false)
                "mug"->it.copy(position=Point3(.10f,.055f,0f),packed=false,support="desk")
                else->it
            }
        },boxStock=s.boxStock.mapValues { (_,v)->v.filterNot { it in setOf("desk","mug") } })
    }
    @Test fun tapDoesNotMoveOrTurnPlacedFurnitureAndExplainsTwoHandTurning() {
        val start=placed()
        val result=rules.reduce(rules.reduce(start,RoomAction.Begin("desk",Rotation.yaw(83f))),RoomAction.End)
        assertEquals(start.items,result.items)
        assertNull(result.angleFocus)
        assertEquals("desk",result.focusId)
    }
    @Test fun angleCarriesSupportedItemsAndSurvivesSaveUndoRedo() {
        var saved: String?=null
        val vm=RoomViewModel(RoomPersistence.encode(placed())) { saved=it }
        vm.onEvent(RoomAction.Begin("desk",Rotation.yaw(80f)));vm.onEvent(RoomAction.End)
        vm.onEvent(RoomAction.RotatePlaced(45f))
        val turned=vm.state.value.game
        assertEquals(45f,turned.item("desk").rotation.yaw(),.001f)
        assertTrue(RoomPhysics.supported(turned,turned.item("mug")))
        assertEquals(turned.items,RoomPersistence.decode(saved)!!.items)
        vm.onEvent(RoomAction.Undo)
        assertEquals(0f,vm.state.value.game.item("desk").rotation.yaw(),.001f)
        vm.onEvent(RoomAction.Redo)
        assertEquals(turned.items,vm.state.value.game.items)
    }
    @Test fun blockedAngleRetainsExactFamilyPose() {
        val start=placed().let { s ->
            val obstacle=s.item("coatrack").copy(position=Point3(0f,-.11f,.24f),packed=false)
            s.copy(items=s.items.map { if(it.spec.id=="coatrack") obstacle else it },angleFocus="desk")
        }
        assertFalse(RoomPhysics.intersects(start,start.item("desk"),start.item("coatrack")))
        val result=rules.reduce(start,RoomAction.RotatePlaced(90f))
        assertEquals(start.items,result.items)
        assertTrue(result.message.contains("原位置"))
    }
    @Test fun everyTickUsesAbsoluteAngleAndDraggingTickCannotTurnFurniture() {
        val initial=placed().copy(angleFocus="desk")
        val down=rules.reduce(initial,RoomAction.Begin("@angle:75"))
        val tapped=rules.reduce(down,RoomAction.End)
        assertEquals(75f,tapped.item("desk").rotation.yaw(),.001f)
        val moved=rules.reduce(down,RoomAction.Transform(GrabDelta(Point3(.06f,0f,0f),Rotation())))
        assertEquals(initial.items,rules.reduce(moved,RoomAction.End).items)
    }
    @Test fun roofActionTogglesPanelTwiceInBothLevelsWithoutAlteringLayout() {
        for(cleaning in listOf(null,CleaningState())) {
            var game=RoomGame(cleaning=cleaning)
            val items=game.items
            repeat(2) { index ->
                game=rules.reduce(game,RoomAction.ToggleControls)
                assertEquals(index==1,game.controlsVisible)
                assertEquals(items,game.items)
            }
        }
    }
    @Test fun largerRoomAndBalconyAcceptFurnitureOutsideFormerFootprint() {
        val s=placed()
        val chair=s.item("chair").copy(position=Point3(-1.20f,-.175f,.65f),packed=false)
        assertTrue(RoomPhysics.inside(chair))
        val bench=s.item("gardenbench").copy(position=Point3(1.84f,-.2025f,.10f),packed=false)
        val next=s.copy(items=s.items.map { if(it.spec.id==bench.spec.id) bench else it })
        assertTrue(RoomPhysics.inside(bench))
        assertTrue(RoomPhysics.supported(next,bench))
        assertTrue(RoomPhysics.rotateInPlace(next,bench.spec.id,90f).valid)
    }
    @Test fun migrationIsIdempotentAndPreservesLightingAndCleaningProgress() {
        val base=RoomGame(cleaning=CleaningState(mapOf("dust-0" to .5f)),dayHour=18.5f,indoorBrightness=.31f)
        val duplicate=base.item("sofa").copy(spec=base.item("sofa").spec.copy(id="sofa~1"),packed=false)
        val old=base.copy(items=base.items+duplicate,boxInventoryVersion=1,nextSerial=2,additions=1)
        val migrated=MovingHome.prepare(old)
        assertEquals(migrated,MovingHome.prepare(migrated))
        assertEquals(base.cleaning,migrated.cleaning)
        assertEquals(base.dayHour,migrated.dayHour,0f)
        assertEquals(base.indoorBrightness,migrated.indoorBrightness,0f)
        assertEquals(1,migrated.items.count { it.spec.assetId=="sofa" })
        assertEquals(migrated.items,RoomPersistence.decode(RoomPersistence.encode(migrated))!!.items)
    }
    @Test fun expansionMovesFamiliesWithoutStretchingSupportedOffsets() {
        val original=RoomGame(boxInventoryVersion=1).let { s ->
            s.copy(items=s.items.map { if(it.spec.id=="mug") it.copy(
                position=s.item("desk").position+Point3(.1f,.205f,0f),support="desk",packed=false) else it })
        }
        val next=MovingHome.prepare(original)
        assertEquals(original.item("mug").position-original.item("desk").position,
            next.item("mug").position-next.item("desk").position)
        assertTrue(RoomPhysics.supported(next,next.item("mug")))
    }
    @Test fun wallPictureRotatesInItsPlaneAndStaysFlush() {
        val s=placed().let { game -> game.copy(items=game.items.map {
            if(it.spec.id=="picture") it.copy(position=Point3(.6f,.3f,RoomLayout.back+.0075f),packed=false,support="@back") else it
        },angleFocus="picture") }
        val next=rules.reduce(s,RoomAction.RotatePlaced(30f))
        assertEquals(30f,RoomPhysics.placedAngle(next.item("picture")),.001f)
        assertEquals(s.item("picture").position,next.item("picture").position)
        assertEquals("@back",next.item("picture").support)
    }
}
