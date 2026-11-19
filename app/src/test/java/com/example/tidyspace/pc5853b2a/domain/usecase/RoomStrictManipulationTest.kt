package com.example.tidyspace.pc5853b2a.domain.usecase

import com.example.tidyspace.pc5853b2a.data.repository.RoomPersistence
import com.example.tidyspace.pc5853b2a.domain.model.*
import com.example.tidyspace.pc5853b2a.ui.sorting.RoomViewModel
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class RoomStrictManipulationTest {
    private fun room(id: String="chair",yaw: Float=0f)=MovingHome.prepare(RoomGame()).let { s ->
        s.copy(items=s.items.map {
            if(it.spec.id==id) it.copy(packed=false,support=null,
                position=Point3(0f,-.32f+it.spec.size.y/2,0f),rotation=Rotation.yaw(yaw))
            else it.copy(packed=true,support=null)
        },boxStock=s.boxStock.mapValues { (_,stock)->stock.filterNot { it==id } })
    }
    private class Session(initial: RoomGame) {
        var saved=RoomPersistence.encode(initial)
        val vm=RoomViewModel(saved) { saved=it }
        val game get()=vm.state.value.game
        val input=RoomGestureInput(
            begin={ vm.onEvent(RoomAction.Begin(it,Rotation.yaw(120f))) },
            transform={ vm.onEvent(RoomAction.Transform(it)) },
            end={ vm.onEvent(RoomAction.End) },cancelled={ vm.onEvent(RoomAction.Cancel) },
            movableRoot={ id -> when {
                id.startsWith("@play:")->id.removePrefix("@play:").substringBefore(':')
                id.startsWith("@unpack:")->game.selectedId
                id.startsWith("@house:")->null
                else->id
            } })
        fun event(id: Long,phase: ContactPhase,p: Point3?,target: String?=null,
                  wrist: Float=0f,source: String="GazePinch")=
            RoomContact(id,phase,target,p?.let { HandPose(it,Rotation.yaw(wrist)) },p,source=source)
        fun send(vararg contacts: RoomContact) { input.events(contacts.toList()) }
    }
    @Test fun eitherSingleHandFacesViewerOnMovementAndIgnoresWristYaw() {
        for(id in listOf(1L,2L)) {
            val s=Session(room(yaw=27f));val start=s.game.item("chair")
            s.send(s.event(id,ContactPhase.Down,Point3(0f,0f,0f),"chair"))
            assertEquals(27f,s.game.item("chair").rotation.yaw(),.001f)
            s.send(s.event(id,ContactPhase.Move,Point3(.10f,.15f,.08f),wrist=160f))
            assertTrue((s.game.item("chair").position-(start.position+Point3(.1f,.15f,.08f))).length()<.00001f)
            assertEquals(120f,s.game.item("chair").rotation.yaw(),.001f)
        }
    }
    @Test fun joiningAndReleasingHandFramesCannotTranslateOrJump() {
        for(first in listOf(1L,2L)) {
            val other=3L-first
            val s=Session(room());val pivot=s.game.item("chair").position
            s.send(s.event(first,ContactPhase.Down,Point3(-.2f,0f,0f),"chair"))
            s.send(s.event(first,ContactPhase.Move,Point3(.3f,.2f,.1f)),
                s.event(other,ContactPhase.Down,Point3(.7f,.2f,.1f)))
            assertEquals(pivot,s.game.item("chair").position)
            val offset=Rotation.yaw(45f).rotate(Point3(.2f,0f,0f))
            val center=Point3(.8f,.4f,.3f)
            s.send(s.event(first,ContactPhase.Move,center-offset),
                s.event(other,ContactPhase.Move,center+offset))
            assertEquals(pivot,s.game.item("chair").position)
            assertEquals(45f,s.game.item("chair").rotation.yaw(),.001f)
            val survivor=Point3(.6f,.2f,.4f)
            s.send(s.event(first,ContactPhase.Up,null),s.event(other,ContactPhase.Move,survivor))
            assertEquals(pivot,s.game.item("chair").position)
            s.send(s.event(other,ContactPhase.Move,survivor+Point3(.03f,.1f,.02f),wrist=-100f))
            assertTrue((s.game.item("chair").position-(pivot+Point3(.03f,.1f,.02f))).length()<.00001f)
            // This is the first actual drag after a pair-only turn.
            assertEquals(120f,s.game.item("chair").rotation.yaw(),.001f)
            s.send(s.event(other,ContactPhase.Up,null))
            assertNull(s.game.selectedId)
            assertEquals(s.game.items,RoomPersistence.decode(s.saved)!!.items)
        }
    }
    @Test fun invalidSecondHandPausesWithoutFallingBackToSingleHandMotion() {
        val s=Session(room());val original=s.game.item("chair")
        s.send(s.event(1,ContactPhase.Down,Point3(-.2f,0f,0f),"chair"))
        s.send(s.event(2,ContactPhase.Down,null))
        s.send(s.event(1,ContactPhase.Move,Point3(.3f,.3f,.3f)))
        assertEquals(original,s.game.item("chair"))
        s.send(s.event(1,ContactPhase.Move,Point3(-.2f,0f,0f)),
            s.event(2,ContactPhase.Move,Point3(.2f,0f,0f)))
        assertEquals(original,s.game.item("chair"))
        s.send(s.event(1,ContactPhase.Move,Point3(0f,-.2f,.2f)),
            s.event(2,ContactPhase.Move,Point3(0f,.2f,-.2f)))
        assertEquals(original.position,s.game.item("chair").position)
        assertEquals(90f,s.game.item("chair").rotation.yaw(),.001f)
    }
    @Test fun nullTrackingSampleAndInputKindChangeRebaselineBeforeResuming() {
        val s=Session(room())
        s.send(s.event(1,ContactPhase.Down,Point3(0f,0f,0f),"chair"))
        s.send(s.event(1,ContactPhase.Move,Point3(.03f,.1f,0f)))
        val held=s.game.item("chair")
        s.send(s.event(1,ContactPhase.Move,null))
        s.send(s.event(1,ContactPhase.Move,Point3(.8f,.5f,0f)))
        assertEquals(held,s.game.item("chair"))
        s.send(s.event(1,ContactPhase.Move,Point3(-.8f,.5f,0f),source="RayBasedPinch"))
        assertEquals(held,s.game.item("chair"))
        s.send(s.event(1,ContactPhase.Move,Point3(-.77f,.5f,0f),source="RayBasedPinch"))
        assertEquals(held.position.x+.03f,s.game.item("chair").position.x,.00001f)
    }
    @Test fun releasingValidHandKeepsCaptureWhileOtherPressedHandHasNoPose() {
        val s=Session(room())
        s.send(s.event(1,ContactPhase.Down,Point3(-.2f,0f,0f),"chair"))
        s.send(s.event(2,ContactPhase.Down,null))
        s.send(s.event(1,ContactPhase.Up,null))
        assertEquals("chair",s.game.selectedId)
        val pivot=s.game.item("chair").position
        s.send(s.event(2,ContactPhase.Move,Point3(.3f,.2f,.1f)))
        assertEquals(pivot,s.game.item("chair").position)
        s.send(s.event(2,ContactPhase.Move,Point3(.33f,.3f,.1f)))
        assertTrue((pivot+Point3(.03f,.1f,0f)-s.game.item("chair").position).length()<.00001f)
        s.send(s.event(2,ContactPhase.Up,null))
        assertNull(s.game.selectedId)
    }
    @Test fun draggingToyMechanismKeepsItsActionAndNeverMovesTheBody() {
        for((id,target) in listOf("woodfish" to "@play:woodfish","popboard" to "@play:popboard:2")) {
        val s=Session(room(id));val original=s.game.item(id)
        s.send(s.event(1,ContactPhase.Down,Point3(0f,0f,0f),target))
        s.send(s.event(1,ContactPhase.Move,Point3(.1f,.1f,0f),wrist=90f))
        assertEquals(target,s.game.selectedId)
        assertEquals(original.position,s.game.item(id).position)
        assertEquals(original.rotation,s.game.item(id).rotation)
        s.send(s.event(1,ContactPhase.Up,null))
        assertEquals(0L,s.game.playSequence)
        }
    }
    @Test fun secondHandKeepsExtractedItemInsteadOfReturningItToTheBox() {
        val initial=MovingHome.prepare(RoomGame(),fresh=true).let { s ->
            s.copy(cleaning=null,previewBox="box-furniture")
        }
        val s=Session(initial)
        val first=initial.stock("box-furniture").first()
        s.vm.onEvent(RoomAction.Add(first))
        s.send(s.event(1,ContactPhase.Down,Point3(-.2f,0f,0f),"@unpack:box-furniture"))
        assertEquals(first,s.game.selectedId)
        val pivot=s.game.item(first).position
        s.send(s.event(2,ContactPhase.Down,Point3(.2f,0f,0f)))
        s.send(s.event(1,ContactPhase.Move,Point3(0f,0f,.2f)),
            s.event(2,ContactPhase.Move,Point3(0f,0f,-.2f)))
        assertEquals(first,s.game.selectedId)
        assertEquals(pivot,s.game.item(first).position)
        assertFalse(s.game.item(first).packed)
        assertEquals(initial.stock("box-furniture"),s.game.stock("box-furniture"))
    }
    @Test fun reducerRejectsTranslationInPairPhaseAndRotationInSinglePhase() {
        val rules=RoomUseCase()
        val held=rules.reduce(room(),RoomAction.Begin("chair"))
        val pair=rules.reduce(held,RoomAction.Transform(GrabDelta(Point3(.4f,.3f,.2f),
            Rotation.yaw(62f),pointerCount=2)))
        assertEquals(held.item("chair").position,pair.item("chair").position)
        assertEquals(62f,pair.item("chair").rotation.yaw(),.001f)
        val single=rules.reduce(pair,RoomAction.Transform(GrabDelta(Point3(.03f,.1f,0f),Rotation.yaw(80f))))
        assertEquals(62f,single.item("chair").rotation.yaw(),.001f)
    }
    @Test fun everyAssetFollowsAllAxesWithoutFloorOrWallStopsWhileDragged() {
        val rules=RoomUseCase()
        for(spec in uniqueCatalogue()) for(yaw in listOf(0f,37f,90f)) {
            var s=room(spec.id,yaw)
            s=rules.reduce(s,RoomAction.Begin(spec.id))
            for(delta in listOf(Point3(0f,-.8f,0f),Point3(-2f,0f,0f),Point3(0f,0f,-2f))) {
                val before=s.item(spec.id).position
                s=rules.reduce(s,RoomAction.Transform(GrabDelta(delta,Rotation.yaw(123f))))
                assertTrue("${spec.id} yaw=$yaw delta=$delta",
                    (s.item(spec.id).position-before-delta).length()<.00001f)
                assertEquals(yaw,s.item(spec.id).rotation.yaw(),.001f)
            }
        }
    }
    @Test fun crossingWallAndReversingRespondsImmediately() {
        val s=Session(room());s.send(s.event(1,ContactPhase.Down,Point3(0f,0f,0f),"chair"))
        s.send(s.event(1,ContactPhase.Move,Point3(-2f,.2f,.3f)))
        val contact=s.game.item("chair")
        assertEquals(.3f,contact.position.z,.00001f)
        assertEquals(-2f,contact.position.x,.00001f)
        s.send(s.event(1,ContactPhase.Move,Point3(-1.97f,.2f,.3f)))
        assertEquals(contact.position.x+.03f,s.game.item("chair").position.x,.00001f)
    }
    @Test fun rotationNearWallKeepsFullYawWithoutTranslatingPivot() {
        val rules=RoomUseCase()
        val start=room("sofa").let { s -> s.copy(items=s.items.map {
            if(it.spec.id=="sofa") it.copy(position=Point3(0f,-.185f,RoomLayout.back+.17f)) else it
        }) }
        val held=rules.reduce(rules.reduce(start,RoomAction.Begin("sofa")),
            RoomAction.Transform(GrabDelta(Point3(1f,.2f,.4f),Rotation.yaw(90f),pointerCount=2)))
        assertEquals(start.item("sofa").position,held.item("sofa").position)
        assertEquals(90f,held.item("sofa").rotation.yaw(),.001f)
    }
    @Test fun singleHandReleaseNearWallAlignsBroadBackAtALegalContact() {
        val rules=RoomUseCase()
        val start=room("desk",90f).let { s -> s.copy(items=s.items.map {
            if(it.spec.id=="desk") it.copy(position=Point3(.5f,-.15f,RoomLayout.back+.35f)) else it
        }) }
        val held=rules.reduce(rules.reduce(start,RoomAction.Begin("desk")),
            RoomAction.Transform(GrabDelta(Point3(0f,.1f,-.07f),Rotation.yaw(160f))))
        val placed=rules.reduce(held,RoomAction.End)
        assertEquals(90f,held.item("desk").rotation.yaw(),.001f)
        assertEquals(0f,placed.item("desk").rotation.yaw(),.001f)
        assertTrue(placed.item("desk").position.z<held.item("desk").position.z)
        assertTrue(RoomPhysics.supported(placed,placed.item("desk")))
        assertTrue(RoomPhysics.heldClear(placed,setOf("desk")))
    }
    @Test fun carriedChildrenFollowAcrossWallsAndFloorAndCancelTogether() {
        val rules=RoomUseCase()
        val start=room("desk").let { s -> s.copy(items=s.items.map {
            if(it.spec.id=="plant") it.copy(packed=false,support="desk",position=Point3(.21f,.10f,0f)) else it
        }) }
        var held=rules.reduce(start,RoomAction.Begin("desk"))
        held=rules.reduce(held,RoomAction.Transform(GrabDelta(Point3(-2f,-1f,-2f),Rotation())))
        assertEquals(start.item("desk").position+Point3(-2f,-1f,-2f),held.item("desk").position)
        assertTrue((held.item("desk").position+Point3(.21f,.25f,0f)-held.item("plant").position).length()<.00001f)
        assertEquals(start.items,rules.reduce(held,RoomAction.Cancel).items)
    }
    @Test fun lostHandRestoresLayoutAndNeverCommitsUnstablePlacement() {
        val initial=room()
        val s=Session(initial)
        s.send(s.event(1,ContactPhase.Down,Point3(0f,0f,0f),"chair"))
        s.send(s.event(1,ContactPhase.Move,Point3(.1f,.1f,0f)))
        s.send(s.event(2,ContactPhase.Down,Point3(.4f,.1f,0f)))
        s.send(s.event(2,ContactPhase.Lost,null))
        assertEquals(initial.items,s.game.items)
        assertNull(s.game.selectedId)
        assertEquals(initial.items,RoomPersistence.decode(s.saved)!!.items)
    }
    @Test fun allCleaningToolsTrashAndBinMoveFreelyAndCancelProgress() {
        val rules=RoomUseCase()
        val initial=RoomGame(cleaning=CleaningState())
        val ids=CleanTool.entries.map { "@clean:${it.name}" }+listOf("@clean:Bin")+
            cleaningMess().filter { it.kind==MessKind.Trash }.map { "@trash:${it.id}" }
        for(id in ids) {
            var s=rules.reduce(initial,RoomAction.Begin(id))
            for(delta in listOf(Point3(-2f,-1f,-2f),Point3(4f,1f,4f))) {
                val before=s.cleaning!!.position
                s=rules.reduce(s,RoomAction.Transform(GrabDelta(delta,Rotation.yaw(120f))))
                val c=s.cleaning!!
                assertTrue(id,(c.position-before-delta).length()<.00001f)
                assertEquals(initial.cleaning!!.progress,c.progress)
            }
            assertEquals(initial.cleaning!!.progress,rules.reduce(s,RoomAction.Cancel).cleaning!!.progress)
        }
    }
}
