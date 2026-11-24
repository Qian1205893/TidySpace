package com.example.tidyspace.pc5853b2a.ui.sorting

import android.content.Context
import android.util.Log
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.tidyspace.pc5853b2a.domain.model.*
import com.example.tidyspace.pc5853b2a.domain.usecase.RoomUseCase
import com.example.tidyspace.pc5853b2a.domain.usecase.MovingHome
import com.example.tidyspace.pc5853b2a.domain.usecase.CleaningRules
import com.example.tidyspace.pc5853b2a.ui.showrooms.ShowroomScreen
import com.example.tidyspace.pc5853b2a.ui.showrooms.ShowroomViewModel
import com.example.tidyspace.pc5853b2a.ui.showrooms.ShowroomViewModelFactory
import com.example.tidyspace.pc5853b2a.ui.showrooms.ShowroomPage
import com.example.tidyspace.pc5853b2a.ui.sorting.scene.RoomScene
import com.example.tidyspace.pc5853b2a.ui.sorting.scene.RoomAudio
import com.example.tidyspace.pc5853b2a.ui.sorting.scene.dragTrace
import com.example.tidyspace.pc5853b2a.ui.sorting.components.RoomCatalogue
import com.example.tidyspace.pc5853b2a.ui.sorting.components.CardGestureCallbacks
import com.example.tidyspace.pc5853b2a.ui.sorting.components.RoomJournal
import com.example.tidyspace.pc5853b2a.ui.sorting.components.CatalogueColors
import com.example.tidyspace.pc5853b2a.ui.sorting.components.RoomLoadingOverlay
import com.example.tidyspace.pc5853b2a.R
import androidx.compose.ui.res.painterResource
import com.example.tidyspace.pc5853b2a.domain.usecase.RoomTime
import com.pico.spatial.ui.design.*
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.foundation.gesture.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.pico.spatial.ui.platform.LocalPhysicalLengthConverter
import com.pico.spatial.ui.platform.LengthUnit
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onGloballyPositioned
import com.example.tidyspace.pc5853b2a.ui.navigation.rememberSpaceSession
import com.example.tidyspace.pc5853b2a.ui.navigation.SpaceSessionUi

@Composable
fun RoomScreen(onSorting: ()->Unit) {
    val context=LocalContext.current.applicationContext
    val factory=remember(context) { RoomViewModelFactory(context) }
    val room: RoomViewModel=viewModel(factory=factory)
    val ui by room.state.collectAsStateWithLifecycle()
    var referenceOpen by rememberSaveable { mutableStateOf(false) }
    var personalOpen by rememberSaveable { mutableStateOf(false) }
    val showroomFactory=remember(context,room) { ShowroomViewModelFactory(context,room,
        if(personalOpen) ShowroomPage.Homes else ShowroomPage.Gallery) }
    val showrooms: ShowroomViewModel=viewModel(key="room-showrooms",factory=showroomFactory)
    val homes by room.homes.collectAsStateWithLifecycle()
    RoomContent(ui.game,{ room.currentGame },{ room.onUiEvent(RoomEvent.Act(it)) },onShowrooms={
        showrooms.open(personal=false)
        personalOpen=false
        referenceOpen=true
        Log.i("TidyShowrooms","catalogue-open-click")
    },onSorting={
        if(room.currentGame.selectedId!=null || room.currentGame.cardDrag!=null)
            room.onUiEvent(RoomEvent.Act(RoomAction.Cancel))
        onSorting()
    },onHomes={
        showrooms.open(personal=true)
        personalOpen=true
        referenceOpen=true
    },homeName=homes?.active?.name ?: "我的小屋")
    if(referenceOpen) ShowroomScreen(room,showroomFactory) { referenceOpen=false }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun RoomContent(game: RoomGame,readGame: ()->RoomGame,send: (RoomAction)->Unit,onShowrooms: ()->Unit,onSorting: ()->Unit,
    onHomes: ()->Unit,homeName: String) {
    var revision by rememberSaveable { mutableIntStateOf(0) }
    key(revision) { RoomSession(game,readGame,send,onShowrooms,onSorting,onHomes,homeName,revision) { revision++ } }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun RoomSession(game: RoomGame,readGame: ()->RoomGame,send: (RoomAction)->Unit,onShowrooms: ()->Unit,onSorting: ()->Unit,
    onHomes: ()->Unit,homeName: String,revision: Int,retry: ()->Unit) {
        val session=rememberSpaceSession()
        val sceneKey="room-scene-$revision"
        val scene=remember(session,sceneKey) { session.retain(sceneKey,::RoomScene) { it.destroy() } }
        val lifecycle=LocalLifecycleOwner.current.lifecycle
        DisposableEffect(scene,lifecycle) {
            val observer=LifecycleEventObserver { _,event ->
                if(event==Lifecycle.Event.ON_START) scene.volume.startTracking()
                if(event==Lifecycle.Event.ON_STOP) scene.volume.stopTracking()
            }
            lifecycle.addObserver(observer)
            if(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) scene.volume.startTracking()
            onDispose { lifecycle.removeObserver(observer);scene.volume.stopTracking() }
        }
        val scope=rememberCoroutineScope()
        val context=LocalContext.current
        val units=LocalPhysicalLengthConverter.current
        val echoWidth=units.dpToLength(420.dp,LengthUnit.Meters)
        val cueWidth=units.dpToLength(800.dp,LengthUnit.Meters)
        val catalogueWidth=units.dpToLength(1440.dp,LengthUnit.Meters)
        val actionsWidth=units.dpToLength(180.dp,LengthUnit.Meters)
        val journalWidth=units.dpToLength(570.dp,LengthUnit.Meters)
        var echoEvent by remember { mutableStateOf<PlayEvent?>(null) }
        val currentSend by rememberUpdatedState(send)
        val currentGame by rememberUpdatedState(game)
        val currentRead by rememberUpdatedState(readGame)
        val density=LocalDensity.current
        val metersPerPixel=units.dpToLength(with(density) { 1f.toDp() },LengthUnit.Meters)
        val currentMetersPerPixel by rememberUpdatedState(metersPerPixel)
        val dispatch=remember(scene) { { action: RoomAction ->
            // Match the native carton pose before collision checks or preview
            // extraction; the caption center is measured in room coordinates.
            // A card transaction must not rewrite unrelated carton poses.
            // Native yaw normalization can otherwise introduce tiny float changes.
            if(action !is RoomAction.CardBegin && action !is RoomAction.CardMove &&
                action !is RoomAction.CardRelease && action !is RoomAction.CardCancel) {
                dragTrace("Tidy.cartonFacing") {
                    val cartons=scene.cartonFacing()
                    if(cartons.isNotEmpty()) currentSend(RoomAction.SyncCartonFacing(cartons))
                }
            }
            dragTrace("Tidy.reduce") { currentSend(action) }
            scene.applyInput(currentRead())
        } }
        scene.bindState { currentRead() }
        val input=remember(scene) { scene.input(dispatch).also { it.resumeSelection(readGame().selectedId) } }
        val spaceState by session.modes.state.collectAsState()
        LaunchedEffect(spaceState.revision) { input.rebaseline() }
        var status by remember { mutableStateOf(if(scene.isPrepared) SceneStatus.Ready else SceneStatus.Loading) }
        var loadPhase by remember { mutableStateOf(RoomLoadPhase.Building) }
        val currentStatus by rememberUpdatedState(status)
        val cards=remember(scene) { CardGestureCallbacks(
            start={ item,point ->
                val held=currentRead().cardDrag
                if(held!=null && held.id==item.spec.id) held.token
                else if(status!=SceneStatus.Ready || held!=null || currentRead().selectedId!=null) null
                else {
                    input.cancel()
                    val token=currentRead().cardSequence+1
                    dispatch(RoomAction.CardBegin(item.spec.id,token,scene.viewerFacing(item.spec.id) ?: Rotation()))
                    if(currentRead().cardDrag?.token!=token) null else {
                        dispatch(RoomAction.CardMove(token,scene.cardPoint(point,currentMetersPerPixel)))
                        scope.launch {
                            try { scene.preloadCard(item.spec) }
                            catch(e: CancellationException) { throw e }
                            catch(e: Exception) { Log.e("TidyCard","preload failed ${item.spec.id}",e) }
                        }
                        Log.i("TidyCard","start id=${item.spec.id} token=$token stock=${currentRead().remainingGoods}")
                        token
                    }
                }
            },
            move={ token,point ->
                if(currentRead().cardDrag?.token==token)
                    dispatch(RoomAction.CardMove(token,scene.cardPoint(point,currentMetersPerPixel)))
            },
            end={ token ->
                dispatch(RoomAction.CardRelease(token))
                Log.i("TidyCard","release token=$token focus=${currentRead().focusId} stock=${currentRead().remainingGoods}")
            },
            cancel={ token -> if(!session.preserveInput) {
                dispatch(RoomAction.CardCancel(token));Log.i("TidyCard","cancel token=$token")
            } },
            prepare={ specs -> specs.forEach { scene.preloadCard(it) } },
        ) }
        DisposableEffect(lifecycle,scene) {
            val observer=LifecycleEventObserver { _,event ->
                if(event==Lifecycle.Event.ON_STOP && !session.preserveInput)
                    currentRead().cardDrag?.let { cards.cancel(it.token) }
            }
            lifecycle.addObserver(observer)
            onDispose {
                lifecycle.removeObserver(observer)
                if(!session.preserveInput) currentRead().cardDrag?.let { cards.cancel(it.token) }
            }
        }
        val tone=remember { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC,35) }.getOrNull() }
        val audio=remember { RoomAudio(context) }
        var heardPlay by remember { mutableLongStateOf(game.playSequence) }
        var heard by remember { mutableIntStateOf(game.celebration) }
        var heardCleaning by remember { mutableIntStateOf(game.cleaning?.count ?: 0) }
        var heardHour by remember { mutableIntStateOf(game.dayHour.toInt()) }
        DisposableEffect(tone) { onDispose { tone?.release() } }
        DisposableEffect(audio) { onDispose { audio.close() } }
        LaunchedEffect(game.celebration) {
            if(game.celebration!=heard) { heard=game.celebration;tone?.startTone(ToneGenerator.TONE_PROP_ACK,180) }
        }
        LaunchedEffect(game.cleaning?.count) {
            val count=game.cleaning?.count ?: 0
            if(count>heardCleaning) audio.note(count-1)
            heardCleaning=count
        }
        LaunchedEffect(game.cleaning?.sequence,game.cleaning?.tool,game.cleaning?.finished) {
            audio.cleaning(game.cleaning)
        }
        LaunchedEffect(game.playSequence) {
            if(game.playSequence!=heardPlay) {
                heardPlay=game.playSequence
                game.playEvent?.let { echoEvent=it;audio.strike(it) }
            }
        }
        LaunchedEffect(game.dayHour.toInt()) {
            val hour=game.dayHour.toInt()
            if(game.selectedId=="@house:clock" && hour!=heardHour) audio.note(hour%7)
            heardHour=hour
        }
        val musicStep=(game.crankRadians/(kotlin.math.PI.toFloat()/2)).toInt()
        LaunchedEffect(game.selectedId,musicStep) {
            val item=game.items.find { "@play:${it.spec.id}"==game.selectedId }
            if(item?.spec?.kind==Furnishing.MusicBox && musicStep>0) audio.note(musicStep-1)
        }
        DisposableEffect(scene) {
            session.bindResource(sceneKey,scene::detachHost)
            onDispose {
                if(!session.preserveInput) input.cancel()
                session.releaseResource(sceneKey,retain=scene.isPrepared)
            }
        }
        if(status!=SceneStatus.Ready) {
            RoomLoadingOverlay(loadPhase,status==SceneStatus.Failed,retry)
        }
        SpatialView(
            modifier=Modifier.fillMaxSize()
                .testTag("room-spatial-input")
                // Capture the hit once and retain it across ray misses. Model
                // bodies, tools, mechanisms and background share one lifecycle.
                .pointerInput(scene) {
                    try { detectSpatialPointerEvent(context,onEvent={ events ->
                        if(status!=SceneStatus.Ready || currentRead().cardDrag!=null) return@detectSpatialPointerEvent false
                        scene.dragDiagnostics.input(events,currentRead(),{ currentRead() },
                            { scene.inputPosition()?.let { Point3(it.x,it.y,it.z) } }) {
                            input.events(events)
                        }
                    }) } finally { if(!session.preserveInput) input.cancel() else input.rebaseline() }
                },
            attachments={
                AttachmentPanel("room-front-ui") { SpaceSessionUi("room-front-ui") { PicoTheme {
                    // Read live Snapshot state inside the panel's own composition.
                    // Keep its texture bounded to the catalogue, leaving room hits clear.
                    val live=currentGame
                    Box(Modifier.size(1440.dp,302.dp).testTag("room-front-ui")
                        .onGloballyPositioned {
                            scene.catalogueLayout(it.size.width.toFloat(),it.size.height.toFloat())
                        }) {
                        if(live.controlsVisible && currentStatus==SceneStatus.Ready) {
                            RoomCatalogue(live,currentStatus,dispatch,cards,retry,onShowrooms,onHomes,homeName)
                        }
                    }
                } } }
                AttachmentPanel("room-actions") { PicoTheme {
                        val live=currentGame
                        Row(Modifier.size(180.dp,72.dp),
                            horizontalArrangement=Arrangement.spacedBy(20.dp)) {
                            if(currentStatus==SceneStatus.Ready) {
                            IconButton(onClick={ dispatch(RoomAction.ToggleControls) },enabled=live.cardDrag==null,
                                colors=IconButtonDefaults.iconButtonColors(
                                    containerColor=CatalogueColors.catalogPaper,contentColor=CatalogueColors.catalogInk)) {
                                Icon(painterResource(R.drawable.ic_grid),
                                    contentDescription=if(live.controlsVisible) "收起物品卡片" else "展开物品卡片")
                            }
                            IconButton(onClick={
                                dispatch(RoomAction.Begin("@room"))
                                dispatch(RoomAction.Transform(GrabDelta(Point3(0f,0f,0f),Rotation.yaw(45f),pointerCount=2)))
                                dispatch(RoomAction.End)
                            },enabled=live.cardDrag==null && live.selectedId==null,
                                colors=IconButtonDefaults.iconButtonColors(
                                    containerColor=CatalogueColors.catalogPaper,contentColor=CatalogueColors.catalogInk)) {
                                Icon(painterResource(R.drawable.ic_arrow_circle_right),contentDescription="转动小屋")
                            }
                            }
                        }
                } }
                AttachmentPanel("room-journal") { SpaceSessionUi("room-journal") { PicoTheme {
                        val live=currentGame
                        if(live.journalOpen) Box(Modifier.size(570.dp,780.dp)
                            .background(PicoTheme.colorScheme.fillSecondary,RoundedCornerShape(26.dp))) {
                            RoomJournal(live,dispatch,onSorting)
                        }
                } } }
                AttachmentPanel("house-cue") { PicoTheme { HouseCue(game) } }
                AttachmentPanel("echo-0") { PicoTheme { PlayEcho(echoEvent) } }
            },
            initial={ content,attachments ->
                scene.volume.content=content
                session.bindResource(sceneKey,scene::detachHost)
                // Keep the hierarchy detached until every mesh and its fitted
                // pose are ready; present() publishes this same root once.
                attachments.entity("room-front-ui")?.let { scene.attachCatalogue(it,catalogueWidth) }
                attachments.entity("room-actions")?.let { scene.attachCatalogueActions(it,actionsWidth) }
                attachments.entity("room-journal")?.let { scene.attachJournal(it,journalWidth) }
                attachments.entity("house-cue")?.let { scene.attachCue(it,cueWidth) }
                attachments.entity("echo-0")?.let { scene.attachEcho(0,it,echoWidth) }
                if(scene.isPrepared) {
                    scene.applyInput(currentRead())
                    scene.present()
                    status=SceneStatus.Ready
                    session.windowReady()
                } else {
                scope.launch {
                    try {
                        scene.build(currentGame) {
                            loadPhase=it
                            Log.i("TidyLoading","phase=$it visible=${scene.presented}")
                        }
                        // Flush the complete hidden hierarchy before one atomic reveal.
                        withFrameNanos { }
                        withFrameNanos { }
                        scene.present()
                        status=SceneStatus.Ready
                        session.windowReady()
                    }
                    catch(e: CancellationException) { scene.conceal();throw e }
                    catch(e: Exception) {
                        scene.conceal()
                        Log.e("TidyRoom","scene-load-failed",e);status=SceneStatus.Failed
                    }
                }
                }
            },
            update={ _,attachments ->
                // Attachment entities may be replaced when their Compose content refreshes.
                // Rebind the current instances; gameplay moves only its own stable anchors.
                attachments.entity("room-front-ui")?.let { scene.attachCatalogue(it,catalogueWidth) }
                attachments.entity("room-actions")?.let { scene.attachCatalogueActions(it,actionsWidth) }
                attachments.entity("room-journal")?.let { scene.attachJournal(it,journalWidth) }
                attachments.entity("house-cue")?.let { scene.attachCue(it,cueWidth) }
                attachments.entity("echo-0")?.let { scene.attachEcho(0,it,echoWidth) }
            },
        )
        LaunchedEffect(scene,status) {
            if(status==SceneStatus.Ready) {
                Log.i("TidyUiPlane","ready center=${scene.catalogueCenter()} half=${scene.volume.windowHalf} roofGap=${scene.catalogueClearance()}")
                try {
                    // A model load must finish before the next reconciliation. Restarting
                    // this coroutine on every grab update cancels native resource work.
                    snapshotFlow { currentGame }.collect { next -> scene.synchronize(next) }
                } catch(e: CancellationException) {
                    throw e
                } catch(e: Exception) {
                    scene.conceal()
                    Log.e("TidyRoom","scene-update-failed",e);status=SceneStatus.Failed
                }
            }
        }
        LaunchedEffect(scene,status) {
            if(status==SceneStatus.Ready) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                var last=0L
                while(true) withFrameNanos { frame ->
                    scene.dragDiagnostics.frame()
                    if(frame-last>=33_000_000L) {
                        val c=currentRead().cleaning
                        if(last!=0L && c?.tool in setOf(CleanTool.Vacuum,CleanTool.Mop) && c?.finished==false) {
                            dispatch(RoomAction.CleanTick(((frame-last)/1_000_000_000.0).toFloat()))
                        }
                        scene.animate(frame/1_000_000_000.0);last=frame
                    }
                }
            }
        }
}

@Composable
private fun HouseCue(game: RoomGame) {
    // Measure the attachment before a grab. Creating it on pointer-down
    // resizes its Android window during the first interactive frame.
    // RoomScene also disables the hidden entity so it cannot intercept hits.
    val alpha=remember { Animatable(0f) }
    val text=when {
        game.selectedId=="@house:clock"->"${RoomTime.label(game.dayHour)} · 沿表盘拨动，看窗外变换"
        game.selectedId=="@house:dimmer"->"转动调亮暗 · 轻点开关灯"
        else->game.message
    }
    LaunchedEffect(game.cueVisible,game.message,game.selectedId,game.guideVisible) {
        if(!game.cueVisible) {
            alpha.snapTo(0f)
            return@LaunchedEffect
        }
        alpha.snapTo(1f)
        delay(3800)
        alpha.animateTo(0f,tween(800))
    }
    Box(Modifier.size(800.dp,230.dp).alpha(if(game.cueVisible) alpha.value else 0f)
        .background(PicoTheme.colorScheme.fillSecondary,RoundedCornerShape(24.dp)).padding(16.dp),
        contentAlignment=androidx.compose.ui.Alignment.Center) {
        Text(if(game.saveError) "保存未成功，下次操作会重试。" else text,maxLines=3,
            style=PicoTheme.typography.bodyLarge.copy(fontSize=44.sp,lineHeight=58.sp),
            color=PicoTheme.colorScheme.labelPrimary)
    }
}

@Composable
private fun PlayEcho(event: PlayEvent?) {
    val alpha=remember { Animatable(0f) }
    LaunchedEffect(event?.sequence) {
        alpha.snapTo(if(event==null) 0f else 1f)
        if(event!=null) { delay(250);alpha.animateTo(0f,tween(750)) }
    }
    Box(Modifier.size(420.dp,84.dp).alpha(alpha.value),contentAlignment=androidx.compose.ui.Alignment.Center) {
        Text(when(event?.kind) {
            Furnishing.Woodfish->"功德 +1"
            Furnishing.MusicBox->"♪  旋律 +1"
            Furnishing.SingingBowl->"清音 +1"
            else->event?.feedback ?: ""
        },style=PicoTheme.typography.titleLarge.copy(fontSize=42.sp,lineHeight=56.sp),
            color=PicoTheme.colorScheme.labelPrimary)
    }
}
