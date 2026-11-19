package com.example.tidyspace.pc5853b2a.platform

import android.app.Application
import com.pico.spatial.ui.foundation.dsl.launch
import com.example.tidyspace.pc5853b2a.mainApp

class SpatialApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        launch(::mainApp)
    }
}
