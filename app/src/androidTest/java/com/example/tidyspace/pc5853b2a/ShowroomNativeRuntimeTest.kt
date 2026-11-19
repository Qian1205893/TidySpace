package com.example.tidyspace.pc5853b2a

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.util.Log
import android.view.inspector.WindowInspector
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.content.FileProvider
import com.example.tidyspace.pc5853b2a.data.repository.*
import com.example.tidyspace.pc5853b2a.domain.model.*
import com.example.tidyspace.pc5853b2a.domain.usecase.*
import com.example.tidyspace.pc5853b2a.platform.LaunchActivity
import com.example.tidyspace.pc5853b2a.ui.sorting.RoomViewModel
import com.example.tidyspace.pc5853b2a.ui.sorting.scene.RoomScene
import com.example.tidyspace.pc5853b2a.ui.sorting.scene.transform
import com.example.tidyspace.pc5853b2a.ui.showrooms.ShowroomPlacement
import com.pico.spatial.ui.foundation.geometry.NormalizedPoint3D
import com.pico.spatial.core.ecs.Entity
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipFile

@RunWith(AndroidJUnit4::class)
class ShowroomNativeRuntimeTest {
    @get:Rule val compose=createEmptyComposeRule()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private fun launch() {
        instrumentation.runOnMainSync {
            instrumentation.targetContext.startActivity(Intent(instrumentation.targetContext,LaunchActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
    /** Headset lid sleep can detach the companion between actions; await its return. */
    private fun clickTag(tag: String) {
        compose.waitUntil(20_000) {
            compose.mainClock.advanceTimeByFrame()
            try { compose.onNodeWithTag(tag).performClick();true }
            catch(e: IllegalStateException) {
                if(e.message?.startsWith("No compose hierarchies")==true) false else throw e
            }
        }
    }
    private fun whileAttached(check: ()->Unit) {
        compose.waitUntil(20_000) {
            compose.mainClock.advanceTimeByFrame()
            try { check();true }
            catch(e: IllegalStateException) {
                if(e.message?.startsWith("No compose hierarchies")==true) false else throw e
            }
        }
    }
    private fun capture(name: String,aspect: Float=900f/1280f) {
        instrumentation.runOnMainSync {
            val view=checkNotNull(WindowInspector.getGlobalWindowViews().firstOrNull {
                it.width>500 && kotlin.math.abs(it.height.toFloat()/it.width-aspect)<.005f
            }) { "Actual reference companion texture missing" }
            val factor=(2048f/view.width).coerceAtMost(1f)
            val bitmap=Bitmap.createBitmap((view.width*factor).toInt(),(view.height*factor).toInt(),Bitmap.Config.ARGB_8888)
            try {
                val canvas=Canvas(bitmap)
                canvas.scale(factor,factor)
                view.draw(canvas)
                val colors=mutableSetOf<Int>()
                for(y in 0 until bitmap.height step 10) for(x in 0 until bitmap.width step 10) colors+=bitmap.getPixel(x,y)
                assertTrue("Reference texture must contain real artwork",colors.size>50)
                File(instrumentation.targetContext.filesDir,name).outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG,100,it)
                }
            } finally { bitmap.recycle() }
        }
    }
    @Test fun actualEntryTwelveReferencesDetailsAndCancelKeepLatestSave() {
        compose.mainClock.autoAdvance=false
        launch()
        compose.waitUntil(180_000) {
            compose.mainClock.advanceTimeByFrame()
            try { compose.onAllNodesWithTag("room-ready").fetchSemanticsNodes().isNotEmpty() }
            catch(e: IllegalStateException) { if(e.message?.startsWith("No compose hierarchies")==true) false else throw e }
        }
        val prefs=File(instrumentation.targetContext.applicationInfo.dataDir,"shared_prefs/tidy-room.xml")
        val before=prefs.readBytes()
        Log.i("TidyShowroomNative","UI_STAGE room-ready")
        capture("showroom-entry-runtime.png",302f/1440f)
        Log.i("TidyShowroomNative","UI_STAGE entry-native")
        compose.onAllNodesWithTag("room-showrooms").assertCountEquals(1)
        compose.onNodeWithTag("room-showrooms")
            .assertContentDescriptionEquals("我的小屋，打开样板间灵感册")
            .assert(hasText("我的小屋")).assertHasClickAction()
        clickTag("room-showrooms")
        compose.waitUntil(20_000) {
            compose.mainClock.advanceTimeByFrame()
            compose.onAllNodesWithTag("showroom-panel").fetchSemanticsNodes().isNotEmpty()
        }
        val bounds=compose.onNodeWithTag("showroom-panel").fetchSemanticsNode().boundsInRoot
        assertEquals(900f/1280f,bounds.height/bounds.width,.003f)
        val anchor=ShowroomPlacement.anchor
        assertTrue("Opening a reference cannot place it outside the volume",listOf(anchor.x,anchor.y,anchor.z).all { it in 0f..1f })
        assertEquals("The reference opens on the user's center line",NormalizedPoint3D.Center.x,anchor.x,.01f)
        assertTrue("The reference is on the caption/user side",anchor.z>NormalizedPoint3D.Center.z)
        compose.mainClock.advanceTimeBy(800)
        capture("showroom-gallery-runtime.png")
        // Exercise real pointer press state rather than the semantics click shortcut.
        compose.onNodeWithTag("showroom-green").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(300)
        capture("showroom-pressed-runtime.png")
        fun pixels(name: String): IntArray {
            val bitmap=checkNotNull(BitmapFactory.decodeFile(File(instrumentation.targetContext.filesDir,name).path))
            return try {
                IntArray(bitmap.width*bitmap.height).also {
                    bitmap.getPixels(it,0,bitmap.width,0,0,bitmap.width,bitmap.height)
                }
            } finally { bitmap.recycle() }
        }
        assertArrayEquals("Pressing a reference cannot draw a gray plate over its photo or background",
            pixels("showroom-gallery-runtime.png"),pixels("showroom-pressed-runtime.png"))
        compose.onNodeWithTag("showroom-green").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithContentDescription("阳光植物家整体全景").assertIsDisplayed()
        clickTag("showroom-back")
        compose.mainClock.advanceTimeBy(400)
        Log.i("TidyShowroomNative","UI_STAGE gallery-native")
        val rooms=LocalShowroomRepository(instrumentation.targetContext).rooms()
        assertEquals(12,rooms.size)
        val galleryBounds=compose.onNodeWithTag("showroom-gallery").fetchSemanticsNode().boundsInRoot
        listOf("showroom-search","showroom-count","showroom-creator","showroom-resume",
            "showroom-style-全部风格","showroom-level-全部难度").forEach {
            compose.onAllNodesWithTag(it).assertCountEquals(0)
        }
        val cards=rooms.map { r ->
            compose.onNodeWithTag("showroom-${r.id}")
                .assertIsDisplayed().assertHasClickAction().assert(hasText(r.title))
                .fetchSemanticsNode().boundsInRoot
        }
        for(card in cards) {
            assertTrue("Every choice must be fully inside the same page",
                card.left>=galleryBounds.left && card.right<=galleryBounds.right &&
                    card.top>=galleryBounds.top && card.bottom<=galleryBounds.bottom)
            assertTrue("Reference choices use landscape cards",card.width>card.height*1.25f)
            assertEquals("All references have the same native width",cards.first().width,card.width,1f)
            assertEquals("All references have the same native height",cards.first().height,card.height,1f)
        }
        cards.chunked(4).forEachIndexed { row,group ->
            group.forEach { assertEquals(group.first().top,it.top,1f) }
            if(row>0) assertTrue(group.first().top>cards[(row-1)*4].bottom)
            assertEquals(bounds.center.x,(group.first().left+group.last().right)/2,1f)
        }
        for(r in rooms) {
            clickTag("showroom-${r.id}")
            compose.mainClock.advanceTimeBy(400)
            whileAttached {
                compose.onNodeWithContentDescription(r.title+"整体全景").assertIsDisplayed()
                compose.onNodeWithTag("showroom-back").assertIsDisplayed()
                compose.onNodeWithTag("showroom-preflight").assertIsDisplayed()
                for(tab in 0..3) compose.onAllNodesWithTag("showroom-tab-$tab").assertCountEquals(0)
                compose.onAllNodesWithTag("showroom-steps").assertCountEquals(0)
                if(r.id=="green") capture("showroom-panorama-runtime.png")
            }
            clickTag("showroom-back")
            compose.mainClock.advanceTimeBy(400)
            whileAttached { rooms.forEach { compose.onNodeWithTag("showroom-${it.id}").assertIsDisplayed() } }
            Log.i("TidyShowroomNative","UI_STAGE preview-${r.id}")
        }
        clickTag("showroom-green")
        compose.mainClock.advanceTimeBy(400);compose.waitForIdle()
        val store=instrumentation.targetContext.getSharedPreferences("tidy-room",0)
        val game=checkNotNull(CleaningExpansionPersistence.restore(store.getString("snapshot",null),
            store.getString(CleaningExpansionPersistence.KEY,null)))
        if(game.decorating) {
            clickTag("showroom-preflight")
            compose.waitUntil(10_000) {
                compose.mainClock.advanceTimeByFrame()
                compose.onAllNodesWithTag("showroom-cancel").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("showroom-apply").assertIsDisplayed()
            clickTag("showroom-cancel")
            compose.mainClock.advanceTimeBy(400)
            compose.onNodeWithTag("showroom-detail").assertIsDisplayed()
        } else {
            compose.onNodeWithTag("showroom-preflight").assertIsNotEnabled()
            compose.onNodeWithText("完成清洁后，就可以选择这间小屋。").assertExists()
        }
        clickTag("showroom-close")
        compose.mainClock.advanceTimeByFrame();compose.waitForIdle()
        compose.onAllNodesWithTag("showroom-panel").assertCountEquals(0)
        clickTag("room-showrooms")
        compose.waitUntil(20_000) {
            compose.mainClock.advanceTimeByFrame()
            compose.onAllNodesWithTag("showroom-gallery").fetchSemanticsNodes().isNotEmpty()
        }
        rooms.forEach { compose.onNodeWithTag("showroom-${it.id}").assertIsDisplayed() }
        clickTag("showroom-close")
        compose.mainClock.advanceTimeByFrame();compose.waitForIdle()
        assertArrayEquals("Browsing and cancelling cannot rewrite latest saves",before,prefs.readBytes())
        Log.i("TidyShowroomNative","PASS My Home entry/reopen, all twelve choices fully visible, four-column landscape grid, twelve previews/back, no legacy filters/tabs, preflight/cancel, native companion 1280x900, saves unchanged")
    }

    private fun nodes(e: Entity): List<Entity> = listOf(e)+e.getChildren().flatMap(::nodes)
    private fun assertSameLayout(expected: List<RoomItem>,actual: List<RoomItem>) {
        assertEquals(expected.size,actual.size)
        expected.zip(actual).forEach { (a,b) ->
            // The existing reader normalizes quaternions; float-rounding may
            // differ by 3e-8 while preserving every placement and its orientation.
            assertEquals(a.copy(rotation=b.rotation),b)
            val qa=a.rotation.normalized();val qb=b.rotation.normalized()
            assertEquals(1f,kotlin.math.abs(qa.x*qb.x+qa.y*qb.y+qa.z*qb.z+qa.w*qb.w),.000001f)
        }
    }
    @Test fun allTwelveLayoutsNativeMeshesFreeDragUndoAndShareUseRealInventory(): Unit=runBlocking {
        launch();delay(2000)
        withContext(Dispatchers.Main) {
            val context=instrumentation.targetContext
            val saved=context.getSharedPreferences("tidy-room",0)
            val original=checkNotNull(CleaningExpansionPersistence.restore(saved.getString("snapshot",null),
                saved.getString(CleaningExpansionPersistence.KEY,null)))
            val initial=original.copy(cleaning=null,achievements=original.achievements+"zen",
                playCounts=original.playCounts+("woodfish" to 123L))
            val rooms=LocalShowroomRepository(context).rooms()
            val rules=ShowroomUseCase()
            val scene=RoomScene()
            var current=initial
            scene.bindState { current }
            try {
                scene.build(initial)
                for(r in rooms) {
                    val result=rules.apply(initial,r)
                    assertNull("${r.id}: ${result.error}",result.error)
                    val applied=checkNotNull(result.game)
                    assertEquals(r.targets.size,applied.items.count { !it.packed })
                    assertEquals(initial.items.map { it.spec.id }.toSet(),applied.items.map { it.spec.id }.toSet())
                    assertEquals(initial.playCounts,applied.playCounts)
                    assertEquals(initial.achievements,applied.achievements)
                    assertNull(RoomPhysics.validateLayout(applied))
                    val loaded=checkNotNull(RoomPersistence.decode(RoomPersistence.encode(applied)))
                    assertSameLayout(applied.items,loaded.items)
                    assertEquals(applied.boxStock,loaded.boxStock)
                    current=applied;scene.synchronize(applied)
                    for(item in applied.items.filterNot { it.packed }) {
                        val root=checkNotNull(scene.volume.roomRoot.findEntity(item.spec.id)) { "Missing native ${r.id}/${item.spec.id}" }
                        assertFalse(item.spec.id,root.getVisualBounds(root,true,true).isEmpty())
                        assertTrue(item.spec.id,nodes(root).any { scene.nativeTarget(it)==item.spec.id })
                        assertEquals(item.position.x,root.transform.position.x,.002f)
                        assertEquals(item.position.y,root.transform.position.y,.002f)
                        assertEquals(item.position.z,root.transform.position.z,.002f)
                    }
                    for(item in applied.items.filter { !it.packed && LifestyleModels.contains(it.spec) }) {
                        val vm=RoomViewModel(applied) {}
                        vm.onEvent(RoomAction.Begin(item.spec.id,Rotation()))
                        assertEquals(item.spec.id,vm.currentGame.selectedId)
                        val held=vm.currentGame.item(item.spec.id).position
                        vm.onEvent(RoomAction.Transform(GrabDelta(Point3(.06f,.09f,.07f),Rotation())))
                        assertEquals(held+Point3(.06f,.09f,.07f),vm.currentGame.item(item.spec.id).position)
                        vm.onEvent(RoomAction.Cancel)
                        assertEquals(applied.items,vm.currentGame.items)
                    }
                    val restored=rules.restore(applied.copy(playCounts=mapOf("woodfish" to 124L)),initial)
                    assertEquals(initial.items,restored.items)
                    assertEquals(124L,restored.playCounts["woodfish"])
                    Log.i("TidyShowroomNative","PASS layout=${r.id} models=${r.targets.size} inventory/physics/persistence/native/free-drag/undo")
                }
                val exporter=ShowroomExporter(context)
                val file=exporter.create(current,rooms.last(),"我的创意改造","保留当前真实摆放")
                val uri=FileProvider.getUriForFile(context,context.packageName+".creations",file)
                assertEquals("content",uri.scheme)
                context.contentResolver.openInputStream(uri).use { assertNotNull(it) }
                ZipFile(file).use { zip ->
                    assertNotNull(zip.getEntry("current-layout.png"))
                    val json=org.json.JSONObject(zip.getInputStream(zip.getEntry("creation.json")).bufferedReader().readText())
                    assertEquals(rooms.last().id,json.getString("referenceId"))
                    val decoded=checkNotNull(RoomPersistence.decode(json.getString("snapshot")))
                    assertSameLayout(current.items,decoded.items)
                }
                File(context.filesDir,"showroom-creation-example.zip").writeBytes(file.readBytes())
                Log.i("TidyShowroomNative","PASS actual-layout share export, content URI and current snapshot")
            } finally { scene.destroy() }
        }
    }
}
