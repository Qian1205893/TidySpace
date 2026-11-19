package com.example.tidyspace.pc5853b2a.domain.usecase

import com.example.tidyspace.pc5853b2a.domain.model.*
import com.example.tidyspace.pc5853b2a.data.repository.RoomPersistence
import com.example.tidyspace.pc5853b2a.ui.sorting.RoomViewModel
import org.junit.Assert.*
import org.junit.Test

class RoomTwoHandInputTest {
    private fun placed()=MovingHome.prepare(RoomGame()).let { s -> s.copy(
        items=s.items.map { item -> when(item.spec.id) {
            "chair"->item.copy(packed=false,position=Point3(0f,-.175f,0f),rotation=Rotation())
            else->item.copy(packed=true,support=null)
        } },
        boxStock=s.boxStock.mapValues { (_,ids)->ids.filterNot { it=="chair" } },
        guideVisible=false)
    }
    private fun contact(id: Long,phase: ContactPhase,degrees: Float,target: String?=null,
                        height: Float=0f): RoomContact {
        val p=Rotation.yaw(degrees).rotate(Point3(if(id==1L) -.2f else .2f,height,0f))
        return RoomContact(id,phase,target,HandPose(p,Rotation.axis(Point3(1f,0f,0f),70f)),p)
    }
    @Test fun differentOrMissedSecondHitTurnsInPlaceAndCommitsUndoAndSave() {
        for(second in listOf(null,"sofa","@house:clock")) for(angle in listOf(1f,75f)) {
            var saved: String?=null
            val vm=RoomViewModel(RoomPersistence.encode(placed())) { saved=it }
            val input=RoomGestureInput(begin={ vm.onEvent(RoomAction.Begin(it)) },
                transform={ vm.onEvent(RoomAction.Transform(it)) },end={ vm.onEvent(RoomAction.End) },
                cancelled={ vm.onEvent(RoomAction.Cancel) },
                movableRoot={ it.takeUnless { key -> key.startsWith("@house:") } })
            input.events(listOf(contact(1,ContactPhase.Down,0f,"chair")))
            input.events(listOf(contact(2,ContactPhase.Down,0f,second)))
            input.events(listOf(contact(1,ContactPhase.Move,angle,height=-.18f),
                contact(2,ContactPhase.Move,angle,height=.25f)))
            val held=vm.state.value.game
            assertEquals("chair",held.selectedId)
            assertEquals(0f,held.roomYaw,0f)
            assertEquals(placed().item("chair").position,held.item("chair").position)
            assertEquals(angle,held.item("chair").rotation.yaw(),.001f)
            assertEquals(Point3(0f,1f,0f),held.item("chair").rotation.rotate(Point3(0f,1f,0f)))
            input.events(listOf(RoomContact(1,ContactPhase.Up)))
            assertEquals("chair",vm.state.value.game.selectedId)
            input.events(listOf(RoomContact(2,ContactPhase.Up)))
            assertNull(vm.state.value.game.selectedId)
            val result=vm.state.value.game
            assertEquals(angle,result.item("chair").rotation.yaw(),.001f)
            assertEquals(result.items,RoomPersistence.decode(saved)!!.items)
            vm.onEvent(RoomAction.Undo)
            assertEquals(0f,vm.state.value.game.item("chair").rotation.yaw(),.001f)
            vm.onEvent(RoomAction.Redo)
            assertEquals(result.items,vm.state.value.game.items)
        }
    }
    @Test fun secondHandRetainsPlayMechanismWithoutMovingTheBody() {
        var s=placed().let { game -> game.copy(items=game.items.map {
            if(it.spec.id=="woodfish") it.copy(packed=false,position=Point3(.6f,-.27f,0f)) else it
        }) }
        val rules=RoomUseCase()
        val input=RoomGestureInput(begin={ s=rules.reduce(s,RoomAction.Begin(it)) },
            transform={ s=rules.reduce(s,RoomAction.Transform(it)) },end={ s=rules.reduce(s,RoomAction.End) },
            cancelled={ s=rules.reduce(s,RoomAction.Cancel) },
            movableRoot={ it.removePrefix("@play:") })
        input.events(listOf(contact(1,ContactPhase.Down,0f,"@play:woodfish")))
        input.events(listOf(contact(2,ContactPhase.Down,0f)))
        input.events(listOf(contact(1,ContactPhase.Move,60f),contact(2,ContactPhase.Move,60f)))
        assertEquals("@play:woodfish",s.selectedId)
        assertEquals(0f,s.item("woodfish").rotation.yaw(),.001f)
        assertEquals(0L,s.playSequence)
        input.events(listOf(RoomContact(1,ContactPhase.Lost)))
        assertEquals(0f,s.item("woodfish").rotation.yaw(),.001f)
        assertNull(s.selectedId)
    }
    @Test fun homeDownTogglesOnceDespiteJitterThenNextPressRestoresPanel() {
        for(cleaning in listOf(null,CleaningState())) {
            var s=placed().copy(cleaning=cleaning)
            val original=s.items
            var count=0
            val rules=RoomUseCase()
            val input=RoomGestureInput(begin={},transform={},end={},cancelled={},
                immediateTap={ target -> if(target=="@house:journal") {
                    count++;s=rules.reduce(s,RoomAction.ToggleControls);true
                } else false })
            repeat(2) { index ->
                input.events(listOf(contact(1,ContactPhase.Down,0f,"@house:journal")))
                repeat(8) {
                    input.events(listOf(contact(1,ContactPhase.Down,20f,"@house:journal"),
                        contact(1,ContactPhase.Move,90f)))
                }
                assertEquals(index==1,s.controlsVisible)
                assertEquals(index+1,count)
                input.events(listOf(RoomContact(1,ContactPhase.Up)))
            }
            assertEquals(original,s.items)
        }
    }
    @Test fun trackingDropAndNearVerticalHandsCannotJumpObjectYaw() {
        val tracker=GrabTracker(uprightPair=true)
        fun pair(degrees: Float)=listOf(1L,2L).associateWith {
            contact(it,ContactPhase.Move,degrees).objectPose!!
        }
        pair(0f).forEach { (id,pose)->tracker.down(id,pose) }
        assertEquals(30f,tracker.update(pair(30f))!!.rotation.yaw(),.001f)
        tracker.update(mapOf(1L to HandPose(Point3(Float.NaN,0f,0f),Rotation())))
        assertEquals(0f,tracker.update(pair(100f))!!.rotation.yaw(),.001f)
        assertEquals(20f,tracker.update(pair(120f))!!.rotation.yaw(),.001f)
        tracker.update(mapOf(1L to HandPose(Point3(0f,-.3f,0f),Rotation()),
            2L to HandPose(Point3(0f,.3f,0f),Rotation())))
        assertEquals(0f,tracker.update(pair(30f))!!.rotation.yaw(),.001f)
        assertEquals(15f,tracker.update(pair(45f))!!.rotation.yaw(),.001f)
    }
    @Test fun releasingAConstrainedTurnNearWallDoesNotSnapYawBack() {
        val start=placed().let { s -> s.copy(items=s.items.map {
            if(it.spec.id=="sofa") it.copy(packed=false,position=Point3(.6f,-.185f,RoomLayout.back+.18f),
                rotation=Rotation()) else it.copy(packed=true)
        }) }
        val rules=RoomUseCase()
        val held=rules.reduce(rules.reduce(start,RoomAction.Begin("sofa")),
            RoomAction.Transform(GrabDelta(Point3(0f,0f,0f),Rotation.yaw(180f),pointerCount=2)))
        val released=rules.reduce(held,RoomAction.End)
        assertTrue(kotlin.math.abs(held.item("sofa").rotation.yaw())>0f)
        assertTrue(kotlin.math.abs(held.item("sofa").rotation.yaw())<180f)
        assertEquals(held.item("sofa").rotation.yaw(),released.item("sofa").rotation.yaw(),.001f)
        assertTrue((start.item("sofa").position-released.item("sofa").position).length()<.000001f)
        assertNull(released.selectedId)
    }
}
