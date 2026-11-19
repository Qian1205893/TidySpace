package com.example.tidyspace.pc5853b2a.domain.usecase

import com.example.tidyspace.pc5853b2a.domain.model.*
import com.example.tidyspace.pc5853b2a.data.repository.RoomPersistence
import com.example.tidyspace.pc5853b2a.ui.sorting.RoomViewModel
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class RoomRitualsTest {
    private val rules=RoomUseCase()
    private fun point(hour: Float)=RoomTime.clock+Point3(sin(hour*PI.toFloat()/12)*.09f,cos(hour*PI.toFloat()/12)*.09f,.02f)
    private fun move(s: RoomGame,from: Float,to: Float)=rules.reduce(s,
        RoomAction.Transform(GrabDelta(point(to)-point(from),Rotation())))
    @Test fun dialWrapsThroughMidnightContinuouslyInBothDirections() {
        var s=rules.reduce(RoomGame(dayHour=23.5f,mood=RoomMood.Evening),
            RoomAction.Begin("@house:clock",contact=point(23.5f)))
        s=move(s,23.5f,.5f)
        assertEquals(.5f,s.dayHour,.001f)
        s=move(s,.5f,23.8f)
        assertEquals(23.8f,s.dayHour,.001f)
        assertEquals(RoomMood.Evening,s.mood)
    }
    @Test fun aFullDialCircuitReturnsToTheSameTimeWithoutDrift() {
        var s=rules.reduce(RoomGame(),RoomAction.Begin("@house:clock",contact=point(9f)))
        for(i in 0..23) s=move(s,9f+i,10f+i)
        assertEquals(9f,s.dayHour,.001f)
        assertEquals(RoomGame().items,s.items)
    }
    @Test fun tappingAnHourSelectsTimeAndTheDialNeverBecomesFurniture() {
        val initial=RoomGame()
        val done=rules.reduce(rules.reduce(initial,RoomAction.Begin("@house:clock",contact=point(17f))),RoomAction.End)
        assertEquals(17f,done.dayHour,.001f)
        assertEquals(RoomMood.Sunset,done.mood)
        assertEquals(initial.items,done.items)
        assertNull(done.selectedId)
    }
    @Test fun losingClockOrDimmerRestoresTheSettledLighting() {
        val start=RoomGame(dayHour=10.25f,indoorBrightness=.4f,lampOn=false)
        var s=rules.reduce(start,RoomAction.Begin("@house:clock",contact=point(10.25f)))
        s=move(s,10.25f,14f)
        val restored=RoomPersistence.decode(RoomPersistence.encode(s))!!
        assertEquals(start.dayHour,restored.dayHour,0f)
        assertEquals(start.items,restored.items)
        s=rules.reduce(s,RoomAction.Cancel)
        assertEquals(start.dayHour,s.dayHour,0f)
        s=rules.reduce(s,RoomAction.Begin("@house:dimmer"))
        s=rules.reduce(s,RoomAction.Transform(GrabDelta(Point3(0f,.1f,0f),Rotation())))
        assertTrue(s.indoorBrightness>start.indoorBrightness)
        assertEquals(start.indoorBrightness,RoomPersistence.decode(RoomPersistence.encode(s))!!.indoorBrightness,0f)
        s=rules.reduce(s,RoomAction.Cancel)
        assertEquals(start.indoorBrightness,s.indoorBrightness,0f)
        assertFalse(s.lampOn)
    }
    @Test fun knobTwistClampsAndTapTogglesWithoutRequiringAPanel() {
        var s=rules.reduce(RoomGame(),RoomAction.Begin("@house:dimmer"))
        s=rules.reduce(s,RoomAction.Transform(GrabDelta(Point3(0f,0f,0f),Rotation.axis(Point3(0f,0f,1f),-170f))))
        s=rules.reduce(s,RoomAction.End)
        assertEquals(1f,s.indoorBrightness,0f)
        s=rules.reduce(rules.reduce(s,RoomAction.Begin("@house:dimmer")),RoomAction.End)
        assertEquals(0f,s.indoorBrightness,0f)
        s=rules.reduce(rules.reduce(s,RoomAction.Begin("@house:dimmer")),RoomAction.End)
        assertEquals(.65f,s.indoorBrightness,0f)
        assertTrue(s.lampOn)
    }
    @Test fun clockWorksDuringCleaningAndChangingFixturesCancelsUnfinishedCleaning() {
        val start=RoomGame(cleaning=CleaningState())
        var s=rules.reduce(start,RoomAction.Begin("@clean:Broom"))
        s=rules.reduce(s,RoomAction.Begin("@house:clock",contact=point(17f)))
        assertNull(s.cleaning!!.tool)
        s=rules.reduce(s,RoomAction.End)
        assertEquals(17f,s.dayHour,.001f)
        assertFalse(s.decorating)
        assertEquals(start.cleaning!!.progress,s.cleaning!!.progress)
    }
    @Test fun historySurvivesRoofToggleAndRemovedHomeCannotCapture() {
        var saved: String?=null
        val vm=RoomViewModel(RoomPersistence.encode(RoomGame())) { saved=it }
        fun act(a: RoomAction)=vm.onEvent(a)
        fun tap(id: String,p: Point3?=null) { act(RoomAction.Begin(id,contact=p));act(RoomAction.End) }
        tap("@house:clock",point(17f))
        assertEquals(17f,vm.state.value.game.dayHour,.001f)
        tap("@house:journal")
        assertNull(vm.currentGame.selectedId)
        assertTrue(vm.currentGame.controlsVisible)
        assertFalse(RoomRituals.isTarget("@house:journal"))
        act(RoomAction.ToggleControls)
        assertFalse(vm.state.value.game.controlsVisible)
        tap("@house:undo")
        assertEquals(9f,vm.state.value.game.dayHour,.001f)
        assertFalse(vm.state.value.game.controlsVisible)
        act(RoomAction.ToggleControls);tap("@house:redo")
        assertEquals(17f,vm.state.value.game.dayHour,.001f)
        assertTrue(vm.state.value.game.controlsVisible)
        assertFalse(RoomPersistence.decode(saved)!!.journalOpen)
    }
    @Test fun secondHandOnClockCannotAccidentallyTurnHouse() {
        var game=RoomGame()
        val input=RoomGestureInput(begin={ game=rules.reduce(game,RoomAction.Begin(it)) },
            transform={ game=rules.reduce(game,RoomAction.Transform(it)) },end={ game=rules.reduce(game,RoomAction.End) },
            cancelled={ game=rules.reduce(game,RoomAction.Cancel) },
            beginAt={ id,p -> game=rules.reduce(game,RoomAction.Begin(id,contact=p?.position)) })
        fun sample(id: Long,phase: ContactPhase,h: Float)=RoomContact(id,phase,"@house:clock",HandPose(point(h),Rotation()),point(h))
        input.events(listOf(sample(1,ContactPhase.Down,9f)))
        input.events(listOf(sample(1,ContactPhase.Move,12f)))
        assertEquals(12f,game.dayHour,.001f)
        input.events(listOf(sample(2,ContactPhase.Down,14f)))
        assertEquals("@house:clock",game.selectedId)
        assertEquals(12f,game.dayHour,.001f)
        assertEquals(0f,game.roomYaw,0f)
    }
    @Test fun v7MigrationPreservesExactFurnitureAndLightAnchors() {
        for(mood in RoomMood.entries) {
            val source=MovingHome.prepare(RoomGame(mood=mood),fresh=true).let { game ->
                game.copy(items=game.items.map { it.copy(position=Point3(it.position.x/RoomLayout.scale,it.position.y,it.position.z/RoomLayout.scale)) },
                    boxInventoryVersion=1,layoutVersion=1)
            }
            val lines=RoomPersistence.encode(source).lines()
            val h=lines.first().split('\t').take(17).toMutableList().apply { this[0]="tidy-room-7" }
            val old=(listOf(h.joinToString("\t"))+lines.drop(1)).joinToString("\n")
            val restored=RoomPersistence.decode(old)!!
            assertEquals(source.items,restored.items)
            assertEquals(source.boxStock,restored.boxStock)
            assertEquals(RoomTime.hour(mood),restored.dayHour,0f)
            assertEquals(source.items,RoomPersistence.decode(RoomPersistence.encode(restored))!!.items)
        }
    }
    @Test fun lightCurveIsContinuousAtAnchorsAndAcrossMidnight() {
        assertEquals(520f,RoomTime.lighting(9f).sun,0f)
        assertEquals(270f,RoomTime.lighting(17f).sun,0f)
        assertEquals(6f,RoomTime.lighting(21f).sun,0f)
        for(h in listOf(0f,5f,9f,13f,17f,19f,21f)) {
            assertEquals(RoomTime.lighting(h-.001f).sun,RoomTime.lighting(h+.001f).sun,.3f)
        }
        assertEquals(RoomTime.lighting(0f),RoomTime.lighting(24f))
    }
    @Test fun invalidMotionOrSnapshotCannotPoisonSavedTime() {
        val s=rules.reduce(MovingHome.prepare(RoomGame(),fresh=true),RoomAction.Begin("@house:clock",contact=point(9f)))
        assertEquals(s,rules.reduce(s,RoomAction.Transform(GrabDelta(Point3(Float.NaN,0f,0f),Rotation()))))
        val lines=RoomPersistence.encode(s).lines().toMutableList()
        val h=lines[0].split('\t').toMutableList().apply { this[17]="24.0" }
        lines[0]=h.joinToString("\t")
        assertNull(RoomPersistence.decode(lines.joinToString("\n")))
    }
}
