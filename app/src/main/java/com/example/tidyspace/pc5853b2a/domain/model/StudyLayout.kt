package com.example.tidyspace.pc5853b2a.domain.model

/** Authored in the former annex, with no room scale or layout-version migration. */
fun studyCatalogue()=listOf(
    RoomSpec("studydesk","黄铜抽屉写字桌",Furnishing.StudyDesk,Point3(.60f,.35f,.29f),Point3(-.72f,-.145f,1.105f)),
    RoomSpec("studychair","藤背软垫木椅",Furnishing.StudyChair,Point3(.225f,.335f,.23f),Point3(-.72f,-.1525f,1.43f)),
    RoomSpec("studybookcase","拱顶藏书柜",Furnishing.StudyBookcase,Point3(.49f,.72f,.16f),Point3(-1.43265f,.04f,1.23f)),
    RoomSpec("studyarmchair","燕麦布艺阅读椅",Furnishing.StudyArmchair,Point3(.30f,.34f,.30f),Point3(-1.17f,-.144f,1.875f)),
    RoomSpec("studystool","织物阅读脚凳",Furnishing.StudyStool,Point3(.20f,.145f,.18f),Point3(-1.17f,-.2415f,2.145f)),
    RoomSpec("studyside","双层圆边茶几",Furnishing.StudySide,Point3(.16f,.21f,.16f),Point3(-.87f,-.209f,1.925f)),
    RoomSpec("studybanker","绿玻璃阅读台灯",Furnishing.StudyLight,Point3(.125f,.18f,.085f),Point3(-.925f,.12f,1.06f)),
    RoomSpec("studybook","摊开的书与钢笔",Furnishing.StudyDecor,Point3(.17f,.018f,.105f),Point3(-.72f,.039f,1.14f)),
    RoomSpec("studyteaset","陶瓷茶杯与茶点",Furnishing.StudyDecor,Point3(.105f,.075f,.095f),Point3(-.87f,-.0665f,1.925f)),
    RoomSpec("studyfern","编织盆蕨叶绿植",Furnishing.StudyPlant,Point3(.15f,.27f,.15f),Point3(-.35f,-.185f,2.105f)),
    RoomSpec("studyrug","流苏羊毛阅读毯",Furnishing.StudyRug,Point3(.69f,.006f,.69f),Point3(-1.13f,-.317f,1.90f)),
    RoomSpec("studyglobe","黄铜小地球仪",Furnishing.StudyDecor,Point3(.095f,.15f,.095f),Point3(-.515f,.105f,1.06f)),
    RoomSpec("studyfloorlamp","褶皱亚麻落地灯",Furnishing.StudyLight,Point3(.12f,.51f,.12f),Point3(-.51f,-.065f,1.84f)),
    RoomSpec("studybookends","雕花书挡与藏书",Furnishing.StudyDecor,Point3(.17f,.135f,.095f),Point3(-1.43265f,.0725f,1.23f)),
)

object StudyLayout {
    const val version=1
    val retiredAssets=setOf("washer","bathvanity","toilet","bathtub","mirror","laundrybasket")
    fun retired(spec: RoomSpec)=spec.assetId in retiredAssets || BathroomLayout.owns(spec.id)
    fun equipment()=studyCatalogue().map { spec ->
        val support=when(spec.id) {
            "studybanker","studybook","studyglobe"->"studydesk"
            "studyarmchair","studystool","studyside"->"studyrug"
            "studyteaset"->"studyside"
            "studybookends"->"studybookcase"
            else->null
        }
        RoomItem(spec,support=support,rotation=Rotation.yaw(when(spec.id) {
            "studychair"->180f
            "studybookcase","studybookends"->90f
            else->0f
        }).normalized())
    }
}
