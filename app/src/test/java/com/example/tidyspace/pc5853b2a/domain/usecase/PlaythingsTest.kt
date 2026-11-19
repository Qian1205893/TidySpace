package com.example.tidyspace.pc5853b2a.domain.usecase

import com.example.tidyspace.pc5853b2a.domain.model.*
import com.example.tidyspace.pc5853b2a.data.repository.RoomPersistence
import com.example.tidyspace.pc5853b2a.ui.sorting.RoomViewModel
import org.junit.Assert.*
import org.junit.Test

class PlaythingsTest {
    private val rules=RoomUseCase()
    private fun add(asset: String)=rules.reduce(RoomGame(),RoomAction.Add(asset))
    private fun act(s: RoomGame,a: RoomAction)=rules.reduce(s,a)
    private fun tap(s: RoomGame,suffix: String="")=act(act(s,RoomAction.Begin("@play:${s.focusId}$suffix")),RoomAction.End)
    private fun turn(s: RoomGame,degrees: Float)=act(s,RoomAction.Transform(GrabDelta(Point3(0f,0f,0f),Rotation.yaw(degrees),.1f)))
    @Test fun allHandheldTypesCanBeAddedAndNaturallyPlaced() {
        var s=RoomGame()
        for(spec in toyCatalogue().drop(3)) {
            s=act(s,RoomAction.Add(spec.id))
            val item=s.items.last()
            assertEquals(s.message,spec.id,item.spec.assetId)
            assertNull(s.selectedId)
            assertTrue(item.position.finite())
        }
        assertEquals(roomCatalogue().size+toyCatalogue().size-3,s.items.size)
        assertEquals(17,toyCatalogue().size)
    }
    @Test fun ballTogglesPerInstanceAndBodyGrabDoesNotOpenIt() {
        var s=add("pokeball");val id=s.focusId!!
        s=tap(s);assertTrue(s.toyStates.getValue(id).open)
        s=act(act(s,RoomAction.Begin(id)),RoomAction.End)
        assertTrue(s.toyStates.getValue(id).open)
        s=act(s,RoomAction.Add("pokeball"));val second=s.focusId!!
        assertNull(s.toyStates[second])
        // Give the second lid clearance; the default free floor spot is beside the coffee table.
        s=act(s,RoomAction.Begin(second))
        s=act(s,RoomAction.Transform(GrabDelta(Point3(-.75f,.35f,.50f)-s.item(second).position,Rotation())))
        s=act(s,RoomAction.End)
        s=tap(s);assertTrue("${s.message} ${s.item(second).position}",s.toyStates[second]?.open==true)
        assertEquals(1L,s.playCounts[id])
    }
    @Test fun ballWillNotOpenIntoAnotherObject() {
        var s=add("pokeball");val id=s.focusId!!;val ball=s.item(id)
        s=act(s,RoomAction.Add("jelly"))
        val blocker=s.focusId!!
        s=s.copy(focusId=id,items=s.items.map { if(it.spec.id==blocker) it.copy(
            position=ball.position+Point3(0f,.07f,-.12f)) else it })
        val next=tap(s)
        assertFalse(next.toyStates[id]?.open ?: false)
        assertNull(next.playCounts[id])
    }
    @Test fun bubblesAreIndependentAndRoundsRequireACompleteReset() {
        var s=add("popboard");val id=s.focusId!!
        s=tap(s,":2")
        assertEquals(4,s.toyStates.getValue(id).bubbles)
        for(i in listOf(0,1,3,4,5)) s=tap(s,":$i")
        assertEquals(63,s.toyStates.getValue(id).bubbles)
        assertEquals(1,s.toyStates.getValue(id).rounds)
        s=tap(tap(s,":5"),":5")
        assertEquals(1,s.toyStates.getValue(id).rounds)
        for(i in 0..5) s=tap(s,":$i")
        assertEquals(0,s.toyStates.getValue(id).bubbles)
        for(i in 0..5) s=tap(s,":$i")
        assertEquals(2,s.toyStates.getValue(id).rounds)
    }
    @Test fun invalidMechanismIdsAndCancelledBubblesDoNothing() {
        val s=add("popboard")
        for(suffix in listOf("",":-1",":6",":a",":0:1")) {
            assertNull(act(s,RoomAction.Begin("@play:${s.focusId}$suffix")).selectedId)
        }
        val cancelled=act(act(s,RoomAction.Begin("@play:${s.focusId}:3")),RoomAction.Cancel)
        assertEquals(s.toyStates,cancelled.toyStates)
    }
    @Test fun squishyCountsOnceOnReleaseAndCancelsCleanly() {
        for(asset in listOf("mochi","jelly")) {
            val s=add(asset);val id=s.focusId!!
            val held=act(s,RoomAction.Begin("@play:$id"))
            val squeezed=act(held,RoomAction.Transform(GrabDelta(Point3(0f,0f,0f),Rotation(),.1f,-.08f)))
            assertEquals(.35f,squeezed.toyGesture.compression,.0001f)
            assertEquals(s.items,squeezed.items)
            assertNull(act(squeezed,RoomAction.Cancel).playCounts[id])
            val released=act(squeezed,RoomAction.End)
            assertEquals(1L,released.playCounts[id])
            assertEquals(released.playCounts,act(released,RoomAction.End).playCounts)
        }
    }
    @Test fun rattleRequiresAlternatingMotionNotStationaryHoldOrJitter() {
        val s=add("rattle");val id=s.focusId!!
        var held=act(s,RoomAction.Begin(id))
        repeat(100) { held=turn(held,if(it%2==0) 1f else -1f) }
        assertNull(held.playCounts[id])
        held=turn(held,25f)
        assertNull(held.playCounts[id])
        repeat(6) { held=turn(held,if(it%2==0) -25f else 25f) }
        assertEquals(6L,held.playCounts[id])
        repeat(10) { held=turn(held,25f) }
        assertEquals(6L,held.playCounts[id])
    }
    @Test fun ratchetRetainsSignedDetentAndDoesNotRewardIdleTap() {
        val s=add("ratchet");val id=s.focusId!!
        assertNull(tap(s).playCounts[id])
        var held=act(s,RoomAction.Begin("@play:$id"))
        held=turn(held,90f);held=turn(held,-30f)
        val end=act(held,RoomAction.End)
        assertEquals(2,end.toyStates.getValue(id).detent)
        assertEquals(4L,end.playCounts[id])
        assertEquals(0f,end.toyGesture.turn,0f)
    }
    @Test fun spinnerAndPenguinNeedAnActualFlick() {
        for(asset in listOf("spinner","tumbler")) {
            val s=add(asset);val id=s.focusId!!
            assertNull(tap(s).playCounts[id])
            val end=act(turn(act(s,RoomAction.Begin("@play:$id")),20f),RoomAction.End)
            assertEquals(1L,end.playCounts[id])
        }
    }
    @Test fun allNewStatesRoundTripAndOldSaveVersionsMigrate() {
        var s=add("pokeball");s=tap(s)
        s=act(s,RoomAction.Add("popboard"));s=tap(s,":4")
        val encoded=RoomPersistence.encode(s)
        val restored=RoomPersistence.decode(encoded)!!
        assertEquals(s.items,restored.items);assertEquals(s.toyStates,restored.toyStates)
        assertEquals(s.playCounts,restored.playCounts)
        for(version in 2..3) {
            val old=encoded.lines().mapIndexed { index,line-> if(index==0)
                line.split('\t').take(if(version==2) 10 else 11).joinToString("\t").replace("tidy-room-6","tidy-room-$version") else line
            }.joinToString("\n")
            assertNotNull(RoomPersistence.decode(old))
        }
    }
    @Test fun corruptToyStatesAndNonfiniteSamplesCannotPoisonState() {
        val s=tap(add("pokeball"))
        val corrupt=RoomPersistence.encode(s).replace(":true:0:0:0:true",":true:64:0:0:true")
        assertNull(RoomPersistence.decode(corrupt))
        val held=act(s,RoomAction.Begin("@play:${s.focusId}"))
        val next=act(held,RoomAction.Transform(GrabDelta(Point3(Float.NaN,0f,0f),Rotation())))
        assertEquals(held,next)
    }
    @Test fun undoAndRecycleRetainPlayStateWithoutSharingWithCopies() {
        val vm=RoomViewModel(RoomPersistence.encode(RoomGame()))
        vm.onEvent(RoomAction.Add("pokeball"));val id=vm.state.value.game.focusId!!
        vm.onEvent(RoomAction.Begin("@play:$id"));vm.onEvent(RoomAction.End)
        vm.onEvent(RoomAction.Duplicate);val second=vm.state.value.game.focusId!!
        assertNull(vm.state.value.game.toyStates[second])
        vm.onEvent(RoomAction.Undo)
        assertTrue(vm.state.value.game.toyStates.getValue(id).open)
        assertEquals(1L,vm.state.value.game.playCounts[id])
    }
}
