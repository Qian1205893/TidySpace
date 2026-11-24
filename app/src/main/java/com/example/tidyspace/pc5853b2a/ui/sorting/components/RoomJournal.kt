package com.example.tidyspace.pc5853b2a.ui.sorting.components

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tidyspace.pc5853b2a.domain.model.*
import com.example.tidyspace.pc5853b2a.domain.usecase.RoomProgress
import com.pico.spatial.ui.design.*

/** Optional reading pages, opened by the real notebook beside the entrance. */
@Composable
internal fun RoomJournal(game: RoomGame,send: (RoomAction)->Unit,onSorting: ()->Unit,
                         embedded: Boolean=false,onBack: (() -> Unit)?=null) {
    var tab by rememberSaveable { mutableStateOf("手记") }
    var page by rememberSaveable { mutableIntStateOf(0) }
    val tasks=remember(game) { RoomProgress.commissions(game)+RoomProgress.discoveries(game) }
    val guides=remember(game) { RoomProgress.guide(game) }
    val current=guides.firstOrNull { it.id !in game.achievements }
    val body=PicoTheme.typography.bodyLarge
    val small=PicoTheme.typography.bodyMedium
    val heading=PicoTheme.typography.titleLarge
    val ink=PicoTheme.colorScheme.labelPrimary
    val secondary=PicoTheme.colorScheme.labelSecondary
    val ready=game.selectedId==null
    @Composable fun action(label: String,enabled: Boolean=ready,click: ()->Unit) {
        Button(onClick=click,enabled=enabled,
            colors=ButtonDefaults.buttonColors(containerColor=PicoTheme.colorScheme.fillSecondary,
                contentColor=PicoTheme.colorScheme.labelPrimary)) { Text(label,style=small) }
    }
    val surface=if(embedded) Modifier.fillMaxSize() else
        Modifier.size(570.dp,780.dp)
    Column(surface.padding(if(embedded) 4.dp else 26.dp),
        verticalArrangement=Arrangement.spacedBy(16.dp)) {
        if(!embedded) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            Column {
                Text("小屋手札",style=heading,color=ink)
                Text("${game.rank} · ${game.xp} 灵感",style=small,color=secondary)
            }
            action(if(embedded) "返回" else "合上") { onBack?.invoke() ?: send(RoomAction.ToggleJournal) }
        }
        SegmentControl(Modifier.fillMaxWidth()) {
            listOf("手记","委托","收藏").forEach { name ->
                SegmentItem(selected=tab==name,onClick={ tab=name;page=0 },modifier=Modifier.weight(1f),
                    textStyle=body,title={ Text(name) })
            }
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement=Arrangement.spacedBy(14.dp)) {
            when(tab) {
                "手记"-> {
                    Text(current?.title ?: "把家过成喜欢的样子",style=body,color=ink)
                    if(current!=null && game.guideVisible) {
                        current.instructions.forEach { Text(it,style=body,color=ink) }
                    } else Text("点箱子，提起上方的实物。\n单手捏住物件，再加一只手水平转向。\n双手在空处捏合，像转托盘一样转动房子。",style=body,color=ink)
                    Text("拨动后墙时钟，窗外走过一天。\n钟下旋钮调亮暗，轻点开关灯。\n手札下的回转箭头可以撤销、重做。",style=small,color=secondary)
                    Text("入口还剩 ${game.remainingGoods} 件，慢慢安家。",style=small,color=secondary)
                    action(if(game.guideVisible) "收起提示" else "继续提示") { send(RoomAction.ToggleGuide) }
                }
                "委托"-> {
                    val pages=(tasks.size+1)/2
                    tasks.drop((page%pages)*2).take(2).forEach { task ->
                        Text("${if(task.id in game.achievements) "✓" else "○"} ${task.title}",style=body,color=ink)
                        Text(task.hint,style=body,color=ink)
                        Text("${task.done} / ${task.total}",style=small,color=secondary)
                    }
                    action("翻一页  ${page%pages+1} / $pages",true) { page=(page+1)%pages }
                }
                else-> {
                    val medals=guides.map { it.id to it.title }+tasks.map { it.id to it.title }
                    Text("已收藏 ${game.achievements.size} 个美好瞬间",style=body,color=ink)
                    medals.drop(page*5).take(5).forEach { (id,title) ->
                        Text("${if(id in game.achievements) "✓" else "○"} $title",style=body,color=ink)
                    }
                    action("翻一页",true) { page=(page+1)%((medals.size+4)/5) }
                }
            }
        }
        val focused=game.items.find { it.spec.id==game.focusId && !it.packed && it.spec.kind!=Furnishing.MovingBox }
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            action("重新装箱",ready && focused!=null) { send(RoomAction.Recycle) }
            action("收纳挑战",ready,click=onSorting)
        }
        if(!embedded) Text(if(game.saveError) "保存未成功，下次操作会重试。" else "自动保存 · 随时回来玩",style=small,color=secondary)
    }
}
