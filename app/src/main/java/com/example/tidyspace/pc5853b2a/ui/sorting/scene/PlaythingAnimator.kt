package com.example.tidyspace.pc5853b2a.ui.sorting.scene

import com.example.tidyspace.pc5853b2a.domain.model.*
import com.example.tidyspace.pc5853b2a.domain.usecase.PlaythingRules
import com.example.tidyspace.pc5853b2a.domain.usecase.BodyPlayRules
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import kotlin.math.*

/** Instance-local, bounded motion. Only soft child nodes deform; support roots never move. */
internal class PlaythingAnimator {
    private data class Motion(var seen: Long=0,var start: Double=-100.0,var heldSince: Double=-1.0,
        var squeeze: Float=0f,var lid: Float=0f,var angle: Float=0f,var speed: Float=0f)
    private val motions=mutableMapOf<String,Motion>()
    private val updates=SceneUpdates()
    private var previous=0.0
    fun frame(game: RoomGame,parts: Map<String,Entity>,seconds: Double) {
        val dt=if(previous==0.0) .016f else (seconds-previous).toFloat().coerceIn(0f,.05f)
        previous=seconds
        motions.keys.retainAll(game.items.map { it.spec.id }.toSet())
        updates.retain(parts.values)
        for(item in game.items.filter { !it.packed && it.spec.kind.playable }) {
            val id=item.spec.id
            val saved=game.toyStates[id] ?: ToyState(detent=if(item.spec.kind==Furnishing.MagneticSlider) 2 else 0)
            val m=motions.getOrPut(id) { Motion(lid=if(saved.open) -105f else 0f) }
            val event=game.playEvent?.takeIf { it.id==id }
            if(event!=null && event.sequence!=m.seen) {
                m.seen=event.sequence;m.start=seconds
                if(item.spec.kind in setOf(Furnishing.Spinner,Furnishing.Windmill))
                    m.speed=(m.speed+1000f).coerceAtMost(1800f)
            }
            val age=seconds-m.start
            val held=playId(game.selectedId)==id ||
                (game.selectedId==id && BodyPlayRules.accepts(item.spec.kind))
            if(held && m.heldSince<0) m.heldSince=seconds
            if(!held) m.heldSince=-1.0
            fun part(name: String)=parts["$id:$name"]
            fun angle(name: String,pitch: Float=0f,yaw: Float=0f,roll: Float=0f) {
                part(name)?.let { updates.angles(it,EulerAngles(pitch=pitch,yaw=yaw,roll=roll)) }
            }
            when(item.spec.kind) {
                Furnishing.Rattle -> {
                    val direction=if((game.playCounts[id] ?: 0)%2==0L) 1 else -1
                    val swing=if(age in 0.0.. .24) (sin(age/.24*PI)*150*direction).toFloat() else 0f
                    angle("rattle_left",yaw=swing);angle("rattle_right",yaw=swing)
                }
                Furnishing.Pokeball -> {
                    val target=if(saved.open) -105f else 0f
                    m.lid+=(target-m.lid)*(1-exp(-dt*12))
                    angle("pokeball_lid",pitch=m.lid)
                    part("pokeball_star")?.let { updates.enabled(it,m.lid< -25f) }
                    angle("pokeball_star",yaw=if(saved.open && age in 0.0..2.0) (sin(age*PI)*15).toFloat() else 0f)
                }
                Furnishing.Mochi,Furnishing.Jelly -> {
                    val target=if(held) (.33f+game.toyGesture.compression+
                        ((seconds-m.heldSince)*.05).toFloat()).coerceAtMost(.53f) else 0f
                    val rate=if(held) 10f else if(item.spec.kind==Furnishing.Jelly) 2.2f else 6.5f
                    m.squeeze+=(target-m.squeeze)*(1-exp(-dt*rate))
                    if(abs(m.squeeze)<.0001f) m.squeeze=0f
                    val height=1-m.squeeze
                    val width=sqrt(1/height).coerceAtMost(1.38f)
                    part(if(item.spec.kind==Furnishing.Mochi) "mochi_soft" else "jelly_soft")?.let { updates.scale(it,Vector3(width,height,width)) }
                }
                Furnishing.PopBoard -> repeat(6) { index ->
                    part("popboard_$index")?.let { updates.scale(it,Vector3(1f,if(saved.bubbles and (1 shl index)!=0) -.55f else 1f,1f)) }
                }
                Furnishing.Spinner -> {
                    if(held) { m.angle=game.toyGesture.turn;m.speed=0f }
                    else { m.angle=(m.angle+m.speed*dt)%360;m.speed*=exp(-dt*1.25f) }
                    if(m.speed<.5f) m.speed=0f
                    angle("spinner_rotor",yaw=m.angle)
                }
                Furnishing.Tumbler -> {
                    val tilt=if(held) game.toyGesture.tilt
                        else if(age in 0.0..3.5) (cos(age*12)*24*exp(-age*1.8)).toFloat() else 0f
                    angle("tumbler_body",roll=tilt)
                    part("tumbler_body")?.let { updates.position(it,Vector3(0f,
                        -.08f+.055f*(1-cos(tilt*PI.toFloat()/180)),0f)) }
                }
                Furnishing.Ratchet -> angle("ratchet_knob",yaw=saved.detent*30f+
                    if(held) game.toyGesture.turn-PlaythingRules.detents(game.toyGesture.turn)*30 else 0f)
                Furnishing.MagneticSlider->part("slider_thumb")?.let {
                    updates.position(it,Vector3((saved.detent-2)*.022f,.006f,0f))
                }
                Furnishing.ToggleBox->repeat(3) { n->
                    angle("togglebox_$n",pitch=if(saved.bubbles and (1 shl n)!=0) 24f else -24f)
                }
                Furnishing.Abacus->repeat(5) { n->part("abacus_$n")?.let {
                    updates.position(it,Vector3((n-2)*.028f,if(saved.bubbles and (1 shl n)!=0) .023f else -.023f,0f))
                } }
                Furnishing.DeskBell->{
                    part("bell_button")?.let { updates.position(it,Vector3(0f,if(held) .020f else .031f,0f)) }
                    angle("bell_dome",roll=if(age in 0.0..1.5) (sin(age*65)*2*exp(-age*5)).toFloat() else 0f)
                }
                Furnishing.Hourglass->{
                    val target=if(saved.open) 180f else 0f
                    m.lid+=(target-m.lid)*(1-exp(-dt*9))
                    angle("hourglass_rotor",roll=m.lid)
                    val t=(age/12).toFloat().coerceIn(0f,1f)
                    val top=if(saved.open) t else 1-t
                    part("hourglass_sand_top")?.let { updates.scale(it,Vector3(1f,.03f+.97f*top,1f)) }
                    part("hourglass_sand_bottom")?.let { updates.scale(it,Vector3(1f,1-.97f*top,1f)) }
                    part("hourglass_stream")?.let { updates.enabled(it,age in 0.0..12.0) }
                }
                Furnishing.Windmill->{
                    if(held) { m.angle=game.toyGesture.turn }
                    else { m.angle=(m.angle+m.speed*dt)%360;m.speed*=exp(-dt*1.4f) }
                    angle("windmill_rotor",roll=-m.angle)
                    angle("windmill_crank",pitch=m.angle)
                }
                else -> Unit
            }
        }
    }
    fun close() { motions.clear();updates.close();previous=0.0 }
}
