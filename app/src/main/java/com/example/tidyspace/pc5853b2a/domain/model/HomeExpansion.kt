package com.example.tidyspace.pc5853b2a.domain.model

private fun extra(id: String,name: String,kind: Furnishing,x: Float,y: Float,z: Float)=
    RoomSpec(id,name,kind,Point3(x,y,z),Point3(0f,0f,0f))

fun homeExpansion()=listOf(
    extra("bed","橡木双人床",Furnishing.Bed,.52f,.28f,.68f),
    extra("wardrobe","百叶双门衣柜",Furnishing.Cabinet,.32f,.60f,.22f),
    extra("dresser","三抽斗柜",Furnishing.Cabinet,.34f,.28f,.18f),
    extra("diningtable","椭圆餐桌",Furnishing.DiningTable,.42f,.30f,.30f),
    extra("diningchair","藤编餐椅",Furnishing.DiningChair,.17f,.28f,.18f),
    extra("tvconsole","胡桃木电视柜",Furnishing.Cabinet,.46f,.20f,.16f),
    extra("television","复古小电视",Furnishing.Decor,.30f,.23f,.075f),
    extra("bookcase","落地高书柜",Furnishing.Bookcase,.26f,.52f,.13f),
    extra("sink","水槽操作台",Furnishing.Appliance,.34f,.31f,.22f),
    extra("stove","珐琅灶烤箱",Furnishing.Appliance,.25f,.31f,.23f),
    extra("fridge","奶油色冰箱",Furnishing.Appliance,.22f,.46f,.21f),
    extra("washer","滚筒洗衣机",Furnishing.Appliance,.24f,.29f,.24f),
    extra("bathvanity","洗漱台柜",Furnishing.Sanitary,.28f,.30f,.20f),
    extra("toilet","陶瓷马桶",Furnishing.Sanitary,.18f,.23f,.26f),
    extra("bathtub","独立浴缸",Furnishing.Sanitary,.48f,.20f,.26f),
    extra("mirror","黄铜拱形镜",Furnishing.WallMirror,.20f,.30f,.015f),
    extra("laundrybasket","藤编洗衣篮",Furnishing.Decor,.16f,.19f,.14f),
    extra("tallplant","落地龟背竹",Furnishing.TallPlant,.18f,.38f,.18f),
    extra("teapot","青釉茶壶",Furnishing.Decor,.115f,.09f,.095f),
    extra("fruitbowl","盛果陶盘",Furnishing.Decor,.15f,.07f,.12f),
    extra("crockery","餐盘与餐具",Furnishing.Decor,.13f,.065f,.10f),
    extra("alarmclock","双铃闹钟",Furnishing.Decor,.10f,.095f,.04f),
    extra("books","旧书一摞",Furnishing.Decor,.12f,.07f,.085f),
    extra("vase","细颈枝条花瓶",Furnishing.Decor,.075f,.16f,.075f),
)
fun handheldExpansion()=listOf(
    extra("slider","磁吸滑块",Furnishing.MagneticSlider,.15f,.045f,.065f),
    extra("togglebox","三联拨片盒",Furnishing.ToggleBox,.15f,.065f,.085f),
    extra("abacus","掌心小算盘",Furnishing.Abacus,.16f,.11f,.055f),
    extra("bell","黄铜按铃",Furnishing.DeskBell,.095f,.085f,.095f),
    extra("hourglass","翻转沙漏",Furnishing.Hourglass,.10f,.16f,.065f),
    extra("windmill","手摇风车",Furnishing.Windmill,.14f,.19f,.10f),
)
fun RoomSpec.inventorySection(): String=when {
    assetId.startsWith("study")->"书房"
    id.startsWith("gaming")->"电竞房"
    kind.playable->"手把件"
    assetId in setOf("bed","wardrobe","dresser","alarmclock")->"卧室"
    assetId in setOf("diningtable","diningchair","sink","stove","fridge","teapot","fruitbowl","crockery")->"餐厨"
    assetId in setOf("washer","bathvanity","toilet","bathtub","mirror","laundrybasket")->"卫浴"
    kind in setOf(Furnishing.Decor,Furnishing.Plant,Furnishing.TallPlant,Furnishing.Picture,
        Furnishing.Mug,Furnishing.Book,Furnishing.Rug,Furnishing.Blanket,Furnishing.Cushion)->"生活小物"
    else->"客厅"
}
