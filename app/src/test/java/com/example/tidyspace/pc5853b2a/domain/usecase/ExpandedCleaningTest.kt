package com.example.tidyspace.pc5853b2a.domain.usecase

import com.example.tidyspace.pc5853b2a.domain.model.*
import com.example.tidyspace.pc5853b2a.data.repository.*
import com.example.tidyspace.pc5853b2a.ui.sorting.RoomViewModel
import org.junit.Assert.*
import org.junit.Test

class ExpandedCleaningTest {
    private val rules=RoomUseCase()
    private fun move(s: RoomGame,p: Point3,count: Int=1)=rules.reduce(s,RoomAction.Transform(
        GrabDelta(p-s.cleaning!!.position,Rotation.yaw(if(count==2) 20f else 0f),pointerCount=count)))
    private fun fresh()=RoomGame(cleaning=CleaningState())
    @Test fun newAreasAndTheirToolsReachTheWholeRealFloor() {
        assertEquals(20,cleaningMess().size)
        for(m in expandedCleaningMess()) {
            assertTrue(m.id,RoomFloor.contains(m.position.x-m.size.x/2,m.position.x+m.size.x/2,
                m.position.z-m.size.z/2,m.position.z+m.size.z/2))
            val tool=if(m.kind==MessKind.Spill) CleanTool.Mop else CleanTool.Vacuum
            val held=rules.reduce(fresh(),RoomAction.Begin("@clean:${tool.name}"))
            val moved=move(held,m.position)
            assertEquals(m.position.x,moved.cleaning!!.position.x,.001f)
            assertEquals(m.position.z,moved.cleaning!!.position.z,.001f)
        }
        val held=rules.reduce(fresh(),RoomAction.Begin("@clean:Vacuum"))
        val free=move(held,Point3(2.8f,0f,2.8f)).cleaning!!
        assertTrue((Point3(2.8f,0f,2.8f)-free.position).length()<.00001f)
    }
    @Test fun mopWetsLocallyBeforeSweptWipingAndStationaryHoldCannotClearIt() {
        val mess=cleaningMess().first { it.id=="spill-0" }
        var s=rules.reduce(fresh(),RoomAction.Begin("@clean:Mop"))
        s=move(s,CleaningGrid.centre(mess,10))
        repeat(8) { s=rules.reduce(s,RoomAction.CleanTick(.05f)) }
        assertEquals(0f,s.cleaning!!.rawAmount(mess.id),0f)
        assertTrue(s.cleaning!!.wetTiles.getValue(mess.id).any { it==1f })
        assertTrue(s.cleaning!!.wetTiles.getValue(mess.id).any { it==0f })
        s=move(s,s.cleaning!!.position+Point3(.07f,0f,0f))
        assertTrue(s.cleaning!!.amount(mess.id)>0f)
        assertTrue(s.cleaning!!.amount(mess.id)<1f)
        val undone=rules.reduce(s,RoomAction.Cancel)
        assertEquals(0f,undone.cleaning!!.amount(mess.id),0f)
        assertTrue(undone.cleaning!!.wetTiles.isEmpty())
    }
    @Test fun pairTurnsPauseWetContactAndLockTheHeldPosition() {
        val mess=cleaningMess().first { it.id=="spill-0" }
        var s=move(rules.reduce(fresh(),RoomAction.Begin("@clean:Mop")),mess.position)
        val before=s.cleaning!!
        s=move(s,before.position+Point3(.09f,.05f,.03f),2)
        repeat(6) { s=rules.reduce(s,RoomAction.CleanTick(.075f)) }
        assertEquals(before.position,s.cleaning!!.position)
        assertEquals(before.wetTiles,s.cleaning!!.wetTiles)
        assertEquals(before.progress,s.cleaning!!.progress)
    }
    @Test fun openingAcceptsNearReleaseWithHysteresisAndCancelAwardsNothing() {
        var s=rules.reduce(fresh(),RoomAction.Begin("@trash:trash-0"))
        s=move(s,CLEAN_BIN+Point3(.17f,.12f,0f))
        assertTrue(s.cleaning!!.binReady)
        assertEquals(0f,s.cleaning!!.amount("trash-0"),0f)
        s=move(s,CLEAN_BIN+Point3(.225f,.12f,0f))
        assertTrue(s.cleaning!!.binReady)
        assertEquals(0f,rules.reduce(s,RoomAction.Cancel).cleaning!!.amount("trash-0"),0f)
        s=rules.reduce(s,RoomAction.End)
        assertEquals(1f,s.cleaning!!.amount("trash-0"),0f)
        assertEquals("trash-0",s.cleaning!!.disposedId)
        assertEquals(1L,s.cleaning!!.disposalSequence)
    }
    @Test fun smallOvershootAcrossOpeningIsAcceptedButLongJumpAndFarReleaseAreNot() {
        val c=CleaningState(heldTrash="trash-0",previousTrash=CLEAN_BIN+Point3(-.22f,.10f,0f),
            position=CLEAN_BIN+Point3(.22f,.10f,0f))
        assertFalse(CleaningBin.captures(c))
        assertTrue(CleaningBin.captures(c.copy(previousTrash=CLEAN_BIN+Point3(.06f,.1f,0f),
            position=CLEAN_BIN+Point3(.30f,.1f,0f))))
        assertFalse(CleaningBin.captures(c.copy(position=CLEAN_BIN+Point3(.40f,.1f,0f))))
    }
    @Test fun binCarriesToStudySettlesAtFloorAndCancelRestoresPose() {
        var s=rules.reduce(fresh(),RoomAction.Begin("@clean:Bin"))
        s=move(s,Point3(-.7f,.1f,1.4f))
        val held=s.cleaning!!
        val pair=move(s,held.position+Point3(.08f,0f,0f),2)
        assertEquals(held.position,pair.cleaning!!.binPosition)
        val cancel=rules.reduce(pair,RoomAction.Cancel)
        assertEquals(CLEAN_BIN,cancel.cleaning!!.binPosition)
        s=rules.reduce(s,RoomAction.End)
        assertEquals(-.025f,s.cleaning!!.binPosition.y,.0001f)
        s=move(rules.reduce(s,RoomAction.Begin("@trash:trash-3")),s.cleaning!!.binPosition+Point3(.14f,.1f,0f))
        assertEquals(1f,rules.reduce(s,RoomAction.End).cleaning!!.amount("trash-3"),0f)
    }
    @Test fun migrationPreservesOriginalCellsAndMeanAndCompletedStage() {
        val c=CleaningState(progress=mapOf("debris-0" to 1f,"wall-0" to .25f),
            workTiles=mapOf("wall-0" to List(24) { .25f }))
        val original=fresh().copy(cleaning=c)
        val restored=CleaningExpansionPersistence.restore(RoomPersistence.encode(original),null)!!
        assertEquals(original.items,restored.items)
        assertEquals(c.workTiles,restored.cleaning!!.workTiles)
        assertEquals(1f,restored.cleaning!!.amount("debris-0"),0f)
        val legacyMean=legacyCleaningMess().map { c.amount(it.id) }.average().toFloat()
        assertEquals((legacyMean*100).toInt(),restored.cleaning!!.percent)
        assertEquals(legacyMean,restored.cleaning!!.rawAmount("dust-2"),.00001f)
        val completed=fresh().copy(cleaning=c.completed())
        assertTrue(CleaningExpansionPersistence.restore(RoomPersistence.encode(completed),null)!!.decorating)
    }
    @Test fun sidecarCannotPrematurelyUnlockOrContaminateLegacySnapshot() {
        val c=CleaningState(progress=cleaningMess().associate { it.id to if(it.id=="spill-0") .2f else 1f },
            workTiles=mapOf("spill-0" to List(24) { .2f }))
        val state=fresh().copy(cleaning=c)
        val main=RoomPersistence.encode(state)
        assertFalse(main.lines().first().contains("spill-"))
        assertFalse(main.lines().first().contains("dust-2"))
        assertNotNull(RoomPersistence.decode(main)) // strict legacy reader can still read the furniture
        val restored=CleaningExpansionPersistence.restore(main,CleaningExpansionPersistence.encode(state))!!
        assertFalse(restored.decorating)
        assertEquals(.2f,restored.cleaning!!.amount("spill-0"),.00001f)
        assertEquals(state.items,restored.items)
        assertEquals(c.workTiles,restored.cleaning!!.workTiles)
    }
    @Test fun typedSaveCommitsBothPartsAndCancelledWipeDoesNotCommitHalfWork() {
        var saved: RoomGame?=null
        val vm=RoomViewModel(fresh()) { saved=it }
        vm.onEvent(RoomAction.Begin("@clean:Mop"))
        val p=cleaningMess().first { it.id=="spill-0" }.position
        vm.onEvent(RoomAction.Transform(GrabDelta(p-vm.currentGame.cleaning!!.position,Rotation())))
        repeat(4) { vm.onEvent(RoomAction.CleanTick(.075f)) }
        assertNull(saved)
        vm.onEvent(RoomAction.Cancel)
        val restore=CleaningExpansionPersistence.restore(RoomPersistence.encode(saved!!),CleaningExpansionPersistence.encode(saved!!))!!
        assertTrue(restore.cleaning!!.wetTiles.isEmpty())
        assertEquals(0f,restore.cleaning!!.rawAmount("spill-0"),0f)
    }
    @Test fun corruptExtraDataCannotLoseFurnitureOrOldProgress() {
        val original=fresh().copy(cleaning=CleaningState(mapOf("debris-0" to 1f)))
        val restored=CleaningExpansionPersistence.restore(RoomPersistence.encode(original),"broken\tgarbage")!!
        assertEquals(original.items,restored.items)
        assertEquals(1f,restored.cleaning!!.amount("debris-0"),0f)
        assertFalse(restored.decorating)
    }
    @Test fun binPoseValidationUsesRotatedBoundsAndBadPoseKeepsExtraCleaningWork() {
        val state=fresh().copy(cleaning=CleaningState(progress=mapOf("trash-3" to 1f,"spill-0" to .4f),
            binPosition=Point3(-.7f,-.025f,2.10f),binRotation=Rotation.yaw(90f)))
        val main=RoomPersistence.encode(state)
        val restored=CleaningExpansionPersistence.restore(main,CleaningExpansionPersistence.encode(state))!!
        assertEquals(state.cleaning!!.binPosition,restored.cleaning!!.binPosition)
        assertEquals(90f,restored.cleaning!!.binRotation.yaw(),.001f)
        val outside=state.copy(cleaning=state.cleaning.copy(binPosition=Point3(5f,3f,5f)))
        val repaired=CleaningExpansionPersistence.restore(main,CleaningExpansionPersistence.encode(outside))!!
        assertEquals(CLEAN_BIN,repaired.cleaning!!.binPosition)
        assertEquals(.4f,repaired.cleaning!!.amount("spill-0"),0f)
        assertEquals(1f,repaired.cleaning!!.amount("trash-3"),0f)
    }
}
