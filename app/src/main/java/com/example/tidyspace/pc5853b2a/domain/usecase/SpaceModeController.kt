package com.example.tidyspace.pc5853b2a.domain.usecase

import com.example.tidyspace.pc5853b2a.domain.model.SpaceMode
import com.example.tidyspace.pc5853b2a.domain.model.SpaceModeState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** Implementations must report the OS space state, rather than the last requested mode. */
interface SpaceModePlatform {
    fun current(): SpaceMode?
    suspend fun enter(): Boolean
    suspend fun exit()
    fun restoreWindow()
}

/**
 * One worker owns all SDK calls. Requests can change during an SDK suspension;
 * a native close always overrides an in-flight entry. No application state is
 * serialized to disk as part of a mode change.
 */
class SpaceModeController(
    private val platform: SpaceModePlatform,
    scope: CoroutineScope,
    private val timeoutMillis: Long=8_000,
) {
    private val mutable=MutableStateFlow(SpaceModeState(mode=platform.current() ?: SpaceMode.Shared))
    val state=mutable.asStateFlow()
    private val wake=Channel<Unit>(Channel.CONFLATED)
    private var restore=false
    private var mustExit=false
    private var closingWindow=false
    private var rejectLateEntry=false

    init {
        scope.launch {
            for(signal in wake) reconcile()
        }
    }

    fun request(mode: SpaceMode) {
        if(closingWindow && mode==SpaceMode.Full) return
        if(mode==SpaceMode.Full) rejectLateEntry=false
        mutable.value=mutable.value.copy(requested=mode,error=null,
            switching=mode!=mutable.value.mode || mustExit)
        wake.trySend(Unit)
    }

    fun nativeWindowClosing() {
        closingWindow=true
        rejectLateEntry=true
        restore=true
        mustExit=true
        mutable.value=mutable.value.copy(requested=SpaceMode.Shared,switching=true,
            recoveringWindow=true,error=null)
        wake.trySend(Unit)
    }

    /** A system/menu close of the Stage also returns the surviving window to Shared. */
    fun stageDestroyed() {
        // Activity destruction can trail a successful re-entry into a new Stage.
        // Only the OS mode is authoritative; a stale callback must not close it.
        // An in-flight transition owns its confirmation and timeout handling.
        observeSystemMode()
    }

    fun windowReady() {
        closingWindow=false
        mutable.value=mutable.value.copy(recoveringWindow=false)
    }

    fun observeSystemMode() {
        val actual=platform.current() ?: return
        if(!mutable.value.switching && actual==SpaceMode.Full && rejectLateEntry) {
            mustExit=true
            request(SpaceMode.Shared)
            return
        }
        if(!mutable.value.switching && actual!=mutable.value.mode) {
            mutable.value=mutable.value.copy(mode=actual,requested=actual,
                revision=mutable.value.revision+1)
        }
    }

    private suspend fun await(mode: SpaceMode) {
        withTimeout(timeoutMillis) {
            while(platform.current()!=mode) delay(16)
        }
    }

    private suspend fun reconcile() {
        while(mustExit || mutable.value.requested!=mutable.value.mode || restore) {
            val entering=mutable.value.requested==SpaceMode.Full && !mustExit
            mutable.value=mutable.value.copy(switching=true)
            try {
                if(entering) {
                    val allowed=withTimeout(timeoutMillis) { platform.enter() }
                    if(!allowed) {
                        rejectLateEntry=true
                        // A close that arrived during a denied request still has to restore its window.
                        mutable.value=mutable.value.copy(requested=SpaceMode.Shared,
                            error=if(closingWindow) null else "暂时无法进入全屏空间，请重试。")
                        mustExit=true
                        continue
                    }
                    await(SpaceMode.Full)
                    mutable.value=mutable.value.copy(mode=SpaceMode.Full,revision=mutable.value.revision+1)
                } else {
                    withTimeout(timeoutMillis) { platform.exit() }
                    await(SpaceMode.Shared)
                    mustExit=false
                    mutable.value=mutable.value.copy(mode=SpaceMode.Shared,revision=mutable.value.revision+1)
                    if(restore) {
                        platform.restoreWindow()
                        restore=false
                    }
                }
            } catch(e: CancellationException) {
                if(e !is kotlinx.coroutines.TimeoutCancellationException) throw e
                if(entering) {
                    // Entry may have been accepted before its callback timed out.
                    mustExit=true
                    rejectLateEntry=true
                    mutable.value=mutable.value.copy(requested=SpaceMode.Shared,
                        error=if(closingWindow) null else "空间切换超时，请重试。")
                    continue
                }
                fail("空间切换超时，请重试。")
                return
            } catch(e: Exception) {
                if(entering) {
                    mustExit=true
                    rejectLateEntry=true
                    mutable.value=mutable.value.copy(requested=SpaceMode.Shared,
                        error="暂时无法进入全屏空间，请重试。")
                    continue
                }
                fail("未能返回共享空间，请重试。")
                return
            }
        }
        mutable.value=mutable.value.copy(switching=false)
    }

    private fun fail(message: String) {
        mutable.value=mutable.value.copy(mode=platform.current() ?: mutable.value.mode,
            switching=false,error=message)
    }
}
