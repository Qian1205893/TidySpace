package com.example.tidyspace.pc5853b2a.domain.model

enum class SpaceMode { Shared, Full }

data class SpaceModeState(
    val mode: SpaceMode=SpaceMode.Shared,
    val requested: SpaceMode=mode,
    val switching: Boolean=false,
    val recoveringWindow: Boolean=false,
    val error: String?=null,
    val revision: Long=0,
)
