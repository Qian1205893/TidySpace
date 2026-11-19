package com.example.tidyspace.pc5853b2a.domain.usecase

import com.example.tidyspace.pc5853b2a.domain.model.*
import com.example.tidyspace.pc5853b2a.data.repository.RoomPersistence
import org.junit.Assert.*
import org.junit.Test

class RoomUseCaseTest {
    private val rules=RoomUseCase()
    private fun put(s: RoomGame,id: String,p: Point3): RoomGame {
        var next=rules.reduce(s,RoomAction.Begin(id))
        next=rules.reduce(next,RoomAction.Transform(GrabDelta(p-next.item(id).position,Rotation())))
        return rules.reduce(next,RoomAction.End)
    }
    private fun opened()=rules.reduce(rules.reduce(RoomGame(),RoomAction.Begin("crate:lid")),RoomAction.End)
    private fun equalPoint(a: Point3,b: Point3) {
        assertEquals(a.x,b.x,.0001f);assertEquals(a.y,b.y,.0001f);assertEquals(a.z,b.z,.0001f)
    }
    @Test fun closedBoxDoesNotAllowInvisibleObjectsToBeGrabbed() {
        assertNull(rules.reduce(RoomGame(),RoomAction.Begin("book")).selectedId)
        assertTrue(opened().crateOpen)
        assertEquals("book",rules.reduce(opened(),RoomAction.Begin("book")).selectedId)
    }
    @Test fun shortPinchAlwaysEndsCapture() {
        val s=rules.reduce(rules.reduce(opened(),RoomAction.Begin("chair")),RoomAction.End)
        assertNull(s.selectedId);assertNull(s.origin)
    }
    @Test fun cancellationRestoresEntireFurnitureFamily() {
        val start=put(opened(),"book",Point3(-.45f,.07f,-.43f))
        assertEquals("desk",start.item("book").support)
        var held=rules.reduce(start,RoomAction.Begin("desk"))
        held=rules.reduce(held,RoomAction.Transform(GrabDelta(Point3(.1f,.1f,0f),Rotation.yaw(30f))))
        assertNotEquals(start.item("book").position,held.item("book").position)
        assertEquals(start.items,rules.reduce(held,RoomAction.Cancel).items)
    }
    @Test fun bookFollowsTableAndDetachesWhenGrabbed() {
        val start=put(opened(),"book",Point3(-.45f,.07f,-.43f))
        val move=rules.reduce(rules.reduce(start,RoomAction.Begin("desk")),
            RoomAction.Transform(GrabDelta(Point3(.05f,0f,.04f),Rotation())))
        equalPoint(start.item("book").position+Point3(.05f,0f,.04f),move.item("book").position)
        val held=rules.reduce(start,RoomAction.Begin("book"))
        val changed=rules.reduce(held,RoomAction.Transform(GrabDelta(Point3(0f,.05f,0f),Rotation())))
        assertNull(changed.item("book").support)
    }
    @Test fun overlapRejectsAndRestoresPosition() {
        val s=opened()
        val result=put(s,"chair",s.item("sofa").position)
        assertEquals(s.item("chair"),result.item("chair"))
        assertTrue(result.message.contains("放不下"))
    }
    @Test fun bookRestsOnNewMiddleShelfAndMovesWithIt() {
        val start=put(opened(),"book",Point3(.47f,.19f,-.8635f))
        assertEquals("shelf",start.item("book").support)
        assertEquals(.185f,start.item("book").position.y,.0001f)
        val held=rules.reduce(rules.reduce(start,RoomAction.Begin("shelf")),
            RoomAction.Transform(GrabDelta(Point3(-.08f,.02f,0f),Rotation())))
        equalPoint(start.item("book").position+Point3(-.08f,.02f,0f),held.item("book").position)
    }
    @Test fun movingContainerCarriesPackedContents() {
        val s=RoomGame()
        val held=rules.reduce(rules.reduce(s,RoomAction.Begin("crate")),
            RoomAction.Transform(GrabDelta(Point3(-.1f,.04f,0f),Rotation.yaw(45f))))
        val oldOffset=s.item("plant").position-s.item("crate").position
        equalPoint(Rotation.yaw(45f).rotate(oldOffset),
            held.item("plant").position-held.item("crate").position)
    }
    @Test fun windowApertureCannotSupportPicture() {
        val s=opened()
        val result=put(s,"picture",Point3(-.34f*RoomLayout.scale,.3f,RoomLayout.back+.082f))
        assertEquals(s.item("picture"),result.item("picture"))
        assertTrue(result.message.contains("窗户"))
    }
    @Test fun roomHasThreeAchievableRelationalGoalsWithoutSlots() {
        var s=opened()
        s=put(s,"book",Point3(-.48f,.08f,-.43f))
        s=put(s,"mug",Point3(-.28f,.09f,-.43f))
        s=put(s,"chair",Point3(-.35f,-.10f,-.15f))
        s=put(s,"plant",Point3(-.38f,.17f,-.43f))
        s=put(s,"cushion",Point3(.33f,-.08f,-.42f))
        s=put(s,"lamp",Point3(.62f,-.02f,-.17f))
        s=put(s,"coffee",Point3(.30f,-.245f,-.08f))
        s=rules.reduce(rules.reduce(s,RoomAction.Begin("lamp:switch")),RoomAction.End)
        assertTrue("items=${s.items} goals=${rules.goals(s)}",rules.goals(s).all { it.complete })
        s=put(s,"mug",Point3(-.1f,-.20f,.20f))
        assertFalse(rules.goals(s).first().complete)
    }
    @Test fun badTransformIsIgnored() {
        val held=rules.reduce(opened(),RoomAction.Begin("book"))
        assertEquals(held,rules.reduce(held,RoomAction.Transform(GrabDelta(Point3(Float.NaN,0f,0f),Rotation()))))
    }
    @Test fun furnitureAndItsSupportedObjectsIgnoreSingleHandWristTurn() {
        val start=put(opened(),"book",Point3(-.45f,.07f,-.43f))
        var held=rules.reduce(start,RoomAction.Begin("desk"))
        held=rules.reduce(held,RoomAction.Transform(GrabDelta(Point3(.05f,.07f,0f),
            Rotation.yaw(43f)*Rotation.axis(Point3(1f,0f,0f),72f))))
        for(id in listOf("desk","book")) {
            equalPoint(Point3(0f,1f,0f),held.item(id).rotation.rotate(Point3(0f,1f,0f)))
            assertEquals(0f,held.item(id).rotation.yaw(),.001f)
        }
        assertEquals(start.items,rules.reduce(held,RoomAction.Cancel).items)
        val turned=rules.reduce(held,RoomAction.Transform(GrabDelta(Point3(.4f,.4f,.4f),
            Rotation.yaw(43f)*Rotation.axis(Point3(1f,0f,0f),72f),pointerCount=2)))
        assertEquals(held.item("desk").position,turned.item("desk").position)
        assertEquals(43f,turned.item("desk").rotation.yaw(),.001f)
    }
    @Test fun interruptedRoomTurnAndDrawerDragRestoreMechanisms() {
        val start=opened().copy(roomYaw=30f,drawerOpen=.2f)
        val turn=rules.reduce(rules.reduce(start,RoomAction.Begin("@room")),
            RoomAction.Transform(GrabDelta(Point3(0f,0f,0f),Rotation.yaw(40f))))
        assertEquals(30f,rules.reduce(turn,RoomAction.Cancel).roomYaw,.001f)
        val drawer=rules.reduce(rules.reduce(start,RoomAction.Begin("desk:drawer")),
            RoomAction.Transform(GrabDelta(Point3(0f,0f,.04f),Rotation())))
        assertEquals(.2f,rules.reduce(drawer,RoomAction.Cancel).drawerOpen,.001f)
    }
    @Test fun roundTripPreservesRelationshipsAndStates() {
        var s=put(opened(),"book",Point3(-.48f,.07f,-.43f))
        s=s.copy(lampOn=true,drawerOpen=.6f,roomYaw=34f)
        val restored=RoomPersistence.decode(RoomPersistence.encode(s))!!
        assertEquals(s.items,restored.items)
        assertEquals(s.lampOn,restored.lampOn)
        assertEquals(s.roomYaw,restored.roomYaw)
        assertEquals(s.drawerOpen,restored.drawerOpen)
    }
    @Test fun corruptOrCyclicSaveIsRejected() {
        assertNull(RoomPersistence.decode("broken"))
        val s=RoomGame().let { it.copy(items=it.items.map { i->if(i.spec.id=="desk") i.copy(support="desk") else i }) }
        assertNull(RoomPersistence.decode(RoomPersistence.encode(s)))
    }
    @Test fun savingDuringGrabRestoresLastLegalLayout() {
        val start=opened()
        val held=rules.reduce(rules.reduce(start,RoomAction.Begin("desk")),
            RoomAction.Transform(GrabDelta(Point3(.1f,.1f,0f),Rotation())))
        assertEquals(start.items,RoomPersistence.decode(RoomPersistence.encode(held))!!.items)
    }
    @Test fun singleHandRotationIsRelativeToItsPreviousPose() {
        val tracker=GrabTracker()
        tracker.down(1,HandPose(Point3(0f,0f,0f),Rotation.yaw(70f)))
        val delta=tracker.update(mapOf(1L to HandPose(Point3(.03f,.02f,0f),Rotation.yaw(90f))))!!
        equalPoint(Point3(.03f,.02f,0f),delta.translation)
        assertEquals(20f,delta.rotation.yaw(),.001f)
    }
    @Test fun twoHandsRotateOutOfTheTablePlane() {
        val tracker=GrabTracker()
        tracker.down(1,HandPose(Point3(-.1f,0f,0f),Rotation()))
        tracker.down(2,HandPose(Point3(.1f,0f,0f),Rotation()))
        val delta=tracker.update(mapOf(1L to HandPose(Point3(0f,-.1f,0f),Rotation()),
            2L to HandPose(Point3(0f,.1f,0f),Rotation())))!!
        equalPoint(Point3(0f,0f,0f),delta.translation)
        equalPoint(Point3(0f,1f,0f),delta.rotation.rotate(Point3(1f,0f,0f)))
    }
    @Test fun addingOrRemovingAHandDoesNotJump() {
        val tracker=GrabTracker()
        val a=HandPose(Point3(0f,0f,0f),Rotation.yaw(20f))
        val b=HandPose(Point3(.2f,0f,0f),Rotation.yaw(80f))
        tracker.down(1,a);tracker.down(2,b)
        equalPoint(Point3(0f,0f,0f),tracker.update(mapOf(1L to a,2L to b))!!.translation)
        tracker.up(1)
        assertEquals(0f,tracker.update(mapOf(2L to b))!!.rotation.yaw(),.001f)
    }
    @Test fun antiparallelHandVectorsHaveStableHalfTurn() {
        equalPoint(Point3(-1f,0f,0f),Rotation.between(Point3(1f,0f,0f),Point3(-1f,0f,0f)).rotate(Point3(1f,0f,0f)))
    }
}
