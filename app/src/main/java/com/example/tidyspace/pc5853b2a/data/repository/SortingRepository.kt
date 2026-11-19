package com.example.tidyspace.pc5853b2a.data.repository

import com.example.tidyspace.pc5853b2a.domain.model.*

interface SortingRepository {
    fun items(): List<ItemSpec>
    fun levels(): List<SortingLevel> = listOf(SortingLevel("desk","文具桌面",false,items()))
}

// Generated from accepted .scratch/design-spec.json revision 3. All dimensions in meters.
class LocalSortingRepository: SortingRepository {
    override fun items() = levels().first().items
    override fun levels() = listOf(
        SortingLevel("desk","文具桌面",false,listOf(
            ItemSpec("notebook","手账本","coralObject",Point3(0.15f,0.025f,0.105f),Point3(-0.3f,0.030000000000000002f,-0.045f),Point3(-0.3f,0.0325f,-0.16f),90.0f,true,SortingSurface.Table),
            ItemSpec("pencil","铅笔","goldObject",Point3(0.18f,0.015f,0.02f),Point3(-0.02f,0.025f,-0.045f),Point3(-0.02f,0.0275f,-0.16f),90.0f,true,SortingSurface.Table),
            ItemSpec("eraser","橡皮","paperCream",Point3(0.065f,0.03f,0.042f),Point3(0.27f,0.0325f,-0.045f),Point3(0.27f,0.035f,-0.16f),90.0f,true,SortingSurface.Table),
            ItemSpec("tape","纸胶带","mintObject",Point3(0.09f,0.035f,0.09f),Point3(-0.3f,0.035f,0.13f),Point3(-0.3f,0.037500000000000006f,-0.035f),90.0f,false,SortingSurface.Table),
            ItemSpec("key","钥匙","goldObject",Point3(0.105f,0.014f,0.045f),Point3(-0.02f,0.0245f,0.13f),Point3(-0.02f,0.027f,-0.035f),90.0f,true,SortingSurface.Table),
            ItemSpec("clip","长尾夹","skyObject",Point3(0.07f,0.035f,0.052f),Point3(0.27f,0.035f,0.13f),Point3(0.27f,0.037500000000000006f,-0.035f),90.0f,true,SortingSurface.Table),
        )),
        SortingLevel("vanity","梳妆台",false,listOf(
            ItemSpec("bottle","香水瓶","mintObject",Point3(0.08f,0.09f,0.06f),Point3(-0.3f,0.0625f,-0.045f),Point3(-0.3f,0.065f,-0.16f),90.0f,false,SortingSurface.Table),
            ItemSpec("brush","梳子","coralObject",Point3(0.14f,0.025f,0.065f),Point3(-0.02f,0.030000000000000002f,-0.045f),Point3(-0.02f,0.0325f,-0.16f),90.0f,true,SortingSurface.Table),
            ItemSpec("lipstick","口红","coralObject",Point3(0.085f,0.028f,0.03f),Point3(0.27f,0.0315f,-0.045f),Point3(0.27f,0.034f,-0.16f),90.0f,true,SortingSurface.Table),
            ItemSpec("jar","面霜罐","paperCream",Point3(0.08f,0.055f,0.08f),Point3(-0.3f,0.045f,0.13f),Point3(-0.3f,0.0475f,-0.035f),90.0f,false,SortingSurface.Table),
            ItemSpec("mirror","随身镜","goldObject",Point3(0.12f,0.018f,0.08f),Point3(-0.02f,0.026500000000000003f,0.13f),Point3(-0.02f,0.028999999999999998f,-0.035f),90.0f,true,SortingSurface.Table),
            ItemSpec("pouch","化妆包","skyObject",Point3(0.135f,0.05f,0.08f),Point3(0.27f,0.0425f,0.13f),Point3(0.27f,0.045f,-0.035f),90.0f,true,SortingSurface.Table),
        )),
        SortingLevel("kitchen","厨房料理台",false,listOf(
            ItemSpec("mug","马克杯","skyObject",Point3(0.1f,0.09f,0.075f),Point3(-0.3f,0.0625f,-0.045f),Point3(-0.3f,0.065f,-0.16f),90.0f,false,SortingSurface.Table),
            ItemSpec("spoon","汤匙","goldObject",Point3(0.15f,0.018f,0.04f),Point3(-0.02f,0.026500000000000003f,-0.045f),Point3(-0.02f,0.028999999999999998f,-0.16f),90.0f,true,SortingSurface.Table),
            ItemSpec("spatula","锅铲","mintObject",Point3(0.17f,0.025f,0.055f),Point3(0.27f,0.030000000000000002f,-0.045f),Point3(0.27f,0.0325f,-0.16f),90.0f,true,SortingSurface.Table),
            ItemSpec("jar","调料罐","paperCream",Point3(0.075f,0.065f,0.075f),Point3(-0.3f,0.05f,0.13f),Point3(-0.3f,0.052500000000000005f,-0.035f),90.0f,false,SortingSurface.Table),
            ItemSpec("board","砧板","coralObject",Point3(0.15f,0.018f,0.11f),Point3(-0.02f,0.026500000000000003f,0.13f),Point3(-0.02f,0.028999999999999998f,-0.035f),90.0f,true,SortingSurface.Table),
            ItemSpec("whisk","打蛋器","goldObject",Point3(0.16f,0.03f,0.04f),Point3(0.27f,0.0325f,0.13f),Point3(0.27f,0.035f,-0.035f),90.0f,true,SortingSurface.Table),
        )),
        SortingLevel("tools","工具收纳墙",true,listOf(
            ItemSpec("hammer","锤子","coralObject",Point3(0.145f,0.03f,0.075f),Point3(-0.3f,0.0325f,-0.045f),Point3(-0.3f,0.42f,-0.185f),90.0f,true,SortingSurface.Wall),
            ItemSpec("wrench","扳手","goldObject",Point3(0.15f,0.022f,0.048f),Point3(-0.02f,0.0285f,-0.045f),Point3(-0.02f,0.42f,-0.189f),90.0f,true,SortingSurface.Wall),
            ItemSpec("screwdriver","螺丝刀","mintObject",Point3(0.16f,0.025f,0.035f),Point3(0.27f,0.030000000000000002f,-0.045f),Point3(0.27f,0.42f,-0.1875f),90.0f,true,SortingSurface.Wall),
            ItemSpec("scissors","剪刀","skyObject",Point3(0.125f,0.02f,0.06f),Point3(-0.3f,0.027500000000000004f,0.13f),Point3(-0.3f,0.2f,-0.19f),90.0f,true,SortingSurface.Wall),
            ItemSpec("tape","胶带卷","mintObject",Point3(0.09f,0.035f,0.09f),Point3(-0.02f,0.035f,0.13f),Point3(-0.02f,0.2f,-0.1825f),90.0f,false,SortingSurface.Wall),
            ItemSpec("brush","清洁刷","paperCream",Point3(0.14f,0.025f,0.065f),Point3(0.27f,0.030000000000000002f,0.13f),Point3(0.27f,0.2f,-0.1875f),90.0f,true,SortingSurface.Wall),
        )),
        SortingLevel("entry","玄关挂墙",true,listOf(
            ItemSpec("key","家门钥匙","goldObject",Point3(0.105f,0.014f,0.045f),Point3(-0.3f,0.0245f,-0.045f),Point3(-0.3f,0.42f,-0.193f),90.0f,true,SortingSurface.Wall),
            ItemSpec("glasses","眼镜","skyObject",Point3(0.14f,0.03f,0.055f),Point3(-0.02f,0.0325f,-0.045f),Point3(-0.02f,0.42f,-0.185f),90.0f,true,SortingSurface.Wall),
            ItemSpec("pouch","随身小包","coralObject",Point3(0.135f,0.05f,0.08f),Point3(0.27f,0.0425f,-0.045f),Point3(0.27f,0.42f,-0.17500000000000002f),90.0f,true,SortingSurface.Wall),
            ItemSpec("notebook","出门手账","mintObject",Point3(0.15f,0.025f,0.105f),Point3(-0.3f,0.030000000000000002f,0.13f),Point3(-0.3f,0.2f,-0.1875f),90.0f,true,SortingSurface.Wall),
            ItemSpec("cap","帽子","paperCream",Point3(0.125f,0.06f,0.09f),Point3(-0.02f,0.0475f,0.13f),Point3(-0.02f,0.2f,-0.17f),90.0f,true,SortingSurface.Wall),
            ItemSpec("umbrella","折叠伞","skyObject",Point3(0.165f,0.04f,0.045f),Point3(0.27f,0.037500000000000006f,0.13f),Point3(0.27f,0.2f,-0.18000000000000002f),90.0f,true,SortingSurface.Wall),
        )),
    )
}
