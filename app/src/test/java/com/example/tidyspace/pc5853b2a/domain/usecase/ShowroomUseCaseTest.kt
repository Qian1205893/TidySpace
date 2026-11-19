package com.example.tidyspace.pc5853b2a.domain.usecase

import com.example.tidyspace.pc5853b2a.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class ShowroomUseCaseTest {
    private val rules=ShowroomUseCase()
    private fun base()=RoomGame(items=availableCatalogue(RoomGame()).map { RoomItem(it,packed=true) },
        achievements=setOf("old"),playCounts=mapOf("woodfish" to 19),studyVersion=1,boxInventoryVersion=2)
    private fun reference()=Showroom("test","绿意","治愈系","入门","植物小屋","植物","挪一件",
        RoomMood.Morning,9f,.7f,listOf(ShowroomTarget("plant",Point3(0f,-.24f,0f),0f,null,"会客")),
        listOf(ShowroomStep("第一步","放植物",listOf("plant"))),emptyList(),emptyList())
    @Test fun applyReusesAliasAndPacksOtherItemsWithoutLosingProgress() {
        val initial=base().let { s->s.copy(items=s.items.map {
            if(it.spec.assetId=="plant") it.copy(spec=it.spec.copy(id="bathroom-plant")) else it
        }) }
        val next=checkNotNull(rules.apply(initial,reference()).game)
        assertEquals(1,next.items.count { !it.packed })
        assertEquals("bathroom-plant",next.items.single { !it.packed }.spec.id)
        assertEquals(initial.achievements,next.achievements)
        assertEquals(initial.playCounts,next.playCounts)
        assertEquals(next.items.count { it.packed },next.remainingGoods)
        assertTrue(rules.pending(next,reference(),0).isEmpty())
    }
    @Test fun absentStockAndCollisionsRejectWholeLayout() {
        val initial=base()
        assertNull(rules.apply(initial.copy(items=initial.items.filterNot { it.spec.assetId=="plant" }),reference()).game)
        val colliding=reference().copy(targets=reference().targets+
            ShowroomTarget("tallplant",Point3(0f,-.13f,0f),0f,null,"会客"))
        assertNull(rules.apply(initial,colliding).game)
        assertTrue(initial.items.all { it.packed })
    }
    @Test fun restoreRetainsNewNonLayoutProgress() {
        val old=base()
        val applied=checkNotNull(rules.apply(old,reference()).game).copy(
            achievements=setOf("old","new"),playCounts=mapOf("woodfish" to 23))
        val restored=rules.restore(applied,old)
        assertEquals(old.items,restored.items)
        assertEquals(setOf("old","new"),restored.achievements)
        assertEquals(23L,restored.playCounts["woodfish"])
    }
    @Test fun filtersCombineStyleDifficultyAndItemSearch() {
        val r=reference()
        assertEquals(listOf(r),rules.filter(listOf(r),"治愈系","入门","绿叶"))
        assertTrue(rules.filter(listOf(r),"治愈系","进阶","").isEmpty())
        assertTrue(rules.filter(listOf(r),"全部风格","全部难度","不存在").isEmpty())
    }
    @Test fun heldAndUncleanRoomsCannotApply() {
        assertNull(rules.apply(base().copy(selectedId="plant"),reference()).game)
        assertNull(rules.apply(base().copy(cleaning=CleaningState()),reference()).game)
    }
}
