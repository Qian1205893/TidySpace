package com.example.tidyspace.pc5853b2a.domain.usecase

import com.example.tidyspace.pc5853b2a.domain.model.*
import kotlin.math.hypot

/** Goals inspect relationships, never a prescribed grid or transient hand pose. */
object RoomProgress {
    private fun near(a: RoomItem,b: RoomItem,d: Float)=
        hypot(a.position.x-b.position.x,a.position.z-b.position.z)<d
    fun green(i: RoomItem,expanded: Boolean=false): Boolean {
        val scale=if(expanded) RoomLayout.scale else 1f
        return !i.packed && i.support!="crate" &&
            ((i.position.x in -.89f*scale..-.10f*scale && i.position.z<-.32f*scale) ||
                (i.position.x<-.65f*scale && i.position.z in -.68f*scale..0f))
    }

    fun commissions(s: RoomGame): List<RoomTask> {
        val visible=s.items.filter { !it.packed }
        fun of(k: Furnishing)=visible.filter { it.spec.kind==k }
        fun supported(i: RoomItem,k: Furnishing)=visible.any { it.spec.id==i.support && it.spec.kind==k }
        val tea=of(Furnishing.CoffeeTable).maxOfOrNull { table ->
            of(Furnishing.Chair).count { near(it,table,.50f) }.coerceAtMost(2)+
                of(Furnishing.Mug).count { it.support==table.spec.id }.coerceAtMost(2)
        } ?: 0
        val library=of(Furnishing.Book).count { supported(it,Furnishing.Shelf) }.coerceAtMost(3)
        val lounge=of(Furnishing.Sofa).maxOfOrNull { sofa ->
            of(Furnishing.Cushion).count { it.support==sofa.spec.id }.coerceAtMost(2)+
                (if(of(Furnishing.CoffeeTable).any { near(it,sofa,.40f) }) 1 else 0)+
                (if(s.lampOn && of(Furnishing.Lamp).any { near(it,sofa,.45f) }) 1 else 0)
        } ?: 0
        val corners=RoomUseCase().goals(s).count { it.complete }
        return listOf(
            RoomTask("tea",if(s.movingHome) "独享茶时" else "双人茶叙",if(s.movingHome) "椅子靠近矮桌，桌上放一只茶杯。" else "同一张矮桌旁放两把椅子，桌上摆两只茶杯。",tea,if(s.movingHome) 2 else 4),
            RoomTask("garden","植物之家",if(s.movingHome) "让唯一的绿植在窗旁安家。" else "让三盆绿植离开箱子，在两扇窗旁安家。",of(Furnishing.Plant).count { green(it,s.layoutVersion==RoomLayout.version) }.coerceAtMost(3),if(s.movingHome) 1 else 3),
            RoomTask("gallery","私人画廊",if(s.movingHome) "把喜欢的画挂上完整墙面。" else "在完整墙面挂三幅画，避开窗户。",of(Furnishing.Picture).count { it.support in setOf("@back","@left") }.coerceAtMost(3),if(s.movingHome) 1 else 3),
            RoomTask("library","书香空间",if(s.movingHome) "把旅行手账放上书架。" else "将三本书摆在书架的任意层板上。",library,if(s.movingHome) 1 else 3),
            RoomTask("lounge","柔软客厅",if(s.movingHome) "靠垫放上沙发，让矮桌与暖灯靠近。" else "同一张沙发放两个靠垫，靠近矮桌与亮着的灯。",lounge,if(s.movingHome) 3 else 4),
            RoomTask("home","完整生活","同时完成书桌、暖光阅读角和窗边绿意。",corners,3),
        )
    }

    fun guide(s: RoomGame): List<GuideStep> {
        fun i(id: String)=s.item(id)
        fun on(a: String,b: String)=i(a).support==b
        val desk=i("desk").position;val sofa=i("sofa").position
        fun step(n: Int,title: String,target: String?,destination: Point3?,done: Boolean,
                 a: String,b: String,c: String)=GuideStep("guide-$n",title,listOf(a,b,c),target,destination,done)
        return listOf(
            step(1,"搬进新家",if(s.movingHome) "box-home" else "crate:lid",null,if(s.movingHome) s.previewBox!=null || s.additions>0 else s.crateOpen,
                if(s.movingHome) "看向入户平台上的纸箱。" else "看向闪动的搬家箱盖。",
                if(s.movingHome) "点一下箱子，下一件物品会出现在上方。" else "捏住盖子，向上掀开，再松手。",
                if(s.movingHome) "捏起预览物件，放稳后箱子会空一点。" else "箱内四件小物都可以直接取出。"),
            step(2,"第一抹绿","plant",if(s.layoutVersion==RoomLayout.version) RoomLayout.point(Point3(-.80f,-.24f,-.73f)) else Point3(-.80f,-.24f,-.73f),green(i("plant"),s.layoutVersion==RoomLayout.version),
                if(s.movingHome) "点入口的生活箱，捏起上方盆栽。" else "捏住箱内盆栽，抬高离开木箱。","移到任意一扇窗旁；光环是参考位置。","松手放下，绿植会稳稳落地或落在桌面。"),
            step(3,"留一本好书","book",desk+Point3(-.11f,.18f,0f),on("book","desk"),
                "从箱里捏起旅行手账。","举到书桌上方，避开桌上其他物件。","松手后，移动书桌也会带着这本书。"),
            step(4,"为自己泡杯茶","mug",desk+Point3(.09f,.205f,0f),on("mug","desk"),
                "捏住茶杯，向上取出。","移到书桌右侧，和手账留些空隙。","保持杯底高于桌面，再松手。"),
            step(5,"坐下来","chair",desk+Point3(0f,-.025f,.29f),near(i("chair"),i("desk"),.34f),
                "捏住弧背椅的椅背或座面。","搬到书桌前，椅子和桌沿留一点距离。","转动手腕调整朝向，再松手。"),
            step(6,"柔软的角落","cushion",sofa+Point3(0f,.011f,.02f),on("cushion","sofa"),
                "从箱子取出靠垫。","放到沙发座面中央，避开扶手和靠背。","松手，让沙发成为更舒服的角落。"),
            step(7,"点亮夜读","lamp",sofa+Point3(.31f,.135f,.15f),
                s.lampOn && near(i("lamp"),i("sofa"),.42f),
                "先把落地灯搬到沙发侧边。","再捏住灯罩旁的短拉绳，松手点亮。","再拉一次就能关灯；家具仍可随意挪动。"),
            step(8,"留出伸手距离","coffee",sofa+Point3(0f,-.06f,.34f),near(i("coffee"),i("sofa"),.39f),
                "捏住矮桌桌面，搬到沙发前。","保持能伸手拿到茶杯的距离。","不要与沙发重叠；光环帮助你找位置。"),
            step(9,"你的第一件添置",null,null,s.additions>0,
                if(s.movingHome) "入口还有生活与快乐箱，挑一件新物品。" else "打开「家具库」，挑一件喜欢的物品。",
                if(s.movingHome) "点箱子，再捏起上方的物品。" else "点击添置，它会出现在房间的空位。",
                if(s.movingHome) "每件物品都是独一份，箱子拆空就会消失。" else "捏住新物件自由摆放；同款可以反复添置。"),
            step(10,"换一种心情",null,null,s.mood!=RoomMood.Morning,
                "捏住后墙时钟，沿着表盘拨动指针。","观察窗外、日光与炉火带来的变化。","转动钟下旋钮调亮暗，轻点可以开关暖灯。"),
            step(11,"自己做设计",null,null,commissions(s).any { it.complete },
                "轻点门边手札，翻到「委托」，找一个感兴趣的目标。","根据承托或距离组合物件，没有唯一摆法。","每项首次完成获得 100 灵感，收入永久成就册。"),
        )
    }
    fun current(s: RoomGame)=guide(s).firstOrNull { it.id !in s.achievements }

    fun discoveries(s: RoomGame): List<RoomTask> {
        fun played(prefix: String)=s.playCounts.filterKeys { it==prefix || it.startsWith("$prefix~") }.values.sum()
        val trio=listOf("woodfish","musicbox","bowl").count { played(it)>0 }
        val stacked=s.items.any { item ->
            val parent=s.items.find { it.spec.id==item.support }
            parent!=null && s.items.any { it.spec.id==parent.support } && !item.packed && !parent.packed
        }
        return listOf(
            RoomTask("zen","心静一刻","敲响木鱼十次，留一段自己的节奏。",played("woodfish").coerceAtMost(10).toInt(),10),
            RoomTask("melody","手心的旋律","转动音乐盒曲柄，累计奏出八个音符。",played("musicbox").coerceAtMost(8).toInt(),8),
            RoomTask("trio","小屋合奏","分别演奏木鱼、音乐盒与清音钵。",trio,3),
            RoomTask("stack","叠放的巧思","尝试桌上放书，再在书上稳稳摆好一个小物。",if(stacked) 1 else 0,1),
        )
    }
    fun settle(s: RoomGame): RoomGame {
        if(!s.decorating) return s
        val earned=guide(s).filter { it.complete }.map { it.id }+
            (commissions(s)+discoveries(s)).filter { it.complete }.map { it.id }
        val fresh=earned.toSet()-s.achievements
        if(fresh.isEmpty()) return s
        val reward=fresh.count { it.startsWith("guide-") }*30+fresh.count { !it.startsWith("guide-") }*100
        val names=guide(s).filter { it.id in fresh }.map { it.title }+
            (commissions(s)+discoveries(s)).filter { it.id in fresh }.map { it.title }
        return s.copy(achievements=s.achievements+fresh,celebration=s.celebration+1,
            message="达成「${names.joinToString(" · ")}」！ +$reward 灵感 · 成就已收藏")
    }
}
