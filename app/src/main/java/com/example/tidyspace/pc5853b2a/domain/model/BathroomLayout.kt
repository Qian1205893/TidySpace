package com.example.tidyspace.pc5853b2a.domain.model

/** Annex coordinates are already authored metres; never reapply RoomLayout.scale. */
object BathroomLayout {
    const val version=1
    const val left=-1.53265f
    const val right=-.10265f
    const val back=.899f
    const val front=2.259f
    const val doorLeft=-.88f
    const val doorRight=-.52f
    const val wallSupport="@bath-left"
    fun owns(id: String)=id.startsWith("bathroom-")
    fun fixtures(): List<RoomItem> {
        fun fixture(asset: String,p: Point3,yaw: Float=0f,support: String?=null): RoomItem {
            val source=homeExpansion().first { it.id==asset }
            return RoomItem(source.copy(id="bathroom-$asset",start=p),p,Rotation.yaw(yaw).normalized(),support,false)
        }
        return listOf(
            fixture("bathtub",Point3(-1.19f,-.22f,1.985f)),
            fixture("toilet",Point3(-.34f,-.205f,1.505f),90f),
            fixture("bathvanity",Point3(-1.36f,-.17f,1.49f),90f),
            fixture("washer",Point3(-1.36f,-.175f,1.125f),90f),
            fixture("laundrybasket",Point3(-1.105f,-.225f,1.13f)),
            fixture("mirror",Point3(left+.0075f,.16f,1.49f),90f,wallSupport),
        )
    }
    /** Separate installation version makes a moved or packed fixture stay that way on reload. */
    fun prepare(s: RoomGame): RoomGame {
        if(s.studyVersion>0 || s.bathroomVersion==version) return s
        val missing=fixtures().filter { initial->s.items.none { it.spec.id==initial.spec.id } }
        return s.copy(items=s.items+missing,bathroomVersion=version)
    }
}

/** Continuous floor union; the former gap is part of the gaming extension. */
object RoomFloor {
    data class Rect(val left: Float,val right: Float,val back: Float,val front: Float)
    val main=Rect(RoomLayout.left,RoomLayout.right,RoomLayout.back,RoomLayout.front)
    val bathroom=Rect(BathroomLayout.left,BathroomLayout.right,BathroomLayout.back,BathroomLayout.front)
    val gaming=Rect(GamingLayout.left,GamingLayout.right,GamingLayout.back,GamingLayout.front)
    private val regions=listOf(main,bathroom,
        Rect(RoomLayout.right,RoomLayout.balconyRight,RoomLayout.balconyBack,RoomLayout.balconyFront),
        gaming)
    fun point(x: Float,z: Float)=regions.any {
        x>=it.left-.000001f && x<=it.right+.000001f && z>=it.back-.000001f && z<=it.front+.000001f
    }
    fun contains(x0: Float,x1: Float,z0: Float,z1: Float): Boolean {
        // Almost every hand frame stays within one room; avoid grid allocations there.
        if(regions.any { x0>=it.left-.000001f && x1<=it.right+.000001f &&
                z0>=it.back-.000001f && z1<=it.front+.000001f }) return true
        fun samples(lo: Float,hi: Float,edges: List<Float>): List<Float> {
            val cuts=(listOf(lo,hi)+edges.filter { it>lo && it<hi }).distinct().sorted()
            return cuts+cuts.zipWithNext { a,b->(a+b)/2 }
        }
        val xs=samples(x0,x1,regions.flatMap { listOf(it.left,it.right) })
        val zs=samples(z0,z1,regions.flatMap { listOf(it.back,it.front) })
        return xs.all { x->zs.all { z->point(x,z) } }
    }
    fun region(p: Point3)=when {
        p.z>=BathroomLayout.back && p.x<=BathroomLayout.right -> bathroom
        p.z>RoomLayout.front -> gaming
        p.x>RoomLayout.right -> regions[2]
        else -> main
    }
    /** Project only outside the actual floor union, including the former doorway seam. */
    fun constrain(p: Point3,lo: Point3,hi: Point3): Point3? {
        if(contains(p.x+lo.x,p.x+hi.x,p.z+lo.z,p.z+hi.z)) return p
        return regions.mapNotNull { r ->
            val left=r.left-lo.x;val right=r.right-hi.x
            val back=r.back-lo.z;val front=r.front-hi.z
            if(left>right || back>front) null else
                p.copy(x=p.x.coerceIn(left,right),z=p.z.coerceIn(back,front))
        }.minByOrNull { (it-p).length() }
    }
    fun skirtClear(p: Point3,h: Point3): Boolean {
        val low=p.y-h.y;val high=p.y+h.y
        if(low>=-.247f || high<=-.319f) return true
        val minZ=p.z-h.z
        val minX=p.x-h.x
        if(minZ<RoomLayout.front && (minX< -1.022f*RoomLayout.scale-.000001f ||
                minZ<-.925f*RoomLayout.scale-.000001f)) return false
        return true
    }
}
