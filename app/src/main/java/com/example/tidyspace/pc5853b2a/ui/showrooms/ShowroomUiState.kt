package com.example.tidyspace.pc5853b2a.ui.showrooms

import com.example.tidyspace.pc5853b2a.domain.model.Showroom

enum class ShowroomPage { Gallery, Detail, Confirm, Applied, Steps, Creator, Homes, NewHome, Manage, DeleteConfirm, SaveError }
data class ShowroomUiState(val rooms: List<Showroom> = emptyList(),val selected: String?=null,
    val page: ShowroomPage=ShowroomPage.Gallery,val tab: Int=0,val style: String="全部风格",
    val level: String="全部难度",val query: String="",val title: String="我的创意改造",val note: String="",
    val message: String="",val exported: String?=null,val selectedHome: String?=null,
    val homeName: String="我的新小屋") {
    val room get()=rooms.find { it.id==selected }
}
sealed interface ShowroomEvent {
    data object MyHomes: ShowroomEvent
    data object NewHome: ShowroomEvent
    data class HomeName(val value: String): ShowroomEvent
    data class EnterHome(val id: String): ShowroomEvent
    data class ManageHome(val id: String): ShowroomEvent
    data object RenameHome: ShowroomEvent
    data object CopyHome: ShowroomEvent
    data object DeleteHome: ShowroomEvent
    data object ConfirmDeleteHome: ShowroomEvent
    data object CancelDeleteHome: ShowroomEvent
    data object UndoDeleteHome: ShowroomEvent
    data object CreateBlankHome: ShowroomEvent
    data object CreateTemplateHome: ShowroomEvent
    data object Retry: ShowroomEvent
    data class Select(val id: String): ShowroomEvent
    data class Style(val value: String): ShowroomEvent
    data class Level(val value: String): ShowroomEvent
    data class Search(val value: String): ShowroomEvent
    data class Tab(val value: Int): ShowroomEvent
    data class Title(val value: String): ShowroomEvent
    data class Note(val value: String): ShowroomEvent
    data object Home: ShowroomEvent
    data object Detail: ShowroomEvent
    data object Preflight: ShowroomEvent
    data object Apply: ShowroomEvent
    data object Undo: ShowroomEvent
    data object Steps: ShowroomEvent
    data object Previous: ShowroomEvent
    data object Next: ShowroomEvent
    data object Create: ShowroomEvent
    data object Save: ShowroomEvent
    data object Export: ShowroomEvent
}
