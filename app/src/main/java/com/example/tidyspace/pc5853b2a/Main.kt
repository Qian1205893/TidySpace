package com.example.tidyspace.pc5853b2a

import com.example.tidyspace.pc5853b2a.ui.sorting.TidyHome
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.dsl.DefaultWindowContainer
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope
import com.pico.spatial.ui.foundation.dsl.Stage
import com.example.tidyspace.pc5853b2a.platform.FullSpaceActivity
import com.example.tidyspace.pc5853b2a.ui.navigation.FULL_SPACE_STAGE
import com.example.tidyspace.pc5853b2a.ui.navigation.SpaceModeControl
import com.example.tidyspace.pc5853b2a.ui.navigation.SpaceSessionUi
import com.example.tidyspace.pc5853b2a.ui.navigation.BindSpaceWindow
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
                SpaceSessionUi("main-window") {
                    BindSpaceWindow()
                    TidyHome()
                    SpaceModeControl()
                }
            }
            }
        }
        // Mixed Stage owns Full Space; the live default volume remains the app's
        // content in both modes, so changing modes does not replace its composition.
        Stage(id=FULL_SPACE_STAGE,targetActivity=FullSpaceActivity::class.java) {
            PicoTheme { }
        }
    }
