package com.example.tidyspace.pc5853b2a.platform

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import com.example.tidyspace.pc5853b2a.ui.navigation.SpaceSession
import com.pico.spatial.ui.platform.stub.SpatialStubActivity

class FullSpaceActivity: SpatialStubActivity()

/** Native caption close uses the same public lifecycle as closeWindowContainer(). */
internal class SpaceLifecycle(private val session: ()->SpaceSession): Application.ActivityLifecycleCallbacks {
    private fun isWindow(activity: Activity)=activity is SpatialStubActivity && activity !is FullSpaceActivity
    override fun onActivityCreated(activity: Activity,savedInstanceState: Bundle?) {
        if(activity is FullSpaceActivity) {
            Log.i("TidySpaceMode","stage-created activity=${System.identityHashCode(activity)} state=${session().modes.state.value}")
        }
        if(isWindow(activity)) {
            Log.i("TidySpaceMode","window-created activity=${System.identityHashCode(activity)} session=${session().id}")
        }
    }
    override fun onActivityPrePaused(activity: Activity) {
        if(isWindow(activity)) {
            session().snapshotUi()
            if(activity.isFinishing && !activity.isChangingConfigurations) session().prepareNativeClose()
        }
    }
    override fun onActivityPreDestroyed(activity: Activity) {
        if(isWindow(activity) && activity.isFinishing && !activity.isChangingConfigurations) {
            session().prepareNativeClose()
        }
    }
    override fun onActivityDestroyed(activity: Activity) {
        if(activity is FullSpaceActivity) {
            Log.i("TidySpaceMode","stage-destroyed activity=${System.identityHashCode(activity)} state=${session().modes.state.value}")
            session().modes.stageDestroyed()
        }
        else if(isWindow(activity) && !activity.isChangingConfigurations) {
            if(session().nativeHandover) session().nativeWindowDestroyed()
            else session().finishSharedSession()
        }
    }
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity,outState: Bundle) {
        if(isWindow(activity)) session().snapshotUi()
    }
}
