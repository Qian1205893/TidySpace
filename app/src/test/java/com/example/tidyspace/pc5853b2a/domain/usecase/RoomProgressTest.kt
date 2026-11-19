package com.example.tidyspace.pc5853b2a.domain.usecase

import com.example.tidyspace.pc5853b2a.domain.model.*
import com.example.tidyspace.pc5853b2a.data.repository.RoomPersistence
import org.junit.Assert.*
import org.junit.Test

class RoomProgressTest {
    private val rules=RoomUseCase()
    private fun put(s: RoomGame,id: String,p: Point3): RoomGame {
        val held=rules.reduce(s,RoomAction.Begin(id))
        return rules.reduce(rules.reduce(held,RoomAction.Transform(GrabDelta(p-held.item(id).position,Rotation()))),RoomAction.End)
    }
    private fun tap(s: RoomGame,id: String)=rules.reduce(rules.reduce(s,RoomAction.Begin(id)),RoomAction.End)

    @Test fun entireGuideCanBeCompletedByPublicActionsAndKeepsRewards() {
        var s=tap(RoomGame(),"crate:lid")
        assertEquals("guide-2",RoomProgress.current(s)!!.id)
        s=put(s,"plant",Point3(-.80f,-.20f,-.73f))
        s=put(s,"book",Point3(-.47f,.08f,-.43f))
        s=put(s,"mug",Point3(-.27f,.09f,-.43f))
        s=put(s,"chair",Point3(-.36f,-.12f,-.14f))
        s=put(s,"cushion",Point3(.34f,-.08f,-.41f))
        s=put(s,"lamp",Point3(.65f,-.02f,-.28f))
        s=tap(s,"lamp:switch")
        s=put(s,"coffee",Point3(.34f,-.20f,-.09f))
        s=rules.reduce(s,RoomAction.Add("chair"))
        s=rules.reduce(s,RoomAction.Mood(RoomMood.Sunset))
        assertNull("remaining=${RoomProgress.current(s)} message=${s.message}",RoomProgress.current(s))
        assertTrue(RoomProgress.commissions(s).first { it.id=="home" }.complete)
        assertEquals(430,s.xp)
        val earned=s.achievements
        s=put(s,"mug",Point3(-.1f,-.2f,.50f))
        assertEquals(earned,s.achievements)
        s=put(s,"mug",Point3(-.27f,.09f,-.43f))
        assertEquals(430,s.xp)
        assertEquals(s.achievements,RoomPersistence.decode(RoomPersistence.encode(s))!!.achievements)
    }
    @Test fun earlyActionsAreRememberedWithoutRepeatingThem() {
        var s=rules.reduce(RoomGame(),RoomAction.Add("plant"))
        s=rules.reduce(s,RoomAction.Mood(RoomMood.Evening))
        s=rules.reduce(s,RoomAction.Mood(RoomMood.Morning))
        assertTrue(s.achievements.containsAll(setOf("guide-9","guide-10")))
        assertEquals("guide-1",RoomProgress.current(s)!!.id)
    }
    @Test fun cloningRecyclingAndSavingPreserveUniqueInstances() {
        var s=rules.reduce(RoomGame(),RoomAction.Add("chair"))
        assertEquals(14,s.items.size)
        s=rules.reduce(s,RoomAction.Duplicate)
        assertEquals(15,s.items.size)
        assertEquals(15,s.items.map { it.spec.id }.toSet().size)
        val restored=RoomPersistence.decode(RoomPersistence.encode(s))!!
        assertEquals(s.items,restored.items)
        s=rules.reduce(s,RoomAction.Recycle)
        assertEquals(14,s.items.size)
        assertEquals(2,s.additions)
    }
    @Test fun capacityAndOccupiedSpaceProvideActionableFeedback() {
        var s=RoomGame()
        // Pictures fill the available wall; rugs exercise the global capacity independently.
        val rug=s.item("rug")
        s=s.copy(items=s.items+List(ROOM_CAPACITY-s.items.size) { n->
            rug.copy(spec=rug.spec.copy(id="rug~${n+1}"),packed=true,support=null)
        },nextSerial=ROOM_CAPACITY)
        assertEquals(ROOM_CAPACITY,s.items.size)
        val full=rules.reduce(s,RoomAction.Add("rug"))
        assertEquals(s.items,full.items)
        assertTrue(full.message.contains(ROOM_CAPACITY.toString()))
        assertTrue(full.message.contains("收回"))
    }
    @Test fun legacySnapshotMigratesWallsAndPreservesFurnitureFamilies() {
        val old=buildString {
            appendLine("tidy-room-1\ttrue\ttrue\t0.4\t35")
            roomCatalogue().forEach { spec ->
                val p=if(spec.id=="shelf") Point3(.47f,.17f,-.557f)
                    else if(spec.id=="picture") Point3(.065f,.28f,-.606f) else spec.start
                val support=if(spec.packed) "crate" else if(spec.kind in setOf(Furnishing.Shelf,Furnishing.Picture)) "@back" else "-"
                appendLine("${spec.id}\t${p.x}\t${p.y}\t${p.z}\t0\t0\t0\t1\t$support\t${spec.packed}")
            }
        }
        val s=RoomPersistence.decode(old)!!
        assertEquals(-.8635f,s.item("shelf").position.z,.0001f)
        assertEquals(RoomGame().item("desk"),s.item("desk"))
        assertEquals(35f,s.roomYaw,.0001f)
        assertTrue(s.lampOn)
        assertNotNull(RoomPersistence.decode(RoomPersistence.encode(s)))
    }
    @Test fun commissionsRequireRealSupportInsteadOfCountsAlone() {
        var s=RoomGame()
        repeat(3) { s=rules.reduce(s,RoomAction.Add("book")) }
        assertFalse(RoomProgress.commissions(s).first { it.id=="library" }.complete)
        val books=s.items.filter { it.spec.id!=it.spec.assetId && it.spec.kind==Furnishing.Book }
        books.forEachIndexed { index,book ->
            s=put(s,book.spec.id,Point3(.47f,.06f+index*.12f,-.8635f))
            assertEquals(s.message,"shelf",s.item(book.spec.id).support)
        }
        assertTrue(RoomProgress.commissions(s).first { it.id=="library" }.complete)
        assertTrue("library" in s.achievements)
    }
    @Test fun teaGardenGalleryAndLoungeAreAchievableWithoutFixedSlots() {
        var tea=tap(RoomGame(),"crate:lid")
        tea=put(tea,"crate",Point3(.80f,-.22f,.40f))
        tea=put(tea,"chair",Point3(-.16f,-.15f,.30f))
        tea=rules.reduce(tea,RoomAction.Add("chair"))
        tea=put(tea,tea.focusId!!,Point3(.36f,-.15f,.30f))
        tea=put(tea,"mug",Point3(.02f,-.10f,.30f))
        tea=rules.reduce(tea,RoomAction.Add("mug"))
        tea=put(tea,tea.focusId!!,Point3(.18f,-.10f,.30f))
        assertTrue("tea: ${tea.message}",RoomProgress.commissions(tea).first { it.id=="tea" }.complete)

        var garden=tap(RoomGame(),"crate:lid")
        garden=put(garden,"plant",Point3(-.80f,-.20f,-.73f))
        for(x in listOf(-.55f,-.26f)) {
            garden=rules.reduce(garden,RoomAction.Add("plant"))
            garden=put(garden,garden.focusId!!,Point3(x,-.20f,-.79f))
        }
        assertTrue(RoomProgress.commissions(garden).first { it.id=="garden" }.complete)
        repeat(2) { garden=rules.reduce(garden,RoomAction.Add("picture")) }
        assertTrue("gallery: ${garden.message}",RoomProgress.commissions(garden).first { it.id=="gallery" }.complete)

        var lounge=tap(RoomGame(),"crate:lid")
        lounge=put(lounge,"cushion",Point3(.25f,-.08f,-.415f))
        lounge=rules.reduce(lounge,RoomAction.Add("cushion"))
        lounge=put(lounge,lounge.focusId!!,Point3(.42f,-.08f,-.415f))
        lounge=put(lounge,"coffee",Point3(.34f,-.20f,-.09f))
        lounge=put(lounge,"lamp",Point3(.65f,-.02f,-.28f))
        lounge=tap(lounge,"lamp:switch")
        assertTrue("lounge: ${lounge.message}",RoomProgress.commissions(lounge).first { it.id=="lounge" }.complete)
    }
}
