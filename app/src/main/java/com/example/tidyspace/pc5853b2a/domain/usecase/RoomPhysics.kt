package com.example.tidyspace.pc5853b2a.domain.usecase

import com.example.tidyspace.pc5853b2a.domain.model.*
import kotlin.math.*

/** Deterministic gravity/contact solver in room metres; no rendering or Android dependency. */
object RoomPhysics {
    data class Box(val center: Point3,val size: Point3)
    data class Surface(val center: Point3,val width: Float,val depth: Float,val ellipse: Boolean=false)
    data class Placement(val game: RoomGame?,val item: RoomItem?,val reason: String) {
        val valid get()=game!=null
    }
    private val up=Point3(0f,1f,0f)
    private const val tolerance=.0015f
    fun boxes(s: RoomGame,i: RoomItem): List<Box> = buildList {
        fun b(x: Float,y: Float,z: Float,px: Float=0f,py: Float=0f,pz: Float=0f) {
            add(Box(Point3(px,py,pz),Point3(x,y,z)))
        }
        if(LifestyleModels.contains(i.spec)) {
            val size=i.spec.size
            val seat=LifestyleModels.seat(i.spec)
            val top=(LifestyleModels.top(i.spec) ?: seat)?.y ?: (size.y/2)
            b(size.x,top+size.y/2,size.z,py=(top-size.y/2)/2)
            if(seat!=null && size.y/2-top>.025f) {
                // Seats and beds retain their high back while the usable sitting area stays open.
                val backDepth=(size.z/2+seat.z-seat.depth/2-.008f).coerceIn(.012f,size.z*.22f)
                b(size.x,size.y/2-top,backDepth,py=(top+size.y/2)/2,pz=-size.z/2+backDepth/2)
                if(i.spec.kind in setOf(Furnishing.Sofa,Furnishing.Chair)) {
                    val arm=((size.x-seat.width)/2-.004f).coerceAtLeast(.009f)
                    for(x in listOf(-size.x/2+arm/2,size.x/2-arm/2))
                        b(arm,(size.y/2-top)*.70f,size.z*.78f,px=x,py=top+(size.y/2-top)*.35f)
                }
            }
            return@buildList
        }
        when(i.spec.kind) {
            Furnishing.StudyDesk -> {
                b(.60f,.022f,.29f,py=.164f)
                b(.54f,.045f,.238f,py=.130f,pz=-.008f)
                for(x in listOf(-.257f,.257f)) for(z in listOf(-.111f,.111f))
                    b(.021f,.317f,.021f,x,-.0165f,z)
                b(.50f,.012f,.014f,py=-.10f,pz=-.111f)
            }
            Furnishing.StudyChair -> {
                b(.218f,.04f,.217f,py=-.021f,pz=.006f)
                b(.218f,.18f,.022f,py=.0775f,pz=-.096f)
                for(x in listOf(-.095f,.095f)) for(z in listOf(-.095f,.095f))
                    b(.022f,.135f,.022f,x,-.10f,z)
            }
            Furnishing.StudyBookcase -> {
                b(.49f,.032f,.16f,py=-.344f)
                b(.46f,.46f,.008f,py=-.09f,pz=-.076f)
                for(x in listOf(-.232f,.232f)) b(.026f,.48f,.16f,x,-.088f,0f)
                for(y in listOf(-.30f,-.175f,-.045f,.125f)) b(.448f,.020f,.148f,py=y)
                for(y in listOf(-.232f,-.107f)) b(.40f,.11f,.11f,py=y,pz=-.005f)
                b(.40f,.20f,.16f,py=.255f)
            }
            Furnishing.StudyArmchair -> {
                b(.278f,.09f,.27f,py=-.06f,pz=.005f)
                b(.268f,.245f,.070f,py=.045f,pz=-.113f)
                for(x in listOf(-.128f,.128f)) b(.044f,.108f,.242f,x,.012f,.015f)
                for(x in listOf(-.107f,.107f)) for(z in listOf(-.106f,.106f))
                    b(.024f,.068f,.024f,x,-.136f,z)
            }
            Furnishing.StudySide -> {
                b(.16f,.012f,.16f,py=.099f)
                b(.132f,.012f,.132f,py=-.041f)
                for(a in listOf(0f,2.094395f,4.18879f))
                    b(.008f,.20f,.008f,.053f*cos(a),-.005f,.053f*sin(a))
            }
            Furnishing.GamingDesk -> {
                b(.70f,.025f,.34f,py=.1675f)
                for(x in listOf(-.30f,.30f)) for(z in listOf(-.13f,.13f))
                    b(.022f,.335f,.022f,x,-.0125f,z)
                b(.61f,.012f,.012f,py=-.095f,pz=-.13f)
            }
            Furnishing.GamingChair -> {
                b(.21f,.040f,.19f,py=-.045f,pz=.022f)
                b(.19f,.235f,.050f,py=.095f,pz=-.082f)
                for(x in listOf(-.117f,.117f)) b(.026f,.12f,.18f,x,.00f,.016f)
                b(.032f,.14f,.032f,py=-.13f)
                b(.26f,.035f,.29f,py=-.1975f)
            }
            Furnishing.GamingDevice -> if(i.spec.id=="gamingspeakers") {
                for(x in listOf(-.24f,.24f)) b(.075f,.10f,.069f,px=x)
            } else b(i.spec.size.x,i.spec.size.y,i.spec.size.z)
            Furnishing.Bed -> {
                b(.52f,.065f,.66f,py=-.065f)
                b(.49f,.07f,.60f,py=0f,pz=.03f)
                b(.52f,.24f,.025f,py=.02f,pz=-.3275f)
                for(x in listOf(-.225f,.225f)) for(z in listOf(-.285f,.285f))
                    b(.025f,.08f,.025f,x,-.10f,z)
                b(.41f,.05f,.125f,py=.06f,pz=-.20f)
            }
            Furnishing.DiningTable -> {
                repeat(14) { n->
                    val x=-.21f+(n+.5f)*.42f/14
                    b(.42f/14,.022f,.30f*sqrt(1-(x/.21f).pow(2)),x,.139f)
                }
                for(x in listOf(-.14f,.14f)) for(z in listOf(-.08f,.08f))
                    b(.022f,.278f,.022f,x,-.011f,z)
            }
            Furnishing.DiningChair -> {
                b(.17f,.032f,.17f,py=-.021f,pz=.005f)
                b(.16f,.145f,.024f,py=.0675f,pz=-.078f)
                for(x in listOf(-.065f,.065f)) for(z in listOf(-.067f,.067f))
                    b(.016f,.115f,.016f,x,-.0825f,z)
            }
            Furnishing.Bookcase -> {
                for(y in listOf(-.254f,-.13f,-.006f,.118f,.254f)) b(.26f,.012f,.13f,py=y)
                for(x in listOf(-.124f,.124f)) b(.012f,.52f,.13f,px=x)
                b(.24f,.50f,.009f,pz=-.0605f)
            }
            Furnishing.Desk -> {
                b(.48f,.025f,.25f,py=.1575f)
                for(x in listOf(-.202f,.202f)) {
                    b(.016f,.055f,.192f,x,.111f)
                    for(z in listOf(-.096f,.096f)) b(.023f,.315f,.023f,x,-.0125f,z)
                }
                b(.413f,.056f,.012f,py=.111f,pz=-.096f)
                val dz=.015f+s.drawerOpen*.10f
                b(.383f,.006f,.166f,py=.079f,pz=dz)
                b(.394f,.055f,.014f,py=.104f,pz=dz+.091f)
                for(x in listOf(-.189f,.189f)) b(.007f,.046f,.172f,x,.104f,dz)
            }
            Furnishing.Chair -> {
                b(.163f,.018f,.151f,py=-.0375f)
                b(.143f,.042f,.145f,py=-.0075f,pz=.008f)
                b(.158f,.161f,.037f,py=.061f,pz=-.066f)
                for(x in listOf(-.076f,.076f)) b(.027f,.109f,.158f,x,.0175f,.003f)
                for(x in listOf(-.072f,.072f)) for(z in listOf(-.068f,.068f))
                    b(.019f,.122f,.020f,x,-.084f,z)
            }
            Furnishing.Sofa -> {
                b(.398f,.035f,.213f,py=-.089f)
                b(.37f,.056f,.174f,py=-.038f,pz=.014f)
                b(.406f,.182f,.060f,py=.033f,pz=-.083f)
                for(x in listOf(-.191f,.191f)) b(.046f,.166f,.225f,x,-.005f)
                for(x in listOf(-.165f,.165f)) for(z in listOf(-.076f,.076f))
                    b(.018f,.029f,.018f,x,-.1205f,z)
            }
            Furnishing.CoffeeTable -> {
                // Narrow strips approximate the oval instead of filling its empty corners.
                repeat(12) { index ->
                    val x=-.155f+(index+.5f)*.31f/12
                    b(.31f/12,.028f,.20f*sqrt(1-(x/.155f).pow(2)),x,.061f)
                }
                b(.22f,.012f,.122f,py=-.035f)
                for(x in listOf(-.115f,.115f)) for(z in listOf(-.063f,.063f))
                    b(.018f,.123f,.018f,x,-.0135f,z)
            }
            Furnishing.Shelf -> {
                for(y in listOf(-.119f,0f,.119f)) b(.27f,.012f,.11f,py=y)
                for(x in listOf(-.129f,.129f)) b(.012f,.238f,.105f,px=x)
            }
            Furnishing.Lamp -> {
                b(.13f,.019f,.13f,py=-.2605f)
                b(.012f,.44f,.012f,py=-.031f)
                b(.127f,.093f,.127f,py=.223f)
            }
            Furnishing.Crate -> {
                b(.25f,.015f,.23f,py=-.0925f)
                for(z in listOf(-.108f,.108f)) b(.25f,.186f,.014f,py=-.0065f,pz=z)
                for(x in listOf(-.118f,.118f)) b(.014f,.186f,.207f,px=x,py=-.0065f)
                if(!s.crateOpen) b(.25f,.012f,.23f,py=.099f)
                else b(.25f,.226f,.075f,py=.205f,pz=-.143f)
            }
            Furnishing.Plant -> {
                b(.051f,.028f,.051f,py=-.066f)
                b(.076f,.034f,.076f,py=-.035f)
                b(.080f,.10f,.080f,py=.028f)
            }
            Furnishing.Mug -> {
                b(.048f,.070f,.05f,px=-.007f)
                b(.02f,.046f,.011f,px=.022f)
            }
            Furnishing.Woodfish -> {
                b(.15f,.018f,.12f,py=-.041f)
                b(.122f,.072f,.104f,py=.004f)
            }
            Furnishing.MusicBox -> {
                b(.136f,.07f,.11f,py=-.0225f)
                b(.085f,.045f,.085f,py=.035f)
                b(.024f,.04f,.024f,px=.078f,py=-.006f)
            }
            Furnishing.SingingBowl -> b(.12f,.07f,.12f)
            Furnishing.Pokeball -> {
                b(.13f,.14f,.145f,pz=.005f)
                if(s.toyStates[i.spec.id]?.open==true) b(.13f,.14f,.145f,py=.065f,pz=-.11f)
            }
            // Reserve the maximum soft-body or rocking envelope so adjacent objects cannot clip.
            Furnishing.Mochi -> b(.155f,.10f,.138f)
            Furnishing.Jelly -> b(.135f,.105f,.135f)
            Furnishing.Tumbler -> b(.17f,.17f,.12f,py=.005f)
            Furnishing.PopBoard -> b(.20f,.047f,.17f,py=.0035f)
            Furnishing.Hourglass -> b(.16f,.16f,.065f)
            Furnishing.ShoeCabinet -> {
                b(.28f,.014f,.13f,py=.113f)
                b(.28f,.191f,.13f,py=.0105f)
                for(x in listOf(-.114f,.114f)) for(z in listOf(-.046f,.046f))
                    b(.014f,.042f,.014f,x,-.099f,z)
                b(.18f,.010f,.010f,py=.060f,pz=.068f)
            }
            Furnishing.SideTable -> {
                b(.17f,.02f,.17f,py=.10f)
                b(.126f,.012f,.126f,py=-.05f)
                for(n in 0..2) {
                    val a=n*2*PI/3;b(.013f,.20f,.013f,(cos(a)*.060).toFloat(),-.01f,(sin(a)*.060).toFloat())
                }
            }
            Furnishing.CoatRack -> {
                b(.15f,.016f,.15f,py=-.202f)
                b(.018f,.39f,.018f,py=-.003f)
                b(.13f,.10f,.13f,py=.15f)
            }
            Furnishing.TableLamp -> {
                b(.062f,.065f,.062f,py=-.0575f)
                b(.10f,.076f,.10f,py=.052f)
            }
            Furnishing.MovingBox -> b(.25f,.20f,.23f)
            Furnishing.Ottoman -> {
                b(.19f,.085f,.19f,py=.025f)
                for(x in listOf(-.058f,.058f)) for(z in listOf(-.058f,.058f))
                    b(.018f,.05f,.018f,x,-.0425f,z)
            }
            Furnishing.GardenBench -> {
                b(.34f,.025f,.145f,py=-.005f)
                b(.34f,.11f,.018f,py=.0625f,pz=-.0635f)
                for(x in listOf(-.142f,.142f)) for(z in listOf(-.055f,.055f))
                    b(.015f,.10f,.015f,x,-.0675f,z)
            }
            else -> b(i.spec.size.x,i.spec.size.y,i.spec.size.z)
        }
    }
    fun surfaces(s: RoomGame,i: RoomItem): List<Surface> {
        if(i.rotation.rotate(up).y<.999f) return emptyList()
        fun p(y: Float,w: Float,d: Float,z: Float=0f,ellipse: Boolean=false)=Surface(Point3(0f,y,z),w,d,ellipse)
        if(LifestyleModels.contains(i.spec)) return (LifestyleModels.top(i.spec) ?: LifestyleModels.seat(i.spec))?.let {
            listOf(p(it.y,it.width,it.depth,it.z))
        }.orEmpty()
        return when(i.spec.kind) {
            Furnishing.StudyDesk->listOf(p(.175f,.60f,.29f))
            Furnishing.StudyChair->listOf(p(.004f,.195f,.18f,.008f))
            Furnishing.StudyBookcase->listOf(p(-.035f,.42f,.14f))
            Furnishing.StudyArmchair->listOf(p(.016f,.22f,.20f,.025f))
            Furnishing.StudyStool->listOf(p(.0725f,.18f,.16f))
            Furnishing.StudySide->listOf(p(.105f,.16f,.16f,ellipse=true))
            Furnishing.StudyRug->listOf(p(.003f,.64f,.66f))
            Furnishing.GamingDesk->listOf(p(.18f,.70f,.34f))
            Furnishing.GamingStorage->listOf(p(.12f,.19f,.17f))
            Furnishing.GamingMat->listOf(p(.002f,.405f,.14f))
            Furnishing.Bed->listOf(p(.035f,.47f,.42f,.115f))
            Furnishing.Cabinet->listOf(p(i.spec.size.y/2,i.spec.size.x,i.spec.size.z))
            Furnishing.DiningTable->listOf(p(.15f,.42f,.30f,ellipse=true))
            Furnishing.DiningChair->listOf(p(-.005f,.15f,.14f,.015f))
            Furnishing.Bookcase->listOf(-.248f,-.124f,0f,.124f,.26f).map { p(it,.235f,.116f,.005f) }
            Furnishing.Decor->if(i.spec.assetId=="books") listOf(p(.035f,.11f,.08f)) else emptyList()
            Furnishing.Desk -> listOf(p(.17f,.48f,.25f))
            Furnishing.CoffeeTable -> listOf(p(.075f,.31f,.20f,ellipse=true),p(-.029f,.22f,.122f))
            Furnishing.Chair -> listOf(p(.0135f,.143f,.145f,.008f))
            Furnishing.Sofa -> listOf(p(-.01f,.332f,.174f,.014f))
            Furnishing.Shelf -> listOf(p(-.113f,.246f,.11f),p(.006f,.246f,.11f),p(.125f,.27f,.11f))
            Furnishing.Crate -> listOf(p(-.085f,.221f,.199f))+
                if(s.crateOpen) emptyList() else listOf(p(.105f,.25f,.23f))
            Furnishing.Book -> listOf(p(.009f,.11f,.075f))
            Furnishing.Rug -> listOf(p(.004f,.63f,.52f))
            Furnishing.ShoeCabinet -> listOf(p(.12f,.28f,.13f))
            Furnishing.SideTable -> listOf(p(.11f,.17f,.17f,ellipse=true),p(-.044f,.126f,.126f,ellipse=true))
            Furnishing.Ottoman -> listOf(p(.0675f,.17f,.17f,ellipse=true))
            Furnishing.GardenBench -> listOf(p(.0075f,.32f,.118f,.004f))
            else -> emptyList()
        }
    }
    /** Actual base contacts, not foliage, handles, chair backs, or lampshades. */
    private fun feet(i: RoomItem): List<Point3> {
        val y=-i.spec.size.y/2
        fun rect(x: Float,z: Float,cx: Float=0f)=listOf(
            Point3(cx-x,y,-z),Point3(cx+x,y,-z),Point3(cx+x,y,z),Point3(cx-x,y,z))
        fun disc(r: Float,cx: Float=0f)=List(8) { n ->
            val a=n*PI/4;Point3(cx+r*cos(a).toFloat(),y,r*sin(a).toFloat())
        }
        if(LifestyleModels.contains(i.spec)) return rect(i.spec.size.x*.4f,i.spec.size.z*.4f)
        return when(i.spec.kind) {
            Furnishing.StudyDesk->rect(.257f,.111f)
            Furnishing.StudyChair->rect(.097f,.097f)
            Furnishing.StudyArmchair->rect(.107f,.106f)
            Furnishing.StudyStool->rect(.071f,.062f)
            Furnishing.StudySide->disc(.053f)
            Furnishing.StudyLight->disc(if(i.spec.assetId=="studybanker") .037f else .052f)
            Furnishing.StudyPlant->disc(.039f)
            Furnishing.StudyDecor->when(i.spec.assetId) {
                "studyteaset"->disc(.040f,-.006f)
                "studyglobe"->disc(.033f)
                else->rect(i.spec.size.x/2-.002f,i.spec.size.z/2-.002f)
            }
            Furnishing.GamingDesk->rect(.30f,.13f)
            Furnishing.GamingDevice->if(i.spec.id=="gamingspeakers")
                listOf(-.24f,.24f).flatMap { rect(.035f,.032f,it) }
                else rect(i.spec.size.x/2-.002f,i.spec.size.z/2-.002f)
            Furnishing.Plant -> disc(.022f)
            Furnishing.Mug -> disc(.016f,-.007f)
            Furnishing.Lamp -> disc(.052f)
            Furnishing.Chair -> rect(.073f,.070f)
            Furnishing.Desk -> rect(.213f,.101f)
            Furnishing.Sofa -> rect(.174f,.085f)
            Furnishing.CoffeeTable -> rect(.121f,.07f)
            Furnishing.Woodfish -> rect(.062f,.047f)
            Furnishing.MusicBox -> rect(.060f,.047f)
            Furnishing.SingingBowl -> disc(.032f)
            Furnishing.Pokeball -> disc(.033f)
            Furnishing.Rattle -> disc(.02f)
            Furnishing.Spinner -> disc(.023f)
            Furnishing.Tumbler -> disc(.013f)
            Furnishing.Ratchet -> disc(.05f)
            Furnishing.CoatRack -> disc(.064f)
            Furnishing.TableLamp -> disc(.027f)
            Furnishing.ShoeCabinet -> rect(.113f,.044f)
            Furnishing.SideTable -> disc(.058f)
            Furnishing.Ottoman -> rect(.058f,.058f)
            Furnishing.GardenBench -> rect(.142f,.055f)
            Furnishing.Bed->rect(.2375f,.2975f)
            Furnishing.DiningTable->rect(.151f,.091f)
            Furnishing.DiningChair->rect(.073f,.075f)
            Furnishing.TallPlant->disc(.045f)
            else -> rect(i.spec.size.x/2-.002f,i.spec.size.z/2-.002f)
        }
    }
    private fun stableRotation(i: RoomItem): Rotation {
        // Broad rigid bases settle level. A picture without a wall mount lies flat.
        val yaw=Rotation.yaw(i.rotation.yaw())
        return if(i.spec.kind in setOf(Furnishing.Picture,Furnishing.WallClock,Furnishing.WallMirror)) yaw*Rotation.between(up,Point3(0f,0f,1f)) else yaw
    }
    fun family(s: RoomGame,id: String): Set<String> {
        val result=mutableSetOf(id)
        while(true) {
            val children=s.items.filter { it.support in result }.map { it.spec.id }
            if(!result.addAll(children)) return result
        }
    }
    fun moveFamily(s: RoomGame,i: RoomItem,p: Point3,q: Rotation): RoomGame {
        val ids=family(s,i.spec.id);val delta=q*i.rotation.inverse()
        return s.copy(items=s.items.map { child ->
            if(child.spec.id !in ids) child else child.copy(
                position=p+delta.rotate(child.position-i.position),rotation=delta*child.rotation)
        })
    }
    /** Held items need no support yet, but every part must stay above the floor
     * and inside the walls. Include carried children and expanded toy envelopes. */
    fun heldClear(s: RoomGame,ids: Set<String>): Boolean =
        s.items.filter { it.spec.id in ids && !it.packed }.all { item ->
            if(!inside(item) || item.position.y-half(item).y<-.320001f) return@all false
            if(s.cleaning!=null && touchesArchitecture(item)) return@all false
            boxes(s,item).all { box ->
                val part=item.copy(spec=item.spec.copy(size=box.size),
                    position=item.position+item.rotation.rotate(box.center))
                val h=half(part)
                inside(part) && part.position.y-h.y>=-.320001f &&
                    RoomFloor.skirtClear(part.position,h)
            }
        }

    /** Like a card preview, a held family has no room-surface constraints.
     * Facing changes orientation without a hidden clearance lift or sideways jump. */
    fun faceForDrag(s: RoomGame,item: RoomItem,facing: Rotation): RoomGame {
        return moveFamily(s,item,item.position,Rotation.yaw(facing.yaw()))
    }

    /** Same free preview motion as catalogue extraction. Floor/wall/support
     * checks belong to solve() on release. The scene applies the shared window
     * envelope; pair motion still keeps the pivot fixed and changes only yaw. */
    fun constrainHeld(s: RoomGame,item: RoomItem,translation: Point3,rotation: Rotation,
                      turning: Boolean): RoomGame {
        if(!translation.finite() || !rotation.finite) return s
        return moveFamily(s,item,if(turning) item.position else item.position+translation,
            if(turning) rotation.gravityTurn()*item.rotation else item.rotation)
    }
    fun rotateInPlace(s: RoomGame,id: String,degrees: Float): Placement {
        val i=s.items.find { it.spec.id==id && !it.packed && it.spec.kind!=Furnishing.MovingBox }
            ?: return Placement(null,null,"先轻点一件放稳的物件。")
        val q=if(isWallSupport(i.support))
            Rotation.yaw(mountYaw(i.support))*Rotation.axis(Point3(0f,0f,1f),-degrees)
            else Rotation.yaw(degrees-i.rotation.yaw())*i.rotation
        val next=moveFamily(s,i,i.position,q)
        val problem=invalid(next,family(s,id))
        return if(problem==null) Placement(next,next.item(id),"")
            else Placement(null,i,"这个角度放不下：$problem。原位置已保留。")
    }
    fun placedAngle(i: RoomItem): Float {
        if(!isWallSupport(i.support)) return i.rotation.yaw()
        val local=(Rotation.yaw(-mountYaw(i.support))*i.rotation).rotate(up)
        return Math.toDegrees(atan2(local.x,local.y).toDouble()).toFloat()
    }
    fun half(i: RoomItem): Point3 {
        val h=i.spec.size*.5f;val q=i.rotation
        val a=q.rotate(Point3(h.x,0f,0f));val b=q.rotate(Point3(0f,h.y,0f));val c=q.rotate(Point3(0f,0f,h.z))
        return Point3(abs(a.x)+abs(b.x)+abs(c.x),abs(a.y)+abs(b.y)+abs(c.y),abs(a.z)+abs(b.z)+abs(c.z))
    }
    fun inside(i: RoomItem): Boolean {
        val h=half(i);val p=i.position
        return RoomFloor.contains(p.x-h.x,p.x+h.x,p.z-h.z,p.z+h.z) &&
            p.y-h.y>=-.3215f && p.y+h.y<=.715f
    }
    private fun dot(a: Point3,b: Point3)=a.x*b.x+a.y*b.y+a.z*b.z
    private fun cross(a: Point3,b: Point3)=Point3(a.y*b.z-a.z*b.y,a.z*b.x-a.x*b.z,a.x*b.y-a.y*b.x)
    private val axes=listOf(Point3(1f,0f,0f),up,Point3(0f,0f,1f))
    private fun overlap(a: RoomItem,ab: Box,b: RoomItem,bb: Box): Boolean {
        val aa=axes.map(a.rotation::rotate);val ba=axes.map(b.rotation::rotate)
        val ah=listOf(ab.size.x/2,ab.size.y/2,ab.size.z/2)
        val bh=listOf(bb.size.x/2,bb.size.y/2,bb.size.z/2)
        val delta=(a.position+a.rotation.rotate(ab.center))-(b.position+b.rotation.rotate(bb.center))
        return (aa+ba+aa.flatMap { x->ba.map { cross(x,it) } }).all { axis ->
            val length=sqrt(dot(axis,axis))
            if(length<.0001f) true else {
                val radius=aa.indices.sumOf { (abs(dot(aa[it],axis))*ah[it]+abs(dot(ba[it],axis))*bh[it]).toDouble() }.toFloat()
                abs(dot(delta,axis))<radius-tolerance*length
            }
        }
    }
    fun intersects(s: RoomGame,a: RoomItem,b: RoomItem): Boolean {
        if(a.packed || b.packed || a.spec.kind==Furnishing.Rug || b.spec.kind==Furnishing.Rug ||
            (a.spec.kind==Furnishing.MovingBox && s.stock(a.spec.id).isEmpty()) ||
            (b.spec.kind==Furnishing.MovingBox && s.stock(b.spec.id).isEmpty())) return false
        // Broad phase is only an optimization; cavities and rotations use the SAT below.
        val h=half(a)+half(b)+Point3(.04f,.25f,.15f);val d=a.position-b.position
        if(abs(d.x)>h.x || abs(d.y)>h.y || abs(d.z)>h.z) return false
        return boxes(s,a).any { ab->boxes(s,b).any { bb->overlap(a,ab,b,bb) } }
    }
    private fun onPatch(i: RoomItem,parent: RoomItem,patch: Surface): Boolean {
        val contacts=if(i.spec.kind in setOf(Furnishing.Picture,Furnishing.WallClock,Furnishing.WallMirror)) {
            val h=half(i)
            listOf(Point3(-h.x,-h.y,-h.z),Point3(h.x,-h.y,-h.z),Point3(h.x,-h.y,h.z),Point3(-h.x,-h.y,h.z))
                .map { i.position+it }
        } else feet(i).map { i.position+i.rotation.rotate(it) }
        return contacts.all { point ->
            val p=parent.rotation.inverse().rotate(point-parent.position)-patch.center
            val x=abs(p.x);val z=abs(p.z)
            abs(p.y)<.002f && if(patch.ellipse) (x/(patch.width/2)).pow(2)+(z/(patch.depth/2)).pow(2)<=1.001f
            else x<=patch.width/2+.0005f && z<=patch.depth/2+.0005f
        }
    }
    fun supported(s: RoomGame,i: RoomItem): Boolean {
        if(i.packed || isWallSupport(i.support)) return true
        if(i.support==null) return abs(i.position.y-half(i).y+.32f)<.002f
        val parent=s.items.find { it.spec.id==i.support } ?: return false
        return surfaces(s,parent).any { onPatch(i,parent,it) }
    }
    /** Validate authored or imported layouts without moving any held object. */
    fun validateLayout(s: RoomGame,ids: Set<String> = s.items.filterNot { it.packed }.map { it.spec.id }.toSet()): String? =
        invalid(s,ids) ?: s.items.firstOrNull {
            it.spec.id in ids && !it.packed && touchesArchitecture(it)
        }?.let { "${it.spec.name}碰到了壁炉或阳台栏杆，请留出空隙" }
    private fun invalid(s: RoomGame,ids: Set<String>): String? {
        for(i in s.items.filter { it.spec.id in ids && !it.packed }) {
            if(!inside(i) || (i.spec.kind.playable && !toyInside(s,i))) return "超出房间边界，请向房间里面挪一点"
            if(!supported(s,i)) return "${i.spec.name}的底部没有稳定承托，请保持承托面水平"
            val h=half(i);val p=i.position;val scale=RoomLayout.scale
            if(i.support=="@back" && p.x+h.x>-.8775f*scale && p.x-h.x<-.135f*scale &&
                p.y+h.y>.10f && p.y-h.y<.61f) return "这个角度挡住了窗户"
            if(i.support=="@left" && p.z+h.z>-.6825f*scale && p.z-h.z<-.0075f*scale &&
                p.y+h.y>.045f && p.y-h.y<.575f) return "这个角度挡住了窗户"
            if(s.cleaning!=null && touchesArchitecture(i)) return "${i.spec.name}碰到了壁炉或阳台栏杆，请留出空隙"
            val other=s.items.firstOrNull { it.spec.id!=i.spec.id && intersects(s,i,it) }
            if(other!=null) return "${i.spec.name}碰到了${other.spec.name}，请留出一点空隙"
        }
        return null
    }
    private fun wall(i: RoomItem): RoomItem? {
        val p=i.position;val h=i.spec.size*.5f
        if(p.z>=BathroomLayout.back && p.x<=BathroomLayout.right) {
            if(p.z-h.x<BathroomLayout.back || p.z+h.x>BathroomLayout.front) return null
            return i.copy(position=Point3(BathroomLayout.left+h.z,p.y,p.z),
                rotation=Rotation.yaw(90f),support=BathroomLayout.wallSupport)
        }
        if(p.z>=1.82f && p.x>GamingLayout.right-.16f)
            return i.copy(position=Point3(GamingLayout.right-.018f-h.z,p.y,p.z),
                rotation=Rotation.yaw(-90f),support=GamingLayout.wallSupport)
        if(p.x<-.89f*RoomLayout.scale && p.z>-.73f*RoomLayout.scale) {
            if(p.z+h.x>-.6825f*RoomLayout.scale && p.z-h.x<-.0075f*RoomLayout.scale && p.y+h.y>.045f && p.y-h.y<.575f) return null
            return i.copy(position=Point3(RoomLayout.left+h.z,p.y,p.z),rotation=Rotation.yaw(90f),support="@left")
        }
        if(p.x+h.x>-.8775f*RoomLayout.scale && p.x-h.x<-.135f*RoomLayout.scale && p.y+h.y>.10f && p.y-h.y<.61f) return null
        return i.copy(position=Point3(p.x,p.y,RoomLayout.back+h.z),rotation=Rotation(),support="@back")
    }
    /** Broad backs meet the real rear/left walls; open cutaway edges never attract furniture. */
    fun wallContact(s: RoomGame,i: RoomItem)=wallContacts(s,i).firstOrNull() ?: i
    private fun wallContacts(s: RoomGame,i: RoomItem): List<RoomItem> {
        if(i.support!=null || i.spec.kind !in setOf(Furnishing.Desk,Furnishing.Chair,Furnishing.Sofa,
                Furnishing.CoffeeTable,Furnishing.ShoeCabinet,Furnishing.SideTable,Furnishing.GardenBench,
                Furnishing.Bed,Furnishing.Cabinet,Furnishing.DiningTable,Furnishing.DiningChair,
                Furnishing.Bookcase,Furnishing.Appliance,Furnishing.Sanitary,
                Furnishing.GamingDesk,Furnishing.GamingChair,Furnishing.GamingStorage,
                Furnishing.StudyDesk,Furnishing.StudyChair,Furnishing.StudyBookcase,Furnishing.StudyArmchair))
            return emptyList()
        val current=half(i)
        // The annex is an open floor now; its former walls cannot attract furniture.
        if(i.position.z>=BathroomLayout.back) return emptyList()
        val backDistance=i.position.z-current.z-RoomLayout.back
        val leftDistance=i.position.x-current.x-RoomLayout.left
        val nearBack=backDistance in -.06f.. .10f
        val nearLeft=leftDistance in -.06f.. .10f
        val walls=listOfNotNull(
            if(nearBack) true to abs(backDistance) else null,
            if(nearLeft) false to abs(leftDistance) else null,
        ).sortedBy { it.second }
        return walls.mapNotNull { (back,_) ->
            // The widest vertical face provides the broadest contact. On equal
            // footprints prefer the back, keeping the seat/doors accessible.
            val side=if(i.spec.kind!=Furnishing.Bed && i.spec.size.z>i.spec.size.x+.001f) {
                val options=listOf(-90f,90f).map { it+(if(back) 0f else 90f) }
                options.minBy { abs((Rotation.yaw(it)*i.rotation.inverse()).yaw()) }
            } else if(back) 0f else 90f
            val aligned=i.copy(rotation=Rotation.yaw(side))
            val h=half(aligned)
            var minX=RoomLayout.left+h.x;var maxX=RoomLayout.right-h.x
            var minZ=RoomLayout.back+h.z;var maxZ=RoomLayout.front-h.z
            for(b in boxes(s,aligned)) {
                val offset=aligned.rotation.rotate(b.center)
                val part=aligned.copy(position=aligned.position+offset,
                    spec=aligned.spec.copy(size=b.size))
                val extent=half(part)
                val low=part.position.y-extent.y;val high=part.position.y+extent.y
                val skirt=low<-.247f && high>-.319f
                minZ=max(minZ,(if(skirt) -.925f*RoomLayout.scale else RoomLayout.back)-offset.z+extent.z)
                minX=max(minX,(if(skirt) -1.022f*RoomLayout.scale else RoomLayout.left)-offset.x+extent.x)
                maxZ=min(maxZ,RoomLayout.front-offset.z-extent.z)
                maxX=min(maxX,RoomLayout.right-offset.x-extent.x)
            }
            if(minX>maxX || minZ>maxZ) return@mapNotNull null
            val p=if(back) Point3(i.position.x.coerceIn(minX,maxX),i.position.y,minZ)
                else Point3(minX,i.position.y,i.position.z.coerceIn(minZ,maxZ))
            // Only assist locally; never move a piece across the room or a doorway.
            aligned.copy(position=p).takeIf { (p-i.position).length()<=.25f }
        }
    }
    private fun touchesArchitecture(i: RoomItem): Boolean {
        val h=half(i);val p=i.position
        fun hit(x:Float,y:Float,z:Float,w:Float,height:Float,d:Float)=
            abs(p.x-x)<h.x+w/2-tolerance && abs(p.y-y)<h.y+height/2-tolerance &&
                abs(p.z-z)<h.z+d/2-tolerance
        // Solid mantel and chimney; reserve the firebox for a future fireplace mechanic.
        val scale=RoomLayout.scale
        if(hit(-1.018f*scale+.04f,-.132f,.29f*scale,.27f,.376f,.39f) ||
            hit(-1.018f*scale-.009f,.30f,.29f*scale,.19f,.49f,.24f)) return true
        if(hit(1.49f*scale,-.19f,(RoomLayout.balconyRailBack+RoomLayout.balconyRailFront)/2,
                .028f*scale,.26f,RoomLayout.balconyRailFront-RoomLayout.balconyRailBack+.028f*scale)) return true
        return listOf(RoomLayout.balconyRailBack,RoomLayout.balconyRailFront).any {
            hit(1.31f*scale,-.19f,it,.38f*scale,.26f,.028f*scale)
        }
    }
    private fun fallObstruction(s: RoomGame,held: RoomItem,landed: RoomItem,ids: Set<String>): RoomItem? {
        val drop=held.position.y-landed.position.y
        if(drop<=.012f || landed.spec.kind==Furnishing.Rug) return null
        val localUp=landed.rotation.inverse().rotate(up)
        val swept=boxes(s,landed).map {
            Box(it.center+localUp*(drop/2),it.size+Point3(abs(localUp.x)*drop,abs(localUp.y)*drop,abs(localUp.z)*drop))
        }
        return s.items.firstOrNull { other ->
            other.spec.id !in ids && !other.packed && other.spec.kind!=Furnishing.Rug &&
                !(other.spec.kind==Furnishing.MovingBox && s.stock(other.spec.id).isEmpty()) &&
                swept.any { ab->boxes(s,other).any { bb->overlap(landed,ab,other,bb) } }
        }
    }
    fun solve(s: RoomGame,preserveYaw: Boolean=false,snapFurnitureToWall: Boolean=!preserveYaw): Placement {
        val held=s.selected ?: return Placement(null,null,"")
        val ids=family(s,held.spec.id)
        fun assess(candidate: RoomItem): Placement {
            val moved=moveFamily(s,held,candidate.position,candidate.rotation)
            val next=moved.copy(items=moved.items.map { if(it.spec.id==held.spec.id) candidate else it })
            val problem=invalid(next,ids) ?: if(!heldClear(next,ids)) "物件边缘碰到了地面、墙面或踢脚线" else null
            return if(problem!=null) Placement(null,candidate,"这里放不下：$problem。")
            else Placement(next,candidate,candidate.support?.let { id ->
                "可放在${next.items.find { it.spec.id==id }?.spec?.name ?: "墙面"}上"
            } ?: "可放在地板上")
        }
        val wallKind=held.spec.kind in setOf(Furnishing.Shelf,Furnishing.Picture,Furnishing.WallClock,Furnishing.WallMirror)
        if(wallKind && (held.position.z<-.79f*RoomLayout.scale || held.position.x<-.89f*RoomLayout.scale ||
                (held.position.z>=BathroomLayout.back && held.position.x<BathroomLayout.left+.16f) ||
                (held.position.z>=1.82f && held.position.x>GamingLayout.right-.16f))) {
            val candidate=wall(held.copy(packed=false))
                ?: return Placement(null,null,"这里放不下：窗户所在的墙面不能安装，请换一处完整墙面。")
            if(preserveYaw && !snapFurnitureToWall && abs((candidate.rotation*held.rotation.inverse()).yaw())>.5f)
                return Placement(null,held,"这个朝向不能贴住墙面，请用双手转正后再放下。")
            return assess(candidate)
        }
        val level=held.copy(rotation=stableRotation(held),packed=false,support=null)
        val h=half(level)
        var reason="这里放不下：底部没有足够的稳定承托。"
        // Tiny local assistance only. Never drag across a desk, a wall, or the room.
        val offsets=listOf(Point3(0f,0f,0f))+listOf(.012f,.024f).flatMap { radius ->
            List(8) { n->Point3(cos(n*PI/4).toFloat()*radius,0f,sin(n*PI/4).toFloat()*radius) }
        }
        for(offset in offsets) {
            val x=held.position.x+offset.x;val z=held.position.z+offset.z
            val candidates=mutableListOf(level.copy(position=Point3(x,-.32f+h.y,z)))
            for(parent in s.items.filter { it.spec.id !in ids && !it.packed }) {
                for(patch in surfaces(s,parent)) {
                    val top=parent.position+parent.rotation.rotate(patch.center)
                    // Permit only a small contact tolerance, never snap up through a shelf.
                    if(top.y>held.position.y-h.y+.012f) continue
                    val candidate=level.copy(position=Point3(x,top.y+h.y,z),support=parent.spec.id)
                    if(onPatch(candidate,parent,patch)) candidates+=candidate
                }
            }
            // Stop at the first stable surface encountered by gravity. Collision failure must
            // not try a lower floor through that surface.
            val landed=candidates.maxBy { it.position.y }
            val contacts=if(snapFurnitureToWall) wallContacts(s,landed) else emptyList()
            for(candidate in contacts+landed) {
                val obstacle=fallObstruction(s,held,candidate,ids)
                if(obstacle!=null) {
                    if(offset==offsets.first()) reason="这里放不下：下落路径被${obstacle.spec.name}挡住，底部要完整放到承托面内。"
                    continue
                }
                val checked=assess(candidate)
                if(checked.valid) return checked
                if(offset==offsets.first()) reason=checked.reason
            }
        }
        return Placement(null,null,reason)
    }
    fun toyClear(s: RoomGame,id: String): Boolean {
        val toy=s.item(id)
        return toyInside(s,toy) && s.items.none { it.spec.id!=id && intersects(s,toy,it) }
    }
    private fun toyInside(s: RoomGame,toy: RoomItem): Boolean = boxes(s,toy).all { box ->
            listOf(-1,1).all { x -> listOf(-1,1).all { y -> listOf(-1,1).all { z ->
                val p=toy.position+toy.rotation.rotate(box.center+Point3(
                    box.size.x*x/2,box.size.y*y/2,box.size.z*z/2))
                RoomFloor.point(p.x,p.z) && p.y in -.322f.. .715f
            } } }
    }
    private fun isWallSupport(id: String?)=id in setOf("@back","@left",BathroomLayout.wallSupport,GamingLayout.wallSupport)
    private fun mountYaw(id: String?)=when(id) {
        GamingLayout.wallSupport->-90f
        "@left",BathroomLayout.wallSupport->90f
        else->0f
    }
    fun mechanismsClear(s: RoomGame): Boolean =
        s.items.filter { !it.packed && it.spec.kind in setOf(Furnishing.Desk,Furnishing.Crate) }.all { body ->
            s.items.none { it.spec.id!=body.spec.id && intersects(s,body,it) }
        }
}
