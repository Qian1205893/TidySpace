package com.example.tidyspace.pc5853b2a.domain.usecase

import com.example.tidyspace.pc5853b2a.domain.model.*
import org.junit.Assert.*
import org.junit.Test

/** Drive the same contact state machine as actual handles, rather than bypassing input with actions. */
class PlayGestureRoutingTest {
    private class Rig(id: String) {
        var state=RoomGame(items=RoomGame().items.map { it.copy(packed=true,support=null) }+
            RoomItem(toyCatalogue().first { it.id==id },Point3(.2f,.1f,.2f),packed=false))
        private val rules=RoomUseCase()
        val input=RoomGestureInput(
            begin={ state=rules.reduce(state,RoomAction.Begin(it)) },
            transform={ state=rules.reduce(state,RoomAction.Transform(it)) },
            end={ state=rules.reduce(state,RoomAction.End) },
            cancelled={ state=rules.reduce(state,RoomAction.Cancel) },
            movableRoot={ it.removePrefix("@play:").substringBefore(':') },
        )
        fun sample(pointer: Long,phase: ContactPhase,target: String?=null,
                   p: Point3=Point3(0f,0f,0f),yaw: Float=0f)=
            RoomContact(pointer,phase,target,HandPose(p,Rotation.yaw(yaw)),p,source="hand")
    }
    @Test fun musicCrankTranslationDoesNotBecomeFurnitureDrag() {
        val r=Rig("musicbox")
        r.input.events(listOf(r.sample(1,ContactPhase.Down,"@play:musicbox")))
        for(n in 1..12) r.input.events(listOf(r.sample(1,ContactPhase.Move,
            p=Point3(0f,n*.01f,0f),yaw=n*15f)))
        assertEquals("@play:musicbox",r.state.selectedId)
        assertTrue(r.state.crankRadians>2f)
        assertEquals(Point3(.2f,.1f,.2f),r.state.item("musicbox").position)
        r.input.events(listOf(r.sample(1,ContactPhase.Up)))
        assertTrue((r.state.playCounts["musicbox"] ?: 0)>0)
    }
    @Test fun softToyBeginsOnDownAndSecondHandRetainsItsSqueezeMechanism() {
        val r=Rig("jelly")
        r.input.events(listOf(r.sample(1,ContactPhase.Down,"@play:jelly",Point3(-.15f,0f,0f))))
        assertEquals("@play:jelly",r.state.selectedId)
        r.input.events(listOf(r.sample(2,ContactPhase.Down,"jelly",Point3(.15f,0f,0f))))
        assertEquals("@play:jelly",r.state.selectedId)
        r.input.events(listOf(r.sample(1,ContactPhase.Move,p=Point3(-.08f,0f,0f)),
            r.sample(2,ContactPhase.Move,p=Point3(.08f,0f,0f))))
        assertTrue(r.state.toyGesture.compression>.2f)
        assertEquals(Point3(.2f,.1f,.2f),r.state.item("jelly").position)
        r.input.events(listOf(r.sample(1,ContactPhase.Up),r.sample(2,ContactPhase.Up)))
        assertEquals(1L,r.state.playCounts["jelly"])
    }
    @Test fun singleHandWristTurnReachesRatchetAndAlternatingRattleStrokes() {
        val knob=Rig("ratchet")
        knob.input.events(listOf(knob.sample(1,ContactPhase.Down,"@play:ratchet")))
        knob.input.events(listOf(knob.sample(1,ContactPhase.Move,yaw=65f)))
        assertEquals(2,knob.state.toyStates["ratchet"]?.detent)
        val drum=Rig("rattle")
        drum.input.events(listOf(drum.sample(1,ContactPhase.Down,"@play:rattle")))
        for(yaw in listOf(25f,25f,25f,25f,25f,25f,-25f,-25f,-25f,-25f,-25f,-25f,25f))
            drum.input.events(listOf(drum.sample(1,ContactPhase.Move,yaw=yaw)),.05f)
        assertTrue((drum.state.playCounts["rattle"] ?: 0)>0)
    }
    @Test fun lostHandleCancelsWithoutInventingATapOrPlayCount() {
        val r=Rig("pokeball")
        r.input.events(listOf(r.sample(1,ContactPhase.Down,"@play:pokeball")))
        r.input.events(listOf(r.sample(1,ContactPhase.Lost)))
        assertNull(r.state.selectedId)
        assertNull(r.state.toyStates["pokeball"])
        assertTrue(r.state.playCounts.isEmpty())
    }
}
