package com.example.tidyspace.pc5853b2a.domain.usecase

import com.example.tidyspace.pc5853b2a.domain.model.*
import com.example.tidyspace.pc5853b2a.data.repository.RoomPersistence
import com.example.tidyspace.pc5853b2a.ui.sorting.RoomViewModel
import org.junit.Assert.*
import org.junit.Test

class CleaningRulesTest {
    private val rules=RoomUseCase()
    private fun start()=RoomGame(cleaning=CleaningState())
    private fun move(s: RoomGame,p: Point3)=rules.reduce(s,RoomAction.Transform(
        GrabDelta(p-checkNotNull(s.cleaning).position,Rotation())))
    private fun stroke(s: RoomGame,tool: CleanTool,id: String): RoomGame {
        val mess=cleaningMess().first { it.id==id }
        val p=mess.position
        var next=rules.reduce(s,RoomAction.Begin("@clean:${tool.name}"))
        if(mess.kind==MessKind.Debris) {
            next=move(next,p-Point3(.08f,0f,0f))
            repeat(8) { i->next=move(next,p+Point3(if(i%2==0) .08f else -.08f,0f,0f)) }
        } else repeat(3) {
            repeat(CleaningGrid.ROWS) { row ->
                val centre=CleaningGrid.centre(mess,row*8)
                val steps=kotlin.math.ceil(mess.size.x/.06f).toInt()
                next=move(next,centre.copy(x=p.x-mess.size.x/2))
                repeat(steps) { col->next=move(next,centre.copy(x=p.x-mess.size.x/2+(col+1)*mess.size.x/steps)) }
            }
        }
        return rules.reduce(next,RoomAction.End)
    }
    private fun bin(s: RoomGame,id: String): RoomGame {
        var next=rules.reduce(s,RoomAction.Begin("@trash:$id"))
        next=move(next,CLEAN_BIN+Point3(0f,.13f,0f))
        return rules.reduce(next,RoomAction.End)
    }
    @Test fun newPlayerStartsDirtyAndCannotFurnishOrOpenFurniture() {
        val vm=RoomViewModel()
        assertFalse(vm.state.value.game.decorating)
        vm.onEvent(RoomAction.Add("chair"))
        vm.onEvent(RoomAction.Begin("crate:lid"))
        assertEquals(13,vm.state.value.game.items.size)
        assertNull(vm.state.value.game.selectedId)
        assertFalse(vm.state.value.game.crateOpen)
    }
    @Test fun onlyMatchingToolCleansEachSurface() {
        assertEquals(0f,stroke(start(),CleanTool.Broom,"dust-0").cleaning!!.amount("dust-0"),0f)
        assertEquals(0f,stroke(start(),CleanTool.Vacuum,"wall-0").cleaning!!.amount("wall-0"),0f)
        assertEquals(1f,stroke(start(),CleanTool.Vacuum,"dust-0").cleaning!!.amount("dust-0"),0f)
        assertEquals(.5f,stroke(start(),CleanTool.Broom,"debris-0").cleaning!!.amount("debris-0"),0f)
        assertEquals(1f,stroke(start(),CleanTool.Brush,"wall-0").cleaning!!.amount("wall-0"),0f)
    }
    @Test fun clickDistanceVerticalMotionAndJumpAwardNothing() {
        var s=rules.reduce(start(),RoomAction.Begin("@clean:Vacuum"))
        s=rules.reduce(s,RoomAction.End)
        assertEquals(0,s.cleaning!!.count)
        s=rules.reduce(s,RoomAction.Begin("@clean:Vacuum"))
        s=move(s,cleaningMess().first().position+Point3(0f,.15f,0f))
        repeat(10) { s=move(s,s.cleaning!!.position+Point3(.02f,0f,0f)) }
        assertEquals(0,s.cleaning!!.count)
        s=move(s,cleaningMess().first().position)
        s=move(s,s.cleaning!!.position+Point3(.9f,0f,0f))
        assertEquals(0,s.cleaning!!.count)
    }
    @Test fun floorToolsIgnoreWristTiltAndStillCleanNaturally() {
        var s=rules.reduce(start(),RoomAction.Begin("@clean:Vacuum"))
        s=move(s,cleaningMess().first().position)
        s=rules.reduce(s,RoomAction.Transform(GrabDelta(Point3(.02f,0f,0f),Rotation.axis(Point3(1f,0f,0f),180f))))
        assertEquals(1f,s.cleaning!!.rotation.rotate(Point3(0f,1f,0f)).y,.0001f)
        assertTrue(s.cleaning!!.amount("dust-0")>0f)
    }
    @Test fun wallBrushIgnoresSingleWristTiltAndKeepsCleaningFaceAgainstWall() {
        var s=rules.reduce(start(),RoomAction.Begin("@clean:Brush"))
        s=move(s,cleaningMess().first { it.kind==MessKind.Wall }.position)
        s=rules.reduce(s,RoomAction.Transform(GrabDelta(Point3(.02f,0f,0f),Rotation.axis(Point3(1f,0f,0f),180f))))
        assertEquals(1f,s.cleaning!!.rotation.rotate(Point3(0f,1f,0f)).y,.0001f)
        assertTrue(s.cleaning!!.amount("wall-0")>0f)
    }
    @Test fun offCentreSweepLeavesUntouchedCellsDirty() {
        val p=cleaningMess().first().position+Point3(0f,.04f,.10f)
        var s=rules.reduce(start(),RoomAction.Begin("@clean:Vacuum"))
        s=move(s,p-Point3(.10f,0f,0f))
        s=move(s,p+Point3(.10f,0f,0f))
        assertTrue(s.cleaning!!.amount("dust-0") in .001f.. .6f)
        val cells=s.cleaning!!.workTiles.getValue("dust-0")
        assertTrue(cells.any { it>0f });assertTrue(cells.any { it==0f })
        assertEquals(0f,s.cleaning!!.amount("dust-1"),0f)
    }
    @Test fun trashStaysUprightWhileTranslatingAndTurning() {
        var s=rules.reduce(start(),RoomAction.Begin("@trash:trash-0"))
        s=rules.reduce(s,RoomAction.Transform(GrabDelta(Point3(.10f,.15f,0f),
            Rotation.yaw(37f)*Rotation.axis(Point3(1f,0f,0f),65f))))
        assertEquals(1f,s.cleaning!!.rotation.rotate(Point3(0f,1f,0f)).y,.0001f)
        assertEquals(0f,s.cleaning!!.rotation.yaw(),.001f)
        val position=s.cleaning!!.position
        s=rules.reduce(s,RoomAction.Transform(GrabDelta(Point3(.1f,.1f,.1f),
            Rotation.yaw(37f),pointerCount=2)))
        assertEquals(position,s.cleaning!!.position)
        assertEquals(37f,s.cleaning!!.rotation.yaw(),.001f)
    }
    @Test fun trashHasForgivingOpeningButRejectsClearlyOutsideAndBelowIt() {
        assertEquals(1f,bin(start(),"trash-0").cleaning!!.amount("trash-0"),0f)
        var s=rules.reduce(start(),RoomAction.Begin("@trash:trash-0"))
        s=move(s,CLEAN_BIN+Point3(.36f,.13f,0f));s=rules.reduce(s,RoomAction.End)
        assertEquals(0f,s.cleaning!!.amount("trash-0"),0f)
        s=rules.reduce(s,RoomAction.Begin("@trash:trash-0"))
        s=move(s,CLEAN_BIN+Point3(0f,-.18f,0f));s=rules.reduce(s,RoomAction.End)
        assertEquals(0f,s.cleaning!!.amount("trash-0"),0f)
        assertNull(s.cleaning!!.heldTrash)
    }
    @Test fun lostCaptureRollsBackStrokeAndDoesNotPersistHalfOperation() {
        var s=rules.reduce(start(),RoomAction.Begin("@clean:Vacuum"))
        s=move(s,cleaningMess().first().position)
        s=move(s,s.cleaning!!.position+Point3(.03f,0f,0f))
        assertTrue(s.cleaning!!.amount("dust-0")>0f)
        assertEquals(0f,RoomPersistence.decode(RoomPersistence.encode(s))!!.cleaning!!.amount("dust-0"),0f)
        val cancelled=rules.reduce(s,RoomAction.Cancel)
        assertEquals(0f,cancelled.cleaning!!.amount("dust-0"),0f)
        assertNull(cancelled.selectedId)
    }
    @Test fun wholeSurfaceAndTwoStageDebrisUnlockFurnishingAndKeepLayout() {
        val original=start().items
        var s=start()
        for(m in cleaningMess()) s=when(m.kind) {
            MessKind.Trash->bin(s,m.id)
            MessKind.Dust->stroke(s,CleanTool.Vacuum,m.id)
            MessKind.Debris->stroke(stroke(s,CleanTool.Broom,m.id),CleanTool.Vacuum,m.id)
            MessKind.Wall->stroke(s,CleanTool.Brush,m.id)
            MessKind.Spill->stroke(s,CleanTool.Mop,m.id)
        }
        assertEquals(20,s.cleaning!!.count)
        assertEquals(100,s.cleaning!!.percent)
        assertTrue(s.decorating)
        assertEquals(original,s.items)
        assertEquals(1,s.celebration)
        assertEquals(14,rules.reduce(s,RoomAction.Add("chair")).items.size)
    }
    @Test fun finalSpotIsRequiredAndReplayKeepsFurniture() {
        val almost=start().copy(cleaning=CleaningState(progress=cleaningMess().drop(1).associate { it.id to 1f }))
        assertFalse(almost.decorating)
        val complete=stroke(almost,CleanTool.Vacuum,"dust-0")
        val replay=rules.reduce(complete,RoomAction.CleanAgain)
        assertFalse(replay.decorating);assertEquals(0,replay.cleaning!!.count)
        assertEquals(1,replay.items.count { it.spec.assetId=="chair" })
        assertEquals(uniqueCatalogue().size,replay.remainingGoods)
        assertTrue(replay.items.filter { it.spec.kind!=Furnishing.MovingBox }.all { it.packed })
    }
    @Test fun v6SavesPartialProgressAndRejectsFalseCompletion() {
        val partial=start().copy(cleaning=CleaningState(mapOf("dust-1" to .3f,"trash-2" to 1f)))
        val restored=RoomPersistence.decode(RoomPersistence.encode(partial))!!
        assertEquals(partial.cleaning!!.progress,restored.cleaning!!.progress)
        assertFalse(restored.decorating)
        assertNull(RoomPersistence.decode(RoomPersistence.encode(partial.copy(cleaning=partial.cleaning.copy(finished=true)))))
    }
    @Test fun oldVersionMigrationKeepsEverythingAndStartsOneCleaningSession() {
        val old=RoomProgress.settle(RoomGame(crateOpen=true,mood=RoomMood.Evening))
        val text=RoomPersistence.encode(old).lines().mapIndexed { index,line ->
            if(index==0) line.split('\t').take(12).joinToString("\t").replace("tidy-room-6","tidy-room-4") else line
        }.joinToString("\n")
        val migrated=RoomPersistence.decode(text)!!
        assertEquals(old.items,migrated.items);assertEquals(old.achievements,migrated.achievements)
        assertEquals(old.mood,migrated.mood);assertTrue(migrated.crateOpen)
        assertFalse(migrated.decorating)
        assertFalse(RoomPersistence.decode(RoomPersistence.encode(migrated))!!.decorating)
    }
    @Test fun completedCleaningSurvivesFurnitureUndo() {
        val done=CleaningState(cleaningMess().associate { it.id to 1f },finished=true)
        val vm=RoomViewModel(RoomPersistence.encode(RoomGame(cleaning=done)))
        vm.onEvent(RoomAction.Add("chair"));vm.onEvent(RoomAction.Undo)
        assertTrue(vm.state.value.game.decorating)
        assertEquals(20,vm.state.value.game.cleaning!!.count)
    }
    @Test fun v5MergeRetainsOldFurnitureAndPartialCleaningWithoutRestart() {
        val oldWork=(0..5).joinToString(",") { "dust-$it:1.0" }+",trash-0:1.0,trash-1:1.0"
        val lines=RoomPersistence.encode(start()).lines().toMutableList()
        val h=lines[0].split('\t').toMutableList()
        h[0]="tidy-room-5";h[13]=oldWork;lines[0]=h.joinToString("\t")
        val restored=RoomPersistence.decode(lines.joinToString("\n"))!!
        assertEquals(start().items,restored.items)
        assertEquals(1f,restored.cleaning!!.amount("dust-0"),0f)
        assertEquals(.5f,restored.cleaning!!.amount("trash-0"),0f)
        assertEquals(0f,restored.cleaning!!.amount("dust-1"),0f)
        assertEquals(restored.cleaning!!.progress,RoomPersistence.decode(RoomPersistence.encode(restored))!!.cleaning!!.progress)
    }
    @Test fun completedV5SaveStaysUnlockedAfterMerge() {
        val lines=RoomPersistence.encode(start()).lines().toMutableList()
        val h=lines[0].split('\t').toMutableList()
        h[0]="tidy-room-5";h[12]="true"
        h[13]=listOf("dust","debris","wall","trash").flatMap { k->(0..11).map { "$k-$it:1.0" } }.joinToString(",")
        lines[0]=h.joinToString("\t")
        val restored=RoomPersistence.decode(lines.joinToString("\n"))!!
        assertTrue(restored.decorating)
        assertEquals(100,restored.cleaning!!.percent)
    }
    @Test fun fireplaceBlocksFurnitureAndBalconyHasRealFloorAndRailingLimits() {
        val done=CleaningState(cleaningMess().associate { it.id to 1f },finished=true)
        val s=RoomGame(cleaning=done,crateOpen=true)
        fun put(p: Point3): RoomGame {
            val held=rules.reduce(s,RoomAction.Begin("plant"))
            return rules.reduce(rules.reduce(held,RoomAction.Transform(GrabDelta(p-held.item("plant").position,Rotation()))),RoomAction.End)
        }
        val balcony=put(Point3(1.30f*RoomLayout.scale,.10f,.10f*RoomLayout.scale))
        assertEquals(1.30f*RoomLayout.scale,balcony.item("plant").position.x,.001f)
        assertEquals(-.24f,balcony.item("plant").position.y,.001f)
        assertEquals(s.item("plant"),put(Point3(-1.018f*RoomLayout.scale+.04f,.10f,.29f*RoomLayout.scale)).item("plant"))
        assertEquals(s.item("plant"),put(Point3(RoomLayout.balconyRight,.10f,.10f*RoomLayout.scale)).item("plant"))
    }
}
