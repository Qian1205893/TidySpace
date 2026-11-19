package com.example.tidyspace.pc5853b2a.domain.usecase

import com.example.tidyspace.pc5853b2a.domain.model.*
import com.example.tidyspace.pc5853b2a.ui.sorting.RoomViewModel
import org.junit.Assert.*
import org.junit.Test

class RoomCardDropTest {
    private fun initial(): RoomGame {
        val specs=roomCatalogue()
        return RoomGame(items=specs.map { RoomItem(it,packed=true,support=null) },movingHome=true,
            boxStock=mapOf("box-furniture" to listOf("chair"),"box-home" to listOf("plant","mug")),
            achievements=setOf("old-achievement"),playCounts=mapOf("woodfish" to 23L))
    }
    private fun begin(s: RoomGame,id: String="chair",token: Long=1)=
        RoomCardDrop.begin(s,id,token,Rotation.yaw(21f))
    private fun at(s: RoomGame,p: Point3=Point3(0f,.35f,0f))=
        RoomCardDrop.move(s,s.cardDrag!!.token,p)

    @Test fun selectionAndMotionDoNotUnpackReorderOrConsume() {
        val s=initial();val dragging=at(begin(s))
        assertEquals(s.items,dragging.items);assertEquals(s.boxStock,dragging.boxStock)
        assertEquals(s.nextSerial,dragging.nextSerial);assertEquals(s.additions,dragging.additions)
        assertNull(dragging.selectedId);assertTrue(RoomCardDrop.preview(dragging)!!.valid)
    }
    @Test fun releaseCommitsTheExactCardOnce() {
        val s=initial();val next=RoomCardDrop.release(at(begin(s,"mug")),1)
        assertFalse(next.item("mug").packed)
        assertEquals(s.item("plant"),next.item("plant"));assertEquals(s.item("chair"),next.item("chair"))
        assertEquals(listOf("plant"),next.stock("box-home"));assertEquals(s.stock("box-furniture"),next.stock("box-furniture"))
        assertEquals(s.nextSerial+1,next.nextSerial);assertEquals(s.additions+1,next.additions)
        assertEquals(next,RoomCardDrop.release(next,1));assertNull(next.cardDrag);assertNull(next.selectedId)
    }
    @Test fun cancelledOutOfBoundsAndReturnedToTrayLeaveInventoryUntouched() {
        val s=initial()
        for(next in listOf(RoomCardDrop.cancel(at(begin(s)),1),
            RoomCardDrop.release(at(begin(s),Point3(10f,.35f,0f)),1),
            RoomCardDrop.release(RoomCardDrop.move(at(begin(s)),1,null),1))) {
            assertEquals(s.items,next.items);assertEquals(s.boxStock,next.boxStock)
            assertEquals(s.nextSerial,next.nextSerial);assertNull(next.cardDrag)
        }
    }
    @Test fun lostOldContactCannotCancelOrCommitAnotherDrag() {
        val s=RoomCardDrop.cancel(begin(initial()),1)
        val retry=at(begin(s,"mug",2))
        assertEquals(retry,RoomCardDrop.release(retry,1))
        assertEquals(retry,RoomCardDrop.cancel(retry,1))
        assertEquals(retry,RoomCardDrop.move(retry,1,Point3(10f,10f,10f)))
        assertEquals(s,begin(s,token=1))
        assertFalse(RoomCardDrop.release(retry,2).item("mug").packed)
    }
    @Test fun exclusiveCaptureCleaningAndArchivedItemsAreRejected() {
        val s=initial()
        assertEquals(s.copy(cleaning=CleaningState()),begin(s.copy(cleaning=CleaningState())))
        assertEquals(s.copy(selectedId="@room"),begin(s.copy(selectedId="@room")))
        assertEquals(s.copy(archivedIds=setOf("chair")),begin(s.copy(archivedIds=setOf("chair"))))
        val drag=begin(s)
        assertEquals(drag,begin(drag,"mug",2))
        val reducer=RoomUseCase()
        assertEquals(drag,reducer.reduce(drag,RoomAction.Begin("plant")))
        assertNull(reducer.reduce(drag,RoomAction.Cancel).cardDrag)
    }
    @Test fun rayDropNeedsNoDepthGestureAndStopsAtDeskSurface() {
        val s=initial()
        val mug=s.item("mug").spec
        val ray=RoomCardDrop.project(s,mug,Point3(0f,1f,3f),Point3(0f,0f,1f))!!
        assertEquals(.36f,ray.z,.0001f)
        assertTrue(RoomCardDrop.preview(at(begin(s,"mug"),ray))!!.valid)
        val desk=RoomItem(roomCatalogue().first { it.id=="desk" },Point3(0f,-.15f,0f),packed=false)
        val supported=s.copy(items=s.items.map { if(it.spec.id=="desk") desk else it })
        val top=RoomCardDrop.project(supported,mug,Point3(0f,1f,0f),Point3(0f,0f,0f))!!
        val placed=RoomCardDrop.release(at(begin(supported,"mug"),top),1).item("mug")
        assertEquals("desk",placed.support)
        assertNull(RoomCardDrop.project(s,mug,Point3(0f,1f,3f),Point3(0f,2f,1f)))
    }
    @Test fun blockedPlacementDoesNotFallThroughFurniture() {
        val s=initial()
        val desk=RoomItem(roomCatalogue().first { it.id=="desk" },Point3(0f,-.15f,0f),packed=false)
        val base=s.copy(items=s.items.map { if(it.spec.id=="desk") desk else it })
        val dragging=at(begin(base),Point3(.22f,.35f,0f))
        assertFalse(RoomCardDrop.preview(dragging)!!.valid)
        val next=RoomCardDrop.release(dragging,1)
        assertTrue(next.item("chair").packed);assertEquals(base.boxStock,next.boxStock)
    }
    @Test fun undoRedoReturnsInventoryAndNativePoseTogetherPreservingProgress() {
        val s=initial();val saves=mutableListOf<RoomGame>()
        val vm=RoomViewModel(s) { saves+=it }
        vm.onEvent(RoomAction.CardBegin("chair",1,Rotation()))
        vm.onEvent(RoomAction.CardMove(1,Point3(0f,.35f,0f)))
        assertTrue(saves.isEmpty())
        vm.onEvent(RoomAction.CardRelease(1))
        val placed=vm.currentGame.item("chair")
        assertFalse(placed.packed);assertTrue(vm.currentGame.canUndo);assertEquals(1,saves.size)
        vm.onEvent(RoomAction.Undo)
        assertTrue(vm.currentGame.item("chair").packed);assertEquals(s.boxStock,vm.currentGame.boxStock)
        assertNull(vm.currentGame.cardDrag);assertEquals(s.playCounts,vm.currentGame.playCounts)
        assertTrue(vm.currentGame.achievements.containsAll(s.achievements))
        vm.onEvent(RoomAction.Redo)
        assertEquals(placed,vm.currentGame.item("chair"));assertNull(vm.currentGame.cardDrag)
    }
    @Test fun nonFinitePointsAndDuplicateReleasesCannotConsumeStock() {
        val s=initial();val vm=RoomViewModel(s) {}
        vm.onEvent(RoomAction.CardBegin("chair",1,Rotation()))
        vm.onEvent(RoomAction.CardMove(1,Point3(Float.NaN,0f,0f)))
        vm.onEvent(RoomAction.CardRelease(1));vm.onEvent(RoomAction.CardRelease(1))
        assertEquals(s.items,vm.currentGame.items);assertEquals(s.boxStock,vm.currentGame.boxStock)
        assertFalse(vm.currentGame.canUndo)
    }
}
