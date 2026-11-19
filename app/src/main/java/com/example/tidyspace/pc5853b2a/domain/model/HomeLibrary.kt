package com.example.tidyspace.pc5853b2a.domain.model

/** A stable work identity. Furniture IDs are local to this home. */
data class SavedHome(
    val id: String,
    val name: String,
    val game: RoomGame,
    val session: ShowroomSession=ShowroomSession(),
    val savedAt: Long=0,
    val revision: Long=1,
    val sourceTemplateId: String?=null,
    val sourceTemplateVersion: Int?=null,
)

data class HomeLibrary(
    val homes: List<SavedHome>,
    val activeId: String,
    val achievements: Set<String> = emptySet(),
    val playCounts: Map<String,Long> = emptyMap(),
    val nextSerial: Int=1,
    val tutorialComplete: Boolean=false,
) {
    val active get()=homes.first { it.id==activeId }
}
