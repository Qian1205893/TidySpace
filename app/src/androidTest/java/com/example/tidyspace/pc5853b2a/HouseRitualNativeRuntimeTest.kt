package com.example.tidyspace.pc5853b2a

import android.content.Intent
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.tidyspace.pc5853b2a.domain.model.*
import com.example.tidyspace.pc5853b2a.domain.usecase.MovingHome
import com.example.tidyspace.pc5853b2a.domain.usecase.RoomUseCase
import com.example.tidyspace.pc5853b2a.domain.usecase.RoomTime
import com.example.tidyspace.pc5853b2a.ui.sorting.scene.*
import com.example.tidyspace.pc5853b2a.platform.LaunchActivity
import com.pico.spatial.core.ecs.DirectionalLightComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.PointLightComponent
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HouseRitualNativeRuntimeTest {
    /** Detached native scene; user's persistence is never written by the test. */
    @Test fun actualClockKnobLightsAndNoHomeModel(): Unit=runBlocking {
        val i=InstrumentationRegistry.getInstrumentation()
        i.startActivitySync(Intent(i.targetContext,LaunchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        delay(4500)
        withContext(Dispatchers.Main) {
            val parent=node();val resources=SceneResources();val targets=mutableMapOf<Entity,String>()
            val house=RoomRenovation(targets)
            try {
                house.build(parent,resources)
                listOf("clock","dimmer").forEach { assertTrue(targets.containsValue("@house:$it")) }
                listOf("journal","undo","redo").forEach { assertFalse(targets.containsValue("@house:$it")) }
                assertNull(parent.findEntity("house_journal"))
                assertNull(parent.findEntity("house_journal_cover"))
                fun descendants(e: Entity): List<Entity> = listOf(e)+e.getChildren().flatMap(::descendants)
                val sun=descendants(parent).first {
                    it.components[DirectionalLightComponent::class.java]?.castsShadowEnabled==true
                }.components[DirectionalLightComponent::class.java]!!
                house.synchronize(RoomGame(dayHour=9f))
                assertEquals(520f,sun.intensity,.01f)
                assertTrue(house.fixtures.keys.none { it.startsWith("window_") })
                assertNull(parent.findEntity("window_back_day"))
                house.synchronize(RoomGame(dayHour=21f,mood=RoomMood.Evening,indoorBrightness=0f,journalOpen=true))
                assertEquals(6f,sun.intensity,.01f)
                assertNull(parent.findEntity("window_side_night"))
                val needle=checkNotNull(parent.findEntity("house_clock_needle")).transform.quaternion
                val end=Rotation(needle.x,needle.y,needle.z,needle.w).rotate(Point3(0f,1f,0f))
                assertEquals(-.7071f,end.x,.001f);assertEquals(.7071f,end.y,.001f)
                assertTrue(descendants(parent).mapNotNull { it.components[PointLightComponent::class.java] }.any { it.intensity==0f })
                house.synchronize(RoomGame(dayHour=17.5f,indoorBrightness=.4f))
                assertTrue(sun.intensity in 200f..210f)
                assertNull(parent.findEntity("window_back_sunset"))
                val knob=checkNotNull(parent.findEntity("house_dimmer_knob")).transform.quaternion
                val notch=Rotation(knob.x,knob.y,knob.z,knob.w).rotate(Point3(0f,1f,0f))
                assertEquals(-kotlin.math.sin(Math.toRadians(27.0)).toFloat(),notch.x,.001f)
                house.fixtures.values.forEach {
                    val enabled=it.enabled
                    it.enabled=true
                    assertFalse(it.getVisualBounds(it,true,true).isEmpty())
                    it.enabled=enabled
                }
                Log.i("TidyHouseNative","PASS HOME model and hits absent; actual clock/knob; open windows; continuous native lights")
            } finally { parent.destroy(true);resources.close() }
        }
    }
    @Test fun stableCueSurvivesAttachmentReplacement(): Unit=runBlocking {
        withContext(Dispatchers.Main) {
            val scene=RoomScene()
            val first=node()
            val game=MovingHome.prepare(RoomGame(),fresh=true)
            try {
                scene.attachCue(first,.8f)
                assertFalse("Idle cue must be disabled",first.enabled)
                first.destroy(true)
                scene.positionCue(game)
                val replacement=node()
                scene.attachCue(replacement,.8f)
                scene.positionCue(RoomUseCase().reduce(game,RoomAction.Begin("@house:clock")))
                val anchor=scene.volume.roomRoot.getChildren().first { replacement in it.getChildren() }
                assertEquals(.84f*RoomLayout.scale-.06f,anchor.transform.position.x,.001f)
                assertEquals(.61f,anchor.transform.position.y,.001f)
                assertEquals(RoomTime.clock.z+.025f,anchor.transform.position.z,.001f)
                assertTrue(anchor.transform.position.z>RoomLayout.back)
                assertEquals("Active cue keeps its existing authored width",
                    .95f,replacement.transform.scaleVector.x*.8f,.001f)
                assertTrue(replacement.enabled)
                scene.positionCue(game.copy(celebration=game.celebration+1))
                assertFalse("Completion must disable the replacement too",replacement.enabled)
                Log.i("TidyHouseNative","PASS stable cue anchor survives destroyed attachment and rebinds replacement")
            } finally { scene.destroy() }
        }
    }
}
