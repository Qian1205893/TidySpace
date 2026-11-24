package com.example.tidyspace.pc5853b2a.domain.usecase

import com.example.tidyspace.pc5853b2a.data.repository.RoomPersistence
import com.example.tidyspace.pc5853b2a.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class StudyRenovationTest {
    private val rules=RoomUseCase()
    private fun old()=GamingRenovation.prepare(MovingHome.prepare(RoomGame()))
    private fun installed()=StudyRenovation.prepare(old())
    @Test fun migrationDeletesEveryBathroomInstanceAndPreservesUnrelatedPlayerState() {
        val before=old().copy(roomYaw=37f,playCounts=mapOf("woodfish" to 491L),
            achievements=setOf("zen"),dayHour=19.7f,indoorBrightness=.4f,
            cleaning=CleaningState(progress=mapOf("dust-0" to .25f)))
        val after=StudyRenovation.prepare(before)
        assertTrue(after.items.none { StudyLayout.retired(it.spec) })
        assertTrue(after.archivedIds.isEmpty())
        for(item in before.items.filterNot { StudyLayout.retired(it.spec) || it.spec.id.startsWith("study") })
            assertEquals(item.spec.id,item,after.item(item.spec.id))
        assertEquals(before.playCounts,after.playCounts);assertEquals(before.achievements,after.achievements)
        assertEquals(before.cleaning,after.cleaning);assertEquals(before.roomYaw,after.roomYaw,0f)
        assertEquals(before.dayHour,after.dayHour,0f);assertEquals(before.indoorBrightness,after.indoorBrightness,0f)
        assertEquals(before.layoutVersion,after.layoutVersion)
        assertEquals(after,StudyRenovation.prepare(after))
    }
    @Test fun plannedEquipmentHasLegalSupportsAndNoIntersections() {
        val s=installed()
        for(i in StudyLayout.equipment()) {
            assertFalse(i.spec.id,s.item(i.spec.id).packed)
            assertTrue("inside ${i.spec.id}",RoomPhysics.inside(i))
            assertTrue("supported ${i.spec.id}",RoomPhysics.supported(s,i))
            assertTrue("clear ${i.spec.id}",RoomPhysics.heldClear(s,RoomPhysics.family(s,i.spec.id)))
            val overlaps=s.items.filter { it.spec.id!=i.spec.id && RoomPhysics.intersects(s,i,it) }.map { it.spec.id }
            assertTrue("overlap ${i.spec.id}: $overlaps",overlaps.isEmpty())
        }
        assertEquals(s.items.size,s.items.map { it.spec.assetId }.distinct().size)
    }
    @Test fun legacyV13LoadsThenV14RoundTripsWithoutResurrectingFixtures() {
        val legacy=RoomPersistence.encode(old())
        assertTrue(legacy.startsWith("tidy-room-13\t"))
        val s=StudyRenovation.prepare(checkNotNull(RoomPersistence.decode(legacy)))
        val text=RoomPersistence.encode(s)
        assertTrue(text.startsWith("tidy-room-14\t"))
        val loaded=checkNotNull(RoomPersistence.decode(text))
        assertEquals(s.items,loaded.items);assertEquals(s.boxStock,loaded.boxStock)
        assertEquals(0,loaded.bathroomVersion);assertEquals(1,loaded.studyVersion)
        val again=rules.reduce(loaded,RoomAction.CleanAgain)
        assertTrue(again.items.none { StudyLayout.retired(it.spec) })
        assertEquals(153,availableCatalogue(again).size)
        assertEquals(loaded.remainingGoods,again.remainingGoods)
        assertSame(loaded,again)
        assertEquals(again.items,MovingHome.prepare(again).items)
        assertEquals(again,BathroomLayout.prepare(again))
        assertEquals(again,StudyRenovation.prepare(again))
        assertNotNull(RoomPersistence.decode(RoomPersistence.encode(again)))
    }
    @Test fun nonBathroomDescendantsReturnToTheirOwnBoxes() {
        val before=old().let { s->s.copy(items=s.items.map { i->when(i.spec.id) {
            "sidetable"->i.copy(packed=false,support="bathroom-bathtub")
            "book"->i.copy(packed=false,support="sidetable")
            else->i
        } }) }
        val next=StudyRenovation.prepare(before)
        for(id in listOf("sidetable","book")) {
            assertTrue(next.item(id).packed);assertNull(next.item(id).support)
            assertEquals(before.item(id).position,next.item(id).position)
            assertTrue(id in next.stock(MovingHome.boxFor(next.item(id).spec)))
        }
    }
    @Test fun obstructionKeepsOldPoseAndPacksNewAssembly() {
        val before=old().let { s->s.copy(items=s.items.map { i->if(i.spec.id=="chair")
            i.copy(packed=false,position=StudyLayout.equipment().first { it.spec.id=="studydesk" }.position)
            else i }) }
        val next=StudyRenovation.prepare(before)
        assertEquals(before.item("chair"),next.item("chair"))
        for(id in listOf("studydesk","studybanker","studybook","studyglobe")) {
            assertTrue(id,next.item(id).packed)
            assertTrue(id in next.stock(MovingHome.boxFor(next.item(id).spec)))
        }
        assertFalse(next.item("studyarmchair").packed)
    }
    @Test fun movingDeskCarriesBookAndLampAndCancelRestoresFamily() {
        val s=installed()
        val held=rules.reduce(s,RoomAction.Begin("studydesk",Rotation()))
        val moved=rules.reduce(held,RoomAction.Transform(GrabDelta(Point3(.02f,.03f,.01f),Rotation())))
        assertNotEquals(s.item("studybook").position,moved.item("studybook").position)
        assertEquals(s.items,rules.reduce(moved,RoomAction.Cancel).items)
        val picked=rules.reduce(s,RoomAction.Begin("studybook",Rotation()))
        val shifted=rules.reduce(picked,RoomAction.Transform(GrabDelta(Point3(.012f,.025f,0f),Rotation())))
        val placed=rules.reduce(shifted,RoomAction.End)
        assertEquals("studydesk",placed.item("studybook").support)
        assertEquals(-.708f,placed.item("studybook").position.x,.001f)
        assertEquals(placed.item("studybook"),RoomPersistence.decode(RoomPersistence.encode(placed))!!.item("studybook"))
    }
    @Test fun staleRetiredInventoryCannotBeExtractedOrAcceptedInV14() {
        val s=installed()
        for(id in StudyLayout.retiredAssets+"bathroom-toilet") {
            assertEquals(s.items,rules.reduce(s,RoomAction.Add(id)).items)
        }
        val stale=s.copy(items=s.items+RoomItem(homeExpansion().first { it.id=="toilet" },packed=true),
            boxStock=s.boxStock+("box-furniture" to listOf("toilet")),previewBox="box-furniture")
        assertNull(MovingHome.begin(stale,"box-furniture").selected)
        assertNull(RoomPersistence.decode(RoomPersistence.encode(stale)))
    }
}
