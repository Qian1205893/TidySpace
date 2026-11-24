package com.example.tidyspace.pc5853b2a.ui.showrooms

import androidx.lifecycle.ViewModel
import com.example.tidyspace.pc5853b2a.domain.model.*
import com.example.tidyspace.pc5853b2a.domain.usecase.ShowroomUseCase
import com.example.tidyspace.pc5853b2a.ui.sorting.RoomViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

class ShowroomViewModel(rooms: List<Showroom>,private val room: RoomViewModel,
    initialPage: ShowroomPage=ShowroomPage.Gallery,
    private val export: (RoomGame,Showroom?,String,String)->String): ViewModel() {
    private val rules=ShowroomUseCase()
    private val mutable=MutableStateFlow(ShowroomUiState(rooms=rooms,page=initialPage,
        selected=room.showroomSession.value.tutorial ?: room.showroomSession.value.reference,
        title=room.showroomSession.value.title,note=room.showroomSession.value.note))
    val state=mutable.asStateFlow()
    private var pending: ShowroomEvent?=null
    /** Select the entry before its native window is attached, including after Applied/error pages. */
    fun open(personal: Boolean) {
        onEvent(if(personal) ShowroomEvent.MyHomes else ShowroomEvent.Home)
    }
    fun onEvent(e: ShowroomEvent) {
        val s=mutable.value
        val r=s.room
        var next=s.copy(message="")
        fun transaction(error: String?,success: ShowroomPage=ShowroomPage.Applied) {
            if(error==null) { next=next.copy(page=success);pending=null }
            else {
                val blocked=error.contains("放下") || error.contains("名称")
                next=next.copy(page=if(blocked) s.page else ShowroomPage.SaveError,message=error)
                pending=if(blocked) null else e
            }
        }
        fun persist(session: ShowroomSession): Boolean = room.updateShowroomSession(session).also {
            if(!it) next=next.copy(message="保存失败，请重试。")
        }
        when(e) {
            ShowroomEvent.MyHomes -> { pending=null;next=next.copy(page=ShowroomPage.Homes) }
            ShowroomEvent.NewHome -> { pending=null;next=next.copy(page=ShowroomPage.NewHome,homeName="我的新小屋") }
            is ShowroomEvent.HomeName -> next=next.copy(homeName=e.value)
            is ShowroomEvent.EnterHome -> transaction(room.switchHome(e.id))
            is ShowroomEvent.ManageHome -> room.homes.value?.homes?.find { it.id==e.id }?.let {
                next=next.copy(page=ShowroomPage.Manage,selectedHome=it.id,homeName=it.name)
            }
            ShowroomEvent.RenameHome -> s.selectedHome?.let {
                transaction(room.renameHome(it,s.homeName),ShowroomPage.Homes)
            }
            ShowroomEvent.CopyHome -> s.selectedHome?.let { transaction(room.copyHome(it)) }
            ShowroomEvent.DeleteHome -> s.selectedHome?.let { id ->
                val library=room.homes.value
                next=if(id==library?.activeId || library?.homes?.size==1) next.copy(
                    message="请先进入另一间小屋，至少保留一间自己的小屋。")
                else next.copy(page=ShowroomPage.DeleteConfirm)
            }
            ShowroomEvent.ConfirmDeleteHome -> s.selectedHome?.let {
                transaction(room.deleteHome(it),ShowroomPage.Homes)
            }
            ShowroomEvent.CancelDeleteHome -> next=next.copy(page=ShowroomPage.Manage)
            ShowroomEvent.UndoDeleteHome -> transaction(room.undoDeleteHome(),ShowroomPage.Homes)
            ShowroomEvent.CreateBlankHome -> transaction(room.createHome(s.homeName))
            ShowroomEvent.CreateTemplateHome -> r?.let { transaction(room.createHome("我的${it.title}",it)) }
            ShowroomEvent.Retry -> {
                val retry=pending
                if(retry!=null) { pending=null;onEvent(retry);return }
                transaction(room.retryHomeSave(),ShowroomPage.Homes)
            }
            is ShowroomEvent.Select -> if(s.rooms.any { it.id==e.id })
                next=next.copy(selected=e.id,page=ShowroomPage.Detail,tab=0)
            is ShowroomEvent.Style -> next=next.copy(style=e.value)
            is ShowroomEvent.Level -> next=next.copy(level=e.value)
            is ShowroomEvent.Search -> next=next.copy(query=e.value.take(60))
            is ShowroomEvent.Tab -> next=next.copy(tab=e.value.coerceIn(0,3))
            is ShowroomEvent.Title -> next=next.copy(title=e.value.take(40),exported=null)
            is ShowroomEvent.Note -> next=next.copy(note=e.value.take(240),exported=null)
            ShowroomEvent.Home -> { pending=null;next=next.copy(page=ShowroomPage.Gallery) }
            ShowroomEvent.Detail -> next=next.copy(page=if(r==null) ShowroomPage.Gallery else ShowroomPage.Detail)
            ShowroomEvent.Preflight -> if(r!=null) {
                val result=rules.apply(room.currentGame,r)
                next=next.copy(page=ShowroomPage.Confirm,message=result.error ?: "")
            }
            ShowroomEvent.Apply -> if(r!=null) {
                val error=room.applyShowroom(r)
                next=next.copy(page=if(error==null) ShowroomPage.Applied else ShowroomPage.Confirm,message=error ?: "")
            }
            ShowroomEvent.Undo -> {
                val error=room.undoShowroom()
                next=next.copy(page=if(error==null) ShowroomPage.Detail else s.page,message=error ?: "")
            }
            ShowroomEvent.Steps -> if(r!=null) {
                run {
                    val old=room.showroomSession.value
                    val session=old.copy(reference=r.id,tutorial=r.id,step=if(old.tutorial==r.id) old.step else 0)
                    if(persist(session)) next=next.copy(page=ShowroomPage.Steps)
                }
            }
            ShowroomEvent.Previous -> {
                val session=room.showroomSession.value
                persist(session.copy(step=(session.step-1).coerceAtLeast(0)))
            }
            ShowroomEvent.Next -> if(r!=null) {
                val session=room.showroomSession.value
                val game=room.currentGame
                val pending=rules.pending(game,r,session.step)
                when {
                    game.selectedId!=null || game.cardDrag!=null -> next=next.copy(message="放下手中的物品后再检查。")
                    pending.isNotEmpty() -> next=next.copy(message="还有 ${pending.size} 件需要对照预览摆放；可以收起参考册继续搭建。")
                    session.step==4 && (game.mood!=r.mood || abs(game.indoorBrightness-r.brightness)>.12f) ->
                        next=next.copy(message="在物品面板的灯光页选择${r.mood.label}，亮度调到约 ${(r.brightness*100).toInt()}%。")
                    session.step==5 && rules.changed(game,r).isEmpty() ->
                        next=next.copy(message="试着移动、替换一件物品，做出自己的改造后再完成。")
                    session.step>=r.steps.lastIndex -> if(persist(session.copy(tutorial=null,step=0)))
                        next=next.copy(page=ShowroomPage.Creator,message="搭建完成，给你的改造起个名字。")
                    else -> persist(session.copy(step=session.step+1))
                }
            }
            ShowroomEvent.Create -> next=next.copy(page=ShowroomPage.Creator)
            ShowroomEvent.Save,ShowroomEvent.Export -> {
                val game=room.currentGame
                if(game.selectedId!=null || game.cardDrag!=null) next=next.copy(message="请先放下物品，再保存作品。")
                else if(persist(room.showroomSession.value.copy(title=s.title.ifBlank { "我的创意改造" },note=s.note))) {
                    if(e==ShowroomEvent.Save) next=next.copy(message="作品名称和说明已保存，当前摆放会随游戏保存。")
                    else {
                        val result=runCatching { export(game,r,s.title.ifBlank { "我的创意改造" },s.note) }
                        next=if(result.isSuccess) next.copy(exported=result.getOrThrow(),message="作品已导出，分享窗口已打开。")
                        else next.copy(message="作品导出未完成，请重试。")
                    }
                }
            }
        }
        mutable.value=next
    }
}
