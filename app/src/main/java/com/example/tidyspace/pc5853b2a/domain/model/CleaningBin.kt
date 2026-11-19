package com.example.tidyspace.pc5853b2a.domain.model

import kotlin.math.*

/** Capture refers to the opening, independent of the carried bin's heading. */
object CleaningBin {
    const val floorOffset=.295f
    fun near(p: Point3,bin: Point3,ready: Boolean=false): Boolean =
        p.finite() && hypot(p.x-bin.x,p.z-bin.z)<(if(ready) .24f else .19f) &&
            p.y>=bin.y-.055f && p.y<=bin.y+(if(ready) .34f else .28f)
    fun captures(c: CleaningState): Boolean {
        if(near(c.position,c.binPosition,c.binReady)) return true
        val old=c.previousTrash ?: return false
        val v=c.position-old
        val length=v.length()
        if(length<.0001f || length>.28f) return false
        val b=c.binPosition-old
        val t=((b.x*v.x+b.z*v.z)/(v.x*v.x+v.z*v.z).coerceAtLeast(.000001f)).coerceIn(0f,1f)
        return near(old+v*t,c.binPosition) &&
            hypot(c.position.x-c.binPosition.x,c.position.z-c.binPosition.z)<.32f
    }
}
