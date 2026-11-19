package com.example.tidyspace.pc5853b2a.domain.usecase

import com.example.tidyspace.pc5853b2a.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class RoomWallSnapTest {
    private val rules=RoomUseCase()
    private val furniture=listOf("desk","chair","sofa","coffee","shoecabinet","sidetable","gardenbench")
    private fun isolated(item: RoomItem,vararg others: RoomItem): RoomGame {
        val visible=listOf(item)+others
        return RoomGame(items=RoomGame().items.filter { old->visible.none { it.spec.id==old.spec.id } }
            .map { it.copy(packed=true,support=null) }+visible)
    }
    private fun item(id: String)=RoomItem(uniqueCatalogue().first { it.id==id },packed=false)

    @Test fun normalViewerFacingDragsStillAttachBroadFurnitureToBackWall() {
        for(id in furniture) {
            val source=item(id).copy(rotation=Rotation.yaw(41f))
            val position=Point3(.55f,-.32f+source.spec.size.y/2,
                RoomLayout.back+RoomPhysics.half(source).z+.045f)
            val start=isolated(source.copy(position=position))
            val held=rules.reduce(start,RoomAction.Begin(id,Rotation.yaw(41f)))
            val moved=rules.reduce(held,RoomAction.Transform(GrabDelta(Point3(0f,.03f,0f),Rotation())))
            assertEquals(id,41f,moved.item(id).rotation.yaw(),.001f)
            val preview=rules.placement(moved)
            assertTrue("$id: ${preview.reason}",preview.valid)
            val placed=rules.reduce(moved,RoomAction.End)
            assertEquals(id,0f,placed.item(id).rotation.yaw(),.001f)
            assertEquals(id,preview.item!!.position,placed.item(id).position)
            assertTrue(id,RoomPhysics.supported(placed,placed.item(id)))
            assertTrue(id,RoomPhysics.heldClear(placed,setOf(id)))
            assertTrue(id,placed.item(id).position.z-RoomPhysics.half(placed.item(id)).z-RoomLayout.back<.065f)
        }
    }
    @Test fun leftWallKeepsSeatsAndDoorsFacingIntoRoom() {
        for(id in furniture) {
            val source=item(id).copy(rotation=Rotation.yaw(32f))
            val position=Point3(RoomLayout.left+RoomPhysics.half(source).x+.04f,
                -.32f+source.spec.size.y/2,-.65f)
            val start=isolated(source.copy(position=position))
            val held=rules.reduce(start,RoomAction.Begin(id,Rotation.yaw(32f)))
            val moved=rules.reduce(held,RoomAction.Transform(GrabDelta(Point3(0f,.03f,0f),Rotation())))
            val placed=rules.reduce(moved,RoomAction.End)
            assertEquals("$id: ${placed.message}",90f,placed.item(id).rotation.yaw(),.001f)
            assertTrue(id,RoomPhysics.heldClear(placed,setOf(id)))
            assertTrue(id,placed.item(id).rotation.rotate(Point3(0f,0f,1f)).x>.999f)
        }
    }
    @Test fun cornerCorrectionUsesRealFootprintAndDoesNotCrossEitherWall() {
        val sofa=item("sofa").copy(position=Point3(RoomLayout.left+.26f,-.185f,RoomLayout.back+.24f),
            rotation=Rotation.yaw(45f))
        val s=isolated(sofa).copy(selectedId="sofa",origin=listOf(sofa))
        val result=RoomPhysics.solve(s,preserveYaw=true,snapFurnitureToWall=true)
        assertTrue(result.reason,result.valid)
        assertTrue(RoomPhysics.heldClear(result.game!!,setOf("sofa")))
        assertTrue((result.item!!.position-sofa.position).length()<.25f)
    }
    @Test fun blockedSnapFallsBackToLegalHeldHeadingInsteadOfOverlappingNeighbor() {
        val sofa=item("sofa").copy(position=Point3(0f,-.185f,RoomLayout.back+.24f),
            rotation=Rotation.yaw(90f))
        val chair=item("chair").copy(position=Point3(.28f,-.175f,RoomLayout.back+.10f))
        val s=isolated(sofa,chair).copy(selectedId="sofa",origin=listOf(sofa,chair))
        val snapped=RoomPhysics.wallContact(s,sofa)
        assertTrue(RoomPhysics.intersects(s,snapped,chair))
        val result=RoomPhysics.solve(s,preserveYaw=true,snapFurnitureToWall=true)
        assertTrue(result.reason,result.valid)
        assertFalse(RoomPhysics.intersects(result.game!!,result.item!!,chair))
        assertEquals(90f,result.item.rotation.yaw(),.001f)
    }
    @Test fun purePairTurnNearWallDoesNotTriggerTranslationOrRealignmentOnRelease() {
        val sofa=item("sofa").copy(position=Point3(.4f,-.185f,RoomLayout.back+.29f),
            rotation=Rotation.yaw(40f))
        val start=isolated(sofa)
        val held=rules.reduce(start,RoomAction.Begin("sofa",Rotation.yaw(-70f)))
        val turned=rules.reduce(held,RoomAction.Transform(GrabDelta(Point3(.8f,.8f,.8f),
            Rotation.yaw(3f),spanDelta=.5f,pointerCount=2)))
        assertEquals(sofa.position,turned.item("sofa").position)
        assertEquals(43f,turned.item("sofa").rotation.yaw(),.001f)
        val placed=rules.reduce(turned,RoomAction.End)
        assertEquals(43f,placed.item("sofa").rotation.yaw(),.001f)
        assertTrue((sofa.position-placed.item("sofa").position).length()<.00001f)
    }
    @Test fun openBalconyAndFrontEdgesDoNotAttractFurniture() {
        for(position in listOf(Point3(.5f,-.185f,RoomLayout.front-.13f),
                Point3(RoomLayout.right-.23f,-.185f,-.4f))) {
            val sofa=item("sofa").copy(position=position,rotation=Rotation.yaw(19f))
            assertEquals(sofa,RoomPhysics.wallContact(isolated(sofa),sofa))
        }
    }
    @Test fun tableSupportedFurnitureDoesNotAttachToNearbyWall() {
        val chair=item("chair").copy(position=Point3(.3f,.25f,RoomLayout.back+.14f),support="desk")
        assertEquals(chair,RoomPhysics.wallContact(isolated(chair),chair))
    }
    @Test fun shelfCanMountOnWallAfterASingleHandViewerFacingDrag() {
        val shelf=item("shelf").copy(position=Point3(.65f,.48f,RoomLayout.back+.13f),
            rotation=Rotation.yaw(28f))
        val held=rules.reduce(isolated(shelf),RoomAction.Begin("shelf",Rotation.yaw(28f)))
        val moved=rules.reduce(held,RoomAction.Transform(GrabDelta(Point3(0f,.025f,0f),Rotation())))
        val result=rules.reduce(moved,RoomAction.End)
        assertEquals(result.message,"@back",result.item("shelf").support)
        assertEquals(0f,result.item("shelf").rotation.yaw(),.001f)
    }
    @Test fun completionAndRemainingBoxPreviewNeverLeaveAFloatingCue() {
        val start=isolated(item("chair").copy(position=Point3(0f,-.175f,.3f)))
        val held=rules.reduce(start,RoomAction.Begin("chair",Rotation.yaw(35f)))
        assertTrue(held.cueVisible)
        val moved=rules.reduce(held,RoomAction.Transform(GrabDelta(Point3(0f,.03f,0f),Rotation())))
        val placed=rules.reduce(moved,RoomAction.End).copy(previewBox="box-furniture",celebration=10)
        assertFalse(placed.cueVisible)
        assertFalse(placed.copy(cleaning=CleaningState(),message="第一关完成").cueVisible)
    }
}
