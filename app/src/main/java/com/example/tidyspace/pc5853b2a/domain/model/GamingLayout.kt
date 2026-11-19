package com.example.tidyspace.pc5853b2a.domain.model

/** New assets retain authored metres, centred pivots and semantic front +Z. */
fun gamingCatalogue()=listOf(
    RoomSpec("gamingdesk","暖木电竞桌",Furnishing.GamingDesk,Point3(.70f,.36f,.34f),Point3(.57f,-.14f,1.935f)),
    RoomSpec("gamingchair","灰蓝电竞椅",Furnishing.GamingChair,Point3(.26f,.43f,.29f),Point3(.57f,-.105f,2.30f)),
    RoomSpec("gamingpc","侧透游戏主机",Furnishing.GamingDevice,Point3(.145f,.34f,.31f),Point3(1.12f,-.15f,1.96f)),
    RoomSpec("gamingmonitor","超宽游戏显示器",Furnishing.GamingDevice,Point3(.39f,.22f,.075f),Point3(.57f,.15f,1.835f)),
    RoomSpec("gamingkeyboard","机械键盘",Furnishing.GamingDevice,Point3(.225f,.024f,.078f),Point3(.515f,.056f,2.013f)),
    RoomSpec("gamingmouse","轻量游戏鼠标",Furnishing.GamingDevice,Point3(.038f,.027f,.064f),Point3(.715f,.0575f,2.02f)),
    RoomSpec("gamingmat","缝边织物桌垫",Furnishing.GamingMat,Point3(.405f,.004f,.14f),Point3(.575f,.042f,2.018f)),
    RoomSpec("gamingheadset","耳机与木支架",Furnishing.GamingDevice,Point3(.083f,.16f,.075f),Point3(.84f,.12f,2.02f)),
    RoomSpec("gamingspeakers","书架音箱一套",Furnishing.GamingDevice,Point3(.555f,.10f,.069f),Point3(.57f,.09f,1.846f)),
    RoomSpec("gaminggamepad","手柄与充电座",Furnishing.GamingDevice,Point3(.098f,.072f,.067f),Point3(.29f,.076f,2.015f)),
    RoomSpec("gamingstorage","电竞收纳边柜",Furnishing.GamingStorage,Point3(.19f,.24f,.17f),Point3(1.45f,-.20f,2.08f)),
    RoomSpec("gaminglight","柔光电竞灯柱",Furnishing.GamingLight,Point3(.065f,.44f,.065f),Point3(1.40f,-.10f,2.36f)),
)

object GamingLayout {
    const val version=1
    const val left=BathroomLayout.right
    const val right=1.5747f
    const val back=.899f
    const val front=2.48f
    const val partitionBack=1.78f
    const val wallSupport="@gaming-right"
    fun equipment(): List<RoomItem> = gamingCatalogue().map { spec->
        val support=when(spec.id) {
            "gamingkeyboard","gamingmouse"->"gamingmat"
            "gamingmonitor","gamingmat","gamingheadset","gamingspeakers","gaminggamepad"->"gamingdesk"
            else->null
        }
        RoomItem(spec,rotation=Rotation.yaw(if(spec.id=="gamingchair") 180f else 0f),support=support)
    }
}
