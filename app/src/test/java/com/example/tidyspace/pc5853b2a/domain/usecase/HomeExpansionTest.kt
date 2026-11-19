package com.example.tidyspace.pc5853b2a.domain.usecase

import com.example.tidyspace.pc5853b2a.domain.model.*
import com.example.tidyspace.pc5853b2a.data.repository.RoomPersistence
import org.junit.Assert.*
import org.junit.Test

class HomeExpansionTest {
    private val rules=RoomUseCase()
    @Test fun oldCurrentSaveAddsOnlyNewStockAndPreservesEveryExistingObject() {
        val all=MovingHome.prepare(RoomGame())
        val added=(homeExpansion()+handheldExpansion()).map { it.id }.toSet()
        val old=all.copy(items=all.items.filter { it.spec.id !in added },
            boxStock=all.boxStock.mapValues { (_,stock)->stock.filterNot { it in added } },
            dayHour=17.5f,roomYaw=71f,toyStates=mapOf("ratchet" to ToyState(detent=7)),
            playCounts=mapOf("ratchet" to 123L),achievements=setOf("tea"),
            cleaning=CleaningState(mapOf("dust-0" to .5f)))
        val migrated=MovingHome.prepare(checkNotNull(RoomPersistence.decode(RoomPersistence.encode(old))))
        assertEquals(old.items,migrated.items.filter { it.spec.id !in added })
        assertEquals(old.toyStates,migrated.toyStates)
        assertEquals(old.playCounts,migrated.playCounts)
        assertEquals(old.cleaning,migrated.cleaning)
        assertEquals(old.achievements,migrated.achievements)
        assertEquals(old.dayHour,migrated.dayHour,0f)
        assertEquals(old.roomYaw,migrated.roomYaw,0f)
        assertEquals(30,migrated.items.count { it.spec.id in added && it.packed })
        assertEquals(migrated,MovingHome.prepare(migrated))
        assertEquals(migrated.items,RoomPersistence.decode(RoomPersistence.encode(migrated))!!.items)
    }
    @Test fun everyNewObjectCanLeaveItsBoxAndFindALegalFloorOrWallPlacement() {
        val base=MovingHome.prepare(RoomGame())
        for(spec in homeExpansion()+handheldExpansion()) {
            val selected=rules.reduce(base,RoomAction.Add(spec.id))
            assertEquals(spec.id,selected.previewId)
            assertEquals(base.remainingGoods,selected.remainingGoods)
            val box=MovingHome.boxFor(spec)
            val held=rules.reduce(selected,RoomAction.Begin("@unpack:$box",Rotation()))
            val target=if(spec.kind==Furnishing.WallMirror) Point3(.8f,.3f,RoomLayout.back+.07f)
                else Point3(.3f,.20f,.0f)
            val moved=rules.reduce(held,RoomAction.Transform(GrabDelta(target-held.selected!!.position,Rotation())))
            val placed=rules.reduce(moved,RoomAction.End)
            assertFalse("${spec.id}: ${placed.message}",placed.item(spec.id).packed)
            assertTrue(spec.id,RoomPhysics.supported(placed,placed.item(spec.id)))
            assertTrue(spec.id,RoomPhysics.heldClear(placed,setOf(spec.id)))
            assertEquals(base.remainingGoods-1,placed.remainingGoods)
        }
    }
    @Test fun newHandheldMechanismsHaveDistinctPersistentOutcomes() {
        val toys=handheldExpansion().map { it.id }.toSet()
        var state=MovingHome.prepare(RoomGame()).let { g->g.copy(
            items=g.items.map { if(it.spec.id in toys) it.copy(packed=false) else it },
            boxStock=g.boxStock.mapValues { (_,stock)->stock.filterNot { it in toys } }) }
        fun action(target: String,delta: GrabDelta?=null) {
            state=rules.reduce(state,RoomAction.Begin(target))
            if(delta!=null) state=rules.reduce(state,RoomAction.Transform(delta))
            state=rules.reduce(state,RoomAction.End)
        }
        action("@play:togglebox:1")
        assertEquals(2,state.toyStates["togglebox"]?.bubbles)
        action("@play:abacus:4")
        assertEquals(16,state.toyStates["abacus"]?.bubbles)
        action("@play:slider",GrabDelta(Point3(.05f,0f,0f),Rotation()))
        assertEquals(4,state.toyStates["slider"]?.detent)
        action("@play:bell")
        assertEquals(1L,state.playCounts["bell"])
        action("@play:hourglass")
        assertTrue(state.toyStates["hourglass"]!!.open)
        action("@play:windmill",GrabDelta(Point3(0f,.075f,0f),Rotation.yaw(40f)))
        assertTrue((state.playCounts["windmill"] ?: 0)>0)
        val restored=RoomPersistence.decode(RoomPersistence.encode(state))
        assertNotNull(restored)
        assertEquals(state.toyStates,restored!!.toyStates)
        assertEquals(state.playCounts,restored.playCounts)
    }
}
