package com.example.tidyspace.pc5853b2a.ui.navigation

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.example.tidyspace.pc5853b2a.domain.model.SpaceMode
import com.example.tidyspace.pc5853b2a.domain.usecase.SpaceModeController
import com.example.tidyspace.pc5853b2a.domain.usecase.SpaceModePlatform
import com.example.tidyspace.pc5853b2a.platform.SpatialApplication
import com.example.tidyspace.pc5853b2a.platform.LaunchActivity
import com.pico.spatial.core.container.SpaceState
import com.pico.spatial.core.container.getSelfSpaceState
import com.pico.spatial.ui.platform.containers.StageStyle
import com.pico.spatial.ui.platform.containers.OpenStageResult
import com.pico.spatial.ui.platform.containers.SpatialNavigator
import com.pico.spatial.ui.platform.containers.LocalSpatialNavigator
import com.pico.spatial.ui.platform.containers.openStage
import com.pico.spatial.ui.platform.containers.closeStage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.UUID

const val FULL_SPACE_STAGE="TidyFullSpace"

/** Process/session-owned state, independent of the native window Activity. */
class SpaceSession(private val context: Context): ViewModelStoreOwner {
    override val viewModelStore=ViewModelStore()
    val id: String=UUID.randomUUID().toString()
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    var nativeHandover=false
        private set
    private val snapshots=mutableMapOf<String,Map<String,List<Any?>>>()
    private val registries=mutableMapOf<String,SaveableStateRegistry>()
    private data class Resource(val value: Any,val close: ()->Unit)
    private val resources=mutableMapOf<String,Resource>()
    private val unbind=mutableMapOf<String,()->Unit>()
    private var transitionStart=0L
    internal var windowNavigator: SpatialNavigator?=null
    internal fun closeNativeWindow()=checkNotNull(windowNavigator).closeWindowContainer()
    val modes=SpaceModeController(object: SpaceModePlatform {
        override fun current()=when(context.getSelfSpaceState()) {
            SpaceState.SHARED_SPACE->SpaceMode.Shared
            SpaceState.FULL_SPACE->SpaceMode.Full
            else->null
        }
        override suspend fun enter(): Boolean {
            transitionStart=SystemClock.elapsedRealtime()
            val result=context.openStage(FULL_SPACE_STAGE,style=StageStyle.Mixed)
            Log.i("TidySpaceMode","enter-result=$result session=$id")
            return result==OpenStageResult.Allowed
        }
        override suspend fun exit() {
            transitionStart=SystemClock.elapsedRealtime()
            closeStage()
        }
        override fun restoreWindow() {
            Log.i("TidySpaceMode","restore-native-window session=$id")
            // The public launcher forwards to the registered default window.
            // Application/session/ViewModelStore are already live and are not restarted.
            context.startActivity(Intent(context,LaunchActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    },scope)

    val preserveInput get()=nativeHandover || modes.state.value.switching

    fun snapshotUi() {
        registries.forEach { (key,registry)->snapshots[key]=registry.performSave() }
    }
    fun registry(key: String): SaveableStateRegistry {
        check(key !in registries) { "Two live UI hosts share registry $key" }
        return SaveableStateRegistry(snapshots.remove(key)) { true }.also { registries[key]=it }
    }
    fun releaseRegistry(key: String,registry: SaveableStateRegistry) {
        if(registries[key]===registry) {
            // Native onPause snapshots before rememberSaveable unregisters its providers.
            registries.remove(key)
        }
    }
    @Suppress("UNCHECKED_CAST")
    fun <T: Any> retain(key: String,create: ()->T,close: (T)->Unit): T =
        (resources[key]?.value as? T) ?: create().also { value ->
            resources[key]=Resource(value) { close(value) }
        }
    fun bindResource(key: String,detach: ()->Unit) { unbind[key]=detach }
    fun releaseResource(key: String,retain: Boolean=true) {
        unbind.remove(key)?.invoke()
        if(!nativeHandover || !retain) resources.remove(key)?.close?.invoke()
    }
    fun prepareNativeClose() {
        if(!preserveInput && modes.state.value.mode!=SpaceMode.Full &&
            modes.state.value.requested!=SpaceMode.Full) return
        nativeHandover=true
        snapshotUi()
        // Remove stable model roots before SpatialView tears its host down.
        unbind.values.toList().forEach { it() }
        unbind.clear()
    }
    fun nativeWindowDestroyed() {
        if(nativeHandover) modes.nativeWindowClosing()
    }
    fun windowReady() {
        nativeHandover=false
        modes.windowReady()
        Log.i("TidySpaceMode","window-ready session=$id retained=${resources.size} elapsed=${SystemClock.elapsedRealtime()-transitionStart}ms")
    }
    fun finishSharedSession() {
        if(nativeHandover) return
        resources.values.forEach { it.close() }
        resources.clear();unbind.clear()
        viewModelStore.clear()
        registries.clear();snapshots.clear()
    }
}

@Composable
fun rememberSpaceSession(): SpaceSession {
    val context=androidx.compose.ui.platform.LocalContext.current
    return (context.applicationContext as SpatialApplication).spaceSession
}

/** Each AttachmentPanel has its own composition and receives its own stable registry. */
@Composable
fun SpaceSessionUi(key: String,content: @Composable ()->Unit) {
    val session=rememberSpaceSession()
    val registry=remember(session,key) { session.registry(key) }
    DisposableEffect(session,key,registry) {
        onDispose { session.releaseRegistry(key,registry) }
    }
    CompositionLocalProvider(LocalViewModelStoreOwner provides session,
        LocalSaveableStateRegistry provides registry,content=content)
}

@Composable
fun BindSpaceWindow() {
    val session=rememberSpaceSession()
    val navigator=LocalSpatialNavigator.current
    DisposableEffect(session,navigator) {
        session.windowNavigator=navigator
        onDispose { if(session.windowNavigator===navigator) session.windowNavigator=null }
    }
}
