package com.example.tidyspace.pc5853b2a.domain.usecase

import com.example.tidyspace.pc5853b2a.data.repository.RoomPersistence
import com.example.tidyspace.pc5853b2a.domain.model.*
import com.example.tidyspace.pc5853b2a.ui.sorting.RoomViewModel
import org.junit.Assert.*
import org.junit.Test

class RoomNativeGestureTest {
    private fun initial()=MovingHome.prepare(RoomGame()).let { s ->
        s.copy(items=s.items.map {
            if(it.spec.id=="chair") it.copy(packed=false,support=null,position=Point3(0f,-.175f,0f))
            else it.copy(packed=true,support=null)
        },boxStock=s.boxStock.mapValues { (_,v)->v.filterNot { it=="chair" } })
    }
    private class Session {
        var saved=""
        val vm=RoomViewModel(RoomPersistence.encode(initialGame())) { saved=it }
        val game get()=vm.currentGame
        val native=RoomNativeGesture({ vm.onEvent(RoomAction.Begin(it)) },
            { vm.onEvent(RoomAction.Transform(it)) },{ vm.onEvent(RoomAction.End) },
            { vm.onEvent(RoomAction.Cancel) })
        companion object {
            private fun initialGame()=RoomNativeGestureTest().initial()
        }
    }
    private fun delta(x: Float=0f,y: Float=0f,z: Float=0f,yaw: Float=0f,hands: Int=1)=
        GrabDelta(Point3(x,y,z),Rotation.yaw(yaw),pointerCount=hands)

    @Test fun sustainedFramesMoveBeforeComposeAndSaveOnRelease() {
        val s=Session();val origin=s.game.item("chair").position
        s.native.start()
        repeat(60) {
            s.native.frame("chair",delta(.003f,.002f,.001f,yaw=70f))
            assertEquals(origin.x+(it+1)*.003f,s.game.item("chair").position.x,.00001f)
            assertEquals(0f,s.game.item("chair").rotation.yaw(),.0001f)
        }
        s.native.finish()
        assertNull(s.game.selectedId)
        assertEquals(s.game.items,RoomPersistence.decode(s.saved)!!.items)
    }
    @Test fun capturedTargetSurvivesRayMissAndOtherObjectHits() {
        val s=Session();s.native.start()
        s.native.frame("chair",delta(y=.1f))
        s.native.frame(null,delta(x=.05f))
        s.native.frame("sofa",delta(x=.05f))
        assertEquals("chair",s.game.selectedId)
        assertEquals(.1f,s.game.item("chair").position.x,.00001f)
    }
    @Test fun secondHandOnlyTurnsAndRemainingHandResumesTranslation() {
        val s=Session();s.native.start();s.native.frame("chair",delta(y=.12f))
        val center=s.game.item("chair").position
        s.native.frame("chair",delta(x=.3f,y=.3f,yaw=20f,hands=2))
        s.native.frame("chair",delta(x=.3f,y=.3f,yaw=35f,hands=2))
        assertEquals(center,s.game.item("chair").position)
        assertEquals(35f*RoomNativeGesture.TURN_GAIN,s.game.item("chair").rotation.yaw(),.001f)
        s.native.frame("chair",delta(x=.8f,yaw=-100f,hands=1))
        assertEquals(center,s.game.item("chair").position)
        s.native.frame("chair",delta(x=.01f,yaw=-100f))
        assertEquals(center.x+.01f,s.game.item("chair").position.x,.00001f)
        assertEquals(35f*RoomNativeGesture.TURN_GAIN,s.game.item("chair").rotation.yaw(),.001f)
    }
    @Test fun cancelRestoresAndLateFramesCannotMove() {
        val s=Session();val before=s.game.items
        s.native.start();s.native.frame("chair",delta(x=.15f,y=.15f))
        s.native.cancel();s.native.frame("chair",delta(x=.20f))
        assertEquals(before,s.game.items)
        assertNull(s.game.selectedId);assertFalse(s.native.active)
    }
    @Test fun transformWithoutTargetDoesNotTurnTapIntoGrab() {
        var begins=0;var ends=0
        val n=RoomNativeGesture({ begins++ },{ fail("No target, no transform") },{ ends++ },{})
        n.start();n.frame(null,delta(x=.1f));n.finish()
        assertEquals(0,begins);assertEquals(0,ends)
    }
    @Test fun stationaryFirstFrameLeavesBoxAndToyTapAvailable() {
        var begins=0
        val tapped=mutableListOf<String>()
        val n=RoomNativeGesture({ begins++ },{ fail("No motion, no drag") },{}, {},tapped::add)
        for(id in listOf("box-home","mochi","@unpack:box-home")) {
            n.start();n.frame(id,delta(yaw=90f),if(id=="mochi") "@play:mochi" else id)
            assertFalse(n.captured)
            n.finish()
        }
        assertEquals(0,begins)
        assertEquals(listOf("box-home","@play:mochi","@unpack:box-home"),tapped)
    }
    @Test fun firstStationaryTargetSurvivesMissingTargetOnMovingFrame() {
        val s=Session();s.native.start()
        s.native.frame("chair",delta())
        s.native.frame(null,delta(x=.025f))
        assertEquals("chair",s.game.selectedId)
        assertEquals(.025f,s.game.item("chair").position.x,.00001f)
    }
    @Test fun twoPressedHandsNeverProduceAStationaryTap() {
        val tapped=mutableListOf<String>()
        val n=RoomNativeGesture({},{},{},{},tapped::add)
        n.start();n.frame("chair",delta())
        n.frame("chair",delta(hands=2))
        n.frame("chair",delta())
        n.finish()
        assertTrue(tapped.isEmpty())
    }
    @Test fun pairScaleWithoutCaptureCannotLeakItsReleaseIntoSingleDrag() {
        val s=Session();val before=s.game.items
        s.native.start()
        s.native.frame("chair",delta())
        repeat(30) { s.native.frame("chair",delta(x=.3f,y=.4f,z=.2f,hands=2)) }
        assertEquals(before,s.game.items)
        s.native.frame("chair",delta(x=1f,y=1f))
        assertEquals(before,s.game.items)
        s.native.frame("chair",delta(y=.02f))
        assertEquals(before.first { it.spec.id=="chair" }.position.y+.02f,
            s.game.item("chair").position.y,.00001f)
    }
    @Test fun finePairTurnsRespondWithoutAnAccumulatedAngleThreshold() {
        val s=Session();s.native.start()
        s.native.frame("chair",delta(y=.12f))
        s.native.frame("chair",delta(hands=2))
        val pivot=s.game.item("chair").position
        repeat(20) { n ->
            s.native.frame(null,delta(x=.5f,y=.5f,z=.5f,yaw=.2f,hands=2))
            assertEquals((n+1)*.2f*RoomNativeGesture.TURN_GAIN,
                s.game.item("chair").rotation.yaw(),.001f)
            assertEquals(pivot,s.game.item("chair").position)
        }
    }
    @Test fun nativeDragAndTrackingCancelNeverFallThroughToTap() {
        val tapped=mutableListOf<String>()
        val n=RoomNativeGesture({},{},{},{},tapped::add)
        n.start();n.frame("chair",delta());n.frame("chair",delta(x=.02f));n.finish()
        n.start();n.frame("mochi",delta(),tapId="@play:mochi");n.cancel();n.finish()
        assertTrue(tapped.isEmpty())
    }
    @Test fun pixelDeltasCompensateDensityFitAndRoomRotation() {
        for(yaw in listOf(0f,35f,90f,180f,-45f)) for(scale in listOf(.35f,.48f,.72f)) {
            val authored=Point3(.05f,.08f,-.03f)
            val view=Rotation.yaw(yaw).rotate(authored)*scale
            val px=Point3(view.x,-view.y,view.z)*(1f/.0004f)
            val actual=RoomNativeGesture.roomDelta(px,.0004f,scale,yaw)
            assertTrue((actual-authored).length()<.000001f)
        }
    }
    @Test fun invalidPixelFramesRemainFinite() {
        assertEquals(Point3(0f,0f,0f),
            RoomNativeGesture.roomDelta(Point3(Float.NaN,1f,1f),.001f,.4f,0f))
        assertEquals(Point3(0f,0f,0f),
            RoomNativeGesture.roomDelta(Point3(1f,1f,1f),.001f,0f,0f))
    }
    @Test fun balconyRunsAlongWallAndAcceptsBothNewEnds() {
        assertEquals(RoomLayout.back,RoomLayout.balconyBack,0f)
        assertEquals(RoomLayout.front,RoomLayout.balconyFront,0f)
        for(z in listOf(RoomLayout.back+.22f,RoomLayout.front-.22f)) {
            val spec=homeCatalogue().first { it.kind==Furnishing.GardenBench }
            val item=RoomItem(spec,Point3(1.28f*RoomLayout.scale,.20f,z),Rotation.yaw(90f))
            val game=initial().copy(items=listOf(item),selectedId=item.spec.id)
            val placement=RoomPhysics.solve(game,preserveYaw=true)
            assertTrue(placement.reason,placement.valid)
            assertEquals(z,placement.item!!.position.z,.00001f)
            assertTrue(RoomPhysics.heldClear(placement.game!!,setOf(item.spec.id)))
        }
    }
}
