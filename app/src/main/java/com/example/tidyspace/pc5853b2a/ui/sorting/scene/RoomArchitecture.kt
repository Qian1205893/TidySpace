package com.example.tidyspace.pc5853b2a.ui.sorting.scene

import android.util.Log
import com.pico.spatial.core.ecs.BoundingBox
import com.pico.spatial.core.ecs.DirectionalLightComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.MeshModel
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import kotlin.math.*
import kotlinx.coroutines.delay

/**
 * Room architecture around the existing manipulation space, in workbench-local meters.
 * Floor top=-.32, desk top=.0175; left/back walls leave front/right/roof open.
 * These are dimension-driven architectural meshes, not the pending fine furniture assets.
 */
internal class RoomArchitecture {
    private val batches = linkedMapOf<String, Geometry>()
    private val supports = mutableListOf<Entity>()
    private lateinit var floor: Entity
    private var sun: Entity?=null
    private var bounce: Entity?=null
    private fun geometry(token: String) = batches.getOrPut(token) { Geometry() }
    private fun box(token: String,x:Float,y:Float,z:Float,w:Float,h:Float,d:Float) =
        geometry(token).box(Vector3(x,y,z),Vector3(w,h,d))

    suspend fun build(parent: Entity,r: SceneResources,wallLevel: Boolean,level: Int,decorating: Boolean=false): Entity {
        // Foundation has a visible cutaway edge. Parquet boards have true seams and thickness.
        floor=r.part(parent,r.box(Vector3(1.50f,.06f,1.12f),.008f),
            "endGrain",Vector3(0f,-.358f,-.12f))
        parquet()
        box("oak2",0f,-.333f,.429f,1.5f,.024f,.022f)
        box("oak2",.739f,-.333f,-.12f,.022f,.024f,1.10f)

        // Back wall aperture: x=-.56..-.12, y=.13.. .58, depth=.045.
        // Four wall sections leave a real empty window opening.
        box("plaster",0f,-.095f,-.6575f,1.50f,.45f,.045f)
        box("plaster",0f,.65f,-.6575f,1.50f,.14f,.045f)
        box("plaster",-.655f,.355f,-.6575f,.19f,.45f,.045f)
        box("plaster",.315f,.355f,-.6575f,.87f,.45f,.045f)
        backWindow()

        // Left wall aperture: z=-.43..-.03, y=.08.. .54.
        box("sageWall",-.7275f,-.12f,-.1075f,.045f,.40f,1.055f)
        box("sageWall",-.7275f,.63f,-.1075f,.045f,.18f,1.055f)
        box("sageWall",-.7275f,.31f,-.5325f,.045f,.46f,.205f)
        box("sageWall",-.7275f,.31f,.195f,.045f,.46f,.45f)
        sideWindow()

        // Skirting and upper cap reveal wall thickness and joinery from oblique views.
        box("creamTrim",0f,-.283f,-.620f,1.41f,.074f,.029f)
        box("creamTrim",-.691f,-.283f,-.108f,.029f,.074f,1.055f)
        box("endGrain",0f,.72f,-.656f,1.51f,.027f,.057f)
        box("endGrain",-.728f,.72f,-.107f,.057f,.027f,1.075f)
        for(x in listOf(-.725f,.729f))
            box("oak2",x,.20f,-.628f,.027f,1.04f,.025f)
        box("oak2",-.697f,.20f,.405f,.024f,1.04f,.032f)

        // Wainscot rails and recessed panels, below the windows.
        box("panelGreen",0f,-.172f,-.626f,1.40f,.216f,.018f)
        for(i in 0..8) {
            val x=-.62f+i*.155f
            box("panelInset",x,-.172f,-.614f,.125f,.163f,.005f)
            box("creamTrim",x,-.172f,-.608f,.008f,.175f,.006f)
        }
        box("creamTrim",0f,-.061f,-.610f,1.40f,.014f,.035f)
        // Small geometric wallpaper motifs are shallow relief on the solid wall only.
        for(row in 0..6) for(col in 0..9) {
            val y=.005f+row*.09f; val z=-.565f+col*.098f
            if(y in .045f.. .575f && z in -.46f..0f) continue
            geometry("wallPattern").diamondOnX(-.7045f,y,z,.014f,.023f)
        }
        if(!decorating) desk(parent,r,level)
        if(wallLevel) {
            // Board is part of a physical storage installation with rails and feet.
            box("endGrain",-.47f,.275f,-.221f,.027f,.59f,.038f)
            box("endGrain",.47f,.275f,-.221f,.027f,.59f,.038f)
            box("oak3",0f,.565f,-.215f,.97f,.025f,.060f)
        }
        // Wall shelf, its brackets, and side shelf all remain outside the working volume.
        if(!decorating) {
        box("oak3",.345f,.358f,-.553f,.50f,.023f,.165f)
        for(x in listOf(.16f,.52f)) {
            box("iron",x,.310f,-.605f,.018f,.076f,.024f)
            box("iron",x,.340f,-.559f,.018f,.013f,.112f)
        }
        box("oak4",-.614f,-.047f,.190f,.18f,.025f,.28f)
        box("oak2",-.650f,-.092f,.10f,.025f,.09f,.020f)
        box("oak2",-.650f,-.092f,.28f,.025f,.09f,.020f)
        }
        for((token,g) in batches) if(g.positions.isNotEmpty())
            r.part(parent,r.mesh("room-$token",g.model(),g.bounds()),token)
        sun=node().apply {
            transform.eulerAngles=EulerAngles(pitch=-48f,yaw=-32f)
            components.set(DirectionalLightComponent(Color4(1f,.92f,.80f,1f),520f,
                castsShadowEnabled=true))
        }
        parent.addChild(sun!!)
        // Broad cool bounce lifts upholstery and wood in the window's shaded corner.
        bounce=node().apply {
            transform.eulerAngles=EulerAngles(pitch=-28f,yaw=135f)
            components.set(DirectionalLightComponent(Color4(.81f,.89f,1f,1f),180f,
                castsShadowEnabled=false))
        }
        parent.addChild(bounce!!)
        validate(parent)
        return floor
    }
    fun mood(value: com.example.tidyspace.pc5853b2a.domain.model.RoomMood) {
        val evening=value==com.example.tidyspace.pc5853b2a.domain.model.RoomMood.Evening
        val sunset=value==com.example.tidyspace.pc5853b2a.domain.model.RoomMood.Sunset
        sun?.components?.get(DirectionalLightComponent::class.java)?.apply {
            color=if(evening) Color4(.55f,.66f,1f,1f) else if(sunset) Color4(1f,.67f,.38f,1f) else Color4(1f,.92f,.80f,1f)
            intensity=if(evening) 190f else if(sunset) 390f else 520f
        }
        bounce?.components?.get(DirectionalLightComponent::class.java)?.apply {
            color=if(evening) Color4(.70f,.78f,1f,1f) else Color4(.81f,.89f,1f,1f)
            intensity=if(evening) 105f else 180f
        }
        sun?.transform?.eulerAngles=EulerAngles(pitch=if(sunset) -22f else -48f,yaw=if(sunset) -65f else -32f)
    }

    private fun backWindow() {
        for(x in listOf(-.567f,-.113f))
            box("creamTrim",x,.355f,-.644f,.031f,.498f,.079f)
        for(y in listOf(.122f,.588f))
            box("creamTrim",-.34f,y,-.644f,.483f,.031f,.079f)
        box("creamTrim",-.34f,.111f,-.605f,.53f,.026f,.147f)
        box("creamTrim",-.34f,.355f,-.642f,.014f,.45f,.026f)
        box("creamTrim",-.34f,.351f,-.642f,.44f,.015f,.026f)
        box("goldObject",-.315f,.33f,-.620f,.014f,.038f,.009f)
    }

    private fun sideWindow() {
        for(z in listOf(-.438f,-.022f))
            box("creamTrim",-.713f,.31f,z,.078f,.503f,.030f)
        for(y in listOf(.072f,.548f))
            box("creamTrim",-.713f,y,-.23f,.078f,.030f,.447f)
        box("creamTrim",-.674f,.060f,-.23f,.154f,.027f,.49f)
        box("creamTrim",-.713f,.31f,-.23f,.027f,.46f,.014f)
        box("creamTrim",-.713f,.31f,-.23f,.027f,.014f,.40f)
    }

    private suspend fun desk(parent: Entity,r: SceneResources,level: Int) {
        val apron=if(level==2) "panelGreen" else "oak2"
        box(apron,0f,-.056f,.177f,.845f,.077f,.035f)
        box(apron,0f,-.056f,-.177f,.845f,.077f,.035f)
        for(x in listOf(-.405f,.405f))
            box(apron,x,-.056f,0f,.025f,.077f,.35f)
        for(x in listOf(-.398f,.398f)) for(z in listOf(-.17f,.17f)) {
            // Tapered legs preserve contact with the foundation top and desktop bottom.
            val g=Geometry()
            g.taperedLeg(x,z,-.32f,-.0175f,.029f,.045f)
            supports += r.part(parent,r.mesh("desk-leg:$x:$z",g.model(),g.bounds()),"oak2")
        }
        for(x in listOf(-.209f,.209f)) {
            box("oak3",x,-.052f,.198f,.394f,.060f,.014f)
            box("endGrain",x,-.052f,.206f,.063f,.014f,.009f)
            box("goldObject",x,-.052f,.213f,.048f,.009f,.009f)
        }
        box("oak1",0f,-.015f,.236f,.916f,.022f,.008f)
    }

    private fun parquet() {
        // Herringbone tiling: pair area=8*w*w; lattice determinant also=8*w*w.
        val w=.032f; val len=4*w; val c=sqrt(.5f)
        for(n in -14..14) for(m in -38..38) {
            val u=(4*n+m)*w; val v=(4*n-m)*w
            for(vertical in listOf(false,true)) {
                val x=u+if(vertical) len else 0f
                val dx=if(vertical) w else len; val dz=if(vertical) len else w
                fun rotated(a:Float,b:Float)=Vector3((a-b)*c,0f,(a+b)*c-.12f)
                val gap=.0008f
                val corners=listOf(rotated(x+gap,v+gap),rotated(x+dx-gap,v+gap),
                    rotated(x+dx-gap,v+dz-gap),rotated(x+gap,v+dz-gap))
                val clipped=clipFloor(corners)
                if(clipped.size<3) continue
                val shade=Math.floorMod(n*13+m*7+if(vertical) 3 else 0,6)
                geometry("oak$shade").prism(clipped,-.328f,-.32f)
                // Subtle geometry grain strokes, clipped against the same floor boundary.
                for(k in 1..2) {
                    val stripe=if(vertical) listOf(
                        rotated(x+dx*k/3,v+.007f),rotated(x+dx*k/3+.00035f,v+.007f),
                        rotated(x+dx*k/3+.00035f,v+dz-.012f),rotated(x+dx*k/3,v+dz-.012f))
                    else listOf(rotated(x+.009f,v+dz*k/3),rotated(x+dx-.012f,v+dz*k/3),
                        rotated(x+dx-.012f,v+dz*k/3+.00035f),rotated(x+.009f,v+dz*k/3+.00035f))
                    val grain=clipFloor(stripe)
                    if(grain.size>=3) geometry("woodGrain").top(grain,-.3198f)
                }
            }
        }
    }

    private fun clipFloor(input: List<Vector3>): List<Vector3> {
        var points=input
        for(axis in 0..3) {
            fun distance(p:Vector3)=when(axis) {
                0->p.x+.736f;1->.736f-p.x;2->p.z+.666f;else->.416f-p.z
            }
            val out=mutableListOf<Vector3>()
            if(points.isEmpty()) return points
            for(i in points.indices) {
                val a=points[i];val b=points[(i+1)%points.size]
                val da=distance(a);val db=distance(b)
                if(da>=0) out+=a
                if((da>=0)!=(db>=0)) out+=a+(b-a)*(da/(da-db))
            }
            points=out
        }
        return points
    }

    private suspend fun validate(owner:Entity) {
        suspend fun readyBounds(entity: Entity): BoundingBox? {
            repeat(30) {
                val bounds=entity.getVisualBounds(owner,recursive=true,enabledOnly=true)
                if(!bounds.isEmpty()) return bounds
                delay(16)
            }
            return null
        }
        val fb=readyBounds(floor)
        if(fb==null) {
            Log.w("TidyScene","native bounds pending; final Volume fit will validate room geometry")
            return
        }
        for((i,leg) in supports.withIndex()) {
            val b=readyBounds(leg)
            if(b==null) {
                Log.w("TidyScene","desk leg $i bounds pending; skipping early support diagnostic")
                continue
            }
            // Visible parquet is 8mm above the foundation.
            val delta=abs(b.min.y-(fb.max.y+.008f))
            check(delta<.002f) { "Desk leg support mismatch: $delta" }
            Log.i("TidyScene","room-support leg=$i owner=workbenchRoot floor=${fb.max.y+.008f} bottom=${b.min.y} delta=$delta PASS")
        }
        Log.i("TidyScene","room-ready open-front open-roof floor=1.50x1.12 walls=2 window-apertures=2 mesh-batches=${batches.size}")
    }

    /** Material-batched mesh; all faces use explicit normals and measured vertex bounds. */
    private class Geometry {
        val positions=mutableListOf<Vector3>()
        private val normals=mutableListOf<Vector3>()
        private val indices=mutableListOf<Int>()
        private fun face(p:List<Vector3>,n:Vector3) {
            val i=positions.size
            positions.addAll(p);repeat(p.size) { normals+=n }
            for(k in 1 until p.size-1) indices.addAll(listOf(i,i+k,i+k+1))
        }
        fun top(p:List<Vector3>,y:Float) =
            face(p.reversed().map { Vector3(it.x,y,it.z) },Vector3(0f,1f,0f))
        fun prism(p:List<Vector3>,bottom:Float,top:Float) {
            top(p,top)
            face(p.map { Vector3(it.x,bottom,it.z) },Vector3(0f,-1f,0f))
            for(i in p.indices) {
                val a=p[i];val b=p[(i+1)%p.size]
                val dx=b.x-a.x;val dz=b.z-a.z;val length=sqrt(dx*dx+dz*dz)
                if(length<.000001f) continue
                face(listOf(Vector3(a.x,bottom,a.z),Vector3(a.x,top,a.z),
                    Vector3(b.x,top,b.z),Vector3(b.x,bottom,b.z)),Vector3(dz/length,0f,-dx/length))
            }
        }
        fun box(c:Vector3,s:Vector3) = prism(listOf(
            Vector3(c.x-s.x/2,0f,c.z-s.z/2),Vector3(c.x+s.x/2,0f,c.z-s.z/2),
            Vector3(c.x+s.x/2,0f,c.z+s.z/2),Vector3(c.x-s.x/2,0f,c.z+s.z/2)),c.y-s.y/2,c.y+s.y/2)
        fun diamondOnX(x:Float,y:Float,z:Float,w:Float,h:Float) =
            face(listOf(Vector3(x,y-h,z),Vector3(x,y,z-w),Vector3(x,y+h,z),Vector3(x,y,z+w)),Vector3(1f,0f,0f))
        fun taperedLeg(x:Float,z:Float,b:Float,t:Float,bw:Float,tw:Float) {
            val lower=listOf(Vector3(x-bw/2,b,z-bw/2),Vector3(x+bw/2,b,z-bw/2),
                Vector3(x+bw/2,b,z+bw/2),Vector3(x-bw/2,b,z+bw/2))
            val upper=listOf(Vector3(x-tw/2,t,z-tw/2),Vector3(x+tw/2,t,z-tw/2),
                Vector3(x+tw/2,t,z+tw/2),Vector3(x-tw/2,t,z+tw/2))
            face(upper.reversed(),Vector3(0f,1f,0f));face(lower,Vector3(0f,-1f,0f))
            val ns=listOf(Vector3(0f,0f,-1f),Vector3(1f,0f,0f),Vector3(0f,0f,1f),Vector3(-1f,0f,0f))
            for(i in 0..3) face(listOf(lower[i],upper[i],upper[(i+1)%4],lower[(i+1)%4]),ns[i])
        }
        fun model()=MeshModel(positions,indices,normals)
        fun bounds():BoundingBox {
            val lo=Vector3(positions.minOf { it.x },positions.minOf { it.y },positions.minOf { it.z })
            val hi=Vector3(positions.maxOf { it.x },positions.maxOf { it.y },positions.maxOf { it.z })
            return BoundingBox((lo+hi)*.5f,(hi-lo)*.5f)
        }
    }
}
