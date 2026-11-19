package com.example.tidyspace.pc5853b2a.ui.sorting.scene

import com.example.tidyspace.pc5853b2a.domain.model.*
import com.example.tidyspace.pc5853b2a.domain.usecase.RoomPhysics
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import kotlin.math.*

/** One shared wheel, only visible for the tapped placed object. No per-item animation. */
internal class RoomAngleWheel(private val targets: MutableMap<Entity,String>) {
    private val root=node()
    private lateinit var ring: Entity
    private lateinit var needle: Entity
    private val ticks=mutableListOf<Entity>()
    suspend fun build(parent: Entity,r: SceneResources) {
        parent.addChild(root);root.enabled=false
        ring=r.part(root,r.ring(1f,.965f,.008f),"goldObject")
        needle=r.part(root,r.box(Vector3(.013f,.004f,.028f)),"leafLight")
        repeat(24) { n ->
            val tick=r.part(root,r.box(Vector3(.016f,.005f,.034f)),
                if(n%6==0) "goldObject" else "ceramic")
            interactive(tick,Vector3(.070f,.024f,.078f))
            targets[tick]="@angle:${n*15}"
            ticks+=tick
        }
    }
    fun synchronize(game: RoomGame) {
        val item=game.items.find { it.spec.id==game.angleFocus && !it.packed }
        root.enabled=game.decorating && item!=null &&
            (game.selectedId==null || game.selectedId.startsWith("@angle:"))
        if(!root.enabled) return
        val i=checkNotNull(item)
        val radius=hypot(i.spec.size.x,i.spec.size.z)/2+.055f
        root.transform.position=Vector3(i.position.x,i.position.y-i.spec.size.y/2+.008f,i.position.z)
        if(i.support=="@back") root.transform.position+=Vector3(0f,0f,radius+.020f)
        if(i.support=="@left") root.transform.position+=Vector3(radius+.020f,0f,0f)
        ring.transform.scaleVector=Vector3(radius,1f,radius)
        ticks.forEachIndexed { n,t ->
            val a=n*PI/12
            t.transform.position=Vector3((sin(a)*radius).toFloat(),0f,(cos(a)*radius).toFloat())
            t.transform.eulerAngles=EulerAngles(yaw=n*15f)
        }
        val degrees=RoomPhysics.placedAngle(i)
        val a=Math.toRadians(degrees.toDouble())
        needle.transform.position=Vector3((sin(a)*(radius-.020f)).toFloat(),.006f,(cos(a)*(radius-.020f)).toFloat())
        needle.transform.eulerAngles=EulerAngles(yaw=degrees)
    }
}
