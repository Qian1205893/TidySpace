package com.example.tidyspace.pc5853b2a.platform

import android.app.Application
import com.pico.spatial.ui.foundation.dsl.launch
import com.example.tidyspace.pc5853b2a.mainApp
import com.example.tidyspace.pc5853b2a.ui.navigation.SpaceSession

class SpatialApplication : Application() {
    val spaceSession by lazy { SpaceSession(this) }
    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(SpaceLifecycle { spaceSession })
        launch(::mainApp)
    }
}
