package com.example.tidyspace.pc5853b2a.ui.sorting

import com.example.tidyspace.pc5853b2a.domain.model.*
import com.example.tidyspace.pc5853b2a.ui.sorting.scene.SharedVolume
import org.junit.Assert.*
import org.junit.Test

class ViewerFacingTest {
    @Test fun everyHouseAngleFacesCaptionbarWithoutGlobalHeadCoordinates() {
        for(yaw in listOf(-359f,-180f,-111.6f,-90f,0f,47f,90f,179f,270f,359f)) {
            val facing=SharedVolume.captionFacing(yaw)
            val window=Rotation.yaw(yaw)*facing
            assertTrue((window.rotate(Point3(0f,0f,1f))-Point3(0f,0f,1f)).length()<.00001f)
            assertTrue((window.rotate(Point3(0f,1f,0f))-Point3(0f,1f,0f)).length()<.00001f)
        }
    }
    @Test fun frontPointsFromTheObjectTowardTheViewerWithoutTilting() {
        for(viewer in listOf(Point3(0f,2f,2f),Point3(2f,2f,0f),Point3(-2f,2f,-2f))) {
            val position=Point3(.3f,.1f,.2f)
            val q=checkNotNull(SharedVolume.facingToward(position,viewer))
            val desired=(viewer-position).copy(y=0f).let { it*(1f/it.length()) }
            assertTrue((q.rotate(Point3(0f,0f,1f))-desired).length()<.000001f)
            assertEquals(Point3(0f,1f,0f),q.rotate(Point3(0f,1f,0f)))
        }
    }
    @Test fun invalidOrVerticalDirectionDoesNotGenerateAnArbitraryTurn() {
        assertNull(SharedVolume.facingToward(Point3(0f,0f,0f),Point3(0f,2f,0f)))
        assertNull(SharedVolume.facingToward(Point3(0f,0f,0f),Point3(Float.NaN,0f,0f)))
    }
}
