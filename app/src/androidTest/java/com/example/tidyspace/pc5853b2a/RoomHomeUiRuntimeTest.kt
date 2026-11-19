package com.example.tidyspace.pc5853b2a

import android.content.Intent
import android.util.Log
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Rule
import org.junit.Test

/** Real main-window controls; native panel visibility is covered by CardDropNativeRuntimeTest. */
class RoomHomeUiRuntimeTest {
    @get:Rule val ui=createEmptyComposeRule()
    @Test fun homeIsAbsentAndRoofActionsStillToggleActualCatalogue() {
        val i=InstrumentationRegistry.getInstrumentation()
        val context=i.targetContext
        ui.mainClock.autoAdvance=false
        i.runOnMainSync { context.startActivity(
            Intent(context,com.example.tidyspace.pc5853b2a.platform.LaunchActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        ui.waitUntil(180_000) {
            ui.mainClock.advanceTimeByFrame()
            try { ui.onAllNodesWithTag("room-ready").fetchSemanticsNodes().isNotEmpty() }
            catch(e: IllegalStateException) {
                if(e.message?.startsWith("No compose hierarchies")==true) false else throw e
            }
        }
        val prefs=File(context.applicationInfo.dataDir,"shared_prefs/tidy-room.xml")
        val before=prefs.readBytes()
        fun awaitLabel(label: String) {
            ui.waitUntil(10_000) {
                ui.mainClock.advanceTimeByFrame()
                try { ui.onAllNodesWithContentDescription(label).fetchSemanticsNodes().isNotEmpty() }
                catch(e: IllegalStateException) {
                    if(e.message?.startsWith("No compose hierarchies")==true) false else throw e
                }
            }
        }
        awaitLabel("收起物品卡片")
        ui.onNodeWithTag("door-home").assertDoesNotExist()
        ui.onNodeWithText("HOME").assertDoesNotExist()
        repeat(6) {
            ui.onNodeWithContentDescription("收起物品卡片").assertHasClickAction().performClick()
            awaitLabel("展开物品卡片")
            ui.onNodeWithTag("room-ready").assertDoesNotExist()
            ui.onNodeWithContentDescription("展开物品卡片").performClick()
            awaitLabel("收起物品卡片")
            ui.onNodeWithTag("room-ready").assertExists()
        }
        assertArrayEquals(before,prefs.readBytes())
        Log.i("TidyHomeUi","PASS HOME absent; 12 roof-action callbacks hide/restore catalogue; save unchanged")
    }
}
