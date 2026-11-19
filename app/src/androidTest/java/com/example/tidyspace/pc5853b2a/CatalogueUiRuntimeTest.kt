package com.example.tidyspace.pc5853b2a

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.inspector.WindowInspector
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.semantics.getOrNull
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.tidyspace.pc5853b2a.platform.LaunchActivity
import com.example.tidyspace.pc5853b2a.data.repository.CleaningExpansionPersistence
import com.example.tidyspace.pc5853b2a.domain.usecase.RoomCardDrop
import com.example.tidyspace.pc5853b2a.ui.sorting.components.CatalogueCategory
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import android.os.ParcelFileDescriptor

/** Uses the actual app's Compose hierarchy. Only selection and pointer cancel, never a saved fixture. */
@RunWith(AndroidJUnit4::class)
class CatalogueUiRuntimeTest {
    @get:Rule val compose=createEmptyComposeRule()
    @Test fun actualCategoryCardsRenderAndTouchCancelLeavesSaveUnchanged() {
        // The real scene has a continuous animation clock. Advance it explicitly,
        // otherwise test autoAdvance attempts to exhaust an infinite frame loop.
        compose.mainClock.autoAdvance=false
        val i=InstrumentationRegistry.getInstrumentation()
        i.runOnMainSync { i.targetContext.startActivity(Intent(i.targetContext,LaunchActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        compose.waitUntil(180_000) {
            compose.mainClock.advanceTimeByFrame()
            try { compose.onAllNodesWithTag("room-ready").fetchSemanticsNodes().isNotEmpty() }
            catch(e: IllegalStateException) {
                if(e.message?.startsWith("No compose hierarchies")==true) false else throw e
            }
        }
        val prefs=File(i.targetContext.applicationInfo.dataDir,"shared_prefs/tidy-room.xml")
        val before=prefs.readBytes()
        val trayBounds=compose.onNodeWithTag("room-ready").fetchSemanticsNode().boundsInRoot
        val panelBounds=compose.onNodeWithTag("room-front-ui").fetchSemanticsNode().boundsInRoot
        assertTrue("Catalogue has its own bounded native texture",panelBounds.width>500f)
        assertEquals(302f/1440f,panelBounds.height/panelBounds.width,.002f)
        assertTrue(panelBounds.contains(trayBounds.center))
        val nativePlanes=i.uiAutomation.executeShellCommand(
            "logcat -d --pid=${android.os.Process.myPid()} -s TidyUiPlane:I")
        val planes=ParcelFileDescriptor.AutoCloseInputStream(nativePlanes).bufferedReader().use { it.readText() }
        val vector="Vector3\\(x=([-0-9.Ee]+), y=([-0-9.Ee]+), z=([-0-9.Ee]+)\\)"
        val native=checkNotNull(Regex("ready center=$vector half=$vector").findAll(planes).lastOrNull()) {
            "Actual UI native-plane coordinates were not published: $planes"
        }.groupValues
        val planeX=native[1].toFloat()
        val planeY=native[2].toFloat()
        val planeZ=native[3].toFloat()
        val halfX=native[4].toFloat()
        val halfY=native[5].toFloat()
        val halfZ=native[6].toFloat()
        val height=1.65f*halfX*302f/1440f
        assertEquals("The catalogue stays centered above the roof",0f,planeX,.002f)
        assertTrue("The panel clears the roof without exceeding the top boundary",
            planeY<=halfY-.035f-height/2+.002f)
        val roofGap=checkNotNull(Regex("roofGap=([-0-9.Ee]+)").findAll(planes).lastOrNull()).groupValues[1].toFloat()
        assertTrue("Native roof cannot overlap the panel: $roofGap",roofGap>.12f)
        assertTrue("The whole native texture stays inside the window",
            planeY-height/2>-halfY && planeY+height/2<halfY && kotlin.math.abs(planeZ)<halfZ)
        assertEquals("Use the central depth actually visible in the compositor",0f,planeZ,.002f)
        val saved=i.targetContext.getSharedPreferences("tidy-room",0)
        val game=checkNotNull(CleaningExpansionPersistence.restore(saved.getString("snapshot",null),
            saved.getString(CleaningExpansionPersistence.KEY,null)))
        fun capture(name: String) {
            // PICO's compositor-backed captureToImage may return a fully blank
            // bitmap. Draw the actual public Android window root instead.
            i.runOnMainSync {
                val view=checkNotNull(WindowInspector.getGlobalWindowViews().firstOrNull {
                    it.width>500 && kotlin.math.abs(it.height.toFloat()/it.width-302f/1440f)<.003f
                }) { "The actual catalogue window root was not found" }
                val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
                try {
                    view.draw(Canvas(bitmap))
                    val colors=mutableSetOf<Int>()
                    for(y in 0 until bitmap.height step 12) for(x in 0 until bitmap.width step 12)
                        colors+=bitmap.getPixel(x,y)
                    assertTrue("A blank texture is not UI evidence",colors.size>20)
                    File(i.targetContext.filesDir,name).outputStream().use {
                        bitmap.compress(Bitmap.CompressFormat.PNG,100,it)
                    }
                    Log.i("TidyCardUi","PANEL_TEXTURE $name ${bitmap.width}x${bitmap.height} colors=${colors.size} public-view-draw=true")
                } finally { bitmap.recycle() }
            }
        }
        capture("catalogue-categories.png")
        if(!game.decorating) {
            compose.onNodeWithText("打扫好，再慢慢安家").assertExists()
            assertArrayEquals(before,prefs.readBytes())
            Log.i("TidyCardUi","PASS actual cleaning lock UI, catalogue stays unavailable, save unchanged")
            return
        }
        val category=CatalogueCategory.entries.first { category ->
            RoomCardDrop.inventory(game).any { category.accepts(it.spec) } }
        compose.onNodeWithTag("category-${category.name}").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        val cards=compose.onAllNodes(SemanticsMatcher("Inventory item cards") {
            it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag)?.startsWith("card-")==true
        })
        assertTrue("The selected category must contain a concrete item card",cards.fetchSemanticsNodes().isNotEmpty())
        compose.waitUntil(30_000) {
            compose.mainClock.advanceTimeByFrame()
            cards[0].fetchSemanticsNode().config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Disabled)==null
        }
        cards[0].performClick()
        compose.mainClock.advanceTimeByFrame()
        capture("catalogue-items.png")
        cards[0].performTouchInput {
            down(center)
            moveBy(Offset(0f,-45f),delayMillis=50)
            moveBy(Offset(0f,-500f),delayMillis=50)
            cancel()
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        // Retry immediately, then return into the card tray and release.
        // This drives the real end callback without changing the player's layout.
        cards[0].performTouchInput {
            down(center)
            moveBy(Offset(0f,-45f),delayMillis=50)
            moveBy(Offset(0f,-45f),delayMillis=50)
            moveBy(Offset(0f,90f),delayMillis=50)
            up()
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        assertArrayEquals("Selection and pointer cancellation cannot change user saves",before,prefs.readBytes())
        compose.waitUntil(5_000) {
            compose.mainClock.advanceTimeByFrame()
            try { compose.onAllNodesWithContentDescription("返回分类").fetchSemanticsNodes().isNotEmpty() }
            catch(e: IllegalStateException) {
                if(e.message?.startsWith("No compose hierarchies")==true) false else throw e
            }
        }
        compose.onNodeWithContentDescription("返回分类")
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.waitUntil(5_000) {
            compose.mainClock.advanceTimeByFrame()
            compose.onAllNodesWithTag("category-${category.name}").fetchSemanticsNodes().isNotEmpty()
        }
        val events=i.uiAutomation.executeShellCommand(
            "logcat -d --pid=${android.os.Process.myPid()} -s TidyCard:I")
        val gestureLog=ParcelFileDescriptor.AutoCloseInputStream(events).bufferedReader().use { it.readText() }
        assertTrue("A real card gesture callback must start; synthetic no-op is not a pass",
            gestureLog.contains("start id="))
        assertTrue("Selecting from the actual UI must show a real mesh",
            gestureLog.contains("model-visible id="))
        assertTrue("Pointer cancellation must reach the actual card callback",
            gestureLog.contains("cancel token="))
        assertTrue("Returning into the tray must exercise the real release callback",
            gestureLog.contains("release token="))
        assertArrayEquals(before,prefs.readBytes())
        Log.i("TidyCardUi","PASS actual category→select item→drag into room→cancel→retry→tray release→return; save unchanged")
    }
}
