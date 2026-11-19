package com.example.tidyspace.pc5853b2a.domain.usecase

import com.example.tidyspace.pc5853b2a.domain.model.*
import com.example.tidyspace.pc5853b2a.data.repository.RoomPersistence
import org.junit.Assert.*
import org.junit.Test

class RoomPhysicsTest {
    private val rules=RoomUseCase()
    @Test fun allThreeAxesRemainFreeAcrossFloorAndLiftRemainsImmediate() {
        val item=RoomGame().item("chair").copy(position=Point3(0f,-.175f,0f),rotation=Rotation())
        var game=RoomGame().copy(items=listOf(item),selectedId="chair")
        repeat(360) { frame ->
            game=RoomPhysics.constrainHeld(game,game.item("chair"),
                Point3(.001f,-.002f,.0005f),Rotation(),false)
            assertEquals((frame+1)*.001f,game.item("chair").position.x,.00002f)
            assertEquals((frame+1)*.0005f,game.item("chair").position.z,.00002f)
            assertEquals(-.175f-(frame+1)*.002f,game.item("chair").position.y,.00002f)
        }
        game=RoomPhysics.constrainHeld(game,game.item("chair"),Point3(0f,.04f,0f),Rotation(),false)
        assertEquals(-.855f,game.item("chair").position.y,.00002f)
    }
    @Test fun carriedChildrenFollowFreeMotionWithoutDroppingFamilyOffsets() {
        val desk=RoomGame().item("desk").copy(position=Point3(0f,.20f,0f),rotation=Rotation())
        val mug=RoomGame().item("mug").copy(position=Point3(.08f,-.285f,0f),rotation=Rotation(),support="desk",packed=false)
        val game=RoomGame().copy(items=listOf(desk,mug),selectedId="desk")
        val next=RoomPhysics.constrainHeld(game,desk,Point3(.02f,-.20f,.03f),Rotation(),false)
        assertEquals(0f,next.item("desk").position.y,.000001f)
        assertEquals(-.485f,next.item("mug").position.y,.000001f)
        assertTrue(((mug.position-desk.position)-(next.item("mug").position-next.item("desk").position)).length()<.000001f)
        assertEquals(.02f,next.item("desk").position.x,.000001f)
        assertEquals(mug.position+Point3(.02f,-.20f,.03f),next.item("mug").position)
    }
    private fun put(s: RoomGame,id: String,p: Point3,q: Rotation=Rotation()): RoomGame {
        val held=rules.reduce(s,RoomAction.Begin(id))
        val moved=rules.reduce(held,RoomAction.Transform(GrabDelta(p-held.item(id).position,Rotation())))
        val turned=rules.reduce(moved,RoomAction.Transform(
            GrabDelta(Point3(0f,0f,0f),q*moved.item(id).rotation.inverse(),pointerCount=2)))
        return rules.reduce(turned,RoomAction.End)
    }
    private fun open()=rules.reduce(rules.reduce(RoomGame(),RoomAction.Begin("crate:lid")),RoomAction.End)
    @Test fun plantBaseFitsTableEdgeEvenWhenLeavesOverhang() {
        val s=put(open(),"plant",Point3(-.153f,.19f,-.43f))
        assertEquals(s.message,"desk",s.item("plant").support)
        assertEquals(.10f,s.item("plant").position.y,.001f)
        assertTrue(s.item("plant").position.x+.0475f>-.12f)
        assertTrue(RoomPhysics.supported(s,s.item("plant")))
    }
    @Test fun rotatedDeskUsesRotatedFootprintAndActualLegs() {
        var s=put(open(),"desk",Point3(-.36f,-.1f,-.43f),Rotation.yaw(38f))
        val target=s.item("desk").position+s.item("desk").rotation.rotate(Point3(.10f,.27f,.02f))
        s=put(s,"plant",target)
        assertEquals(s.message,"desk",s.item("plant").support)
        assertFalse(RoomPhysics.intersects(s,s.item("desk"),s.item("plant")))
    }
    @Test fun objectsCanBePlacedUnderDeskWithoutCrossingTop() {
        val s=put(open(),"mug",Point3(-.36f,-.22f,-.43f))
        assertNull(s.item("mug").support)
        assertEquals(-.285f,s.item("mug").position.y,.001f)
        assertFalse(RoomPhysics.intersects(s,s.item("mug"),s.item("desk")))
    }
    @Test fun cupRestsOnChairSeat() {
        val s=put(open(),"mug",Point3(-.34f,-.08f,.095f))
        assertEquals(s.message,"chair",s.item("mug").support)
        assertEquals(-.1265f,s.item("mug").position.y,.001f)
    }
    @Test fun furnitureCanRestOnLargerFurniture() {
        val s=put(open(),"chair",Point3(-.36f,.30f,-.43f),Rotation.yaw(24f))
        assertEquals(s.message,"desk",s.item("chair").support)
        assertEquals(.165f,s.item("chair").position.y,.001f)
    }
    @Test fun plantOnBookOnDeskFollowsAndCancelsAsOneFamily() {
        var s=put(open(),"book",Point3(-.40f,.07f,-.43f))
        s=put(s,"plant",Point3(-.40f,.20f,-.43f))
        assertEquals(s.message,"book",s.item("plant").support)
        assertEquals("desk",s.item("book").support)
        assertTrue("stack" in s.achievements)
        val held=rules.reduce(rules.reduce(s,RoomAction.Begin("desk")),
            RoomAction.Transform(GrabDelta(Point3(-.10f,.08f,.04f),Rotation.yaw(30f))))
        assertNotEquals(s.item("plant").position,held.item("plant").position)
        assertEquals(s.items,rules.reduce(held,RoomAction.Cancel).items)
        val landed=rules.reduce(held,RoomAction.End)
        assertTrue(landed.message,RoomPhysics.supported(landed,landed.item("book")))
        assertTrue(RoomPhysics.supported(landed,landed.item("plant")))
        assertEquals(landed.items,RoomPersistence.decode(RoomPersistence.encode(landed))!!.items)
    }
    @Test fun tallPlantCannotPenetrateUpperShelf() {
        val s=open()
        val next=put(s,"plant",Point3(.47f,.14f,-.86f))
        assertEquals(s.item("plant"),next.item("plant"))
        assertTrue(next.message.contains("放不下"))
    }
    @Test fun unstableEdgeCannotTunnelThroughDeskToFloor() {
        val s=open()
        val next=put(s,"plant",Point3(-.11f,.20f,-.43f))
        // Local assistance may find a real supported point; it may not fall through.
        if(next.item("plant")!=s.item("plant")) {
            assertEquals("desk",next.item("plant").support)
            assertTrue(RoomPhysics.supported(next,next.item("plant")))
        } else assertTrue(next.message.contains("放不下"))
    }
    @Test fun wallItemsDoNotTeleportFromMiddleOfRoom() {
        val s=put(open(),"picture",Point3(-.8f,.20f,.35f))
        assertNull(s.item("picture").support)
        assertEquals(-.3125f,s.item("picture").position.y,.001f)
        assertEquals(.35f,s.item("picture").position.z,.001f)
        assertTrue(RoomPhysics.supported(s,s.item("picture")))
    }
    @Test fun movingGuideToAugmentFreesTheFormerWallArea() {
        val s=open()
        val next=put(s,"picture",Point3(.64f,.5f,RoomLayout.back+.082f))
        assertEquals("@back",next.item("picture").support)
        assertEquals(.64f,next.item("picture").position.x,.001f)
        assertEquals(.5f,next.item("picture").position.y,.001f)
    }
    @Test fun crateWillNotOpenUnderAnObjectOrCloseThroughIt() {
        var s=put(RoomGame(),"chair",Point3(.49f,.22f,.24f))
        assertEquals(s.message,"crate",s.item("chair").support)
        s=rules.reduce(rules.reduce(s,RoomAction.Begin("crate:lid")),RoomAction.End)
        assertFalse(s.crateOpen)
        assertTrue(s.message.contains("挡住"))
        var open=open()
        open=put(open,"plant",Point3(.49f,-.03f,.24f))
        assertEquals("crate",open.item("plant").support)
        // This plant fits below the lip, so a lid can close without intersecting it.
        val closed=rules.reduce(rules.reduce(open,RoomAction.Begin("crate:lid")),RoomAction.End)
        assertFalse(closed.crateOpen)
    }
    @Test fun heldFurnitureStopsAtBoundaryAndReleasesAtALegalContact() {
        val s=open()
        val next=put(s,"sofa",Point3(RoomLayout.balconyRight,-.15f,RoomLayout.balconyFront))
        assertTrue(RoomPhysics.heldClear(next,setOf("sofa")))
        assertTrue(RoomPhysics.supported(next,next.item("sofa")))
        assertTrue(next.item("sofa").position.x<RoomLayout.balconyRight)
    }
    @Test fun releaseBelowTableDoesNotJumpToItsTop() {
        val s=put(open(),"book",Point3(-.36f,-.20f,-.43f))
        assertNull(s.item("book").support)
        assertEquals(-.311f,s.item("book").position.y,.001f)
    }
    @Test fun intermediateShelfReceivesShortObjectWithClearance() {
        val s=put(open(),"mug",Point3(.47f,.217f,-.848f))
        assertEquals(s.message,"shelf",s.item("mug").support)
        assertEquals(.211f,s.item("mug").position.y,.001f)
        assertFalse(RoomPhysics.intersects(s,s.item("mug"),s.item("shelf")))
    }
}
