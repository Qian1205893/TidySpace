package com.example.tidyspace.pc5853b2a.domain.usecase

import com.example.tidyspace.pc5853b2a.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class SpaceGestureRetentionTest {
    private fun contact(phase: ContactPhase,x: Float)=RoomContact(1,phase,"chair",
        objectPose=HandPose(Point3(x,0f,0f),Rotation()),source="ray")

    @Test fun resumedGrabKeepsTransactionAndDoesNotJumpOnFirstPinch() {
        var begins=0;var ends=0;var cancels=0
        val deltas=mutableListOf<GrabDelta>()
        val input=RoomGestureInput({ begins++ },{ deltas+=it },{ ends++ },{ cancels++ })
        input.resumeSelection("chair")
        input.events(listOf(contact(ContactPhase.Down,10f)))
        assertEquals(0,begins);assertTrue(deltas.isEmpty())
        input.events(listOf(contact(ContactPhase.Move,10.2f)))
        assertEquals(.2f,deltas.last().translation.x,.0001f)
        input.events(listOf(contact(ContactPhase.Up,10.2f)))
        assertEquals(1,ends);assertEquals(0,cancels)
    }
    @Test fun liveModeSwitchRebaselinesWithoutCancelOrEnd() {
        var ends=0;var cancels=0
        val deltas=mutableListOf<GrabDelta>()
        val input=RoomGestureInput({}, { deltas+=it },{ ends++ },{ cancels++ })
        input.events(listOf(contact(ContactPhase.Down,0f)))
        input.rebaseline()
        input.events(listOf(contact(ContactPhase.Move,5f)))
        assertTrue(deltas.isEmpty())
        input.events(listOf(contact(ContactPhase.Move,5.1f)))
        assertEquals(.1f,deltas.last().translation.x,.0001f)
        assertEquals(0,ends);assertEquals(0,cancels)
    }
    @Test fun realTrackingLossStillCancelsAfterSwitch() {
        var cancels=0
        val input=RoomGestureInput({}, {}, {},{ cancels++ })
        input.resumeSelection("chair")
        input.events(listOf(contact(ContactPhase.Down,5f)))
        input.events(listOf(contact(ContactPhase.Lost,5f)))
        assertEquals(1,cancels)
    }
}
