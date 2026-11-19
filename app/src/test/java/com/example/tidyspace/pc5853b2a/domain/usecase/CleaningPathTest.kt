package com.example.tidyspace.pc5853b2a.domain.usecase

import com.example.tidyspace.pc5853b2a.domain.model.*
import com.example.tidyspace.pc5853b2a.data.repository.RoomPersistence
import org.junit.Assert.*
import org.junit.Test

class CleaningPathTest {
    private val rules=RoomUseCase()
    private fun move(s: RoomGame,p: Point3)=rules.reduce(s,RoomAction.Transform(GrabDelta(p-s.cleaning!!.position,Rotation())))
    private fun sweep(s: RoomGame,tool: CleanTool,id: String): RoomGame {
        val p=cleaningMess().first { it.id==id }.position
        var n=rules.reduce(s,RoomAction.Begin("@clean:${tool.name}"))
        n=move(n,p-Point3(.08f,0f,0f))
        repeat(10) { n=move(n,p+Point3(if(it%2==0) .08f else -.08f,0f,0f)) }
        return rules.reduce(n,RoomAction.End)
    }
    @Test fun looseDebrisCanBeVacuumedAndOptionalBroomStillGathersHalf() {
        val s=RoomGame(cleaning=CleaningState())
        assertEquals(1f,sweep(s,CleanTool.Vacuum,"debris-0").cleaning!!.amount("debris-0"),0f)
        val gathered=sweep(s,CleanTool.Broom,"debris-0")
        assertEquals(.5f,gathered.cleaning!!.amount("debris-0"),0f)
        assertEquals(.5f,sweep(gathered,CleanTool.Broom,"debris-0").cleaning!!.amount("debris-0"),0f)
        assertEquals(1f,sweep(gathered,CleanTool.Vacuum,"debris-0").cleaning!!.amount("debris-0"),0f)
    }
    @Test fun localCommittedWorkRoundTripsAndHeldWorkRollsBack() {
        var s=RoomGame(cleaning=CleaningState(progress=mapOf("dust-0" to .37f)))
        s=rules.reduce(s,RoomAction.Begin("@clean:Vacuum"))
        val p=CleaningGrid.centre(cleaningMess().first(),9)
        s=move(s,p);s=move(s,p+Point3(.04f,0f,0f))
        assertEquals(.37f,RoomPersistence.decode(RoomPersistence.encode(s))!!.cleaning!!.amount("dust-0"),0f)
        val held=s
        s=rules.reduce(s,RoomAction.End)
        val text=RoomPersistence.encode(s)
        assertTrue(text.startsWith("tidy-room-11"))
        val restored=RoomPersistence.decode(text)!!
        assertEquals(s.cleaning!!.workTiles,restored.cleaning!!.workTiles)
        assertEquals(s.cleaning!!.progress,restored.cleaning!!.progress)
        assertEquals(.37f,rules.reduce(held,RoomAction.Cancel).cleaning!!.amount("dust-0"),0f)
        assertTrue(rules.reduce(held,RoomAction.Cancel).cleaning!!.workTiles.isEmpty())
        assertTrue(restored.cleaning!!.workTiles.getValue("dust-0").any { it==.37f })
    }
    @Test fun v11RejectsMalformedGridAndMismatchedMean() {
        val mess=cleaningMess().first()
        val values=List(24) { if(it==0) 1f else 0f }
        val s=RoomGame(cleaning=CleaningState(progress=mapOf(mess.id to 1f/24),
            workTiles=mapOf(mess.id to values)))
        val valid=RoomPersistence.encode(s)
        assertNotNull(RoomPersistence.decode(valid))
        assertNull(RoomPersistence.decode(valid.replace(values.joinToString(","),"NaN,"+values.drop(1).joinToString(","))))
        assertNull(RoomPersistence.decode(valid.replace("dust-0:${1f/24}","dust-0:0.9")))
        assertNull(RoomPersistence.decode(valid.replace(values.joinToString(","),values.drop(1).joinToString(","))))
    }
    @Test fun stationaryRemoteAndTeleportFramesNeverClearCells() {
        var s=rules.reduce(RoomGame(cleaning=CleaningState()),RoomAction.Begin("@clean:Brush"))
        val p=cleaningMess().first { it.kind==MessKind.Wall }.position
        s=move(s,p)
        repeat(20) { s=move(s,p) }
        assertTrue(s.cleaning!!.workTiles.isEmpty())
        s=move(s,p+Point3(0f,0f,.15f))
        repeat(10) { s=move(s,s.cleaning!!.position+Point3(.02f,0f,0f)) }
        assertTrue(s.cleaning!!.workTiles.isEmpty())
        s=move(s,p+Point3(-.2f,0f,0f));s=move(s,p+Point3(.4f,0f,0f))
        assertTrue(s.cleaning!!.workTiles.isEmpty())
    }
    @Test fun actualHeadsetInvisibleWallCornerCannotBlockCompletionOrAlterOtherData() {
        val cells=List(CleaningGrid.COUNT) { if(it==0) 0f else 1f }
        val c=CleaningState(progress=cleaningMess().associate { it.id to
            if(it.id=="wall-1") cells.average().toFloat() else 1f },
            workTiles=mapOf("wall-1" to cells))
        val old=MovingHome.prepare(RoomGame(cleaning=c,playCounts=mapOf("woodfish" to 23L),
            achievements=setOf("guide-1"),dayHour=18.5f))
        assertEquals(.9583333f,c.rawAmount("wall-1"),.000001f)
        assertEquals(1f,c.amount("wall-1"),0f)
        assertTrue(CleaningVisibility.cells(c,cleaningMess().first { it.id=="wall-1" }).all { it==1f })
        val repaired=RoomPersistence.decode(RoomPersistence.encode(old))!!
        assertTrue(repaired.decorating)
        assertEquals(100,repaired.cleaning!!.percent)
        assertEquals(old.items,repaired.items);assertEquals(old.boxStock,repaired.boxStock)
        assertEquals(old.playCounts,repaired.playCounts);assertEquals(old.achievements,repaired.achievements)
        assertEquals(old.dayHour,repaired.dayHour,0f)
        assertTrue(repaired.cleaning!!.workTiles.values.all { values->values.all { it==1f } })
        assertTrue(repaired.cleaning!!.progress.values.all { it==1f })
        assertTrue(RoomPersistence.decode(RoomPersistence.encode(repaired))!!.decorating)
    }
    @Test fun acceptedVisibleCompletionCommitsRawMeansForOlderV11Readers() {
        val values=List(24) { if(it==0) 0f else 1f }
        val state=RoomGame(cleaning=CleaningState(progress=cleaningMess().associate {
            it.id to if(it.id=="wall-1") values.average().toFloat() else 1f },
            workTiles=mapOf("wall-1" to values)))
        val completed=rules.reduce(state,RoomAction.End)
        assertTrue(completed.decorating)
        assertTrue(cleaningMess().all { completed.cleaning!!.rawAmount(it.id)==1f })
        assertTrue(completed.cleaning!!.workTiles.values.all { it.average()==1.0 })
        assertTrue(RoomPersistence.decode(RoomPersistence.encode(completed))!!.decorating)
    }
    @Test fun invisiblePaddingAndFaintTailsDisappearButUntouchedCentreStillNeedsCleaning() {
        val m=cleaningMess().first { it.id=="wall-1" }
        val pad=CleaningVisibility.cells(CleaningState(),m)
        assertEquals(1f,pad[0],0f);assertEquals(1f,pad[7],0f)
        assertEquals(0f,pad[10],0f)
        val dirtyCentre=List(24) { if(it==10) 0f else 1f }
        val c=CleaningState(progress=mapOf(m.id to dirtyCentre.average().toFloat()),
            workTiles=mapOf(m.id to dirtyCentre))
        assertFalse(c.amount(m.id)==1f)
        assertEquals(0f,CleaningVisibility.cells(c,m)[10],0f)
        val faint=dirtyCentre.map { if(it==0f) .91f else it }
        assertEquals(1f,c.copy(progress=mapOf(m.id to faint.average().toFloat()),
            workTiles=mapOf(m.id to faint)).amount(m.id),0f)
    }
    @Test fun oneBroadPassPerRowCleansAllFourRealSurfaceFootprints() {
        for(m in cleaningMess().filter { it.kind==MessKind.Dust || it.kind==MessKind.Wall }) {
            var s=rules.reduce(RoomGame(cleaning=CleaningState()),RoomAction.Begin(
                "@clean:${if(m.kind==MessKind.Wall) "Brush" else "Vacuum"}"))
            repeat(CleaningGrid.ROWS) { row ->
                val p=CleaningGrid.centre(m,row*8)
                val steps=kotlin.math.ceil(m.size.x/.04f).toInt()
                s=move(s,p.copy(x=m.position.x-m.size.x/2))
                repeat(steps) { i->s=move(s,p.copy(x=m.position.x-m.size.x/2+(i+1)*m.size.x/steps)) }
            }
            assertEquals(m.id,1f,s.cleaning!!.amount(m.id),0f)
        }
    }
    @Test fun stationarySuctionWorksLocallyAndCancelRollsBackWithoutSaving() {
        val m=cleaningMess().first()
        var s=rules.reduce(RoomGame(cleaning=CleaningState()),RoomAction.Begin("@clean:Vacuum"))
        s=move(s,CleaningGrid.centre(m,10))
        repeat(24) { s=rules.reduce(s,RoomAction.CleanTick(.033f)) }
        val tiles=s.cleaning!!.workTiles.getValue(m.id)
        assertTrue(tiles[10]>=.90f);assertEquals(0f,tiles[22],0f)
        assertTrue(s.cleaning!!.sequence>0)
        assertEquals(0f,RoomPersistence.decode(RoomPersistence.encode(s))!!.cleaning!!.amount(m.id),0f)
        assertEquals(0f,rules.reduce(s,RoomAction.Cancel).cleaning!!.amount(m.id),0f)
        val saved=rules.reduce(s,RoomAction.End)
        assertEquals(saved.cleaning!!.workTiles,RoomPersistence.decode(RoomPersistence.encode(saved))!!.cleaning!!.workTiles)
    }
    @Test fun suctionRejectsRemoteUnheldInvalidAndTwoHandFramesAndBoundsLongPause() {
        val m=cleaningMess().first()
        val initial=RoomGame(cleaning=CleaningState())
        assertEquals(initial,rules.reduce(initial,RoomAction.CleanTick(1f)))
        var s=rules.reduce(initial,RoomAction.Begin("@clean:Vacuum"))
        s=move(s,m.position+Point3(0f,.15f,0f))
        repeat(30) { s=rules.reduce(s,RoomAction.CleanTick(.033f)) }
        assertTrue(s.cleaning!!.workTiles.isEmpty())
        s=move(s,CleaningGrid.centre(m,10))
        // Isolate tick accumulation from the preceding free three-axis stroke.
        val contact=s.copy(cleaning=s.cleaning!!.copy(progress=emptyMap(),workTiles=emptyMap()))
        assertEquals(s,rules.reduce(s,RoomAction.CleanTick(Float.NaN)))
        s=rules.reduce(s,RoomAction.Transform(GrabDelta(Point3(0f,0f,0f),Rotation(),pointerCount=2)))
        val paired=s
        repeat(30) { s=rules.reduce(s,RoomAction.CleanTick(.033f)) }
        assertEquals(paired,s)
        val resumed=rules.reduce(contact,RoomAction.CleanTick(20f))
        val increase=resumed.cleaning!!.workTiles.getValue(m.id)[10]-
            CleaningGrid.cells(contact.cleaning!!,m)[10]
        assertTrue(increase in .001f.. .108f)
        val wall=rules.reduce(initial,RoomAction.Begin("@clean:Brush"))
        assertEquals(wall,rules.reduce(wall,RoomAction.CleanTick(1f)))
    }
    @Test fun freePairTurnAtRoomBoundaryStillPausesSuction() {
        val c=CleaningState(tool=CleanTool.Vacuum,position=Point3(RoomLayout.left+.080f,.1f,0f),
            origin=emptyMap())
        val s=RoomGame(selectedId="@clean:Vacuum",cleaning=c)
        val paired=rules.reduce(s,RoomAction.Transform(GrabDelta(Point3(0f,0f,0f),
            Rotation.yaw(90f),pointerCount=2)))
        assertEquals(c.position,paired.cleaning!!.position)
        assertEquals(90f,paired.cleaning!!.rotation.yaw(),.001f)
        assertTrue(paired.twoHandTurning)
        assertEquals(paired,rules.reduce(paired,RoomAction.CleanTick(.033f)))
    }
}
