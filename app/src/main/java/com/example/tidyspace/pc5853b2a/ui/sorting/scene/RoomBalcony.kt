package com.example.tidyspace.pc5853b2a.ui.sorting.scene

import android.util.Log
import com.example.tidyspace.pc5853b2a.domain.model.RoomLayout
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.resource.Material
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import com.pico.spatial.core.math.Vector3

/** A static increment under the authored balcony; the room owns its existing fit. */
internal object RoomBalcony {
    suspend fun complete(balcony: Entity,r: SceneResources) {
        if(balcony.findEntity("balcony_railing_completion")!=null) return
        fun finish(e: Entity): Material? =
            e.components[ModelComponent::class.java]?.materials?.firstOrNull {
                it is PhysicallyBasedMaterial && it.getName()=="Atelier | Ivory painted joinery"
            } ?: e.getChildren().firstNotNullOfOrNull(::finish)
        val material=checkNotNull(finish(balcony)) { "Balcony painted joinery material missing" }
        val root=node().also { it.setName("balcony_railing_completion");balcony.addChild(it) }
        val inner=1.13f*RoomLayout.scale
        val outer=1.49f*RoomLayout.scale
        val back=RoomLayout.balconyRailBack
        val front=RoomLayout.balconyRailFront
        suspend fun beam(name: String,size: Vector3,p: Vector3) {
            r.part(root,r.box(size,.002f),material,p).setName(name)
        }
        // Three perimeter edges; the inner edge remains an unobstructed passage.
        // Continuous top bars close the old cylinder/baluster end gaps.
        for((label,y,thickness) in listOf(Triple("lower",-.277f,.014f),Triple("upper",-.08f,.024f))) {
            beam("balcony_${label}_outer",Vector3(thickness,thickness,front-back+.016f),
                Vector3(outer,y,(back+front)/2))
            for((side,z) in listOf("back" to back,"front" to front)) {
                beam("balcony_${label}_$side",Vector3(outer-inner+.016f,thickness,thickness),
                    Vector3((inner+outer)/2,y,z))
            }
        }
        for((side,z) in listOf("back" to back,"front" to front)) {
            for((edge,x) in listOf("inner" to inner,"outer" to outer)) {
                beam("balcony_corner_${side}_$edge",Vector3(.024f,.252f,.024f),
                    Vector3(x,-.194f,z))
            }
            for((n,x) in listOf(1.215f,1.325f,1.43f).withIndex()) {
                beam("balcony_infill_${side}_$n",Vector3(.011f,.20f,.011f),
                    Vector3(x*RoomLayout.scale,-.18f,z))
            }
        }
        // Midpoints fill the wide original outer baluster intervals.
        repeat(15) { n ->
            val z=(-1.02f+.04f+(n+.5f)*(1.68f-.08f)/15)*RoomLayout.scale
            beam("balcony_infill_outer_$n",Vector3(.011f,.20f,.011f),Vector3(outer,-.18f,z))
        }
        Log.i("TidyBalcony","complete parts=${root.getChildren().size} same-material=true inner-edge=open")
    }
}
