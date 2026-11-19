package com.example.tidyspace.pc5853b2a.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.foundation.geometry.DpOffset3D
import com.pico.spatial.ui.foundation.geometry.NormalizedPoint3D
import com.pico.spatial.ui.platform.LengthUnit
import com.pico.spatial.ui.platform.LocalPhysicalLengthConverter

/** One opening plane for room management and loading; +Z points to the user. */
internal object FrontWindowPlacement {
    val anchor=NormalizedPoint3D.Front
    const val setbackMeters=.6f

    @Composable
    fun offset(): DpOffset3D {
        val setback=LocalPhysicalLengthConverter.current.lengthToDp(setbackMeters,LengthUnit.Meters)
        return DpOffset3D(0.dp,0.dp,-setback)
    }
}
