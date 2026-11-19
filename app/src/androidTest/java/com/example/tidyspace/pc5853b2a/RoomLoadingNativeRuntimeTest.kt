package com.example.tidyspace.pc5853b2a

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.inspector.WindowInspector
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.tidyspace.pc5853b2a.data.repository.LocalHomeRepository
import com.example.tidyspace.pc5853b2a.domain.model.RoomLoadPhase
import com.example.tidyspace.pc5853b2a.platform.LaunchActivity
import com.example.tidyspace.pc5853b2a.ui.sorting.scene.RoomScene
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class RoomLoadingNativeRuntimeTest {
    @get:Rule val compose=createEmptyComposeRule()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private fun waitFor(tag: String) {
        compose.waitUntil(180_000) {
            compose.mainClock.advanceTimeByFrame()
            try { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
            catch(e: IllegalStateException) {
                if(e.message?.startsWith("No compose hierarchies")==true) false else throw e
            }
        }
    }
    private fun captureLoading() {
        instrumentation.runOnMainSync {
            val view=checkNotNull(WindowInspector.getGlobalWindowViews().firstOrNull {
                it.width>500 && kotlin.math.abs(it.height.toFloat()/it.width-380f/520f)<.01f
            })
            val image=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(image))
                File(context.filesDir,"startup-loading.png").outputStream().use {
                    image.compress(Bitmap.CompressFormat.PNG,100,it)
                }
            } finally { image.recycle() }
        }
    }
    @Test fun coldStartShowsAnimationThenAllCardsAndPublishesOnlyPreparedModels() {
        compose.mainClock.autoAdvance=false
        val preferences=File(context.applicationInfo.dataDir,"shared_prefs/tidy-room.xml")
        val before=preferences.readBytes()
        instrumentation.runOnMainSync {
            context.startActivity(Intent(context,LaunchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        waitFor("room-loading-overlay")
        compose.onNodeWithTag("room-load-animation").assertExists()
        compose.onAllNodesWithTag("room-ready").assertCountEquals(0)
        captureLoading()
        waitFor("room-ready")
        compose.onAllNodesWithTag("room-loading-overlay").assertCountEquals(0)
        compose.onNodeWithTag("room-showrooms").performClick()
        waitFor("showroom-gallery")
        val rooms=com.example.tidyspace.pc5853b2a.data.repository.LocalShowroomRepository(context).rooms()
        val bounds=compose.onNodeWithTag("showroom-panel").fetchSemanticsNode().boundsInRoot
        rooms.forEach { room ->
            val card=compose.onNodeWithTag("showroom-${room.id}").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue("${room.id} must be fully inside the panel",bounds.contains(card.topLeft) && bounds.contains(card.bottomRight))
        }
        // Let the external runner capture the system composite, including the
        // native pressed layer. These PNGs are distinct from View.draw evidence.
        fun checkpoint(name: String) {
            val marker=File(context.filesDir,"ui-composite-checkpoint")
            val ack=File(context.filesDir,"ui-composite-ack")
            marker.writeText(name)
            compose.waitUntil(30_000) {
                compose.mainClock.advanceTimeByFrame()
                ack.exists() && ack.readText()==name
            }
        }
        if(InstrumentationRegistry.getArguments().getString("compositeCapture")=="true") {
            checkpoint("gallery")
            compose.onNodeWithTag("showroom-minimal").performTouchInput { down(center) }
            compose.mainClock.advanceTimeBy(400)
            checkpoint("gallery-pressed")
            compose.onNodeWithTag("showroom-minimal").performTouchInput { cancel() }
        }
        compose.onNodeWithTag("showroom-close").performClick()
        val game=checkNotNull(LocalHomeRepository(context).load()).active.game
        runBlocking {
            withContext(Dispatchers.Main) {
                val scene=RoomScene()
                val phases=mutableListOf<RoomLoadPhase>()
                try {
                    assertFalse(scene.presented)
                    assertTrue("Detached preparation retains local model visibility",scene.volume.turntable.enabled)
                    assertTrue(runCatching { scene.present() }.isFailure)
                    scene.build(game) { phase ->
                        phases+=phase
                        assertFalse("No partial room during $phase",scene.presented)
                    }
                    assertEquals(RoomLoadPhase.entries.toList(),phases)
                    assertFalse(scene.presented)
                    val hidden=scene.volume.measuredBounds(scene.volume.turntable,scene.volume.turntable)
                    assertFalse("Hidden models must retain their real fitting bounds",hidden.isEmpty())
                    scene.present()
                    assertTrue(scene.presented)
                    val shown=scene.volume.measuredBounds(scene.volume.turntable,scene.volume.turntable)
                    assertFalse(shown.isEmpty())
                    assertEquals(shown.min.x,hidden.min.x,.001f)
                    assertEquals(shown.min.y,hidden.min.y,.001f)
                    assertEquals(shown.min.z,hidden.min.z,.001f)
                    assertEquals(shown.max.x,hidden.max.x,.001f)
                    assertEquals(shown.max.y,hidden.max.y,.001f)
                    assertEquals(shown.max.z,hidden.max.z,.001f)
                    scene.conceal()
                    assertFalse(scene.presented)
                } finally { scene.destroy() }
                val failed=RoomScene()
                try {
                    val result=runCatching {
                        failed.build(game) { if(it==RoomLoadPhase.Furnishing) error("injected model failure") }
                    }
                    assertTrue(result.isFailure)
                    assertFalse("Failed load must not expose the already prepared building",failed.presented)
                    assertTrue(runCatching { failed.present() }.isFailure)
                } finally { failed.destroy() }
            }
        }
        assertArrayEquals("Loading and browsing cannot alter latest saved rooms",before,preferences.readBytes())
    }
}
