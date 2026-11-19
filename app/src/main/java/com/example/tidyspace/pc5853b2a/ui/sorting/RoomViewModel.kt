package com.example.tidyspace.pc5853b2a.ui.sorting

import androidx.lifecycle.ViewModel
import com.example.tidyspace.pc5853b2a.domain.model.*
import com.example.tidyspace.pc5853b2a.domain.usecase.RoomUseCase
import com.example.tidyspace.pc5853b2a.domain.usecase.RoomProgress
import com.example.tidyspace.pc5853b2a.domain.usecase.CleaningRules
import com.example.tidyspace.pc5853b2a.data.repository.RoomPersistence
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.example.tidyspace.pc5853b2a.domain.usecase.ShowroomUseCase
import com.example.tidyspace.pc5853b2a.domain.usecase.HomeUseCase

class RoomViewModel(initial: RoomGame,initialShowrooms: ShowroomSession,
    private val saveAll: ((RoomGame,ShowroomSession)->Unit)?,
    private val saveGame: (RoomGame)->Unit,
    private val homeRules: HomeUseCase?): ViewModel() {
    constructor(initial: RoomGame,initialShowrooms: ShowroomSession,
        saveAll: ((RoomGame,ShowroomSession)->Unit)?,saveGame: (RoomGame)->Unit):
        this(initial,initialShowrooms,saveAll,saveGame,null)
    constructor(initial: RoomGame,saveGame: (RoomGame)->Unit):this(initial,ShowroomSession(),null,saveGame)
    constructor(snapshot: String?=null,save: (String)->Unit={}):this(
        RoomPersistence.decode(snapshot) ?: RoomGame(cleaning=CleaningState(),
            message=CleaningRules.hint(CleaningState())),
        { game -> save(RoomPersistence.encode(game)) })
    private val rules=RoomUseCase()
    private val mutable=MutableStateFlow(RoomUiState(
        RoomProgress.settle(initial).copy(celebration=0)))
    val state=mutable.asStateFlow()
    /** Gesture callbacks read the reducer result immediately, before Compose collects it. */
    val currentGame: RoomGame get()=mutable.value.game
    private val showroomMutable=MutableStateFlow(initialShowrooms)
    val showroomSession=showroomMutable.asStateFlow()
    private val showroomRules=ShowroomUseCase()
    private val undo=ArrayDeque<RoomGame>()
    private val redo=ArrayDeque<RoomGame>()
    private var grabStart: RoomGame?=null
    private val homesMutable=MutableStateFlow(homeRules?.library)
    val homes=homesMutable.asStateFlow()
    val deletedHomeName get()=homeRules?.undoName
    private fun persist(game: RoomGame,session: ShowroomSession=showroomMutable.value) {
        if(homeRules!=null) homesMutable.value=homeRules.save(game,session)
        else if(saveAll!=null) saveAll.invoke(game,session) else saveGame(game)
    }
    private fun homeOperation(changeGame: Boolean,operation: (HomeUseCase)->HomeLibrary): String? {
        val h=homeRules ?: return "多小屋存储暂时不可用。"
        return runCatching {
            val library=operation(h)
            homesMutable.value=library
            if(changeGame) {
                undo.clear();redo.clear();grabStart=null
                showroomMutable.value=library.active.session
                mutable.value=RoomUiState(h.activeGame().copy(saveError=false,canUndo=false,canRedo=false,
                    message="已进入${library.active.name}，每次布置都会独立保存。"))
            } else mutable.value=RoomUiState(currentGame.copy(saveError=false))
        }.exceptionOrNull()?.message
    }
    fun switchHome(id: String)=homeOperation(true) { it.switch(id,currentGame,showroomMutable.value) }
    fun createHome(name: String,template: Showroom?=null)=homeOperation(true) {
        it.create(name,template,currentGame,showroomMutable.value)
    }
    fun copyHome(id: String)=homeOperation(true) { it.duplicate(id,currentGame,showroomMutable.value) }
    fun renameHome(id: String,name: String)=homeOperation(false) {
        it.rename(id,name,currentGame,showroomMutable.value)
    }.also { if(it==null) showroomMutable.value=homeRules!!.library.active.session }
    fun deleteHome(id: String)=homeOperation(false) { it.delete(id,currentGame,showroomMutable.value) }
    fun undoDeleteHome()=homeOperation(false) { it.undoDelete(currentGame,showroomMutable.value) }
    fun retryHomeSave(): String? {
        if(homeRules?.loadError==true) return homeOperation(true) { it.retryRead() }
        return runCatching { persist(currentGame);mutable.value=RoomUiState(currentGame.copy(saveError=false)) }
            .exceptionOrNull()?.message
    }
    private fun showroomCommit(next: RoomGame,session: ShowroomSession): Boolean {
        if(runCatching { persist(next,session) }.isFailure) return false
        mutable.value=RoomUiState(next.copy(canUndo=undo.isNotEmpty(),canRedo=redo.isNotEmpty(),saveError=false))
        showroomMutable.value=session
        return true
    }
    fun applyShowroom(room: Showroom): String? {
        if(homeRules!=null) return createHome("我的${room.title}",room)
        val before=currentGame
        val result=showroomRules.apply(before,room)
        val next=result.game ?: return result.error
        if(!showroomCommit(next,showroomMutable.value.copy(reference=room.id,undo=before,tutorial=null,step=0)))
            return "保存失败，原小屋保持原样，请重试。"
        undo.addLast(before);if(undo.size>40) undo.removeFirst()
        redo.clear()
        mutable.value=RoomUiState(next.copy(canUndo=true,canRedo=false))
        return null
    }
    fun undoShowroom(): String? {
        if(currentGame.selectedId!=null || currentGame.cardDrag!=null) return "请先放下物品。"
        val previous=showroomMutable.value.undo ?: return "没有可以撤回的样板间。"
        val next=showroomRules.restore(currentGame,previous)
        if(!showroomCommit(next,showroomMutable.value.copy(undo=null,tutorial=null,step=0)))
            return "保存失败，请重试。"
        undo.clear();redo.clear()
        mutable.value=RoomUiState(next.copy(canUndo=false,canRedo=false))
        return null
    }
    fun updateShowroomSession(session: ShowroomSession): Boolean = showroomCommit(currentGame,session)
    fun onUiEvent(event: RoomEvent) { when(event) { is RoomEvent.Act->onEvent(event.action) } }
    fun onEvent(action: RoomAction) {
        val current=mutable.value.game
        if(action==RoomAction.End && current.selectedId in setOf("@house:undo","@house:redo")) {
            val id=current.selectedId
            val tap=current.ritual?.let { it.travel<.016f && it.turn<.07f }==true
            mutable.value=RoomUiState(rules.reduce(current,RoomAction.End))
            grabStart=null
            if(tap) onEvent(if(id=="@house:undo") RoomAction.Undo else RoomAction.Redo)
            return
        }
        if(action is RoomAction.Begin) {
            grabStart=if(current.selectedId==null) current else rules.reduce(current,RoomAction.Cancel)
        }
        val commit=action==RoomAction.End || action is RoomAction.Add || action==RoomAction.Duplicate ||
            action is RoomAction.CardRelease ||
            action==RoomAction.Recycle || action is RoomAction.Mood || action is RoomAction.Brightness ||
            action is RoomAction.RotatePlaced || action==RoomAction.CleanAgain
        var next=when(action) {
            RoomAction.Undo -> history(current,undo,redo,"撤销了上一步。已获得的成就和灵感仍然保留。")
            RoomAction.Redo -> history(current,redo,undo,"重做完成，继续你的设计。")
            else -> rules.reduce(current,action)
        }
        if(commit) {
            val before=if(action==RoomAction.End) grabStart ?: current else current.copy(cardDrag=null)
            if(layout(before)!=layout(next)) {
                undo.addLast(before);if(undo.size>40) undo.removeFirst()
                redo.clear()
            }
        }
        if(action==RoomAction.End || action==RoomAction.Cancel) grabStart=null
        if(commit || action==RoomAction.Cancel || action==RoomAction.Undo || action==RoomAction.Redo || action==RoomAction.ToggleGuide) {
            val saved=runCatching { persist(next) }.isSuccess
            next=next.copy(saveError=!saved)
        }
        mutable.value=RoomUiState(next.copy(canUndo=undo.isNotEmpty(),canRedo=redo.isNotEmpty()))
    }
    private fun layout(s: RoomGame)=listOf(s.items,s.crateOpen,s.lampOn,s.drawerOpen,s.roomYaw,s.mood,s.boxStock,s.indoorBrightness,s.dayHour)
    private fun history(s: RoomGame,from: ArrayDeque<RoomGame>,to: ArrayDeque<RoomGame>,message: String): RoomGame {
        if(s.selectedId!=null || s.cardDrag!=null || from.isEmpty()) return s
        to.addLast(s)
        val old=from.removeLast()
        return RoomProgress.settle(old.copy(selectedId=null,origin=null,mechanismOrigin=null,
            achievements=s.achievements,celebration=s.celebration,nextSerial=s.nextSerial,
            additions=s.additions,guideVisible=s.guideVisible,playCounts=s.playCounts,toyStates=s.toyStates,
            toyGesture=ToyGesture(),cleaning=s.cleaning,journalOpen=s.journalOpen,controlsVisible=s.controlsVisible,
            angleFocus=s.angleFocus?.takeIf { id -> old.items.any { it.spec.id==id && !it.packed } },ritual=null,
            playSequence=s.playSequence,playEvent=s.playEvent,cardDrag=null,cardSequence=s.cardSequence,message=message))
    }
}
