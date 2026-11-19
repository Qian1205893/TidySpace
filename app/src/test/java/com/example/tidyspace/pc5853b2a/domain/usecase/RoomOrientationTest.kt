package com.example.tidyspace.pc5853b2a.domain.usecase

import com.example.tidyspace.pc5853b2a.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class RoomOrientationTest {
    private val rules=RoomUseCase()
    @Test fun halfTurnHeadingIsConsumedOnceWithoutResettingALaterManualTurn() {
        for(yaw in listOf(180f,-180f)) {
            val s=RoomGame().let { game->game.copy(items=game.items.map {
                if(it.spec.id=="sofa") it.copy(position=Point3(.4f,-.1f,.2f)) else it.copy(packed=true,support=null)
            }) }
            val held=rules.reduce(s,RoomAction.Begin("sofa",Rotation.yaw(yaw)))
            val faced=rules.reduce(held,RoomAction.Transform(GrabDelta(Point3(0f,.025f,0f),Rotation())))
            assertNull(faced.dragFacing)
            val turned=rules.reduce(faced,RoomAction.Transform(GrabDelta(Point3(0f,0f,0f),
                Rotation.yaw(17f),pointerCount=2)))
            val moved=rules.reduce(turned,RoomAction.Transform(GrabDelta(Point3(.01f,0f,0f),Rotation())))
            assertTrue(kotlin.math.abs((moved.item("sofa").rotation*turned.item("sofa").rotation.inverse()).yaw())<.001f)
        }
    }
    @Test fun everyNewDragRefreshesViewerHeadingAfterReleaseAndManualTurning() {
        for(spec in uniqueCatalogue()+movingBoxes()+roomCatalogue().filter { it.kind==Furnishing.Crate }) {
            val item=RoomItem(spec,Point3(.1f,.15f,.2f),Rotation.yaw(-128f),packed=false)
            var game=RoomGame(items=RoomGame().items.filter { it.spec.id!=spec.id }
                .map { it.copy(packed=true,support=null) }+item)
            for(yaw in listOf(37f,-61f,142f)) {
                game=rules.reduce(game,RoomAction.Begin(spec.id,Rotation.yaw(yaw-10f)))
                game=rules.reduce(game,RoomAction.Transform(GrabDelta(Point3(0f,.035f,0f),Rotation()),
                    facing=Rotation.yaw(yaw)))
                assertEquals(spec.id,yaw,game.item(spec.id).rotation.yaw(),.001f)
                game=rules.reduce(game,RoomAction.Transform(GrabDelta(Point3(0f,0f,0f),
                    Rotation.yaw(12f),pointerCount=2)))
                game=rules.reduce(game,RoomAction.Transform(GrabDelta(Point3(.01f,0f,0f),Rotation()),
                    facing=Rotation.yaw(-170f)))
                assertEquals(spec.id,(Rotation.yaw(yaw+12f)).yaw(),game.item(spec.id).rotation.yaw(),.001f)
                game=rules.reduce(game,RoomAction.End)
                assertNull(spec.id,game.selectedId)
                assertFalse(spec.id,game.cueVisible)
            }
        }
    }
    @Test fun everyMovableFacesViewerOnFirstDragAndThenKeepsExplicitPairHeading() {
        for(spec in uniqueCatalogue()+movingBoxes()+roomCatalogue().filter { it.kind==Furnishing.Crate }) {
            val item=RoomItem(spec,Point3(.1f,.15f,.2f),Rotation.yaw(-128f),packed=false)
            val s=RoomGame(items=RoomGame().items.filter { it.spec.id!=spec.id }+item)
            val held=rules.reduce(s,RoomAction.Begin(spec.id,Rotation.yaw(37f)))
            assertEquals(spec.id,-128f,held.item(spec.id).rotation.yaw(),.001f)
            assertEquals(spec.id,Point3(0f,1f,0f),held.item(spec.id).rotation.rotate(Point3(0f,1f,0f)))
            assertEquals(s.items,rules.reduce(held,RoomAction.Cancel).items)
            val turned=rules.reduce(held,RoomAction.Transform(GrabDelta(Point3(0f,.1f,0f),Rotation.yaw(15f))))
            assertEquals(spec.id,37f,turned.item(spec.id).rotation.yaw(),.001f)
            val paired=rules.reduce(turned,RoomAction.Transform(GrabDelta(Point3(0f,.1f,0f),
                Rotation.yaw(15f),pointerCount=2)))
            assertEquals(spec.id,52f,paired.item(spec.id).rotation.yaw(),.001f)
            assertEquals(turned.item(spec.id).position,paired.item(spec.id).position)
            val resumed=rules.reduce(paired,RoomAction.Transform(GrabDelta(Point3(.01f,0f,0f),Rotation())))
            assertEquals(spec.id,52f,resumed.item(spec.id).rotation.yaw(),.001f)
            assertEquals(s.items,rules.reduce(resumed,RoomAction.Cancel).items)
        }
    }
    @Test fun extractingEveryBoxItemFacesViewerOnMovementAndRestoresStockOnCancel() {
        for(spec in uniqueCatalogue()) {
            val s=MovingHome.prepare(RoomGame(),fresh=true)
            val box=MovingHome.boxFor(spec)
            val one=s.copy(boxStock=s.boxStock+(box to listOf(spec.id)))
            val preview=rules.reduce(one,RoomAction.Add(spec.id))
            val held=rules.reduce(preview,RoomAction.Begin("@unpack:$box",Rotation.yaw(-61f)))
            assertEquals(spec.id,one.item(box).rotation.yaw(),held.selected!!.rotation.yaw(),.001f)
            val dragged=rules.reduce(held,RoomAction.Transform(GrabDelta(Point3(0f,.03f,0f),Rotation())))
            assertEquals(spec.id,-61f,dragged.selected!!.rotation.yaw(),.001f)
            assertEquals(one.boxStock,rules.reduce(held,RoomAction.Cancel).boxStock)
        }
    }
    @Test fun firstDragFacesViewerWithoutAddingWallClearanceMotion() {
        val item=RoomGame().item("sofa").copy(position=Point3(0f,-.185f,RoomLayout.back+.152f))
        val s=RoomGame(items=RoomGame().items.map { if(it.spec.id=="sofa") item else it })
        val held=rules.reduce(s,RoomAction.Begin("sofa",Rotation.yaw(90f)))
        val dragged=rules.reduce(held,RoomAction.Transform(GrabDelta(Point3(0f,.02f,.01f),Rotation())))
        assertEquals(90f,dragged.item("sofa").rotation.yaw(),.001f)
        assertEquals(item.position+Point3(0f,.02f,.01f),dragged.item("sofa").position)
    }
    @Test fun allCleaningToolsAndTrashFaceOnMovementAndStayFixedDuringPairFrames() {
        val ids=CleanTool.entries.map { "@clean:${it.name}" }+
            cleaningMess().filter { it.kind==MessKind.Trash }.map { "@trash:${it.id}" }
        for(id in ids) {
            val held=rules.reduce(RoomGame(cleaning=CleaningState()),RoomAction.Begin(id,Rotation.yaw(28f)))
            val dragged=rules.reduce(held,RoomAction.Transform(GrabDelta(Point3(.02f,.02f,.01f),Rotation())))
            assertEquals(id,28f,dragged.cleaning!!.rotation.yaw(),.001f)
            val pivot=dragged.cleaning.position
            val pair=rules.reduce(dragged,RoomAction.Transform(GrabDelta(Point3(.5f,.5f,.5f),Rotation.yaw(1f),
                spanDelta=.4f,pointerCount=2)))
            assertEquals(id,pivot,pair.cleaning!!.position)
            val next=rules.reduce(pair,RoomAction.Transform(GrabDelta(Point3(.01f,.01f,0f),Rotation())))
            assertEquals(id,pair.cleaning.rotation.yaw(),next.cleaning!!.rotation.yaw(),.001f)
        }
    }
    @Test fun viewerHeadingCannotRotateDrawerOrPlayMechanisms() {
        val s=RoomGame()
        for(id in listOf("desk:drawer","crate:lid","lamp:switch")) {
            assertEquals(s.items,rules.reduce(s,RoomAction.Begin(id,Rotation.yaw(80f))).items)
        }
    }
    @Test fun sofaBackFacesInteriorEvenWhenHeldSidewaysOrReversedNearBackWall() {
        val s=RoomGame()
        for(yaw in listOf(0f,53f,90f,180f,-90f)) {
            val sofa=s.item("sofa").copy(position=Point3(.25f,-.185f,
                RoomLayout.back+(if(yaw==0f || yaw==180f) .152f else .232f)),rotation=Rotation.yaw(yaw))
            val contact=RoomPhysics.wallContact(s,sofa)
            assertEquals("yaw=$yaw",0f,contact.rotation.yaw(),.001f)
            assertEquals(.25f,contact.position.x,0f)
            assertEquals(-.925f*RoomLayout.scale,RoomPhysics.boxes(s,contact).filter {
                contact.position.y+it.center.y-it.size.y/2<-.247f
            }.minOf { contact.position.z+it.center.z-it.size.z/2 },.0001f)
        }
    }
    @Test fun leftWallTurnsFurnitureBackToWallAndInteriorFrontRightward() {
        val s=RoomGame()
        for(id in listOf("sofa","desk")) {
            val item=s.item(id).copy(position=Point3(RoomLayout.left+.197f,s.item(id).position.y,-.40f),rotation=Rotation.yaw(180f))
            val contact=RoomPhysics.wallContact(s,item)
            assertEquals(90f,contact.rotation.yaw(),.001f)
            assertEquals(-.40f,contact.position.z,0f)
            assertTrue(RoomPhysics.inside(contact))
        }
    }
    @Test fun distantOrSupportedFurnitureKeepsUserOrientation() {
        val s=RoomGame()
        val item=s.item("sofa").copy(rotation=Rotation.yaw(43f))
        assertEquals(item,RoomPhysics.wallContact(s,item))
        val supported=item.copy(position=Point3(.2f,.3f,-.8f),support="desk")
        assertEquals(supported,RoomPhysics.wallContact(s,supported))
    }
}
