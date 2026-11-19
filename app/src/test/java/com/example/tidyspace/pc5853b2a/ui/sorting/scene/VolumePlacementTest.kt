package com.example.tidyspace.pc5853b2a.ui.sorting.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.example.tidyspace.pc5853b2a.domain.model.*

class VolumePlacementTest {
    @Test fun roofBandRemainsClearThroughEveryRoomTurn() {
        val authored=listOf(-1.8f,2.6f).flatMap { x ->
            listOf(-.571f,.94f).flatMap { y -> listOf(-1.6f,2.0f).map { z -> Point3(x,y,z) } }
        }
        for(halfX in listOf(.8f,1.08f,3f)) for(degrees in 0..360 step 5) {
            val points=authored.map { Rotation.yaw(degrees.toFloat()).rotate(it) }
            val halfY=halfX
            val height=halfX*1.65f*302f/1440f
            val p=VolumePlacement.solveBounds(halfX,halfY,halfX,
                points.minOf { it.x },points.maxOf { it.x },points.minOf { it.y },points.maxOf { it.y },
                points.minOf { it.z },points.maxOf { it.z },height+.14f)
            val roof=points.maxOf { it.y }*p.scale+p.y
            assertTrue("Roof must clear the UI at yaw=$degrees",halfY-.035f-height-roof>=.13f)
            assertEquals(-halfY+.008f,points.minOf { it.y }*p.scale+p.y,.00001f)
        }
    }
    @Test fun contentBottomAndFrontAreAnchoredToSystemSurfaces() {
        val p=VolumePlacement.solve(
            halfX=1.08f,halfY=1.08f,halfZ=1.08f,
            radiusX=1.55f,radiusZ=1.55f,lowY=-.5f,highY=.7f,
        )
        assertEquals(-1.08f+.008f,p.y-.5f*p.scale,.0001f)
        assertEquals(1.08f-.018f,p.z+1.55f*p.scale,.0001f)
    }

    @Test fun yawSafeBoundsRemainInsideVolume() {
        val p=VolumePlacement.solve(
            halfX=.8f,halfY=.6f,halfZ=.7f,
            radiusX=1.4f,radiusZ=1.2f,lowY=-.4f,highY=.9f,
        )
        assertTrue(1.4f*p.scale<=.8f-.03f+.0001f)
        assertTrue(p.y-.4f*p.scale>=-.6f)
        assertTrue(p.y+.9f*p.scale<=.6f)
        assertTrue(p.z-1.2f*p.scale>=-.7f)
        assertTrue(p.z+1.2f*p.scale<=.7f)
    }
    @Test fun asymmetricRoomUsesWindowWidthAndFitsEveryViewingAngle() {
        val authored=listOf(-1.8f,2.6f).flatMap { x ->
            listOf(-.571f,.94f).flatMap { y -> listOf(-1.6f,2.0f).map { z -> Point3(x,y,z) } }
        }
        for(degrees in 0..360) {
            val points=authored.map { Rotation.yaw(degrees.toFloat()).rotate(it) }
            val p=VolumePlacement.solveBounds(1.08f,1.08f,1.08f,
                points.minOf { it.x },points.maxOf { it.x },points.minOf { it.y },points.maxOf { it.y },
                points.minOf { it.z },points.maxOf { it.z })
            points.forEach {
                assertTrue(kotlin.math.abs(it.x*p.scale+p.x)<=1.05f+.00001f)
                assertTrue(it.y*p.scale+p.y>=-1.072f-.00001f)
                assertTrue(it.y*p.scale+p.y<=1.05f+.00001f)
                assertTrue(it.z*p.scale+p.z>=-1.05f-.00001f)
                assertTrue(it.z*p.scale+p.z<=1.062f+.00001f)
            }
            if(degrees==0) {
                val old=VolumePlacement.solve(1.08f,1.08f,1.08f,
                    kotlin.math.hypot(2.6f,2f)+.10f,kotlin.math.hypot(2.6f,2f)+.10f,-.571f,.94f)
                assertTrue(p.scale>old.scale*1.45f)
            }
        }
    }
}
