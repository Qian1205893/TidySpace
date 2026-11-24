package com.example.tidyspace.pc5853b2a.ui.showrooms.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tidyspace.pc5853b2a.domain.model.*
import com.example.tidyspace.pc5853b2a.domain.usecase.ShowroomUseCase
import com.example.tidyspace.pc5853b2a.ui.showrooms.*
import com.example.tidyspace.pc5853b2a.ui.theme.ShowroomColors
import com.pico.spatial.ui.design.*
import com.pico.spatial.ui.design.windows.AlertDialog
import com.pico.spatial.ui.design.R as SuiR

@Composable
internal fun ShowroomContent(s: ShowroomUiState,game: RoomGame,session: ShowroomSession,
    send: (ShowroomEvent)->Unit,onClose: ()->Unit,modifier: Modifier=Modifier,
    library: HomeLibrary?=null,undoName: String?=null) {
    if(library!=null && s.page in setOf(ShowroomPage.Homes,ShowroomPage.NewHome,
        ShowroomPage.Manage,ShowroomPage.DeleteConfirm,ShowroomPage.SaveError)) {
        HomeContent(s,game,library,undoName,send,onClose,modifier)
        return
    }
    CottagePanel(modifier,"showroom-panel") {
        CottageHeader(if(s.page==ShowroomPage.Gallery) "样板间" else s.room?.title ?: "我的小屋",
            false,send,"showroom-close",onClose)
        if(s.message.isNotBlank()) Text(s.message,style=referenceBody(),
            color=ShowroomColors.roomAccent,modifier=Modifier.testTag("showroom-message"))
        when(s.page) {
            ShowroomPage.Gallery -> {
                Text("挑一套喜欢的布置，开始一间新小屋",style=homeBody(),color=ShowroomColors.roomMuted)
                ShowroomGallery(s,send,Modifier.weight(1f))
            }
            ShowroomPage.Detail -> s.room?.let { Detail(it,game,send,Modifier.weight(1f)) }
            ShowroomPage.Confirm -> s.room?.let { Confirm(it,game,send) }
            ShowroomPage.Applied -> s.room?.let { Applied(it,session,send,onClose,Modifier.weight(1f)) }
            ShowroomPage.Steps -> s.room?.let { Steps(it,game,session,send,onClose,Modifier.weight(1f)) }
            ShowroomPage.Creator -> Creator(s,game,send,Modifier.weight(1f))
            else -> Unit
        }
    }
}

@Composable
private fun Detail(r: Showroom,game: RoomGame,send: (ShowroomEvent)->Unit,modifier: Modifier) {
    Column(modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        CottageCard(Modifier.weight(1f).fillMaxWidth().testTag("showroom-detail")) {
            Artwork("${r.id}-panorama",r.title+"整体全景",Modifier.fillMaxSize().padding(16.dp))
        }
        Text(r.brief,color=ShowroomColors.roomMuted,style=referenceBody())
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            HomeButton("返回","showroom-back") { send(ShowroomEvent.Home) }
            Spacer(Modifier.weight(1f))
            HomeButton("以此新建小屋","showroom-create-home",true) {
                send(ShowroomEvent.CreateTemplateHome)
            }
        }
    }
}

@Composable
private fun Confirm(r: Showroom,game: RoomGame,send: (ShowroomEvent)->Unit) {
    val result=ShowroomUseCase().apply(game,r)
    AlertDialog(onDismissRequest={ send(ShowroomEvent.Detail) },title={ Text("套用 ${r.title}") },
        content={ Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text("现有物品会按这套布局重新摆放，其余收回库存。")
            Text("当前小屋会自动保存，可随时撤回。进度和成就保留。")
            if(result.error!=null) Text(result.error,modifier=Modifier.testTag("showroom-preflight-status"))
        } },buttons={ Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            Button(onClick={ send(ShowroomEvent.Detail) },colors=referenceButtonColors(),
                modifier=Modifier.testTag("showroom-cancel")) { Text("返回预览") }
            Button(onClick={ send(ShowroomEvent.Apply) },enabled=result.game!=null,colors=referenceButtonColors(primary=true),
                modifier=Modifier.testTag("showroom-apply")) { Text("确认套用") }
        } })
}

@Composable
private fun Applied(r: Showroom,session: ShowroomSession,send: (ShowroomEvent)->Unit,onClose: ()->Unit,modifier: Modifier) {
    Column(modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Artwork("${r.id}-panorama",r.title,Modifier.weight(1f).fillMaxWidth())
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Button(onClick={ send(ShowroomEvent.Undo) },enabled=session.undo!=null,colors=referenceButtonColors(),
                modifier=Modifier.testTag("showroom-undo")) { Text("撤回套用",style=referenceBody()) }
            Spacer(Modifier.weight(1f))
            Text("已选好，可以自由摆放",style=referenceCaption(),color=ShowroomColors.roomMuted)
            Button(onClick=onClose,colors=referenceButtonColors(primary=true)) { Text("回到小屋",style=referenceBody()) }
        }
    }
}

@Composable
private fun Steps(r: Showroom,game: RoomGame,session: ShowroomSession,send: (ShowroomEvent)->Unit,onClose: ()->Unit,modifier: Modifier) {
    val index=session.step.coerceIn(0,r.steps.lastIndex)
    val step=r.steps[index]
    val pending=ShowroomUseCase().pending(game,r,index)
    Column(modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Text("第 ${index+1} / ${r.steps.size} 步 · ${step.title}",style=referenceTitle(),color=ShowroomColors.roomInk)
        Text(step.hint,style=referenceBody(),color=ShowroomColors.roomMuted)
        Row(Modifier.fillMaxWidth().weight(1f),horizontalArrangement=Arrangement.spacedBy(28.dp)) {
            Artwork("${r.id}-panorama",r.title+"搭建参考",Modifier.width(730.dp).fillMaxHeight())
            LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                item { Text(if(pending.isEmpty()) "本步摆放已就绪" else "还需摆放 ${pending.size} 件",
                    style=referenceTitle(),color=ShowroomColors.roomAccent) }
                items(r.targets.filter { it.asset in step.assets }) { target ->
                    Text("${if(target in pending) "待摆放" else "已就位"} · ${name(target.asset)}\n${target.zone}"+
                        (target.support?.let { "\n放在${name(it)}上" } ?: "\n放在地面"),
                        style=referenceBody(),color=ShowroomColors.roomInk)
                }
                item { Text("收起灵感册后可自由拖动；点「我的小屋」随时继续。",
                    style=referenceCaption(),color=ShowroomColors.roomMuted) }
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Button(onClick={ send(ShowroomEvent.Previous) },enabled=index>0,colors=referenceButtonColors()) { Text("上一步",style=referenceBody()) }
            Spacer(Modifier.weight(1f))
            Button(onClick=onClose,colors=referenceButtonColors()) { Text("收起继续搭建",style=referenceBody()) }
            Button(onClick={ send(ShowroomEvent.Next) },colors=referenceButtonColors(primary=true),modifier=Modifier.testTag("showroom-next")) {
                Text(if(index==r.steps.lastIndex) "完成并记录改造" else "检查并进入下一步",style=referenceBody())
            }
        }
    }
}

@Composable
private fun Creator(s: ShowroomUiState,game: RoomGame,send: (ShowroomEvent)->Unit,modifier: Modifier) {
    Column(modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            item { Text("记录自己的小屋",style=referenceTitle(),color=ShowroomColors.roomInk) }
            item {
                Text("作品名称",style=referenceBody(),color=ShowroomColors.roomInk)
                TextField(value=s.title,onValueChange={ send(ShowroomEvent.Title(it)) },placeholder={ Text("作品名称") },
                    modifier=Modifier.fillMaxWidth().padding(top=8.dp).testTag("showroom-title"),colors=referenceFieldColors())
            }
            item {
                Text("我改了什么",style=referenceBody(),color=ShowroomColors.roomInk)
                TextArea(value=s.note,onValueChange={ send(ShowroomEvent.Note(it)) },placeholder={ Text("我改了什么") },
                    modifier=Modifier.fillMaxWidth().padding(top=8.dp).height(140.dp),colors=referenceFieldColors())
            }
            item {
                val changes=s.room?.let { ShowroomUseCase().changed(game,it) }.orEmpty()
                Text(s.room?.let { "参考来源 · ${it.title}" } ?: "自由创作 · 当前小屋",style=referenceBody(),color=ShowroomColors.roomAccent)
                Text(if(changes.isEmpty()) "移动、替换或收回喜欢的物品，留下自己的想法。" else
                    "与你选中的参考不同的物品：${changes.take(16).joinToString("、")}${if(changes.size>16) "…" else ""}",
                    style=referenceBody(),color=ShowroomColors.roomMuted,modifier=Modifier.padding(top=12.dp))
            }
            item { Text("分享包含当前摆放俯视图、物品清单和改造说明。",style=referenceCaption(),color=ShowroomColors.roomMuted) }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Button(onClick={ send(ShowroomEvent.Detail) },colors=referenceButtonColors(),
                modifier=Modifier.testTag("showroom-back-reference")) { Text("回看参考",style=referenceBody()) }
            Spacer(Modifier.weight(1f))
            Button(onClick={ send(ShowroomEvent.Save) },colors=referenceButtonColors(),modifier=Modifier.testTag("showroom-save")) { Text("保存草稿",style=referenceBody()) }
            Button(onClick={ send(ShowroomEvent.Export) },colors=referenceButtonColors(primary=true),modifier=Modifier.testTag("showroom-share")) { Text("分享我的改造",style=referenceBody()) }
        }
    }
}

private fun name(asset: String)=uniqueCatalogue().find { it.assetId==asset }?.name ?: asset
