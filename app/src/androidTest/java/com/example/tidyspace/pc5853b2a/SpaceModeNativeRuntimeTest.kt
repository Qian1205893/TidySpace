package com.example.tidyspace.pc5853b2a

import android.app.Activity
import android.content.Intent
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage as ActivityStage
import com.example.tidyspace.pc5853b2a.domain.model.*
import com.example.tidyspace.pc5853b2a.domain.usecase.RoomCardDrop
import com.example.tidyspace.pc5853b2a.platform.*
import com.example.tidyspace.pc5853b2a.ui.sorting.*
import com.example.tidyspace.pc5853b2a.ui.sorting.components.CatalogueCategory
import com.example.tidyspace.pc5853b2a.ui.sorting.scene.RoomScene
import com.example.tidyspace.pc5853b2a.ui.sorting.scene.SortingScene
import com.example.tidyspace.pc5853b2a.ui.sorting.scene.dragTrace
import com.example.tidyspace.pc5853b2a.ui.showrooms.*
import com.pico.spatial.core.container.SpaceState
import com.pico.spatial.core.container.getSelfSpaceState
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.GroundShadowComponent
import com.pico.spatial.ui.platform.containers.closeStage
import com.pico.spatial.ui.platform.stub.SpatialStubActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Executes real OS space transitions and the native window-close lifecycle.
 * The public closeWindowContainer path is the SDK's documented caption-close path;
 * it deliberately does not call a custom app close button or Activity.recreate().
 */
class SpaceModeNativeRuntimeTest {
    @get:Rule val ui=createEmptyComposeRule()
    private val i get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=i.targetContext
    private val session get()=(context.applicationContext as SpatialApplication).spaceSession
    private val prefs get()=File(context.applicationInfo.dataDir,"shared_prefs/tidy-room.xml")
    private fun start() {
        ui.mainClock.autoAdvance=false
        i.runOnMainSync { context.startActivity(Intent(context,LaunchActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        waitFor("room-spatial-input")
        ui.waitUntil(180_000) {
            ui.mainClock.advanceTimeByFrame()
            runCatching { scene().isPrepared }.getOrDefault(false)
        }
        waitFor("room-space-mode")
        visibleUi { ui.onNodeWithTag("space-mode-controls").assertDoesNotExist() }
    }
    private fun waitFor(tag: String) {
        ui.waitUntil(180_000) {
            ui.mainClock.advanceTimeByFrame()
            try { ui.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
            catch(e: IllegalStateException) {
                if(e.message?.startsWith("No compose hierarchies")==true) false else throw e
            }
        }
    }
    private fun visibleUi(action: ()->Unit) {
        ui.waitUntil(20_000) {
            ui.mainClock.advanceTimeByFrame()
            try { action();true }
            catch(e: IllegalStateException) {
                // Off-head sleep / native screenshot capture can suspend the host.
                // Retry only missing hierarchies; assertion and state failures still fail.
                if(e.message?.startsWith("No compose hierarchies")==true) false else throw e
            }
        }
    }
    private fun click(tag: String) { waitFor(tag);visibleUi { ui.onNodeWithTag(tag).performClick() } }
    private fun waitForText(text: String) {
        ui.waitUntil(20_000) {
            ui.mainClock.advanceTimeByFrame()
            try { ui.onAllNodesWithText(text,useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty() }
            catch(e: IllegalStateException) {
                if(e.message?.startsWith("No compose hierarchies")==true) false else throw e
            }
        }
    }
    private fun clickText(text: String) {
        waitForText(text)
        visibleUi { ui.onNodeWithText(text,useUnmergedTree=true).performClick() }
    }
    private fun nameField()=ui.onNode(
        hasSetTextAction() and hasAnyAncestor(hasTestTag("home-new-name")),useUnmergedTree=true)
    private fun editDraft(text: String)=visibleUi { nameField().performTextReplacement(text) }
    private fun assertDraft(text: String)=visibleUi { nameField().assertTextContains(text) }
    private fun mode(mode: SpaceMode) {
        val start=SystemClock.elapsedRealtime()
        // Exercise lifecycle transitions even on a page or active operation
        // where the catalogue toggle is not available.
        i.runOnMainSync { session.modes.request(mode) }
        try {
            ui.waitUntil(20_000) {
                ui.mainClock.advanceTimeByFrame()
                session.modes.state.value.let { it.mode==mode && !it.switching } &&
                    context.getSelfSpaceState()==if(mode==SpaceMode.Full) SpaceState.FULL_SPACE else SpaceState.SHARED_SPACE
            }
        } catch(e: Exception) {
            Log.e("TidySpaceModeTest","failed requested=$mode state=${session.modes.state.value} actual=${context.getSelfSpaceState()}",e)
            throw e
        }
        if(mode==SpaceMode.Full) {
            // Native destruction of the previous Stage can arrive after re-entry.
            SystemClock.sleep(750)
            assertEquals(SpaceMode.Full,session.modes.state.value.mode)
            assertEquals(SpaceState.FULL_SPACE,context.getSelfSpaceState())
        }
        Log.i("TidySpaceModeTest","mode=$mode elapsed=${SystemClock.elapsedRealtime()-start}ms pid=${Process.myPid()} session=${session.id}")
    }
    private fun activity(): Activity {
        var found: Activity?=null
        i.runOnMainSync {
            found=listOf(ActivityStage.RESUMED,ActivityStage.STARTED,ActivityStage.PAUSED).flatMap {
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(it)
            }.firstOrNull { it is SpatialStubActivity && it !is FullSpaceActivity }
        }
        return checkNotNull(found) { "Native window host missing" }
    }
    private fun room(): RoomViewModel {
        var model: RoomViewModel?=null
        i.runOnMainSync { model=ViewModelProvider(session,RoomViewModelFactory(context))[RoomViewModel::class.java] }
        return checkNotNull(model)
    }
    private fun scene(): RoomScene {
        var value: RoomScene?=null
        i.runOnMainSync { value=session.retain("room-scene-0",::RoomScene) { it.destroy() } }
        return checkNotNull(value)
    }
    private fun record(name: String,text: String) {
        File(context.filesDir,"space-$name.txt").writeText(text)
    }
    private fun captureCheckpoint(name: String) {
        if(InstrumentationRegistry.getArguments().getString("captureModes")!="true") return
        File(context.filesDir,"space-capture-request").writeText(name)
        ui.waitUntil(120_000) {
            ui.mainClock.advanceTimeByFrame()
            File(context.filesDir,"space-capture-ack").run { exists() && readText().trim()==name }
        }
    }

    /** A real native window in both modes; drive the same room turn on device vsync. */
    @Test fun profileRoomInBothSpaces() {
        val args=InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("captureModes")=="true")
        start()
        val root=scene()
        val vm=room()
        val motion=args.getString("motion") ?: "turn"
        val turn=motion.startsWith("turn")
        if(motion=="turn-no-ground") i.runOnMainSync {
            fun disableGroundCasters(entity: Entity) {
                if(entity.components[GroundShadowComponent::class.java]!=null)
                    entity.components.set(GroundShadowComponent(castsShadowEnabled=false,receivesShadowEnabled=true))
                entity.getChildren().forEach(::disableGroundCasters)
            }
            disableGroundCasters(root.volume.roomRoot)
        }
        for(target in listOf(SpaceMode.Shared,SpaceMode.Full)) {
            if(session.modes.state.value.mode!=target) mode(target)
            ui.waitUntil(20_000) {
                ui.mainClock.advanceTimeByFrame()
                var presented=false
                i.runOnMainSync { presented=root.presented }
                presented
            }
            var first=0L
            var lastYaw=0f
            var frames=0
            val callback=object : Choreographer.FrameCallback {
                override fun doFrame(frameTimeNanos: Long) {
                    if(first==0L) first=frameTimeNanos
                    // Same 70-degree turntable sweep at any callback cadence.
                    val yaw=(35.0*kotlin.math.sin((frameTimeNanos-first)/1e9)).toFloat()
                    dragTrace("Tidy.profileTurn") {
                        vm.onEvent(RoomAction.Transform(GrabDelta(Point3(0f,0f,0f),
                            Rotation.yaw(yaw-lastYaw),pointerCount=2)))
                        root.applyInput(vm.currentGame)
                    }
                    lastYaw=yaw
                    frames++
                    Choreographer.getInstance().postFrameCallback(this)
                }
            }
            i.runOnMainSync {
                assertTrue(root.presented)
                if(turn) {
                    vm.onEvent(RoomAction.Begin("@room"))
                    root.applyInput(vm.currentGame)
                    Choreographer.getInstance().postFrameCallback(callback)
                }
                if(motion=="hidden") root.volume.owner.enabled=false
            }
            try {
                captureCheckpoint(target.name.lowercase())
                assertEquals(target,session.modes.state.value.mode)
                assertEquals(if(target==SpaceMode.Full) SpaceState.FULL_SPACE else SpaceState.SHARED_SPACE,
                    context.getSelfSpaceState())
            } finally {
                i.runOnMainSync {
                    if(turn) {
                        Choreographer.getInstance().removeFrameCallback(callback)
                        vm.onEvent(RoomAction.Cancel)
                        root.applyInput(vm.currentGame)
                    }
                    if(motion=="hidden") root.volume.owner.enabled=true
                    Log.i("TidySpaceProfile","mode=$target motion=$motion callbacks=$frames")
                }
            }
            if(turn) assertTrue("Room turn did not receive device frames",frames>=90)
        }
    }

    @After fun closeTestWindowBeforeComposeClockIsDisposed() {
        // The Spatial host can still be measuring while the test clock shuts down.
        // Finish it first; ordinary Shared teardown may persist Cancel metadata.
        val host=runCatching { activity() }.getOrNull() ?: return
        if(session.modes.state.value.mode==SpaceMode.Full) mode(SpaceMode.Shared)
        i.runOnMainSync { host.finish() }
        ui.waitUntil(20_000) {
            ui.mainClock.advanceTimeByFrame()
            host.isDestroyed
        }
    }

    @Test fun liveRoundTripsRetainNativeWindowModelsOpenFormAndSave() {
        start()
        val before=prefs.readBytes();val host=activity();val vm=room();val root=scene()
        val original=vm.currentGame
        click("room-homes");waitFor("home-library-panel");click("home-new")
        waitFor("home-new-name")
        editDraft("尚未保存的全屏测试小屋")
        repeat(3) { index ->
            mode(SpaceMode.Full)
            if(index==0) captureCheckpoint("full")
            assertDraft("尚未保存的全屏测试小屋")
            assertSame(host,activity());assertSame(vm,room());assertSame(root,scene())
            mode(SpaceMode.Shared)
            if(index==0) captureCheckpoint("shared")
            assertDraft("尚未保存的全屏测试小屋")
        }
        assertEquals(original,vm.currentGame);assertEquals(1,root.buildCount)
        assertArrayEquals(before,prefs.readBytes())
        record("round-trip","PASS: 6 native space transitions; same Activity, ViewModels, models; open draft; save bytes unchanged.")
    }

    @Test fun decoratingStartsWithoutCleaningAndTrayButtonSwitchesRealSpace() {
        start()
        waitFor("room-space-mode")
        val before=prefs.readBytes();val vm=room();val original=vm.currentGame
        val root=scene();val host=activity()
        assertTrue(original.decorating)
        assertTrue(original.remainingGoods>0 || original.items.any { !it.packed })
        visibleUi {
            ui.onNodeWithText("打扫好，再慢慢安家").assertDoesNotExist()
            ui.onNodeWithTag("room-space-mode").assertTextContains("进入全屏空间")
        }
        i.runOnMainSync {
            for(name in listOf("clean_bin","clean_stand","clean_stand_extra")+
                CleanTool.entries.map { "clean_${it.name}" }+cleaningMess().map { it.id }) {
                assertNull("Decorating should not build cleaning content: $name",root.volume.roomRoot.findEntity(name))
            }
        }
        repeat(2) {
            click("room-space-mode")
            ui.waitUntil(20_000) {
                ui.mainClock.advanceTimeByFrame()
                session.modes.state.value.let { it.mode==SpaceMode.Full && !it.switching } &&
                    context.getSelfSpaceState()==SpaceState.FULL_SPACE
            }
            visibleUi { ui.onNodeWithTag("room-space-mode").assertTextContains("返回共享空间") }
            i.runOnMainSync {
                assertFalse(root.volume.roomRoot.getVisualBounds(root.volume.owner,true,true).isEmpty())
            }
            click("room-space-mode")
            ui.waitUntil(20_000) {
                ui.mainClock.advanceTimeByFrame()
                session.modes.state.value.let { it.mode==SpaceMode.Shared && !it.switching } &&
                    context.getSelfSpaceState()==SpaceState.SHARED_SPACE
            }
        }
        i.runOnMainSync { vm.onEvent(RoomAction.CleanAgain) }
        assertEquals(original,vm.currentGame)
        assertSame(host,activity());assertSame(root,scene());assertEquals(1,root.buildCount)
        assertArrayEquals(before,prefs.readBytes())
        record("direct-decorating","PASS: cleaning nodes absent; tray button completed four real OS transitions; same window, models, layout, inventory and save bytes; retired cleaning action ignored.")
    }

    @Test fun tapsKeepCatalogueEnabledAndRealDragsBlockInBothSpaces() {
        check(context.packageName.endsWith(".perftest")) { "Use isolated test preferences" }
        start()
        val vm=room();val root=scene()
        val original=vm.currentGame
        fun dispatch(action: RoomAction) {
            i.runOnMainSync { vm.onEvent(action);root.applyInput(vm.currentGame) }
            ui.mainClock.advanceTimeBy(80)
        }
        fun catalogue(enabled: Boolean) {
            visibleUi {
                for(tag in listOf("room-homes","room-showrooms")+
                    CatalogueCategory.entries.map { "category-${it.name}" }) {
                    val node=ui.onNodeWithTag(tag)
                    if(enabled) node.assertIsEnabled() else node.assertIsNotEnabled()
                }
            }
        }
        val targets=original.items.filter { !it.packed }.map { it.spec.id }+
            listOf("@room","@house:clock","@house:dimmer")+
            original.items.filter { !it.packed && it.spec.kind in setOf(Furnishing.Woodfish,Furnishing.SingingBowl) }
                .map { "@play:${it.spec.id}" }
        for(space in listOf(SpaceMode.Shared,SpaceMode.Full)) {
            mode(space)
            for(id in targets) {
                dispatch(RoomAction.Begin(id))
                assertEquals(id,vm.currentGame.selectedId)
                catalogue(true)
                dispatch(RoomAction.Transform(GrabDelta(Point3(.001f,0f,0f),Rotation())))
                catalogue(true)
                dispatch(RoomAction.Cancel)
                catalogue(true)
            }
            val item=original.items.first { !it.packed && it.spec.kind in setOf(Furnishing.Chair,Furnishing.Desk) }
            dispatch(RoomAction.Begin(item.spec.id))
            catalogue(true)
            dispatch(RoomAction.Transform(GrabDelta(Point3(.03f,0f,0f),Rotation())))
            catalogue(false)
            dispatch(RoomAction.Transform(GrabDelta(Point3(-.03f,0f,0f),Rotation())))
            catalogue(false)
            dispatch(RoomAction.Cancel)
            catalogue(true)
        }
        assertEquals(original.items,vm.currentGame.items)
        assertEquals(original.playCounts,vm.currentGame.playCounts)
        record("catalogue-taps","PASS: both spaces; ${targets.size} targets stay enabled on press and slight movement; real drags block until cancel; layout and toy counts retained.")
    }

    @Test fun nativeFullWindowCloseReturnsSharedWithSameProcessPageDraftAndPendingOperation() {
        start()
        val before=prefs.readBytes();val app=context.applicationContext
        val pid=Process.myPid();val oldHost=activity();val vm=room();val root=scene();val sessionId=session.id
        click("room-homes");waitFor("home-library-panel");click("home-new")
        waitFor("home-new-name")
        editDraft("关闭全屏后继续编辑")
        mode(SpaceMode.Full)
        i.runOnMainSync { vm.onEvent(RoomAction.Begin("@room")) }
        val pending=vm.currentGame
        assertEquals("@room",pending.selectedId)
        val began=SystemClock.elapsedRealtime()
        // Public native close, equivalent to clicking the system caption's X.
        i.runOnMainSync { session.closeNativeWindow() }
        ui.waitUntil(30_000) {
            ui.mainClock.advanceTimeByFrame()
            session.modes.state.value.let { it.mode==SpaceMode.Shared && !it.switching && !it.recoveringWindow } &&
                context.getSelfSpaceState()==SpaceState.SHARED_SPACE
        }
        waitFor("home-new-name")
        assertDraft("关闭全屏后继续编辑")
        assertNotSame(oldHost,activity())
        assertSame(app,context.applicationContext);assertEquals(pid,Process.myPid())
        assertEquals(sessionId,session.id);assertSame(vm,room());assertSame(root,scene())
        i.runOnMainSync {
            assertFalse(root.volume.roomRoot.getVisualBounds(root.volume.owner,true,true).isEmpty())
        }
        assertTrue(root.presented);assertEquals(1,root.buildCount)
        assertEquals(pending,vm.currentGame);assertArrayEquals(before,prefs.readBytes())
        record("native-close","PASS: native WINDOW close -> Shared; warm host replacement in ${SystemClock.elapsedRealtime()-began}ms; pid=$pid session=$sessionId; same ViewModels/models; draft/page/pending @room retained; save bytes unchanged.")
        Log.i("TidySpaceModeTest","PASS native close restored Shared, same process/session/models, pending operation and draft")
        mode(SpaceMode.Full);mode(SpaceMode.Shared)
    }

    @Test fun externalStageCloseKeepsWindowAndSynchronizesUi() {
        start()
        val before=prefs.readBytes();val host=activity();val root=scene();val vm=room()
        mode(SpaceMode.Full)
        i.runOnMainSync { CoroutineScope(Dispatchers.Main.immediate).launch { closeStage() } }
        ui.waitUntil(20_000) {
            ui.mainClock.advanceTimeByFrame()
            session.modes.state.value.mode==SpaceMode.Shared && !session.modes.state.value.switching
        }
        assertEquals(SpaceState.SHARED_SPACE,context.getSelfSpaceState())
        visibleUi { ui.onNodeWithTag("room-space-mode").assertTextContains("进入全屏空间") }
        assertSame(host,activity());assertSame(root,scene());assertSame(vm,room())
        assertArrayEquals(before,prefs.readBytes())
        record("stage-close","PASS: system Stage close keeps native window, models, ViewModels and save; UI returns Shared.")
    }

    @Test fun sortingPageAndActiveGrabSurviveNativeClose() {
        start()
        val before=prefs.readBytes()
        clickText("手札")
        clickText("查看手札与委托")
        clickText("收纳挑战")
        var vm: SortingViewModel?=null
        i.runOnMainSync { vm=ViewModelProvider(session,SortingViewModelFactory())[SortingViewModel::class.java] }
        val model=checkNotNull(vm)
        ui.waitUntil(120_000) {
            ui.mainClock.advanceTimeByFrame()
            model.state.value.sceneStatus==SceneStatus.Ready
        }
        val state=model.state.value
        val key="sorting-scene-${state.sceneRevision}-${state.game.levelIndex}"
        var scene: SortingScene?=null
        i.runOnMainSync { scene=session.retain(key,::SortingScene) { it.destroy() } }
        val root=checkNotNull(scene)
        mode(SpaceMode.Full)
        val host=activity()
        i.runOnMainSync { model.onEvent(SortingEvent.Act(SortingAction.BeginGrab(state.game.items.first().spec.id,"SpatialPointer"))) }
        val pending=model.state.value
        assertTrue(pending.game.dragging)
        i.runOnMainSync { session.closeNativeWindow() }
        ui.waitUntil(30_000) {
            ui.mainClock.advanceTimeByFrame()
            session.modes.state.value.let { it.mode==SpaceMode.Shared && !it.switching && !it.recoveringWindow }
        }
        assertNotSame(host,activity())
        assertEquals(pending,model.state.value)
        i.runOnMainSync {
            assertFalse(root.workbenchRoot.getVisualBounds(root.placementOwner,true,true).isEmpty())
        }
        assertEquals(1,root.buildCount)
        waitForText("布置小屋")
        assertArrayEquals(before,prefs.readBytes())
        mode(SpaceMode.Full);mode(SpaceMode.Shared)
        record("sorting-close","PASS: sorting page, same models and pending grab survive Full native close; save bytes unchanged.")
    }

    @Test fun ordinarySharedCaptionCloseDoesNotReopenApplication() {
        start()
        val before=prefs.readBytes();val host=activity()
        i.runOnMainSync { session.closeNativeWindow() }
        ui.waitUntil(20_000) {
            ui.mainClock.advanceTimeByFrame()
            host.isDestroyed
        }
        assertFalse(session.nativeHandover)
        assertFalse(session.modes.state.value.recoveringWindow)
        assertEquals(SpaceState.SHARED_SPACE,context.getSelfSpaceState())
        assertArrayEquals(before,prefs.readBytes())
        record("shared-close","PASS: ordinary Shared native close closes normally without a recovery loop; save unchanged.")
    }

    @Test fun journalTabCataloguePageAndCardDragSurviveRepeatedNativeClose() {
        start()
        val before=prefs.readBytes();val vm=room();val root=scene();val pid=Process.myPid()
        i.runOnMainSync { vm.onEvent(RoomAction.ToggleJournal) }
        clickText("收藏")
        ui.mainClock.advanceTimeByFrame()
        visibleUi { ui.onNodeWithText("已收藏",substring=true,useUnmergedTree=true).assertExists() }
        mode(SpaceMode.Full)
        i.runOnMainSync { session.closeNativeWindow() }
        ui.waitUntil(30_000) {
            ui.mainClock.advanceTimeByFrame()
            session.modes.state.value.let { it.mode==SpaceMode.Shared && !it.switching && !it.recoveringWindow }
        }
        visibleUi { ui.onNodeWithText("已收藏",substring=true,useUnmergedTree=true).assertExists() }
        i.runOnMainSync { vm.onEvent(RoomAction.ToggleJournal) }
        clickText("布置")
        val inventory=RoomCardDrop.inventory(vm.currentGame)
        val category=CatalogueCategory.entries.first { entry -> inventory.count { entry.accepts(it.spec) }>7 }
        val items=inventory.filter { category.accepts(it.spec) }
        click("category-${category.name}")
        waitFor("card-${items.first().spec.id}")
        clickText("下一页")
        val item=items[7];waitFor("card-${item.spec.id}")
        val page="2 / ${(items.size+6)/7}"
        visibleUi { ui.onNodeWithText(page,useUnmergedTree=true).assertExists() }
        mode(SpaceMode.Full)
        val token=vm.currentGame.cardSequence+1
        i.runOnMainSync {
            vm.onEvent(RoomAction.CardBegin(item.spec.id,token,Rotation()))
            vm.onEvent(RoomAction.CardMove(token,Point3(.1f,.6f,.2f)))
        }
        val pending=vm.currentGame
        assertEquals(token,pending.cardDrag?.token)
        repeat(2) {
            i.runOnMainSync { session.closeNativeWindow() }
            ui.waitUntil(30_000) {
                ui.mainClock.advanceTimeByFrame()
                session.modes.state.value.let { it.mode==SpaceMode.Shared && !it.switching && !it.recoveringWindow }
            }
            waitFor("card-${item.spec.id}")
            visibleUi { ui.onNodeWithText(page,useUnmergedTree=true).assertExists() }
            assertEquals(pending,vm.currentGame)
            assertSame(vm,room());assertSame(root,scene());assertEquals(pid,Process.myPid())
            i.runOnMainSync {
                assertFalse(root.volume.roomRoot.getVisualBounds(root.volume.owner,true,true).isEmpty())
            }
            assertEquals(1,root.buildCount)
            mode(SpaceMode.Full)
        }
        mode(SpaceMode.Shared)
        assertArrayEquals(before,prefs.readBytes())
        record("card-close","PASS: journal collection tab, catalogue category/page and active card token/point survive 3 native closes; same process/ViewModels/models; single scene build; save bytes unchanged.")
    }
}
