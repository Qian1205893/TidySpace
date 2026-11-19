package com.example.tidyspace.pc5853b2a.domain.model

data class ShowroomTarget(val asset: String,val position: Point3,val yaw: Float,
    val support: String?,val zone: String)
data class ShowroomStep(val title: String,val hint: String,val assets: List<String>)
data class ShowroomDetail(val title: String,val image: String)
data class ShowroomVariant(val title: String,val remove: String,val add: String,val reason: String)
data class Showroom(val id: String,val title: String,val style: String,val difficulty: String,
    val brief: String,val feature: String,val challenge: String,val mood: RoomMood,
    val hour: Float,val brightness: Float,val targets: List<ShowroomTarget>,
    val steps: List<ShowroomStep>,val details: List<ShowroomDetail>,val variants: List<ShowroomVariant>,
    val version: Int=2)

/** Independent metadata; the room snapshot retains its existing persistence format. */
data class ShowroomSession(val reference: String?=null,val undo: RoomGame?=null,
    val tutorial: String?=null,val step: Int=0,val title: String="我的创意改造",val note: String="")
