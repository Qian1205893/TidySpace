package com.example.tidyspace.pc5853b2a.ui.showrooms.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tidyspace.pc5853b2a.domain.model.*
import com.example.tidyspace.pc5853b2a.ui.showrooms.*
import com.example.tidyspace.pc5853b2a.ui.theme.HomeColors
import com.example.tidyspace.pc5853b2a.ui.theme.CottageColors
import com.pico.spatial.ui.design.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun HomeContent(s: ShowroomUiState,game: RoomGame,library: HomeLibrary,
    undoName: String?,send: (ShowroomEvent)->Unit,close: ()->Unit,modifier: Modifier) {
    val selected=library.homes.find { it.id==s.selectedHome } ?: library.active
    CottagePanel(modifier,"home-library-panel") {
        CottageHeader(when(s.page) {
                ShowroomPage.NewHome->"新建小屋";ShowroomPage.Manage,ShowroomPage.DeleteConfirm->"管理小屋"
                ShowroomPage.SaveError->"暂时没有保存成功";else->"我的小屋"
            },true,send,"home-close",onClose={
                if(s.page in setOf(ShowroomPage.Manage,ShowroomPage.NewHome)) send(ShowroomEvent.MyHomes) else close()
            },newAction=s.page==ShowroomPage.Homes)
        Column(Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement=Arrangement.spacedBy(16.dp)) {
            if(s.message.isNotBlank()) Text(s.message,color=HomeColors.homeError,style=homeBody(),
                modifier=Modifier.testTag("home-message"))
            when(s.page) {
                ShowroomPage.Homes -> {
                    Text("${library.homes.size} 间小屋 · 选择一间，继续布置你的生活",color=HomeColors.homeMuted,style=homeBody())
                    if(game.saveError) Row(horizontalArrangement=Arrangement.spacedBy(16.dp),
                        verticalAlignment=Alignment.CenterVertically) {
                        Text("尚未保存",color=HomeColors.homeError,style=homeBody())
                        HomeButton("重试保存","home-retry-save",true) { send(ShowroomEvent.Retry) }
                    }
                    HomeGallery(library,send,Modifier.weight(1f))
                    if(undoName!=null) Row(verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                        Text("已删除「$undoName」",color=HomeColors.homeMuted,style=homeBody())
                        HomeButton("撤销删除","home-undo-delete") { send(ShowroomEvent.UndoDeleteHome) }
                    }
                }
                ShowroomPage.NewHome -> NewHomeForm(s,library,send,Modifier.weight(1f))
                ShowroomPage.Manage,ShowroomPage.DeleteConfirm -> {
                    Row(Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(32.dp),
                        verticalAlignment=Alignment.CenterVertically) {
                        CottageCard(Modifier.weight(1f).height(450.dp)) {
                            HomeArtwork(selected,Modifier.fillMaxSize().padding(20.dp))
                        }
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                            Text(selected.name,style=homeDisplay(),color=HomeColors.homeInk)
                            val canDelete=selected.id!=library.activeId && library.homes.size>1
                            if(s.page==ShowroomPage.DeleteConfirm) {
                                Text("确认删除这间小屋？",style=homeTitle(),color=HomeColors.homeError)
                                Text("删除后可在我的小屋内撤销。",style=homeBody(),color=HomeColors.homeMuted)
                                HomeButton("取消","home-delete-cancel",modifier=Modifier.fillMaxWidth()) {
                                    send(ShowroomEvent.CancelDeleteHome)
                                }
                                HomeButton("确认删除","home-delete-confirm",true,danger=true,
                                    modifier=Modifier.fillMaxWidth()) { send(ShowroomEvent.ConfirmDeleteHome) }
                            } else {
                                HomeNameField(s.homeName,send,"home-rename-name")
                                HomeButton("保存名称","home-rename",true,modifier=Modifier.fillMaxWidth()) {
                                    send(ShowroomEvent.RenameHome)
                                }
                                HomeButton("复制一份","home-copy",modifier=Modifier.fillMaxWidth()) {
                                    send(ShowroomEvent.CopyHome)
                                }
                                HomeButton("删除小屋","home-delete",danger=true,enabled=canDelete,
                                    modifier=Modifier.fillMaxWidth()) { send(ShowroomEvent.DeleteHome) }
                                Text(if(canDelete) "删除前会再次确认。" else "请先进入另一间小屋，至少保留一间。",
                                    color=HomeColors.homeMuted,style=homeCaption())
                            }
                        }
                    }
                }
                ShowroomPage.SaveError -> Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(24.dp,Alignment.CenterVertically),
                    horizontalAlignment=Alignment.CenterHorizontally) {
                    CottageCard(Modifier.size(480.dp,300.dp)) {
                        HomeArtwork(library.active,Modifier.fillMaxSize().padding(20.dp))
                    }
                    Text("当前小屋和原始存档仍然保留。",color=HomeColors.homeMuted,style=homeBody())
                    Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                        HomeButton("重试","home-retry",true) { send(ShowroomEvent.Retry) }
                        HomeButton("留在当前小屋","home-stay",onClick=close)
                    }
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun NewHomeForm(s: ShowroomUiState,library: HomeLibrary,send: (ShowroomEvent)->Unit,
    modifier: Modifier) {
    Box(modifier.fillMaxWidth().testTag("home-new-body"),contentAlignment=Alignment.Center) {
        Column(Modifier.widthIn(max=480.dp).fillMaxWidth().verticalScroll(rememberScrollState())
            .testTag("home-new-form"),
            horizontalAlignment=Alignment.CenterHorizontally,
            verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text("留一间空白，慢慢布置。",style=homeDisplay(),color=HomeColors.homeInk,
                textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth())
            CottageCard(Modifier.size(280.dp,160.dp).testTag("home-new-preview")) {
                HomeArtwork(library.active.copy(name="空白小屋",game=library.active.game.copy(
                items=library.active.game.items.map { it.copy(packed=true) })),
                Modifier.fillMaxSize().padding(16.dp),centerBuilding=true)
            }
            Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                HomeNameField(s.homeName,send,"home-new-name")
            }
            Column(horizontalAlignment=Alignment.CenterHorizontally,
                verticalArrangement=Arrangement.spacedBy(8.dp)) {
                HomeButton("创建空白小屋","home-create-blank",true,modifier=Modifier.width(280.dp)) {
                    send(ShowroomEvent.CreateBlankHome)
                }
                HomeButton("从样板间开始","home-from-template",modifier=Modifier.width(280.dp)) {
                    send(ShowroomEvent.Home)
                }
            }
            Text("已解锁的家具会放进这间小屋的物品库。",color=HomeColors.homeMuted,
                style=homeBody(),textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun HomeGallery(library: HomeLibrary,send: (ShowroomEvent)->Unit,modifier: Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth().testTag("home-gallery")) {
        val columns=if(maxWidth<=896.dp) 2 else 3
        val cards: List<SavedHome?> = library.homes+listOf(null)
        LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(20.dp)) {
            items(cards.chunked(columns),key={ it.first()?.id ?: "create-home" }) { row ->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(20.dp)) {
                    row.forEach { h ->
                        if(h==null) {
                            CottageCard(Modifier.weight(1f).testTag("home-new-tile")) {
                                Column(Modifier.fillMaxWidth().padding(16.dp),
                                    verticalArrangement=Arrangement.spacedBy(8.dp),
                                    horizontalAlignment=Alignment.CenterHorizontally) {
                                    HomeArtwork(library.active.copy(name="新小屋",
                                        game=library.active.game.copy(items=library.active.game.items.map {
                                            it.copy(packed=true)
                                        })),Modifier.fillMaxWidth().height(190.dp),centerBuilding=true)
                                    Text("再添一间小屋",style=homeTitle(),color=HomeColors.homeInk)
                                    Text("空白小屋或喜欢的样板间",style=homeCaption(),color=HomeColors.homeMuted)
                                    HomeButton("新建小屋","home-new-tile-create",true,modifier=Modifier.fillMaxWidth()) {
                                        send(ShowroomEvent.NewHome)
                                    }
                                }
                            }
                        } else CottageCard(Modifier.weight(1f).testTag("home-card-${h.id}"),
                            selected=h.id==library.activeId) {
                            Column(Modifier.fillMaxWidth().padding(16.dp),
                                verticalArrangement=Arrangement.spacedBy(8.dp),
                                horizontalAlignment=Alignment.CenterHorizontally) {
                                HomeArtwork(h,Modifier.fillMaxWidth().height(190.dp))
                                Text(h.name,style=homeTitle(),color=HomeColors.homeInk,maxLines=1)
                                Text(if(h.id==library.activeId) "正在布置" else
                                    "保存于 "+SimpleDateFormat("M/d HH:mm",Locale.CHINA).format(Date(h.savedAt)),
                                    color=HomeColors.homeMuted,style=homeCaption())
                                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                    HomeButton(if(h.id==library.activeId) "继续布置" else "进入小屋",
                                        "home-enter-${h.id}",true,modifier=Modifier.weight(1f)) {
                                        send(ShowroomEvent.EnterHome(h.id))
                                    }
                                    HomeButton("管理","home-manage-${h.id}") { send(ShowroomEvent.ManageHome(h.id)) }
                                }
                            }
                        }
                    }
                    repeat(columns-row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
@Composable
private fun HomeNameField(value: String,send: (ShowroomEvent)->Unit,tag: String) {
    Text("小屋名称",style=homeBody(),color=HomeColors.homeInk)
    TextField(value=value,onValueChange={ send(ShowroomEvent.HomeName(it)) },
        modifier=Modifier.fillMaxWidth().testTag(tag),placeholder={ Text("给小屋取个名字") },
        colors=TextFieldDefaults.textFieldColors(textColor=HomeColors.homeInk,
            backgroundColor=HomeColors.homePaper,focusedColor=CottageColors.pink,placeholderColor=HomeColors.homeMuted))
    Text("1–20 个字，可随时修改",style=homeCaption(),color=HomeColors.homeMuted)
}
@Composable
internal fun HomeButton(label: String,tag: String,primary: Boolean=false,danger: Boolean=false,
    enabled: Boolean=true,modifier: Modifier=Modifier,selected: Boolean=false,onClick: ()->Unit) {
    Button(onClick=onClick,enabled=enabled,modifier=modifier.heightIn(min=56.dp).testTag(tag),
        shape=RoundedCornerShape(50.dp),contentPadding=PaddingValues(horizontal=20.dp,vertical=10.dp),
        colors=ButtonDefaults.buttonColors(
            containerColor=when { danger->CottageColors.pink;primary->CottageColors.green
                selected->CottageColors.pink;else->CottageColors.paper },
            contentColor=HomeColors.homeInk)) {
        Text(label,style=homeBody())
    }
}
@Composable internal fun homeDisplay()=PicoTheme.typography.displaySmall.copy(fontSize=32.sp,lineHeight=42.sp)
@Composable internal fun homeTitle()=PicoTheme.typography.titleLarge.copy(fontSize=22.sp,lineHeight=30.sp)
@Composable internal fun homeBody()=PicoTheme.typography.bodyLarge.copy(fontSize=20.sp,lineHeight=26.sp)
@Composable internal fun homeCaption()=PicoTheme.typography.bodyMedium.copy(fontSize=16.sp,lineHeight=22.sp)
