package com.example.tidyspace.pc5853b2a.domain.usecase

import com.example.tidyspace.pc5853b2a.data.repository.RoomPersistence
import com.example.tidyspace.pc5853b2a.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class GamingRenovationTest {
    private val rules=RoomUseCase()
    private fun old()=BathroomLayout.prepare(MovingHome.prepare(RoomGame()))
    private fun installed()=GamingRenovation.prepare(old())
    @Test fun migrationKeepsMovedBathroomFixturesAndArchivesOnlyMatchingOriginals() {
        val before=old().let { s->s.copy(items=s.items.map { i->
            when(i.spec.id) {
                "bathroom-toilet"->i.copy(position=Point3(-.4f,-.205f,1.38f),rotation=Rotation.yaw(16f))
                "bathtub"->i.copy(packed=false,position=Point3(.1f,-.22f,0f))
                else->i
            }
        },playCounts=mapOf("woodfish" to 271L),achievements=setOf("zen"),roomYaw=37f) }
        val after=GamingRenovation.prepare(before)
        assertEquals(before.item("bathroom-toilet"),after.item("bathroom-toilet"))
        assertEquals(before.item("bathtub").position,after.item("bathtub").position)
        assertTrue(after.item("bathtub").packed)
        assertEquals(6,after.archivedIds.size)
        assertTrue(after.boxStock.values.flatten().none { it in after.archivedIds })
        assertEquals(before.playCounts,after.playCounts)
        assertEquals(before.achievements,after.achievements)
        assertEquals(before.roomYaw,after.roomYaw,0f)
        assertEquals(after,GamingRenovation.prepare(after))
    }
    @Test fun equipmentHasStableSupportsNoIntersectionsAndOneOfEachAsset() {
        val s=installed()
        for(i in GamingLayout.equipment()) {
            assertFalse(i.spec.id,s.item(i.spec.id).packed)
            assertTrue(i.spec.id,RoomPhysics.inside(i))
            assertTrue(i.spec.id,RoomPhysics.supported(s,i))
            assertTrue(i.spec.id,RoomPhysics.heldClear(s,RoomPhysics.family(s,i.spec.id)))
            assertFalse(i.spec.id,s.items.any { it.spec.id!=i.spec.id && RoomPhysics.intersects(s,i,it) })
        }
        val active=s.items.filter { it.spec.id !in s.archivedIds }
        assertEquals(active.size,active.map { it.spec.assetId }.distinct().size)
    }
    @Test fun v13RoundtripPackedAliasesAndCleanAgainCannotReintroduceDuplicates() {
        val s=installed()
        val saved=RoomPersistence.encode(s)
        assertTrue(saved.startsWith("tidy-room-13\t"))
        val loaded=checkNotNull(RoomPersistence.decode(saved))
        assertEquals(s.items,loaded.items)
        assertEquals(s.archivedIds,loaded.archivedIds)
        assertEquals(s.boxStock,loaded.boxStock)
        val packed=rules.reduce(loaded.copy(focusId="bathroom-washer"),RoomAction.Recycle)
        assertTrue("bathroom-washer" in packed.stock("box-furniture"))
        assertFalse("washer" in packed.stock("box-furniture"))
        val again=rules.reduce(packed,RoomAction.CleanAgain)
        assertEquals(6,again.archivedIds.size)
        assertTrue(again.stock("box-furniture").contains("bathroom-washer"))
        assertTrue(again.boxStock.values.flatten().none { it in again.archivedIds })
        assertEquals(1,again.items.count { it.spec.assetId=="washer" && it.spec.id !in again.archivedIds })
        assertNotNull(RoomPersistence.decode(RoomPersistence.encode(again)))
        assertEquals(again,GamingRenovation.prepare(again))
    }
    @Test fun archivedOriginalCannotBeExtractedEvenIfStaleStockReferencesIt() {
        val s=installed().let { it.copy(boxStock=it.boxStock+("box-furniture" to listOf("washer")),previewBox="box-furniture") }
        assertNull(MovingHome.begin(s,"box-furniture").selected)
        assertEquals(s.items,rules.reduce(s,RoomAction.Add("washer")).items)
        assertNull(RoomPersistence.decode(RoomPersistence.encode(s)))
    }
    @Test fun movingDeskCarriesSupportedDevicesAndCancelRestoresAll() {
        val initial=installed()
        val held=rules.reduce(initial,RoomAction.Begin("gamingdesk",Rotation.yaw(25f)))
        val moved=rules.reduce(held,RoomAction.Transform(GrabDelta(Point3(0f,.04f,.01f),Rotation())))
        assertEquals(25f,moved.item("gamingdesk").rotation.yaw(),.001f)
        assertTrue(moved.item("gamingkeyboard").position!=initial.item("gamingkeyboard").position)
        val pivot=moved.item("gamingdesk").position
        val pair=rules.reduce(moved,RoomAction.Transform(GrabDelta(Point3(.6f,.5f,.4f),Rotation.yaw(3f),pointerCount=2)))
        assertEquals(pivot,pair.item("gamingdesk").position)
        assertEquals(initial.items,rules.reduce(pair,RoomAction.Cancel).items)
    }
    @Test fun continuousFloorSupportsSmallGoodsAndMotionCrossesRemovedPartition() {
        val spec=homeExpansion().first { it.id=="laundrybasket" }
        val base=installed().copy(items=installed().items.map { it.copy(packed=true,support=null) })
        val initial=base.copy(items=base.items+RoomItem(spec.copy(id="test"),Point3(.15f,-.225f,2.0f)))
        val held=RoomPhysics.constrainHeld(initial,initial.item("test"),Point3(-.7f,0f,0f),Rotation(),false)
        assertEquals(-.55f,held.item("test").position.x,.001f)
        val gap=RoomItem(spec,Point3(0f,-.225f,1.2f))
        assertTrue(RoomPhysics.inside(gap))
        assertTrue(RoomPhysics.supported(base,gap))
    }
    @Test fun userObstructionPacksOnlyNewEquipmentWithoutMovingOldObject() {
        val before=old().let { s->s.copy(items=s.items.map { i->if(i.spec.id=="chair")
            i.copy(packed=false,position=GamingLayout.equipment().first { it.spec.id=="gamingchair" }.position)
            else i }) }
        val next=GamingRenovation.prepare(before)
        assertEquals(before.item("chair"),next.item("chair"))
        assertTrue(next.item("gamingchair").packed)
        assertTrue("gamingchair" in next.stock("box-furniture"))
        assertFalse(next.item("gamingdesk").packed)
    }
}
