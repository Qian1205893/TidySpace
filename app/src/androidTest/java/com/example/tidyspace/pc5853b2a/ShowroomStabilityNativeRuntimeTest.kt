package com.example.tidyspace.pc5853b2a

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.inspector.WindowInspector
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.tidyspace.pc5853b2a.data.repository.LocalHomeRepository
import com.example.tidyspace.pc5853b2a.platform.LaunchActivity
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ShowroomStabilityNativeRuntimeTest {
    @get:Rule val compose=createEmptyComposeRule()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private val trace=StringBuilder()
    private fun waitFor(tag: String) {
        compose.waitUntil(180_000) {
            compose.mainClock.advanceTimeByFrame()
            try { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
            catch(e: IllegalStateException) {
                if(e.message?.startsWith("No compose hierarchies")==true) false else throw e
            }
        }
    }
    private fun click(tag: String) {
        compose.waitUntil(20_000) {
            compose.mainClock.advanceTimeByFrame()
            try { compose.onNodeWithTag(tag).performClick();true }
            catch(e: IllegalStateException) {
                if(e.message?.startsWith("No compose hierarchies")==true) {
                    trace.append("lifecycle-wait before $tag\n")
                    false
                } else throw e
            }
        }
        compose.mainClock.advanceTimeBy(160)
    }
    private fun attached(check: ()->Unit) {
        compose.waitUntil(20_000) {
            compose.mainClock.advanceTimeByFrame()
            try { check();true }
            catch(e: IllegalStateException) {
                if(e.message?.startsWith("No compose hierarchies")==true) {
                    trace.append("lifecycle-wait during assertion\n")
                    false
                } else throw e
            }
        }
    }
    private fun panel(stage: String): Int {
        waitFor("room-ready")
        var identity=0
        instrumentation.runOnMainSync {
            val windows=WindowInspector.getGlobalWindowViews().filter {
                it.width>500 && kotlin.math.abs(it.height.toFloat()/it.width-800f/1104f)<.005f
            }
            trace.append("$stage windows=${windows.size}")
            windows.forEach { trace.append(" id=${System.identityHashCode(it)} size=${it.width}x${it.height}") }
            trace.append('\n')
            assertEquals("$stage must have one companion window",1,windows.size)
            val view=windows.single()
            identity=System.identityHashCode(view)
            assertTrue("$stage must remain attached",view.isAttachedToWindow)
            val image=Bitmap.createBitmap(64,48,Bitmap.Config.ARGB_8888)
            try {
                val canvas=Canvas(image)
                canvas.scale(64f/view.width,48f/view.height)
                view.draw(canvas)
                val pixel=image.getPixel(32,2)
                assertTrue("$stage cannot flash a transparent/black shell",
                    Color.alpha(pixel)>240 && Color.red(pixel)>220 && Color.green(pixel)>200)
            } finally { image.recycle() }
        }
        return identity
    }
    @Test fun repeatedPageChangesKeepOneWindowAndLatestSave() {
        compose.mainClock.autoAdvance=false
        instrumentation.runOnMainSync {
            context.startActivity(Intent(context,LaunchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        waitFor("room-ready")
        val prefs=File(context.applicationInfo.dataDir,"shared_prefs/tidy-room.xml")
        val before=prefs.readBytes()
        val active=checkNotNull(LocalHomeRepository(context).load()).activeId
        try {
            repeat(30) { cycle ->
                click(if(cycle%2==0) "room-homes" else "room-showrooms")
                waitFor(if(cycle%2==0) "home-library-panel" else "showroom-gallery")
                val window=panel("$cycle/open")
                fun same(stage: String) {
                    assertEquals("$stage cannot replace its surface while changing pages",window,panel("$cycle/$stage"))
                }
                click("collection-homes");same("homes")
                click("home-manage-$active");same("manage")
                click("home-close");same("back")
                click("home-new");same("new")
                click("home-from-template");same("templates")
                click("showroom-bakery");same("detail")
                click("showroom-back");same("gallery")
                // Close from different retained pages to exercise warm reopening.
                if(cycle%2==0) {
                    click("showroom-bakery");same("detail-close")
                    click("showroom-close")
                } else {
                    click("collection-homes");same("homes-close")
                    click("home-close")
                }
                attached {
                    compose.onAllNodesWithTag("showroom-panel").assertCountEquals(0)
                    compose.onAllNodesWithTag("home-library-panel").assertCountEquals(0)
                }
            }
            assertArrayEquals("Browsing stress must preserve the latest user save",before,prefs.readBytes())
        } finally {
            File(context.filesDir,"showroom-stability.txt").writeText(trace.toString())
        }
    }
}
