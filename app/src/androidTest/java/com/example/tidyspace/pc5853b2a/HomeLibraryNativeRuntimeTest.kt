package com.example.tidyspace.pc5853b2a

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.inspector.WindowInspector
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.tidyspace.pc5853b2a.data.repository.*
import com.example.tidyspace.pc5853b2a.domain.model.*
import com.example.tidyspace.pc5853b2a.domain.usecase.*
import com.example.tidyspace.pc5853b2a.platform.LaunchActivity
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class HomeLibraryNativeRuntimeTest {
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
    private fun click(tag: String) {
        waitFor(tag);compose.onNodeWithTag(tag).performClick();compose.mainClock.advanceTimeBy(400)
    }
    private fun attached(check: ()->Unit) {
        compose.waitUntil(20_000) {
            compose.mainClock.advanceTimeByFrame()
            try { check();true }
            catch(e: IllegalStateException) {
                if(e.message?.startsWith("No compose hierarchies")==true) false else throw e
            }
        }
    }
    private fun assertItems(expected: List<RoomItem>,actual: List<RoomItem>) {
        assertEquals(expected.map { it.spec.id },actual.map { it.spec.id })
        expected.zip(actual).forEach { (a,b) ->
            val label=a.spec.id
            assertEquals(label,a.spec.assetId,b.spec.assetId)
            assertEquals(label,a.packed,b.packed)
            assertEquals(label,a.support,b.support)
            assertEquals(label,a.position.x,b.position.x,.000001f)
            assertEquals(label,a.position.y,b.position.y,.000001f)
            assertEquals(label,a.position.z,b.position.z,.000001f)
            assertEquals(label,a.rotation.x,b.rotation.x,.000001f)
            assertEquals(label,a.rotation.y,b.rotation.y,.000001f)
            assertEquals(label,a.rotation.z,b.rotation.z,.000001f)
            assertEquals(label,a.rotation.w,b.rotation.w,.000001f)
        }
    }
    private fun capture(name: String,aspect: Float=800f/1104f) {
        instrumentation.runOnMainSync {
            val view=checkNotNull(WindowInspector.getGlobalWindowViews().firstOrNull {
                it.width>500 && kotlin.math.abs(it.height.toFloat()/it.width-aspect)<.01f
            }) { "Actual companion window missing" }
            val scale=(2048f/view.width).coerceAtMost(1f)
            val bitmap=Bitmap.createBitmap((view.width*scale).toInt(),(view.height*scale).toInt(),Bitmap.Config.ARGB_8888)
            try {
                val canvas=Canvas(bitmap);canvas.scale(scale,scale);view.draw(canvas)
                File(context.filesDir,name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            } finally { bitmap.recycle() }
        }
    }
    @Test fun realPeerEntriesAndLibraryManageTemplateBrowsePreserveUserSave() {
        compose.mainClock.autoAdvance=false
        instrumentation.runOnMainSync {
            context.startActivity(Intent(context,LaunchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        waitFor("room-ready")
        val prefs=File(context.applicationInfo.dataDir,"shared_prefs/tidy-room.xml")
        val before=prefs.readBytes()
        compose.onNodeWithTag("room-homes").assertHasClickAction().assert(hasText("我的小屋"))
        compose.onNodeWithTag("room-showrooms").assertHasClickAction().assert(hasText("样板间"))
        capture("multi-home-entry.png",302f/1440f)
        click("room-homes");waitFor("home-library-panel")
        capture("multi-home-library.png")
        val library=checkNotNull(LocalHomeRepository(context).load())
        compose.onNodeWithTag("home-card-${library.activeId}").assertIsDisplayed()
        click("home-manage-${library.activeId}")
        compose.onNodeWithTag("home-delete").assertIsNotEnabled()
        capture("multi-home-manage.png")
        click("home-close");click("home-new")
        compose.onNodeWithTag("home-new-name").assertExists()
        attached {
            val window=compose.onNodeWithTag("home-library-panel").fetchSemanticsNode().boundsInRoot
            val body=compose.onNodeWithTag("home-new-body").fetchSemanticsNode().boundsInRoot
            val form=compose.onNodeWithTag("home-new-form").fetchSemanticsNode().boundsInRoot
            assertEquals("New-home form must be horizontally centered in its actual window",
                window.center.x,form.center.x,1f)
            assertEquals("New-home form must be vertically centered in the content area",
                body.center.y,form.center.y,1f)
            listOf("home-new-preview","home-new-name","home-create-blank","home-from-template").forEach { tag ->
                val bounds=compose.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                assertEquals("$tag must share the window center line",window.center.x,bounds.center.x,1f)
            }
            File(context.filesDir,"new-home-centering.txt").writeText(
                "windowCenterX=${window.center.x}\nformCenterX=${form.center.x}\n"+
                "bodyCenterY=${body.center.y}\nformCenterY=${form.center.y}\n"+
                "windowOffsetX=0\nwindowOffsetY=0\n")
        }
        capture("multi-home-new.png")
        click("home-from-template");waitFor("showroom-gallery")
        LocalShowroomRepository(context).rooms().forEach {
            attached { compose.onNodeWithTag("showroom-${it.id}").assertIsDisplayed().assertHasClickAction() }
        }
        capture("multi-home-templates.png")
        click("showroom-bakery")
        compose.onNodeWithTag("showroom-create-home").assert(hasText("以此新建小屋"))
        capture("multi-home-template-detail.png")
        click("showroom-back");click("showroom-close")
        assertArrayEquals("Read-only personal/template browsing cannot rewrite the latest save",before,prefs.readBytes())
    }
    @Test fun actualAndroidStoreRoundTripsAllTemplatesAndIndependentSwitches() {
        val name="multi-home-native-${System.nanoTime()}"
        val user=File(context.applicationInfo.dataDir,"shared_prefs/tidy-room.xml").readBytes()
        try {
            val original=checkNotNull(LocalHomeRepository(context).load()).active
            // The simulator may still be in its first cleaning lesson. Finish
            // only this isolated store's seed to exercise template creation;
            // never unlock or replace the player's actual cleaning state.
            val source=original.copy(game=original.game.copy(cleaning=CleaningState().completed()))
            val store=LocalHomeRepository(context,name)
            val rules=HomeUseCase(store)
            rules.initialize(source.game,source.session)
            val first=rules.library.activeId
            for(template in LocalShowroomRepository(context).rooms()) {
                rules.create("我的${template.title}",template,rules.activeGame(),rules.library.active.session)
                val roundTrip=checkNotNull(store.load())
                assertEquals(template.id,roundTrip.active.sourceTemplateId)
                assertEquals(template.version,roundTrip.active.sourceTemplateVersion)
                assertItems(rules.library.active.game.items,roundTrip.active.game.items)
                val sourceGame=roundTrip.homes.first().game
                assertItems(source.game.items,sourceGame.items)
                assertEquals(source.game.boxStock,sourceGame.boxStock)
            }
            assertEquals(13,rules.library.homes.size)
            val copy=rules.duplicate(first,rules.activeGame(),rules.library.active.session)
            val copiedId=copy.activeId
            rules.save(rules.activeGame().copy(indoorBrightness=.19f,roomYaw=97f),copy.active.session)
            rules.switch(first,rules.activeGame(),copy.active.session)
            assertEquals(source.game.roomYaw,rules.activeGame().roomYaw,0f)
            assertItems(source.game.items,rules.activeGame().items)
            rules.rename(copiedId,"另外一间",rules.activeGame(),rules.library.active.session)
            rules.delete(copiedId,rules.activeGame(),rules.library.active.session)
            rules.undoDelete(rules.activeGame(),rules.library.active.session)
            assertEquals(.19f,rules.library.homes.last().game.indoorBrightness,0f)
            assertEquals(14,checkNotNull(store.load()).homes.size)
            val encoded=HomeLibraryCodec.encode(rules.library)
            assertTrue(runCatching { HomeLibraryCodec.decode(encoded.replace("\"version\":1","\"version\":99")) }.isFailure)
            assertArrayEquals(user,File(context.applicationInfo.dataDir,"shared_prefs/tidy-room.xml").readBytes())
        } finally { context.deleteSharedPreferences(name) }
    }
}
