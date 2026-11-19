package com.example.tidyspace.pc5853b2a.ui.sorting.scene

import kotlin.math.min

internal data class VolumePlacementResult(
    val scale: Float,
    val y: Float,
    val z: Float,
    val x: Float=0f,
)

internal data class VolumeEnvelope(
    val radiusX: Float,
    val radiusZ: Float,
    val lowY: Float,
    val highY: Float,
    val minX: Float=-radiusX,
    val maxX: Float=radiusX,
    val minZ: Float=-radiusZ,
    val maxZ: Float=radiusZ,
)

/**
 * Fits yaw-safe content into a volume, then anchors it to the system base panel
 * and the front edge where the CaptionBar appears.
 */
internal object VolumePlacement {
    private const val SIDE_GAP=.03f
    private const val BASE_GAP=.008f
    private const val TOP_GAP=.03f
    private const val CAPTION_GAP=.018f
    private const val BACK_GAP=.03f

    fun solve(
        halfX: Float,
        halfY: Float,
        halfZ: Float,
        radiusX: Float,
        radiusZ: Float,
        lowY: Float,
        highY: Float,
    ): VolumePlacementResult {
        return solveBounds(halfX,halfY,halfZ,-radiusX,radiusX,lowY,highY,-radiusZ,radiusZ)
    }

    fun solveBounds(halfX: Float,halfY: Float,halfZ: Float,
                    minX: Float,maxX: Float,lowY: Float,highY: Float,minZ: Float,maxZ: Float,
                    reservedTop: Float=0f): VolumePlacementResult {
        require(min(halfX,min(halfY,halfZ))>0f)
        require(maxX>minX && maxZ>minZ && highY>lowY)
        val scale=min(
            (2*halfX-2*SIDE_GAP)/(maxX-minX),
            min(
                (2*halfY-BASE_GAP-TOP_GAP-reservedTop)/(highY-lowY),
                (2*halfZ-CAPTION_GAP-BACK_GAP)/(maxZ-minZ),
            ),
        )
        require(scale.isFinite() && scale>0f)
        return VolumePlacementResult(
            scale=scale,
            y=-halfY+BASE_GAP-lowY*scale,
            z=halfZ-CAPTION_GAP-maxZ*scale,
            x=-(maxX+minX)*.5f*scale,
        )
    }
}
