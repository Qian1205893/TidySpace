package com.example.tidyspace.pc5853b2a.domain.usecase

import com.example.tidyspace.pc5853b2a.data.repository.RoomPersistence
import com.example.tidyspace.pc5853b2a.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class BathroomExpansionTest {
    private val rules=RoomUseCase()
    private fun installed()=BathroomLayout.prepare(MovingHome.prepare(RoomGame()))
    private fun isolated(id: String,p: Point3?=null): RoomGame = installed().let { s->
        s.copy(items=s.items.map { if(it.spec.id==id) it.copy(position=p ?: it.position,packed=false)
            else it.copy(packed=true,support=null) })
    }
    @Test fun migrationOnlyAppendsFixturesAndIsIdempotent() {
        val old=MovingHome.prepare(RoomGame()).copy(roomYaw=67f,dayHour=17.5f,
            achievements=setOf("tea"),toyStates=mapOf("ratchet" to ToyState(detent=7)),
            playCounts=mapOf("ratchet" to 41L),cleaning=CleaningState(mapOf("dust-0" to .37f)))
        val next=BathroomLayout.prepare(old)
        assertEquals(old.items,next.items.take(old.items.size))
        assertEquals(old.boxStock,next.boxStock)
        assertEquals(old.cleaning,next.cleaning)
        assertEquals(old.achievements,next.achievements)
        assertEquals(old.playCounts,next.playCounts)
        assertEquals(old.toyStates,next.toyStates)
        assertEquals(old.roomYaw,next.roomYaw,0f)
        assertEquals(old.dayHour,next.dayHour,0f)
        assertEquals(old.layoutVersion,next.layoutVersion)
        assertEquals(6,next.items.size-old.items.size)
        assertEquals(next,BathroomLayout.prepare(next))
        assertEquals(next,MovingHome.prepare(next))
    }
    @Test fun allInitialFixturesFitHaveSupportAndDoNotIntersect() {
        val s=installed()
        for(i in BathroomLayout.fixtures()) {
            assertTrue(i.spec.id,RoomPhysics.inside(i))
            assertTrue(i.spec.id,RoomPhysics.supported(s,i))
            assertTrue(i.spec.id,RoomPhysics.heldClear(s,setOf(i.spec.id)))
            assertFalse(i.spec.id,s.items.any { it.spec.id!=i.spec.id && RoomPhysics.intersects(s,i,it) })
        }
    }
    @Test fun v12RoundtripKeepsNativeAnnexPosesAndIndependentAssetCopies() {
        val s=installed()
        val text=RoomPersistence.encode(s)
        assertTrue(text.startsWith("tidy-room-12\t"))
        val loaded=checkNotNull(RoomPersistence.decode(text))
        assertEquals(s.items,loaded.items)
        assertEquals(s.boxStock,loaded.boxStock)
        assertEquals(1,loaded.bathroomVersion)
        assertEquals("@bath-left",loaded.item("bathroom-mirror").support)
        assertEquals(2,loaded.items.count { it.spec.assetId=="bathtub" })
        assertEquals(159,uniqueCatalogue().size)
    }
    @Test fun unsupportedDuplicateAliasStillRejectsCorruptSave() {
        val text=RoomPersistence.encode(installed())
        assertNull(RoomPersistence.decode(text.replace("bathroom-bathtub\t","bathroom-bathtub-evil\t")))
        assertNull(RoomPersistence.decode(text.replace("bathroom-mirror\t","bathroom-toilet\t")))
        assertNull(RoomPersistence.decode(text.replace("@bath-left","@bath-evil")))
    }
    @Test fun floorUnionConnectsBathroomAndGamingRoomWithoutGap() {
        assertTrue(RoomFloor.contains(-.79f,-.61f,.81f,1.04f))
        assertTrue(RoomFloor.point(0f,1.1f))
        assertTrue(RoomFloor.contains(-.3f,.5f,1.0f,1.2f))
        assertFalse(RoomFloor.contains(-1.6f,-1.4f,1.2f,1.3f))
        assertTrue(RoomFloor.point(1.8f,0f))
        assertTrue(RoomFloor.point(.7f,1.3f))
    }
    @Test fun heldDragMovesAcrossFormerGapThroughOpenConnection() {
        val id="bathroom-laundrybasket"
        val s=isolated(id,Point3(-.70f,-.225f,.72f))
        val held=rules.reduce(s,RoomAction.Begin(id,Rotation()))
        val moved=rules.reduce(held,RoomAction.Transform(GrabDelta(Point3(0f,0f,.43f),Rotation())))
        assertEquals(1.15f,moved.item(id).position.z,.001f)
        val stopped=rules.reduce(moved,RoomAction.Transform(GrabDelta(Point3(1f,0f,0f),Rotation())))
        assertTrue(RoomPhysics.heldClear(stopped,setOf(id)))
        assertEquals(.30f,stopped.item(id).position.x,.001f)
    }
    @Test fun everyBodyDragFacesCaptionAndPairMotionKeepsPivot() {
        for(id in listOf("bathroom-bathtub","bathroom-toilet","bathroom-bathvanity","bathroom-washer","bathroom-laundrybasket")) {
            val s=isolated(id,Point3(-.85f,-.10f,1.52f))
            val held=rules.reduce(s,RoomAction.Begin(id,Rotation.yaw(33f)))
            val dragged=rules.reduce(held,RoomAction.Transform(GrabDelta(Point3(0f,.02f,0f),Rotation())))
            assertEquals(id,33f,dragged.item(id).rotation.yaw(),.001f)
            val origin=dragged.item(id).position
            val pair=rules.reduce(dragged,RoomAction.Transform(GrabDelta(Point3(.6f,.5f,.4f),
                Rotation.yaw(4f),spanDelta=.4f,pointerCount=2)))
            assertEquals(id,origin,pair.item(id).position)
            assertEquals(id,s.items,rules.reduce(pair,RoomAction.Cancel).items)
        }
    }
    @Test fun movedFixturesReleaseAndReloadAtTheirNewAnnexPosition() {
        val id="bathroom-toilet"
        val s=isolated(id,Point3(-.8f,-.205f,1.48f))
        val held=rules.reduce(s,RoomAction.Begin(id,Rotation()))
        val moved=rules.reduce(held,RoomAction.Transform(GrabDelta(Point3(.1f,.03f,.1f),Rotation())))
        val placed=rules.reduce(moved,RoomAction.End)
        assertEquals(-.7f,placed.item(id).position.x,.001f)
        assertEquals(1.58f,placed.item(id).position.z,.001f)
        assertTrue(RoomPhysics.supported(placed,placed.item(id)))
        val loaded=checkNotNull(RoomPersistence.decode(RoomPersistence.encode(placed)))
        assertEquals(placed.item(id),BathroomLayout.prepare(loaded).item(id))
    }
    @Test fun annexLeftWallSnapFacesIntoRoomWithoutJumpingBackToOldRoom() {
        val id="bathroom-washer"
        val s=isolated(id,Point3(BathroomLayout.left+.18f,-.175f,1.4f))
        val held=s.copy(selectedId=id,origin=s.items)
        val result=RoomPhysics.solve(held,snapFurnitureToWall=true)
        assertTrue(result.reason,result.valid)
        val i=checkNotNull(result.item)
        assertEquals(90f,i.rotation.yaw(),.001f)
        assertTrue(i.position.z>=BathroomLayout.back)
        assertTrue((i.position-s.item(id).position).length()<.25f)
        assertTrue(RoomPhysics.heldClear(result.game!!,setOf(id)))
    }
    @Test fun bathMirrorCanRemountAndItsAngleSurvivesPersistence() {
        val id="bathroom-mirror"
        val s=isolated(id,Point3(BathroomLayout.left+.07f,.2f,1.7f))
            .let { it.copy(items=it.items.map { i->if(i.spec.id==id) i.copy(support=null) else i },
                selectedId=id) }
        val placement=RoomPhysics.solve(s)
        assertTrue(placement.reason,placement.valid)
        val placed=placement.game!!
        assertEquals(BathroomLayout.wallSupport,placed.item(id).support)
        val rotated=RoomPhysics.rotateInPlace(placed,id,12f)
        assertTrue(rotated.reason,rotated.valid)
        assertEquals(12f,RoomPhysics.placedAngle(rotated.item!!),.001f)
        assertNotNull(RoomPersistence.decode(RoomPersistence.encode(rotated.game!!)))
    }
    @Test fun recyclingAndCleanAgainKeepNewFixturesAndOriginalInventory() {
        val initial=installed().copy(focusId="bathroom-washer")
        val packed=rules.reduce(initial,RoomAction.Recycle)
        assertTrue(packed.item("bathroom-washer").packed)
        assertEquals(initial.boxStock["box-furniture"]!!+"bathroom-washer",packed.stock("box-furniture"))
        val again=rules.reduce(packed,RoomAction.CleanAgain)
        assertEquals(6,again.items.count { BathroomLayout.owns(it.spec.id) })
        assertTrue("bathroom-washer" in again.stock("box-furniture"))
        assertEquals(1,again.bathroomVersion)
        assertNotNull(RoomPersistence.decode(RoomPersistence.encode(again)))
        assertEquals(again.items,BathroomLayout.prepare(again).items)
    }
    @Test fun expandedPlayableEnvelopeCanCrossOldGapButNotNewPartition() {
        val spec=toyCatalogue().first { it.id=="tumbler" }
        fun room(x: Float)=RoomGame(items=listOf(RoomItem(spec,Point3(x,-.235f,1.5f))))
        assertTrue(RoomPhysics.toyClear(room(-.8f),"tumbler"))
        assertTrue(RoomPhysics.toyClear(room(-.14f),"tumbler"))
    }
}
