package com.example.tidyspace.pc5853b2a.domain.usecase

import com.example.tidyspace.pc5853b2a.data.repository.RoomPersistence
import com.example.tidyspace.pc5853b2a.domain.model.*
import com.example.tidyspace.pc5853b2a.ui.sorting.RoomViewModel
import org.junit.Assert.*
import org.junit.Test

class CartonPlacementTest {
    private val rules=RoomUseCase()
    private fun chairGame(): RoomGame {
        val game=RoomGame()
        return game.copy(items=game.items.map { item -> if(item.spec.id=="chair")
            item.copy(position=Point3(0f,.3f,.2f),support=null,packed=false)
            else item.copy(support=null,packed=true) })
    }
    @Test fun idleGuideAndPreviewCannotLeaveADropMarker() {
        val game=MovingHome.prepare(RoomGame()).copy(guideVisible=true,achievements=setOf("guide-1"))
        assertNotNull("Reproduce an idle guide with a real empty destination",RoomProgress.current(game)?.destination)
        assertNull(RoomPlacementCue.resolve(game))
        assertNull(RoomPlacementCue.resolve(MovingHome.reveal(game,"box-toys")))
    }
    @Test fun heldObjectShowsBottomAndReleaseHidesIt() {
        val held=rules.reduce(chairGame(),RoomAction.Begin("chair"))
        val cue=checkNotNull(RoomPlacementCue.resolve(held))
        assertTrue(cue.valid)
        assertEquals("Marker follows the object's bottom on the authored floor",-.32f,cue.point.y,.0001f)
        val released=rules.reduce(held,RoomAction.End)
        assertNull(released.selectedId)
        assertNull(RoomPlacementCue.resolve(released))
    }
    @Test fun cancelHidesDropMarkerAndRestoresFurniture() {
        val initial=chairGame()
        val held=rules.reduce(initial,RoomAction.Begin("chair"))
        val moved=rules.reduce(held,RoomAction.Transform(GrabDelta(Point3(.04f,.02f,0f),Rotation())))
        assertNotNull(RoomPlacementCue.resolve(moved))
        val cancelled=rules.reduce(moved,RoomAction.Cancel)
        assertEquals(initial.items,cancelled.items)
        assertNull(RoomPlacementCue.resolve(cancelled))
    }
    @Test fun hiddenPackedAndEmptyBoxSelectionsHaveNoMarker() {
        val packed=chairGame().let { it.copy(items=it.items.map { item -> item.copy(packed=true) },selectedId="chair") }
        assertNull(RoomPlacementCue.resolve(packed))
        val boxes=MovingHome.prepare(RoomGame())
        assertNull(RoomPlacementCue.resolve(boxes.copy(selectedId="box-toys",
            boxStock=boxes.boxStock+("box-toys" to emptyList()))))
        assertNull(RoomPlacementCue.resolve(boxes.copy(cleaning=CleaningState(),selectedId="box-toys")))
    }
    @Test fun roomAndToyMechanismsHaveNoPlacementMarker() {
        val game=MovingHome.prepare(RoomGame())
        for(id in listOf("@room","@play:woodfish","crate:lid","desk:drawer","@house:journal","@angle:90"))
            assertNull(id,RoomPlacementCue.resolve(game.copy(selectedId=id)))
    }
    @Test fun nativeCartonSyncChangesOnlyStockedUnheldBoxRotations() {
        val game=MovingHome.prepare(RoomGame()).copy(selectedId="box-light")
        val rotations=game.items.associate { it.spec.id to Rotation.yaw(63f) }+
            ("box-home" to Rotation(Float.NaN,0f,0f,1f))
        val updated=rules.reduce(game,RoomAction.SyncCartonFacing(rotations))
        assertEquals(63f,updated.item("box-toys").rotation.yaw(),.001f)
        assertEquals(63f,updated.item("box-furniture").rotation.yaw(),.001f)
        assertEquals(game.item("box-light"),updated.item("box-light"))
        assertEquals(game.item("box-home"),updated.item("box-home"))
        assertEquals(game.copy(items=updated.items),updated)
        game.items.forEach { old ->
            val new=updated.item(old.spec.id)
            assertEquals(old.copy(rotation=new.rotation),new)
            if(old.spec.kind!=Furnishing.MovingBox) assertEquals(old,new)
        }
    }
    @Test fun syncDoesNotWritePreferencesAndExtractionUsesVisibleBoxAngle() {
        val initial=MovingHome.prepare(RoomGame())
        var writes=0
        val vm=RoomViewModel(RoomPersistence.encode(initial)) { writes++ }
        vm.onEvent(RoomAction.SyncCartonFacing(mapOf("box-toys" to Rotation.yaw(-52f))))
        assertEquals(0,writes)
        val revealed=MovingHome.reveal(vm.currentGame,"box-toys")
        val extracted=rules.reduce(revealed,RoomAction.Begin("@unpack:box-toys"))
        assertEquals(-52f,checkNotNull(extracted.selected).rotation.yaw(),.001f)
        assertEquals(revealed.boxStock,extracted.boxStock)
        assertEquals(revealed.items,rules.reduce(extracted,RoomAction.Cancel).items)
    }
}
