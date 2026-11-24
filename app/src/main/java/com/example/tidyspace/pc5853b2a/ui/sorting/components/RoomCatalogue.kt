package com.example.tidyspace.pc5853b2a.ui.sorting.components

import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tidyspace.pc5853b2a.R
import com.example.tidyspace.pc5853b2a.domain.model.*
import com.example.tidyspace.pc5853b2a.domain.usecase.RoomCardDrop
import com.example.tidyspace.pc5853b2a.ui.sorting.SceneStatus
import com.example.tidyspace.pc5853b2a.ui.navigation.SpaceModeToggle
import com.example.tidyspace.pc5853b2a.ui.theme.ShowroomColors
import com.example.tidyspace.pc5853b2a.ui.theme.CottageColors
import com.pico.spatial.ui.design.*
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect
import com.pico.spatial.ui.input.pointer.position3d
import kotlin.math.ceil
import kotlinx.coroutines.CancellationException

/** App-owned reference artwork colors; the native PicoTheme ColorScheme stays unchanged. */
internal object CatalogueColors {
    val catalogPaper=CottageColors.paper
    val catalogGold=CottageColors.gold
    val catalogInk=CottageColors.ink
    val catalogTray=Color(0xBFE6D7BB) // design-style: fixed-figma-color reference screenshot translucent warm tray
    val catalogSelected=Color(0xFFFFE5A4) // design-style: fixed-figma-color reference screenshot warm selected card
}

internal enum class CatalogueCategory(val label: String,val artwork: String) {
    Seats("座椅","chair"),Tables("桌几","coffee"),Storage("收纳","shoecabinet"),
    Lights("灯具","tablelamp"),Decor("装饰","picture"),Toys("手把件","musicbox"),
    Plants("植物","plant");
    fun accepts(spec: RoomSpec): Boolean=when(this) {
        Seats -> spec.kind in setOf(Furnishing.Chair,Furnishing.Sofa,Furnishing.Ottoman,Furnishing.GardenBench,
            Furnishing.Bed,Furnishing.DiningChair,Furnishing.GamingChair,Furnishing.StudyChair,
            Furnishing.StudyArmchair,Furnishing.StudyStool)
        Tables -> spec.kind in setOf(Furnishing.Desk,Furnishing.CoffeeTable,Furnishing.SideTable,
            Furnishing.DiningTable,Furnishing.GamingDesk,Furnishing.StudyDesk,Furnishing.StudySide)
        Storage -> spec.kind in setOf(Furnishing.Shelf,Furnishing.Cabinet,Furnishing.Bookcase,
            Furnishing.ShoeCabinet,Furnishing.CoatRack,Furnishing.Appliance,Furnishing.Sanitary,
            Furnishing.GamingStorage,Furnishing.StudyBookcase)
        Lights -> spec.kind in setOf(Furnishing.Lamp,Furnishing.TableLamp,Furnishing.GamingLight,Furnishing.StudyLight)
        Toys -> spec.kind.playable
        Plants -> spec.kind in setOf(Furnishing.Plant,Furnishing.TallPlant,Furnishing.StudyPlant)
        Decor -> spec.kind !in setOf(Furnishing.Crate,Furnishing.MovingBox) &&
            entries.filterNot { it==Decor }.none { it.accepts(spec) }
    }
}

internal class CardGestureCallbacks(
    val start: (RoomItem,Point3)->Long?,
    val move: (Long,Point3)->Unit,
    val end: (Long)->Unit,
    val cancel: (Long)->Unit,
    val prepare: suspend (List<RoomSpec>)->Unit={},
)

/** One in-window tray, with category and individual-card pages sharing the same layout. */
@Composable
internal fun RoomCatalogue(game: RoomGame,status: SceneStatus,send: (RoomAction)->Unit,
                           gestures: CardGestureCallbacks,retry: ()->Unit,onShowrooms: ()->Unit={},
                           onHomes: ()->Unit=onShowrooms,homeName: String="我的小屋") {
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var page by rememberSaveable { mutableIntStateOf(0) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var mode by rememberSaveable { mutableStateOf("布置") }
    val chosen=CatalogueCategory.entries.find { it.name==category }
    val inventory=remember(game.items,game.boxStock,game.archivedIds) { RoomCardDrop.inventory(game) }
    val items=remember(inventory,chosen) { inventory.filter { chosen?.accepts(it.spec)==true } }
    val pages=ceil(items.size/7.0).toInt().coerceAtLeast(1)
    val safePage=page.coerceIn(0,pages-1)
    val busy=game.cardDrag!=null || game.selectedId!=null
    val ready=status==SceneStatus.Ready && !busy
    val preview=remember(game.cardDrag,game.items,game.boxStock) { RoomCardDrop.preview(game) }
    val currentGestures by rememberUpdatedState(gestures)
    var preparedIds by remember { mutableStateOf(emptySet<String>()) }
    var preparationFailed by remember { mutableStateOf(false) }
    val currentPrepared by rememberUpdatedState(preparedIds)
    val pageItems=items.drop(safePage*7).take(7)
    val preparing=chosen!=null && pageItems.any { it.spec.id !in preparedIds }
    LaunchedEffect(chosen,safePage,status,pageItems.map { it.spec.id }) {
        preparationFailed=false
        if(status==SceneStatus.Ready && chosen!=null) {
            try {
                gestures.prepare(pageItems.map { it.spec })
                preparedIds=preparedIds+pageItems.map { it.spec.id }
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { preparationFailed=true;Log.e("TidyCard","page model preparation failed",e) }
        }
    }
    var panelOrigin by remember { mutableStateOf(Offset.Zero) }
    val ink=CatalogueColors.catalogInk
    val label=PicoTheme.typography.bodyLarge.copy(fontSize=24.sp,lineHeight=30.sp)
    LaunchedEffect(game.journalOpen) { if(game.journalOpen) mode="手札" }
    Column(Modifier.size(1440.dp,302.dp).onGloballyPositioned { panelOrigin=it.positionInRoot() }
        .testTag(if(status==SceneStatus.Ready) "room-ready" else "room-loading"),
        horizontalAlignment=Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().height(46.dp).padding(horizontal=208.dp),
            horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            Text(when {
                status==SceneStatus.Failed -> "房间未能打开"
                status==SceneStatus.Loading -> "正在打开小屋…"
                preview!=null -> preview.reason
                preparationFailed -> "物品未能取出，请重试"
                preparing -> "正在取出这一页的物品…"
                chosen!=null -> "${chosen.label} · 捏住即拿起模型，拖入房间，松手放置"
                game.saveError -> "$homeName · 尚未保存"
                else -> "$homeName · 已保存"
            },color=ink,style=label,maxLines=1,modifier=Modifier.weight(1f))
            if(status==SceneStatus.Failed || preparationFailed) Button(onClick=retry) { Text("重试") }
            if(chosen!=null && mode=="布置") {
                Button(onClick={ page=(safePage-1+pages)%pages },enabled=ready && pages>1) { Text("上一页") }
                Text("${safePage+1} / $pages",color=ink,style=label,modifier=Modifier.padding(horizontal=12.dp))
                Button(onClick={ page=(safePage+1)%pages },enabled=ready && pages>1) { Text("下一页") }
            }
        }
        Row(Modifier.fillMaxWidth().height(194.dp)
            .background(CatalogueColors.catalogTray,RoundedCornerShape(32.dp))
            .padding(horizontal=20.dp,vertical=8.dp),
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(18.dp)) {
            Column(Modifier.width(176.dp),horizontalAlignment=Alignment.CenterHorizontally,
                verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Button(onClick=onHomes,enabled=ready,
                    modifier=Modifier.size(142.dp,80.dp).testTag("room-homes")
                        .semantics { contentDescription="我的小屋，管理独立保存的小屋" },
                    shape=RoundedCornerShape(24.dp),contentPadding=PaddingValues(8.dp),
                    colors=ButtonDefaults.buttonColors(containerColor=ShowroomColors.roomPaper,
                        contentColor=ShowroomColors.roomInk)) {
                    Text("我的小屋",style=PicoTheme.typography.titleMedium.copy(fontSize=22.sp,lineHeight=28.sp))
                }
                Button(onClick=onShowrooms,enabled=ready,
                    modifier=Modifier.size(142.dp,80.dp).testTag("room-showrooms")
                        .semantics { contentDescription="样板间，选择模板新建小屋" },
                    shape=RoundedCornerShape(24.dp),contentPadding=PaddingValues(8.dp),
                    colors=ButtonDefaults.buttonColors(containerColor=ShowroomColors.roomPaper,
                        contentColor=ShowroomColors.roomInk)) {
                    Text("样板间",style=PicoTheme.typography.titleMedium.copy(fontSize=22.sp,lineHeight=28.sp))
                }
            }
            when {
                mode=="灯光" -> Row(Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(24.dp)) {
                    RoomMood.entries.forEach { mood -> Button(onClick={ send(RoomAction.Mood(mood)) },enabled=ready) {
                        Text(mood.label,style=label)
                    } }
                    Button(onClick={ send(RoomAction.Brightness((game.indoorBrightness-.2f).coerceAtLeast(0f))) },enabled=ready) { Text("调暗") }
                    Text("${(game.indoorBrightness*100).toInt()}%",color=ink,style=label)
                    Button(onClick={ send(RoomAction.Brightness((game.indoorBrightness+.2f).coerceAtMost(1f))) },enabled=ready) { Text("调亮") }
                }
                mode=="手札" -> Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text("${game.rank} · ${game.xp} 灵感",color=ink,style=PicoTheme.typography.titleLarge)
                    Text("库存 ${inventory.size} 件 · 已安放 ${game.items.count { !it.packed && it.spec.kind!=Furnishing.MovingBox }} 件",color=ink,style=label)
                    Text("放好后仍可直接搬动；双手水平转向。模型拖回底栏即可取消。",color=ink,style=label)
                    Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                        Button(onClick={ send(RoomAction.ToggleJournal) },enabled=ready) { Text("查看手札与委托") }
                        Button(onClick={ send(RoomAction.ToggleGuide) },enabled=ready) { Text(if(game.guideVisible) "收起提示" else "打开提示") }
                    }
                }
                chosen==null -> CatalogueCategory.entries.forEach { entry ->
                    ReferenceCard(entry.label,entry.artwork,false,{ category=entry.name;page=0;selected=null },
                        Modifier.weight(1f).fillMaxHeight().testTag("category-${entry.name}"),enabled=ready)
                }
                items.isEmpty() -> Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text("${chosen.label}已经全部安放",style=PicoTheme.typography.titleLarge,color=ink)
                    Text("直接拿起场景里的物品继续布置，或先收回物品。",style=label,color=ink)
                    Button(onClick={ category=null },enabled=ready) { Text("查看其他物品") }
                }
                else -> {
                    items.drop(safePage*7).take(7).forEach { item ->
                        key(item.spec.id) {
                            var origin by remember { mutableStateOf(Offset.Zero) }
                            val dragModifier=Modifier.onGloballyPositioned { origin=it.positionInRoot()-panelOrigin }
                                .pointerInput(item.spec.id) {
                                    var token: Long?=null
                                    try {
                                        awaitEachGesture {
                                            val down=awaitFirstDown(requireUnconsumed=false)
                                            if(status!=SceneStatus.Ready || item.spec.id !in currentPrepared)
                                                return@awaitEachGesture
                                            val point=Point3(origin.x+down.position.x,origin.y+down.position.y,0f)
                                            val initialDepth=down.position3d().z
                                            selected=item.spec.id
                                            token=currentGestures.start(item,point)
                                            val active=token ?: return@awaitEachGesture
                                            down.consume()
                                            while(true) {
                                                val event=awaitPointerEvent()
                                                val change=event.changes.find { it.id==down.id }
                                                if(change==null || change.isConsumed) {
                                                    currentGestures.cancel(active);break
                                                }
                                                currentGestures.move(active,Point3(origin.x+change.position.x,
                                                    origin.y+change.position.y,change.position3d().z-initialDepth))
                                                change.consume()
                                                if(!change.pressed) { currentGestures.end(active);break }
                                            }
                                            token=null
                                        }
                                    } finally { token?.let { currentGestures.cancel(it) } }
                                }
                            ReferenceCard(item.spec.name,item.spec.assetId,selected==item.spec.id,
                                { selected=item.spec.id },Modifier.weight(1f).fillMaxHeight().then(dragModifier)
                                    .testTag("card-${item.spec.id}"),
                                enabled=status==SceneStatus.Ready && item.spec.id in preparedIds)
                        }
                    }
                    repeat(7-items.drop(safePage*7).take(7).size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        Row(Modifier.padding(top=10.dp).height(48.dp)
            .background(CatalogueColors.catalogTray,RoundedCornerShape(28.dp)).padding(horizontal=12.dp),
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            if(chosen!=null) IconButton(onClick={ category=null;page=0 },enabled=ready,
                colors=IconButtonDefaults.iconButtonColors(
                    containerColor=CatalogueColors.catalogPaper,contentColor=ink)) {
                Icon(painterResource(R.drawable.ic_arrow_left),contentDescription="返回分类",tint=ink)
            }
            listOf("布置","灯光","手札").forEach { name ->
                Button(onClick={ mode=name },enabled=ready,
                    colors=ButtonDefaults.buttonColors(containerColor=if(mode==name) CatalogueColors.catalogPaper else Color.Transparent,
                        contentColor=ink),contentPadding=PaddingValues(horizontal=20.dp,vertical=8.dp)) { Text(name,style=label) }
            }
            Button(onClick={ send(RoomAction.Undo) },enabled=ready && game.canUndo) { Text("撤销") }
            Button(onClick={ send(RoomAction.Redo) },enabled=ready && game.canRedo) { Text("重做") }
            if(game.focusId!=null) Button(onClick={ send(RoomAction.Recycle) },enabled=ready) { Text("收回") }
            SpaceModeToggle()
        }
    }
}

/** Custom because a stock Card cannot own the continuous 2D→3D drag lifecycle.
 * Double rims are the user's explicit reference-card artwork, confined to these cards. */
@Composable
private fun ReferenceCard(label: String,asset: String,selected: Boolean,onClick: ()->Unit,
                          modifier: Modifier=Modifier,enabled: Boolean=true) {
    Box(modifier.clip(RoundedCornerShape(22.dp)).spatialHoverEffect(enabled=enabled)
        .clickable(enabled=enabled,onClick=onClick)
        .background(if(selected) CatalogueColors.catalogSelected else CatalogueColors.catalogPaper)) {
        Canvas(Modifier.fillMaxSize()) {
            val rim=CatalogueColors.catalogGold
            drawRoundRect(rim,Offset(2.dp.toPx(),2.dp.toPx()),
                Size(size.width-4.dp.toPx(),size.height-4.dp.toPx()),CornerRadius(20.dp.toPx()),style=Stroke(3.dp.toPx()))
            drawRoundRect(rim,Offset(8.dp.toPx(),8.dp.toPx()),
                Size(size.width-16.dp.toPx(),size.height-16.dp.toPx()),CornerRadius(14.dp.toPx()),style=Stroke(1.dp.toPx()))
        }
        Column(Modifier.fillMaxSize().padding(12.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            CatalogueArtwork(asset,Modifier.weight(1f).fillMaxWidth().padding(4.dp))
            Text(label,style=PicoTheme.typography.bodyLarge.copy(fontSize=22.sp,lineHeight=27.sp),
                color=CatalogueColors.catalogInk,maxLines=2,modifier=Modifier.height(54.dp))
        }
    }
}

@SuppressLint("DiscouragedApi")
@Composable
internal fun CatalogueArtwork(asset: String,modifier: Modifier=Modifier) {
    val context=LocalContext.current
    val id=remember(asset) { context.resources.getIdentifier(
        "furniture_"+asset.replace('-','_'),"drawable",context.packageName) }
    if(id!=0) Image(painterResource(id),contentDescription=null,modifier=modifier)
}
