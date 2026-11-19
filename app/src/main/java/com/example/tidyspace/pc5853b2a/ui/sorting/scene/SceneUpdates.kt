package com.example.tidyspace.pc5853b2a.ui.sorting.scene

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import com.example.tidyspace.pc5853b2a.domain.model.Rotation

/** Native setters submit ECS changes even for identical values. Keep static nodes quiet. */
internal class SceneUpdates {
    private class Values {
        var enabled: Boolean?=null
        var position: Vector3?=null
        var scale: Vector3?=null
        var angles: EulerAngles?=null
        var rotation: Rotation?=null
    }
    private val values=mutableMapOf<Entity,Values>()
    private fun value(e: Entity)=values.getOrPut(e) { Values() }
    fun enabled(e: Entity,on: Boolean) {
        val v=value(e)
        if(v.enabled!=on) { e.enabled=on;v.enabled=on }
    }
    fun position(e: Entity,p: Vector3) {
        val v=value(e)
        if(v.position!=p) { e.transform.position=p;v.position=p }
    }
    fun scale(e: Entity,s: Vector3) {
        val v=value(e)
        if(v.scale!=s) { e.transform.scaleVector=s;v.scale=s }
    }
    fun angles(e: Entity,a: EulerAngles) {
        val v=value(e)
        if(v.angles!=a || v.rotation!=null) {
            e.transform.eulerAngles=a;v.angles=a;v.rotation=null
        }
    }
    fun rotation(e: Entity,q: Rotation) {
        val v=value(e)
        if(v.rotation!=q || v.angles!=null) {
            e.transform.quaternion=q.quat();v.rotation=q;v.angles=null
        }
    }
    fun forget(e: Entity) { values.remove(e) }
    fun retain(entities: Collection<Entity>) { values.keys.retainAll(entities.toSet()) }
    fun close() { values.clear() }
}
