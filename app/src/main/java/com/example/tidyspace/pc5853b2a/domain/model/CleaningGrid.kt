package com.example.tidyspace.pc5853b2a.domain.model

import kotlin.math.*

/** Cells occupy the same UV space as the exported continuous stain texture. */
object CleaningGrid {
    const val COLUMNS=8
    const val ROWS=3
    const val COUNT=COLUMNS*ROWS
    fun centre(m: Mess,index: Int): Point3 {
        val x=((index%COLUMNS+.5f)/COLUMNS-.5f)*m.size.x
        val b=((index/COLUMNS+.5f)/ROWS-.5f)*
            if(m.kind==MessKind.Wall) m.size.y else m.size.z
        return m.position+if(m.kind==MessKind.Wall) Point3(x,b,0f) else Point3(x,0f,b)
    }
    fun cells(c: CleaningState,m: Mess)=c.workTiles[m.id] ?: List(COUNT) { c.rawAmount(m.id) }
    const val NORMAL_TOLERANCE=.10f
    fun touches(m: Mess,index: Int,q: Point3,radius: Float): Boolean {
        val p=centre(m,index)
        val halfA=m.size.x/COLUMNS/2
        val halfB=(if(m.kind==MessKind.Wall) m.size.y else m.size.z)/ROWS/2
        val normal=if(m.kind==MessKind.Wall) abs(q.z-p.z) else abs(q.y-p.y)
        val a=max(0f,abs(q.x-p.x)-halfA)
        val b=max(0f,abs(if(m.kind==MessKind.Wall) q.y-p.y else q.z-p.z)-halfB)
        return normal<=NORMAL_TOLERANCE && hypot(a,b)<=radius
    }
    /** Integrate only the part of a swept segment inside the nozzle footprint. */
    fun work(m: Mess,index: Int,start: Point3,end: Point3,radius: Float): Float {
        val v=end-start
        val length=if(m.kind==MessKind.Wall) hypot(v.x,v.y) else hypot(v.x,v.z)
        if(length<.0001f) return 0f
        val steps=ceil(v.length()/.008f).toInt().coerceIn(1,40)
        var touchedSteps=0
        repeat(steps) { n ->
            val q=start+v*((n+.5f)/steps)
            if(touches(m,index,q,radius)) touchedSteps++
        }
        return length*touchedSteps/steps
    }
}
