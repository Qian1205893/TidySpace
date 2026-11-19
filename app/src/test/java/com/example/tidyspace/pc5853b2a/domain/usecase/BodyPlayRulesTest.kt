package com.example.tidyspace.pc5853b2a.domain.usecase

import com.example.tidyspace.pc5853b2a.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class BodyPlayRulesTest {
    private val rules=RoomUseCase()
    private fun game(id: String)=RoomGame(items=RoomGame().items.map { it.copy(packed=true,support=null) }+
        RoomItem(toyCatalogue().first { it.id==id },Point3(0f,.2f,.2f),packed=false))
    @Test fun bodyStationaryHoldCapturesImmediatelyAndSoftPairOnlyCompresses() {
        var s=game("jelly")
        val original=s.items
        val n=RoomNativeGesture({ s=rules.reduce(s,RoomAction.Begin(it)) },
            { s=rules.reduce(s,RoomAction.Transform(it)) },{ s=rules.reduce(s,RoomAction.End) },
            { s=rules.reduce(s,RoomAction.Cancel) },captureImmediately={ true },allowCompression={ true })
        n.start();n.frame("jelly",GrabDelta(Point3(0f,0f,0f),Rotation()))
        assertEquals("jelly",s.selectedId)
        n.frame(null,GrabDelta(Point3(1f,1f,1f),Rotation(),spanDelta=-.1f,pointerCount=2))
        n.frame(null,GrabDelta(Point3(1f,1f,1f),Rotation(),spanDelta=-.1f,pointerCount=2))
        assertEquals(original.map { it.position },s.items.map { it.position })
        assertTrue(s.toyGesture.compression>0f)
        n.finish()
        assertEquals(original,s.items)
        assertEquals(1L,s.playCounts["jelly"])
    }
    @Test fun allThreeLongBodyDragsMoveAndDoNotAwardStationaryPlay() {
        for(id in listOf("jelly","mochi","tumbler")) {
            var s=rules.reduce(game(id),RoomAction.Begin(id,Rotation.yaw(43f)))
            val original=s.item(id).position
            repeat(40) { s=rules.reduce(s,RoomAction.Transform(GrabDelta(Point3(.003f,.002f,0f),Rotation()))) }
            assertTrue((s.item(id).position-original).length()>.1f)
            assertEquals(43f,s.item(id).rotation.yaw(),.001f)
            s=rules.reduce(s,RoomAction.End)
            assertNull(s.selectedId)
            assertNotEquals(original,s.item(id).position)
            assertNull(s.playCounts[id])
        }
    }
    @Test fun tumblerSmallPushRocksThenRestoresPoseButLongDragPlaces() {
        val original=game("tumbler")
        var s=rules.reduce(original,RoomAction.Begin("tumbler"))
        s=rules.reduce(s,RoomAction.Transform(GrabDelta(Point3(.012f,0f,0f),Rotation())))
        assertTrue(s.toyGesture.tilt>0f)
        s=rules.reduce(s,RoomAction.End)
        assertEquals(original.items,s.items);assertEquals(1L,s.playCounts["tumbler"])
    }
    @Test fun extractionShortReleaseStillCancelsInsteadOfAwardingPlay() {
        val initial=MovingHome.prepare(RoomGame())
        var s=rules.reduce(initial,RoomAction.Add("mochi"))
        s=rules.reduce(s,RoomAction.Begin("@unpack:box-home"))
        s=rules.reduce(s,RoomAction.End)
        assertTrue(s.item("mochi").packed)
        assertNull(s.playCounts["mochi"])
    }
    @Test fun intentionalFinePairTurnIsNotMistakenForStationarySoftPlay() {
        var s=game("mochi")
        s=s.copy(items=s.items.map { if(it.spec.id=="mochi")
            it.copy(position=Point3(0f,-.32f+it.spec.size.y/2,.2f)) else it })
        s=rules.reduce(s,RoomAction.Begin("mochi"))
        val p=s.item("mochi").position
        s=rules.reduce(s,RoomAction.Transform(GrabDelta(Point3(.5f,.5f,.5f),
            Rotation.yaw(.25f),pointerCount=2)))
        assertEquals(p,s.item("mochi").position)
        s=rules.reduce(s,RoomAction.End)
        assertEquals(.25f,s.item("mochi").rotation.yaw(),.001f)
        assertNull(s.playCounts["mochi"])
    }
    @Test fun threeCentimetreBodyPlacementDoesNotSpringBackAsPlay() {
        for(id in listOf("jelly","mochi","tumbler")) {
            var s=game(id)
            val origin=s.item(id).position
            s=rules.reduce(s,RoomAction.Begin(id))
            s=rules.reduce(s,RoomAction.Transform(GrabDelta(Point3(.03f,0f,0f),Rotation())))
            s=rules.reduce(s,RoomAction.End)
            assertEquals(origin.x+.03f,s.item(id).position.x,.0001f)
            assertNull(s.playCounts[id])
        }
    }
}
