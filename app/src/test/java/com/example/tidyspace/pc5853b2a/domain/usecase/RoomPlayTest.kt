package com.example.tidyspace.pc5853b2a.domain.usecase

import com.example.tidyspace.pc5853b2a.domain.model.*
import com.example.tidyspace.pc5853b2a.data.repository.RoomPersistence
import com.example.tidyspace.pc5853b2a.ui.sorting.RoomViewModel
import org.junit.Assert.*
import org.junit.Test

class RoomPlayTest {
    private val rules=RoomUseCase()
    private fun added(asset: String)=rules.reduce(RoomGame(),RoomAction.Add(asset))
    private fun tap(s: RoomGame,id: String)=rules.reduce(rules.reduce(s,RoomAction.Begin("@play:$id")),RoomAction.End)
    @Test fun repeatedWoodfishStrikesAwardExactlyOncePerCompletedCapture() {
        var s=added("woodfish")
        val id=s.focusId!!
        repeat(10) { index ->
            s=tap(s,id)
            assertEquals((index+1).toLong(),s.merit)
            val ended=rules.reduce(s,RoomAction.End)
            assertEquals(s.merit,ended.merit)
        }
        assertTrue("zen" in s.achievements)
        val xp=s.xp
        repeat(20) { s=tap(s,id) }
        assertEquals(30,s.merit)
        assertEquals(xp,s.xp)
    }
    @Test fun cancelAndBodyGrabDoNotCountAsStrikes() {
        val s=added("woodfish");val id=s.focusId!!
        val held=rules.reduce(s,RoomAction.Begin("@play:$id"))
        assertEquals(0,rules.reduce(held,RoomAction.Cancel).merit)
        assertEquals(0,rules.reduce(rules.reduce(s,RoomAction.Begin(id)),RoomAction.End).merit)
        assertNull(rules.reduce(s,RoomAction.Begin("@play:desk")).selectedId)
    }
    @Test fun draggingStrikeZoneCancelsInsteadOfAwardingAccidentally() {
        val s=added("woodfish");val id=s.focusId!!
        val held=rules.reduce(rules.reduce(s,RoomAction.Begin("@play:$id")),
            RoomAction.Transform(GrabDelta(Point3(.12f,0f,0f),Rotation())))
        val next=rules.reduce(held,RoomAction.End)
        assertEquals(0,next.merit)
        assertEquals(s.items,next.items)
    }
    @Test fun musicRequiresCrankingAndStopsOnRelease() {
        val s=added("musicbox");val id=s.focusId!!
        assertTrue(tap(s,id).playCounts.isEmpty())
        var held=rules.reduce(s,RoomAction.Begin("@play:$id"))
        repeat(4) { held=rules.reduce(held,RoomAction.Transform(GrabDelta(Point3(0f,0f,0f),Rotation.axis(Point3(1f,0f,0f),90f)))) }
        val end=rules.reduce(held,RoomAction.End)
        assertEquals(4L,end.playCounts[id])
        assertEquals(0f,end.crankRadians,0f)
        assertNull(end.selectedId)
        assertEquals(end.playCounts,rules.reduce(end,RoomAction.End).playCounts)
    }
    @Test fun separateInstancesAndAllThreeInstrumentsHaveSeparateCounts() {
        var s=added("woodfish")
        val first=s.focusId!!
        s=tap(s,first)
        s=rules.reduce(s,RoomAction.Add("woodfish"))
        val second=s.focusId!!
        repeat(2) { s=tap(s,second) }
        s=rules.reduce(s,RoomAction.Add("bowl"))
        s=tap(s,s.focusId!!)
        s=rules.reduce(s,RoomAction.Add("musicbox"))
        s=rules.reduce(s,RoomAction.Begin("@play:${s.focusId}"))
        s=rules.reduce(s,RoomAction.Transform(GrabDelta(Point3(0f,.05f,0f),Rotation())))
        s=rules.reduce(s,RoomAction.End)
        assertEquals(1L,s.playCounts[first])
        assertEquals(2L,s.playCounts[second])
        assertEquals(3,s.merit)
        assertTrue("trio" in s.achievements)
        val decoded=RoomPersistence.decode(RoomPersistence.encode(s))!!
        assertEquals(s.items,decoded.items)
        assertEquals(s.playCounts,decoded.playCounts)
        assertNull(decoded.playEvent)
    }
    @Test fun undoRecycleAndRedoNeverSubtractEarnedMeritOrReuseIds() {
        val vm=RoomViewModel(RoomPersistence.encode(RoomGame()))
        vm.onEvent(RoomAction.Add("woodfish"))
        val id=vm.state.value.game.focusId!!
        vm.onEvent(RoomAction.Begin("@play:$id"));vm.onEvent(RoomAction.End)
        vm.onEvent(RoomAction.Recycle)
        assertEquals(1,vm.state.value.game.merit)
        assertNull(vm.state.value.game.items.find { it.spec.id==id })
        vm.onEvent(RoomAction.Undo)
        assertNotNull(vm.state.value.game.items.find { it.spec.id==id })
        assertEquals(1,vm.state.value.game.merit)
        vm.onEvent(RoomAction.Undo)
        assertNull(vm.state.value.game.items.find { it.spec.id==id })
        assertEquals(1,vm.state.value.game.merit)
        vm.onEvent(RoomAction.Add("woodfish"))
        assertNotEquals(id,vm.state.value.game.focusId)
        assertEquals(1,RoomPersistence.decode(RoomPersistence.encode(vm.state.value.game))!!.merit)
    }
    @Test fun v2MigratesAndInvalidCounterIsRejected() {
        val s=added("woodfish")
        val encoded=RoomPersistence.encode(s)
        val v2=encoded.lines().mapIndexed { index,line ->
            if(index==0) line.split('\t').take(10).joinToString("\t").replace("tidy-room-6","tidy-room-2") else line
        }.joinToString("\n")
        assertNotNull(RoomPersistence.decode(v2))
        val corrupt=encoded.lines().mapIndexed { index,line ->
            if(index==0) line.substringBeforeLast('\t')+"\twoodfish~1:-5" else line
        }.joinToString("\n")
        assertNull(RoomPersistence.decode(corrupt))
    }
}
