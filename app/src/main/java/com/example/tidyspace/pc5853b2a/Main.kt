package com.example.tidyspace.pc5853b2a

import com.example.tidyspace.pc5853b2a.ui.sorting.TidyHome
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.dsl.DefaultWindowContainer
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import com.pico.spatial.ui.platform.LocalPhysicalLengthConverter
import com.pico.spatial.ui.platform.LengthUnit

fun mainApp(scope: SpatialAppScope) =
    with(scope) {
        DefaultWindowContainer {
            val units=LocalPhysicalLengthConverter.current
            Box(Modifier.windowConstraints(
                width=units.lengthToDp(6f,LengthUnit.Meters),
                height=units.lengthToDp(4.5f,LengthUnit.Meters),
                depth=units.lengthToDp(6f,LengthUnit.Meters),
            )) {
            PicoTheme {
                TidyHome()
            }
            }
        }
    }
