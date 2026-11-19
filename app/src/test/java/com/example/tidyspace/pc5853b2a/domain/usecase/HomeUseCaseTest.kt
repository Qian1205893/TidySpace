package com.example.tidyspace.pc5853b2a.domain.usecase

import com.example.tidyspace.pc5853b2a.data.repository.HomeRepository
import com.example.tidyspace.pc5853b2a.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class HomeUseCaseTest {
    private class Store: HomeRepository {
        var value: HomeLibrary?=null
        var fail=false
        var unreadable=false
        var writes=0
        override fun load(): HomeLibrary? { check(!unreadable);return value }
        override fun save(library: HomeLibrary) { check(!fail);value=library;writes++ }
    }
    private fun game()=StudyRenovation.prepare(MovingHome.prepare(RoomGame(
        cleaning=CleaningState().completed(),achievements=setOf("zen"),
        playCounts=mapOf("woodfish" to 13L)),fresh=true))
    private fun template()=Showroom("test","窗边植物","植物","入门","植物","植物","挪一件",
        RoomMood.Sunset,17f,.7f,listOf(ShowroomTarget("plant",Point3(0f,-.24f,0f),0f,null,"会客")),
        emptyList(),emptyList(),emptyList(),version=3)
    private fun rules(store: Store)=HomeUseCase(store,clock={1234L},identity={ "home-${store.writes}" })
    @Test fun migrationPreservesGameAndUsesStableIdentityOnce() {
        val store=Store();val first=rules(store);val old=game().copy(
            roomYaw=47f,indoorBrightness=.34f,boxStock=game().boxStock.mapValues { it.value.reversed() })
        val session=ShowroomSession(title="收藏小屋",note="保留说明",reference="retro",undo=old)
        val library=first.initialize(old,session)
        assertEquals("migrated-primary-home",library.activeId)
        assertEquals("收藏小屋",library.active.name)
        assertEquals(old.items,library.active.game.items)
        assertEquals(old.boxStock,library.active.game.boxStock)
        assertEquals(old.playCounts,library.playCounts)
        assertEquals(session,library.active.session)
        val second=rules(store);second.initialize(game(),ShowroomSession())
        assertEquals(1,second.library.homes.size);assertEquals(1,store.writes)
    }
    @Test fun templateCreatesIndependentVersionPinnedHome() {
        val store=Store();val rules=rules(store);rules.initialize(game(),ShowroomSession())
        val before=rules.library.active.game
        val next=rules.create("植物小屋",template(),before,ShowroomSession())
        assertEquals(2,next.homes.size);assertNotEquals("migrated-primary-home",next.activeId)
        assertEquals(before.items,next.homes.first().game.items)
        assertEquals(3,next.active.sourceTemplateVersion)
        assertEquals("test",next.active.sourceTemplateId)
        assertFalse(next.active.game.item("plant").packed)
        assertEquals(RoomMood.Sunset,next.active.game.mood)
        assertEquals(13L,next.active.game.playCounts["woodfish"])
    }
    @Test fun blankHomeKeepsAllUnlockedStockAndStartsClean() {
        val store=Store();val rules=rules(store);rules.initialize(game(),ShowroomSession())
        val next=rules.create("留白",null,rules.activeGame(),ShowroomSession())
        assertTrue(next.active.game.items.all { it.packed || it.spec.kind==Furnishing.MovingBox })
        assertTrue(next.active.game.decorating)
        assertTrue(next.active.game.remainingGoods>100)
        assertEquals(game().items.size,next.active.game.items.size)
        assertEquals(setOf("zen"),next.achievements)
    }
    @Test fun copyingPreservesLayoutAndNewEditsDoNotMoveSource() {
        val store=Store();val rules=rules(store);rules.initialize(game(),ShowroomSession())
        val original=rules.library.active
        rules.duplicate(original.id,rules.activeGame(),ShowroomSession())
        assertNotEquals(original.id,rules.library.activeId)
        assertEquals(original.game.items,rules.library.active.game.items)
        val updated=rules.activeGame().copy(indoorBrightness=.12f,roomYaw=90f,
            boxStock=rules.activeGame().boxStock.mapValues { it.value.reversed() })
        rules.save(updated,ShowroomSession())
        assertEquals(original.game.items,rules.library.homes.first().game.items)
        assertEquals(original.game.roomYaw,rules.library.homes.first().game.roomYaw)
        assertEquals(original.game.boxStock,rules.library.homes.first().game.boxStock)
    }
    @Test fun switchingCommitsSourceAndUsesTargetLightAndToyState() {
        val store=Store();val rules=rules(store);rules.initialize(game(),ShowroomSession())
        val source=rules.library.activeId
        rules.create("晚间",template(),rules.activeGame(),ShowroomSession())
        val target=rules.library.activeId
        rules.save(rules.activeGame().copy(toyStates=mapOf("popboard" to ToyState(bubbles=5))),ShowroomSession())
        rules.switch(source,rules.activeGame(),ShowroomSession())
        val sourceChanged=rules.activeGame().copy(indoorBrightness=.19f,playCounts=mapOf("woodfish" to 20L))
        rules.switch(target,sourceChanged,ShowroomSession())
        assertEquals(.19f,rules.library.homes.first().game.indoorBrightness,0f)
        assertEquals(.7f,rules.activeGame().indoorBrightness,0f)
        assertEquals(5,rules.activeGame().toyStates["popboard"]?.bubbles)
        assertEquals(20L,rules.activeGame().playCounts["woodfish"])
    }
    @Test fun failedCreationAndSwitchLeaveCommittedLibraryUnchanged() {
        val store=Store();val rules=rules(store);rules.initialize(game(),ShowroomSession())
        rules.duplicate(rules.library.activeId,rules.activeGame(),ShowroomSession())
        val before=rules.library
        store.fail=true
        assertTrue(runCatching { rules.create("失败",null,rules.activeGame(),ShowroomSession()) }.isFailure)
        assertEquals(before,rules.library);assertEquals(before,store.value)
        assertTrue(runCatching { rules.switch(before.homes.first().id,rules.activeGame(),ShowroomSession()) }.isFailure)
        assertEquals(before.activeId,rules.library.activeId)
        store.fail=false
        rules.switch(before.homes.first().id,rules.activeGame(),ShowroomSession())
        assertEquals(before.homes.first().id,rules.library.activeId)
    }
    @Test fun heldObjectsBlockSwitchCopyAndCreate() {
        val store=Store();val rules=rules(store);rules.initialize(game(),ShowroomSession())
        val held=rules.activeGame().copy(selectedId="plant")
        val writes=store.writes
        assertTrue(runCatching { rules.switch(rules.library.activeId,held,ShowroomSession()) }.isFailure)
        assertTrue(runCatching { rules.duplicate(rules.library.activeId,held,ShowroomSession()) }.isFailure)
        assertTrue(runCatching { rules.create("未放下",null,held,ShowroomSession()) }.isFailure)
        assertEquals(writes,store.writes)
    }
    @Test fun currentAndLastHomesCannotBeDeletedAndUndoRestoresSnapshot() {
        val store=Store();val rules=rules(store);rules.initialize(game(),ShowroomSession())
        val source=rules.library.active
        assertTrue(runCatching { rules.delete(source.id,rules.activeGame(),ShowroomSession()) }.isFailure)
        rules.duplicate(source.id,rules.activeGame(),ShowroomSession())
        val dirty=rules.activeGame().copy(roomYaw=123f)
        rules.delete(source.id,dirty,ShowroomSession())
        assertEquals(1,rules.library.homes.size)
        assertEquals(123f,rules.library.active.game.roomYaw,0f)
        rules.undoDelete(rules.activeGame(),ShowroomSession())
        assertEquals(source.id,rules.library.homes.last().id)
        assertEquals(source.name,rules.library.homes.last().name)
        assertEquals(source.game.items,rules.library.homes.last().game.items)
        assertEquals(source.game.boxStock,rules.library.homes.last().game.boxStock)
        assertEquals(2,rules.library.homes.size)
        assertNull(rules.undoName)
    }
    @Test fun failedDeleteDoesNotPublishUndoOrLoseSource() {
        val store=Store();val rules=rules(store);rules.initialize(game(),ShowroomSession())
        val id=rules.library.activeId
        rules.duplicate(id,rules.activeGame(),ShowroomSession());store.fail=true
        assertTrue(runCatching { rules.delete(id,rules.activeGame(),ShowroomSession()) }.isFailure)
        assertNull(rules.undoName);assertEquals(2,rules.library.homes.size)
    }
    @Test fun unicodeNameLimitsAndRepeatedNamesDoNotBecomeIdentity() {
        val store=Store();val rules=rules(store);rules.initialize(game(),ShowroomSession())
        assertTrue(HomeUseCase.validName("🪴".repeat(20)))
        assertFalse(HomeUseCase.validName("🪴".repeat(21)))
        assertFalse(HomeUseCase.validName("   "))
        rules.rename(rules.library.activeId," 相同名称 ",rules.activeGame(),ShowroomSession())
        rules.create("相同名称",null,rules.activeGame(),ShowroomSession())
        assertEquals("相同名称 2",rules.library.active.name)
        rules.rename(rules.library.activeId,"相同名称",rules.activeGame(),ShowroomSession())
        assertEquals(2,rules.library.homes.map { it.id }.distinct().size)
    }
    @Test fun unfinishedCleaningIsNotResetAndCannotCreateAnotherHouse() {
        val store=Store();val rules=rules(store);val dirty=game().copy(cleaning=CleaningState(progress=mapOf("dust-0" to .25f)))
        rules.initialize(dirty,ShowroomSession())
        assertEquals(dirty.cleaning,rules.activeGame().cleaning)
        assertTrue(runCatching { rules.create("第二间",null,rules.activeGame(),ShowroomSession()) }.isFailure)
        assertFalse(rules.library.tutorialComplete)
    }
    @Test fun corruptExistingLibraryIsProtectedUntilSuccessfulRead() {
        val store=Store();store.unreadable=true
        val rules=rules(store);rules.initialize(game(),ShowroomSession())
        assertTrue(rules.loadError);assertEquals(0,store.writes)
        assertTrue(runCatching { rules.save(game(),ShowroomSession()) }.isFailure)
        val good=rules.library
        store.value=good;store.unreadable=false
        rules.retryRead();assertFalse(rules.loadError)
        rules.save(game(),ShowroomSession());assertEquals(1,store.writes)
    }
}
