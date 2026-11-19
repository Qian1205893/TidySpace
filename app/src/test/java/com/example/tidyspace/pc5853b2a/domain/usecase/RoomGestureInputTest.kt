package com.example.tidyspace.pc5853b2a.domain.usecase

import com.example.tidyspace.pc5853b2a.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class RoomGestureInputTest {
    @Test fun mappedSingleMotionAndPhysicalPairRotationRebaselineWithoutJump() {
        val deltas=mutableListOf<GrabDelta>()
        val input=RoomGestureInput({},deltas::add,{},{})
        fun contact(id: Long,phase: ContactPhase,mapped: Point3,physical: Point3)=
            RoomContact(id,phase,if(phase==ContactPhase.Down) "chair" else null,
                HandPose(mapped,Rotation()),physical,pairPose=HandPose(physical,Rotation()))
        input.events(listOf(contact(1,ContactPhase.Down,Point3(10f,5f,8f),Point3(-.2f,0f,0f))))
        input.events(listOf(contact(1,ContactPhase.Move,Point3(10.1f,5.2f,7.7f),Point3(-.2f,0f,0f))))
        assertTrue((deltas.single().translation-Point3(.1f,.2f,-.3f)).length()<.00001f)
        input.events(listOf(contact(2,ContactPhase.Down,Point3(-20f,2f,30f),Point3(.2f,0f,0f))))
        assertEquals("Adding a hand must not apply either coordinate jump",1,deltas.size)
        input.events(listOf(
            contact(1,ContactPhase.Move,Point3(99f,99f,99f),Point3(0f,0f,.2f)),
            contact(2,ContactPhase.Move,Point3(-99f,-99f,-99f),Point3(0f,0f,-.2f))))
        assertEquals(90f,deltas.last().rotation.yaw(),.001f)
        assertEquals(Point3(0f,0f,0f),deltas.last().translation)
        input.events(listOf(
            contact(2,ContactPhase.Up,Point3(0f,0f,0f),Point3(0f,0f,0f)),
            contact(1,ContactPhase.Move,Point3(3f,4f,5f),Point3(0f,0f,.2f))))
        assertEquals(2,deltas.size)
        input.events(listOf(contact(1,ContactPhase.Move,Point3(3.04f,4.02f,5.03f),Point3(0f,0f,.2f))))
        assertTrue((deltas.last().translation-Point3(.04f,.02f,.03f)).length()<.00001f)
    }

    private class Session(start: RoomGame=RoomGame()) {
        var game=start
        val actions=mutableListOf<RoomAction>()
        val deltas=mutableListOf<GrabDelta>()
        private val rules=RoomUseCase()
        private fun send(a: RoomAction) { actions+=a;game=rules.reduce(game,a) }
        val input=RoomGestureInput(
            begin={ send(RoomAction.Begin(it)) },
            transform={ deltas+=it;send(RoomAction.Transform(it)) },
            end={ send(RoomAction.End) },cancelled={ send(RoomAction.Cancel) },
        )
        fun sample(id: Long,phase: ContactPhase,p: Point3,target: String?=null,rotation: Rotation=Rotation())=
            RoomContact(id,phase,target,HandPose(p,rotation),p)
        fun down(id: Long,p: Point3,target: String?=null)=input.events(listOf(sample(id,ContactPhase.Down,p,target)))
        fun up(id: Long)=input.events(listOf(RoomContact(id,ContactPhase.Up)))
        fun pair(degrees: Float,phase: ContactPhase=ContactPhase.Move,center: Point3=Point3(0f,0f,0f)) {
            val offset=Rotation.yaw(degrees).rotate(Point3(.2f,0f,0f))
            input.events(listOf(sample(1,phase,center-offset),sample(2,phase,center+offset)))
        }
    }

    @Test fun singleBackgroundFloorOrWallCannotStartRoomRotation() {
        for(target in listOf(null,"@room")) {
            val s=Session()
            assertFalse(s.down(1,Point3(-.2f,0f,0f),target))
            s.input.events(listOf(s.sample(1,ContactPhase.Move,Point3(.3f,.2f,.1f),target,Rotation.yaw(90f))))
            s.up(1)
            assertTrue(s.actions.isEmpty())
            assertEquals(0f,s.game.roomYaw,0f)
        }
    }

    @Test fun twoAirPinchesTurnHouseWithoutMovingOrTiltingIt() {
        val s=Session()
        val items=s.game.items
        s.pair(0f,ContactPhase.Down)
        assertEquals("@room",s.game.selectedId)
        assertEquals(0f,s.game.roomYaw,.001f)
        s.pair(65f,center=Point3(.6f,.3f,-.2f))
        assertEquals(65f,s.game.roomYaw,.001f)
        assertEquals(items,s.game.items)
        assertEquals(Point3(0f,0f,0f),s.deltas.single().translation)
        assertEquals(Point3(0f,1f,0f),s.deltas.single().rotation.rotate(Point3(0f,1f,0f)))
    }

    @Test fun secondPinchRebaselinesCurrentFirstHandWithoutJump() {
        val s=Session(RoomGame(roomYaw=34f))
        s.down(1,Point3(-.2f,0f,0f))
        s.input.events(listOf(
            s.sample(1,ContactPhase.Move,Point3(-.1f,.1f,.1f)),
            s.sample(2,ContactPhase.Down,Point3(.3f,.1f,.1f)),
        ))
        assertEquals(34f,s.game.roomYaw,.001f)
        s.input.events(listOf(
            s.sample(1,ContactPhase.Move,Point3(-.1f,.1f,.1f)),
            s.sample(2,ContactPhase.Move,Point3(.3f,.1f,.1f)),
        ))
        assertEquals(34f,s.game.roomYaw,.001f)
    }

    @Test fun firstReleaseStopsBeforeApplyingLastSamplesAndRequiresBothHandsUp() {
        val s=Session()
        s.pair(0f,ContactPhase.Down);s.pair(40f)
        s.input.events(listOf(
            s.sample(1,ContactPhase.Up,Point3(.2f,0f,0f)),
            s.sample(2,ContactPhase.Move,Point3(-.2f,0f,0f)),
        ))
        assertNull(s.game.selectedId)
        assertEquals(40f,s.game.roomYaw,.001f)
        s.down(1,Point3(.2f,0f,0f));s.pair(120f)
        assertNull(s.game.selectedId)
        assertEquals(40f,s.game.roomYaw,.001f)
        s.up(1);s.up(2)
        s.pair(80f,ContactPhase.Down);s.pair(110f)
        assertEquals(70f,s.game.roomYaw,.001f)
        assertEquals(1,s.actions.count { it==RoomAction.End })
    }

    @Test fun losingEitherHandCancelsAndNeverCommits() {
        val s=Session(RoomGame(roomYaw=22f))
        s.pair(0f,ContactPhase.Down);s.pair(35f)
        s.input.events(listOf(RoomContact(2,ContactPhase.Lost),
            s.sample(1,ContactPhase.Move,Point3(0f,0f,.2f))))
        assertNull(s.game.selectedId)
        assertEquals(22f,s.game.roomYaw,.001f)
        s.down(2,Point3(0f,0f,-.2f));s.pair(90f)
        assertEquals(22f,s.game.roomYaw,.001f)
        assertEquals(1,s.actions.count { it==RoomAction.Cancel })
        assertFalse(s.actions.contains(RoomAction.End))
    }

    @Test fun closelySpacedOrVerticalHandsPauseThenRebaseline() {
        val s=Session()
        s.pair(0f,ContactPhase.Down);s.pair(30f)
        s.input.events(listOf(
            s.sample(1,ContactPhase.Move,Point3(0f,-.2f,0f)),
            s.sample(2,ContactPhase.Move,Point3(.001f,.2f,0f)),
        ))
        assertEquals(30f,s.game.roomYaw,.001f)
        s.pair(100f)
        assertEquals(30f,s.game.roomYaw,.001f)
        s.pair(120f)
        assertEquals(50f,s.game.roomYaw,.001f)
    }

    @Test fun invalidHandSampleCannotPoisonYawOrCauseResumeJump() {
        val s=Session()
        s.pair(0f,ContactPhase.Down);s.pair(20f)
        s.input.events(listOf(s.sample(1,ContactPhase.Move,Point3(Float.NaN,0f,0f))))
        s.pair(80f)
        assertEquals(20f,s.game.roomYaw,.001f)
        s.pair(90f)
        assertEquals(30f,s.game.roomYaw,.001f)
    }

    @Test fun crossingAngleWrapKeepsRotationContinuous() {
        val s=Session()
        s.pair(175f,ContactPhase.Down);s.pair(-175f)
        assertEquals(10f,s.game.roomYaw,.001f)
        s.pair(-160f)
        assertEquals(25f,s.game.roomYaw,.001f)
    }

    @Test fun wristsAndHandSeparationDoNotRotateOrScaleTheHouse() {
        val s=Session()
        s.pair(0f,ContactPhase.Down)
        s.input.events(listOf(
            s.sample(1,ContactPhase.Move,Point3(-.4f,-.2f,0f),rotation=Rotation.yaw(70f)),
            s.sample(2,ContactPhase.Move,Point3(.4f,.2f,0f),rotation=Rotation.yaw(-70f)),
        ))
        assertEquals(0f,s.game.roomYaw,.001f)
        assertEquals(0f,s.deltas.single().spanDelta,0f)
    }

    @Test fun sameObjectTwoHandPinchesRetainGrabAndSpanInteraction() {
        val s=Session()
        s.down(1,Point3(-.2f,0f,0f),"chair")
        s.down(2,Point3(.2f,0f,0f),"chair")
        s.input.events(listOf(
            s.sample(1,ContactPhase.Move,Point3(-.1f,0f,0f),"chair"),
            s.sample(2,ContactPhase.Move,Point3(.1f,0f,0f),"chair"),
        ))
        assertEquals("chair",s.game.selectedId)
        assertEquals(-.2f,s.deltas.last().spanDelta,.001f)
        assertEquals(0f,s.game.roomYaw,0f)
        assertFalse(s.actions.any { it==RoomAction.Begin("@room") })
        s.up(1);assertEquals("chair",s.game.selectedId)
        s.up(2);assertNull(s.game.selectedId)
    }

    @Test fun secondBackgroundPinchLocksTheHeldObjectWithoutTurningTheHouse() {
        val s=Session()
        val original=s.game.items
        s.down(1,Point3(-.2f,0f,0f),"chair")
        s.input.events(listOf(s.sample(1,ContactPhase.Move,Point3(-.1f,.1f,0f),"chair")))
        assertNotEquals(original,s.game.items)
        s.down(2,Point3(.3f,.1f,0f))
        val held=s.game.items
        s.pair(45f)
        assertEquals("chair",s.game.selectedId)
        assertEquals(held.first { it.spec.id=="chair" }.position,s.game.item("chair").position)
        assertEquals(45f,s.game.item("chair").rotation.yaw(),.001f)
        assertEquals(0f,s.game.roomYaw,0f)
        assertFalse(s.actions.contains(RoomAction.Cancel))
    }

    @Test fun roomTurnWorksDuringCleaningWithoutChangingProgress() {
        val s=Session(RoomGame(cleaning=CleaningState()))
        val progress=s.game.cleaning!!.progress
        s.pair(0f,ContactPhase.Down);s.pair(-50f);s.up(2)
        assertFalse(s.game.decorating)
        assertEquals(-50f,s.game.roomYaw,.001f)
        assertEquals(progress,s.game.cleaning!!.progress)
        assertNull(s.game.selectedId)
    }

    @Test fun viewDisposalCancelsTurnAndAllowsFreshCapture() {
        val s=Session(RoomGame(roomYaw=12f))
        s.pair(0f,ContactPhase.Down);s.pair(60f)
        s.input.cancel()
        assertEquals(12f,s.game.roomYaw,.001f)
        s.pair(30f,ContactPhase.Down);s.pair(45f)
        assertEquals(27f,s.game.roomYaw,.001f)
    }
}
