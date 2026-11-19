package com.example.tidyspace.pc5853b2a.domain.model

/** Authored horizontal expansion; movable furniture keeps its original dimensions. */
object RoomLayout {
    const val scale=1.45f
    const val version=2
    fun point(p: Point3)=Point3(p.x*scale,p.y,p.z*scale)
    fun footprint(p: Point3)=Point3(p.x*scale,p.y,p.z*scale)
    val left=-1.057f*scale
    val back=-.952f*scale
    val right=1.086f*scale
    val front=.62f*scale
    val balconyRight=1.47f*scale
    val balconyBack=back
    val balconyFront=front
    val balconyRailBack=-1.01f*scale
    val balconyRailFront=.65f*scale
    val entryLeft=.25f*scale
    val entryFront=1.23f*scale
}
