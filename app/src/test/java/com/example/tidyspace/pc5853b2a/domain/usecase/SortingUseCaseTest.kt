package com.example.tidyspace.pc5853b2a.domain.usecase

import com.example.tidyspace.pc5853b2a.data.repository.LocalSortingRepository
import com.example.tidyspace.pc5853b2a.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class SortingUseCaseTest {
    private val rules = SortingUseCase(LocalSortingRepository())
    @Test fun initialRotatedEnvelopesClearFrontRail() {
        for(item in rules.initial().items) {
            val halfDepth=if(item.angle%180f==90f) item.spec.size.x/2f else item.spec.size.z/2f
            assertTrue(item.position.z+halfDepth <= .217001f)
        }
    }
    @Test fun wrongSlotRetainsHeldItem() {
        val held = rules.reduce(rules.initial(), SortingAction.Pick("notebook"))
        val wrong = rules.reduce(held, SortingAction.Place("pencil"))
        assertEquals("notebook", wrong.selectedId)
        assertEquals(0, wrong.placedCount)
        assertEquals(1, wrong.mistakes)
    }
    @Test fun perpendicularAngleDoesNotFit() {
        val held = rules.reduce(rules.initial(), SortingAction.Pick("notebook"))
        assertEquals(0, rules.reduce(held, SortingAction.Place("notebook")).placedCount)
    }
    @Test fun symmetricOrientationFits() {
        val held = rules.reduce(rules.initial(), SortingAction.Pick("notebook"))
        val rotated = rules.reduce(held, SortingAction.Rotate)
        assertEquals(180f, rotated.selected!!.angle)
        assertEquals(1, rules.reduce(rotated, SortingAction.Place("notebook")).placedCount)
    }
    @Test fun fullThreeAxisDistanceIsRequired() {
        var held = rules.reduce(rules.initial(), SortingAction.Pick("tape"))
        val slot = held.selected!!.spec.slot
        held = rules.reduce(held, SortingAction.Place("tape", slot.copy(y = slot.y + .07f)))
        assertEquals(0, held.placedCount)
    }
    @Test fun tapeDoesNotRequireRotation() {
        val held = rules.reduce(rules.initial(), SortingAction.Pick("tape"))
        assertEquals(1, rules.reduce(held, SortingAction.Place("tape")).placedCount)
    }
    @Test fun completeCycleAndRestart() {
        var state = rules.initial()
        for(item in state.items) {
            state = rules.reduce(state, SortingAction.Pick(item.spec.id))
            if(item.spec.angleRequired) state = rules.reduce(state, SortingAction.Rotate)
            state = rules.reduce(state, SortingAction.Place(item.spec.id))
        }
        assertTrue(state.completed)
        assertEquals(6, state.placedCount)
        val reset = rules.reduce(state, SortingAction.Restart)
        assertFalse(reset.completed)
        assertEquals(0, reset.placedCount)
        assertEquals(2, reset.rounds)
    }
    @Test fun placedItemCanBeSelectedAgainAndRestoredOnCancel() {
        val held = rules.reduce(rules.initial(), SortingAction.Pick("tape"))
        val placed = rules.reduce(held, SortingAction.Place("tape"))
        val picked=rules.reduce(placed, SortingAction.Pick("tape"))
        assertEquals("tape",picked.selectedId)
        assertEquals(0,picked.placedCount)
        assertEquals(placed.items,rules.reduce(picked,SortingAction.CancelGrab).items)
    }
    @Test fun releasePreservesRotationAndResetsSelection() {
        var state = rules.reduce(rules.initial(), SortingAction.Pick("key"))
        state = rules.reduce(state, SortingAction.Rotate)
        state = rules.reduce(state, SortingAction.Release)
        assertNull(state.selectedId)
        assertEquals(180f, state.items.first { it.spec.id == "key" }.angle)
    }
    @Test fun fiveDifferentLevelsHaveSixObjectsAndWallTargets() {
        val levels=LocalSortingRepository().levels()
        assertEquals(5,levels.size)
        assertEquals(30,levels.sumOf { it.items.size })
        assertEquals(2,levels.count { it.wall })
        for(level in levels) {
            assertEquals(6,level.items.map { it.id }.distinct().size)
            assertTrue(level.items.all { (it.surface==SortingSurface.Wall)==level.wall })
        }
    }
    @Test fun rearTableObjectsRestOnRaisedPlateButWallLevelsUseFlatTray() {
        assertEquals(.0275f+.0125f,rules.initial().items.first().position.y,.0001f)
        assertEquals(.0175f+.015f,rules.initial(3).items.first().position.y,.0001f)
    }
    @Test fun allLevelsAreCompletableAndAllObjectsRegrabbable() {
        for(index in 0 until 5) {
            var state=rules.initial(index)
            for(item in state.items) {
                state=rules.reduce(state,SortingAction.BeginGrab(item.spec.id,"GazePinch"))
                state=rules.reduce(state,SortingAction.Turn(90f))
                val held=state.selected!!
                state=rules.reduce(state,SortingAction.Move(Point3(
                    held.spec.slot.x-held.position.x,held.spec.slot.y-held.position.y,
                    held.spec.slot.z-held.position.z)))
                state=rules.reduce(state,SortingAction.EndGrab)
                assertTrue("level=$index item=${item.spec.id}",state.items.first { it.spec.id==item.spec.id }.placed)
            }
            assertTrue(state.completed)
            val grabbed=rules.reduce(state,SortingAction.BeginGrab(state.items.last().spec.id,"DirectPinch"))
            assertFalse(grabbed.completed)
            assertEquals(state.items,rules.reduce(grabbed,SortingAction.CancelGrab).items)
        }
    }
    @Test fun dragUpdatesAllThreeCoordinatesWithoutSnapUntilRelease() {
        val held=rules.reduce(rules.initial(),SortingAction.BeginGrab("tape","DirectPinch"))
        val before=held.selected!!.position
        val moved=rules.reduce(held,SortingAction.Move(Point3(.02f,.03f,.04f)))
        assertEquals(before+Point3(.02f,.03f,.04f),moved.selected!!.position)
        assertTrue(moved.dragging)
        assertEquals(0,moved.placedCount)
    }
    @Test fun wallPlacementUsesVerticalSurfaceNormalAndTableRemainsAvailable() {
        var state=rules.reduce(rules.initial(3),SortingAction.BeginGrab("hammer","GazePinch"))
        state=rules.reduce(state,SortingAction.Turn(23f))
        val from=state.selected!!.position
        state=rules.reduce(state,SortingAction.Move(Point3(-.40f-from.x,.32f-from.y,-.16f-from.z)))
        state=rules.reduce(state,SortingAction.EndGrab)
        val wall=state.items.first { it.spec.id=="hammer" }
        assertEquals(SortingSurface.Wall,wall.surface)
        assertEquals(-.20f+wall.spec.size.y/2,wall.position.z,.0001f)
        assertFalse(wall.placed)
    }
    @Test fun invalidNumbersCannotPoisonSceneTransforms() {
        val held=rules.reduce(rules.initial(),SortingAction.BeginGrab("key","Pointer"))
        assertEquals(held,rules.reduce(held,SortingAction.Move(Point3(Float.NaN,0f,0f))))
        assertEquals(held,rules.reduce(held,SortingAction.Turn(Float.POSITIVE_INFINITY)))
    }
    @Test fun switchingLevelsDropsGrabAndRestartStaysOnSameLevel() {
        val held=rules.reduce(rules.initial(3),SortingAction.BeginGrab("hammer","RayBasedPinch"))
        val next=rules.reduce(held,SortingAction.NextLevel)
        assertEquals(4,next.levelIndex);assertNull(next.selectedId);assertNull(next.grabOrigin)
        assertEquals(4,rules.reduce(next,SortingAction.Restart).levelIndex)
        assertEquals(0,rules.reduce(next,SortingAction.NextLevel).levelIndex)
        assertEquals(4,rules.reduce(rules.initial(),SortingAction.PreviousLevel).levelIndex)
    }
    @Test fun farReleaseStaysOnSupportAndDoesNotPretendToComplete() {
        var state=rules.reduce(rules.initial(),SortingAction.BeginGrab("tape","RayBasedPinch"))
        state=rules.reduce(state,SortingAction.Move(Point3(4f,2f,3f)))
        state=rules.reduce(state,SortingAction.EndGrab)
        val item=state.items.first { it.spec.id=="tape" }
        assertTrue(item.position.x<.44f && item.position.z<.222f)
        assertEquals(.0175f+item.spec.size.y/2,item.position.y,.0001f)
        assertFalse(item.placed)
    }
}
