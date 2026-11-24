package com.example.tidyspace.pc5853b2a.domain.usecase

import com.example.tidyspace.pc5853b2a.domain.model.SpaceMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SpaceModeControllerTest {
    private class Platform: SpaceModePlatform {
        var actual: SpaceMode?=SpaceMode.Shared
        var enters=0
        var exits=0
        var restores=0
        var allowed=true
        var gate: CompletableDeferred<Unit>?=null
        var enterFailure=false
        var exitFailure=false
        var confirmEnter=true
        var confirmExit=true
        override fun current()=actual
        override suspend fun enter(): Boolean {
            enters++;gate?.await()
            if(enterFailure) error("entry service failure")
            if(allowed && confirmEnter) actual=SpaceMode.Full
            return allowed
        }
        override suspend fun exit() {
            exits++
            if(exitFailure) error("exit service failure")
            if(confirmExit) actual=SpaceMode.Shared
        }
        override fun restoreWindow() { restores++ }
    }

    @Test fun roundTripKeepsSameSessionAndPublishesConfirmedState()=runTest {
        val p=Platform();val c=SpaceModeController(p,backgroundScope)
        c.request(SpaceMode.Full);runCurrent()
        assertEquals(SpaceMode.Full,c.state.value.mode);assertFalse(c.state.value.switching)
        c.request(SpaceMode.Shared);runCurrent()
        assertEquals(SpaceMode.Shared,c.state.value.mode)
        assertEquals(1,p.enters);assertEquals(1,p.exits);assertEquals(0,p.restores)
    }
    @Test fun duplicateRequestsDoNotOpenDuplicateStages()=runTest {
        val p=Platform();val c=SpaceModeController(p,backgroundScope)
        repeat(20) { c.request(SpaceMode.Full) };runCurrent()
        repeat(20) { c.request(SpaceMode.Full) };runCurrent()
        assertEquals(1,p.enters);assertEquals(SpaceMode.Full,c.state.value.mode)
    }
    @Test fun nativeCloseDuringEntryWinsOverLateSuccess()=runTest {
        val p=Platform().apply { gate=CompletableDeferred() }
        val c=SpaceModeController(p,backgroundScope)
        c.request(SpaceMode.Full);runCurrent()
        c.nativeWindowClosing();c.request(SpaceMode.Full)
        p.gate!!.complete(Unit);runCurrent()
        assertEquals(SpaceMode.Shared,c.state.value.mode)
        assertEquals(1,p.restores);assertEquals(1,p.exits)
        assertTrue(c.state.value.recoveringWindow)
        c.windowReady();assertFalse(c.state.value.recoveringWindow)
    }
    @Test fun rapidFullSharedFullIsSerialized()=runTest {
        val p=Platform().apply { gate=CompletableDeferred() }
        val c=SpaceModeController(p,backgroundScope)
        c.request(SpaceMode.Full);runCurrent()
        c.request(SpaceMode.Shared);c.request(SpaceMode.Full)
        p.gate!!.complete(Unit);runCurrent()
        assertEquals(1,p.enters);assertEquals(0,p.exits)
        assertEquals(SpaceMode.Full,c.state.value.mode)
    }
    @Test fun requestSharedDuringEntryClosesLateStage()=runTest {
        val p=Platform().apply { gate=CompletableDeferred() }
        val c=SpaceModeController(p,backgroundScope)
        c.request(SpaceMode.Full);runCurrent();c.request(SpaceMode.Shared)
        p.gate!!.complete(Unit);runCurrent()
        assertEquals(SpaceMode.Shared,c.state.value.mode);assertEquals(1,p.exits)
    }
    @Test fun denialLeavesSharedAndAllowsRetry()=runTest {
        val p=Platform().apply { allowed=false };val c=SpaceModeController(p,backgroundScope)
        c.request(SpaceMode.Full);runCurrent()
        assertEquals(SpaceMode.Shared,c.state.value.mode);assertNotNull(c.state.value.error)
        p.allowed=true;c.request(SpaceMode.Full);runCurrent()
        assertEquals(SpaceMode.Full,c.state.value.mode);assertNull(c.state.value.error)
    }
    @Test fun nativeCloseDuringDenialStillRestoresWindow()=runTest {
        val p=Platform().apply { gate=CompletableDeferred();allowed=false }
        val c=SpaceModeController(p,backgroundScope)
        c.request(SpaceMode.Full);runCurrent();c.nativeWindowClosing()
        p.gate!!.complete(Unit);runCurrent()
        assertEquals(1,p.restores);assertNull(c.state.value.error)
    }
    @Test fun entryExceptionCleansUpExclusiveSpace()=runTest {
        val p=Platform().apply { enterFailure=true };val c=SpaceModeController(p,backgroundScope)
        c.request(SpaceMode.Full);runCurrent()
        assertEquals(SpaceMode.Shared,c.state.value.mode);assertEquals(1,p.exits)
        assertFalse(c.state.value.switching);assertNotNull(c.state.value.error)
    }
    @Test fun acceptedEntryWithoutOsConfirmationTimesOutAndReturnsShared()=runTest {
        val p=Platform().apply { confirmEnter=false };val c=SpaceModeController(p,backgroundScope,100)
        c.request(SpaceMode.Full);runCurrent()
        assertEquals(SpaceMode.Shared,c.state.value.mode);assertTrue(c.state.value.switching)
        advanceTimeBy(101);runCurrent()
        assertEquals(1,p.exits);assertFalse(c.state.value.switching)
    }
    @Test fun failedExitDoesNotLieAboutModeAndCanBeRetried()=runTest {
        val p=Platform();val c=SpaceModeController(p,backgroundScope)
        c.request(SpaceMode.Full);runCurrent();p.exitFailure=true
        c.nativeWindowClosing();runCurrent()
        assertEquals(SpaceMode.Full,c.state.value.mode);assertEquals(0,p.restores)
        assertNotNull(c.state.value.error)
        p.exitFailure=false;c.request(SpaceMode.Shared);runCurrent()
        assertEquals(SpaceMode.Shared,c.state.value.mode);assertEquals(1,p.restores)
    }
    @Test fun systemStageCloseSynchronizesSharedUi()=runTest {
        val p=Platform();val c=SpaceModeController(p,backgroundScope)
        c.request(SpaceMode.Full);runCurrent()
        p.actual=SpaceMode.Shared;c.stageDestroyed();runCurrent()
        assertEquals(SpaceMode.Shared,c.state.value.mode);assertEquals(0,p.restores)
        assertEquals(0,p.exits)
    }
    @Test fun delayedOldStageDestructionCannotCloseNewFullSpace()=runTest {
        val p=Platform();val c=SpaceModeController(p,backgroundScope)
        c.request(SpaceMode.Full);runCurrent()
        c.request(SpaceMode.Shared);runCurrent()
        c.request(SpaceMode.Full);runCurrent()
        c.stageDestroyed();runCurrent()
        assertEquals(SpaceMode.Full,c.state.value.mode)
        assertEquals(SpaceMode.Full,c.state.value.requested)
        assertFalse(c.state.value.switching)
        assertEquals(2,p.enters);assertEquals(1,p.exits)
    }
    @Test fun delayedOldStageDestructionDuringEntryDoesNotCancelRequest()=runTest {
        val p=Platform();val c=SpaceModeController(p,backgroundScope)
        c.request(SpaceMode.Full);runCurrent()
        c.request(SpaceMode.Shared);runCurrent()
        p.gate=CompletableDeferred()
        c.request(SpaceMode.Full);runCurrent()
        c.stageDestroyed();runCurrent()
        assertEquals(SpaceMode.Full,c.state.value.requested)
        p.gate!!.complete(Unit);runCurrent()
        assertEquals(SpaceMode.Full,c.state.value.mode)
        assertFalse(c.state.value.switching)
        assertEquals(2,p.enters);assertEquals(1,p.exits)
    }
    @Test fun unknownSystemStateDoesNotOverwriteConfirmedMode()=runTest {
        val p=Platform();val c=SpaceModeController(p,backgroundScope)
        c.request(SpaceMode.Full);runCurrent();p.actual=null;c.observeSystemMode()
        assertEquals(SpaceMode.Full,c.state.value.mode)
    }
    @Test fun lateOsEntryAfterTimeoutIsClosedInsteadOfResurrectingFullMode()=runTest {
        val p=Platform().apply { confirmEnter=false };val c=SpaceModeController(p,backgroundScope,100)
        c.request(SpaceMode.Full);runCurrent();advanceTimeBy(101);runCurrent()
        p.actual=SpaceMode.Full;c.observeSystemMode();runCurrent()
        assertEquals(SpaceMode.Shared,c.state.value.mode);assertEquals(2,p.exits)
        p.confirmEnter=true;c.request(SpaceMode.Full);runCurrent()
        assertEquals(SpaceMode.Full,c.state.value.mode)
    }
    @Test fun repeatedNativeCloseHasOneRestoreAndExit()=runTest {
        val p=Platform();val c=SpaceModeController(p,backgroundScope)
        c.request(SpaceMode.Full);runCurrent()
        repeat(10) { c.nativeWindowClosing() };runCurrent()
        assertEquals(1,p.restores);assertEquals(1,p.exits)
    }
}
