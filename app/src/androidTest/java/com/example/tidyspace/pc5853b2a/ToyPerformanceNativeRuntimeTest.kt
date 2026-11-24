package com.example.tidyspace.pc5853b2a

import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.tidyspace.pc5853b2a.domain.model.*
import com.example.tidyspace.pc5853b2a.data.repository.LocalHomeRepository
import com.example.tidyspace.pc5853b2a.platform.*
import com.example.tidyspace.pc5853b2a.ui.sorting.*
import com.example.tidyspace.pc5853b2a.ui.sorting.scene.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Use real Compose/Android clocks: test clocks distort animated attachment backpressure. */
class ToyPerformanceNativeRuntimeTest {
    @Test fun repeatedStrikesInBothSpaces() {
        val i=InstrumentationRegistry.getInstrumentation()
        val context=i.targetContext
        check(context.packageName.endsWith(".perftest"))
        val session=(context.applicationContext as SpatialApplication).spaceSession
        fun <T> main(block: ()->T): T {
            var result: Result<T>?=null
            i.runOnMainSync { result=runCatching(block) }
            return checkNotNull(result).getOrThrow()
        }
        fun await(timeout: Long=30_000,condition: ()->Boolean) {
            val end=SystemClock.uptimeMillis()+timeout
            while(!condition()) {
                check(SystemClock.uptimeMillis()<end) { "Native benchmark wait timed out" }
                SystemClock.sleep(30)
            }
        }
        main { context.startActivity(Intent(context,LaunchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        var scene: RoomScene?=null
        await(90_000) {
            main {
                scene=session.retain("room-scene-0",::RoomScene) { it.destroy() }
                scene!!.presented
            }
        }
        val root=checkNotNull(scene)
        val vm=main { ViewModelProvider(session,RoomViewModelFactory(context))[RoomViewModel::class.java] }
        val toys=main { vm.currentGame.items.filter { !it.packed &&
            it.spec.kind in setOf(Furnishing.Woodfish,Furnishing.SingingBowl) } }
        assertTrue(toys.isNotEmpty())
        for(mode in listOf(SpaceMode.Shared,SpaceMode.Full)) {
            main { session.modes.request(mode) }
            await { main { session.modes.state.value.let { it.mode==mode && !it.switching } && root.presented } }
            var last=0L
            var strikes=0
            val intervals=mutableListOf<Long>()
            var previous=0L
            val before=main { vm.currentGame.playSequence }
            val callback=object: Choreographer.FrameCallback {
                override fun doFrame(now: Long) {
                    if(previous!=0L) intervals+=now-previous
                    previous=now
                    if(now-last>=180_000_000L) {
                        val item=toys[strikes%toys.size]
                        dragTrace("Tidy.profileClick") {
                            fun dispatch(action: RoomAction) {
                                val cartons=root.cartonFacing()
                                if(cartons.isNotEmpty()) vm.onEvent(RoomAction.SyncCartonFacing(cartons))
                                vm.onEvent(action)
                                root.applyInput(vm.currentGame)
                            }
                            dispatch(RoomAction.Begin("@play:${item.spec.id}"))
                            dispatch(RoomAction.End)
                        }
                        strikes++
                        last=now
                    }
                    Choreographer.getInstance().postFrameCallback(this)
                }
            }
            main { Choreographer.getInstance().postFrameCallback(callback) }
            try {
                File(context.filesDir,"space-capture-request").writeText(mode.name.lowercase())
                await(120_000) {
                    File(context.filesDir,"space-capture-ack").run { exists() && readText().trim()==mode.name.lowercase() }
                }
            } finally {
                main { Choreographer.getInstance().removeFrameCallback(callback) }
            }
            val sequence=main { vm.currentGame.playSequence }
            assertEquals(strikes.toLong(),sequence-before)
            assertTrue(strikes>=20)
            val sorted=intervals.sorted()
            val record="mode=$mode strikes=$strikes frames=${sorted.size} p95ms=${sorted[(sorted.size*.95).toInt()]/1e6} maxms=${sorted.last()/1e6}"
            File(context.filesDir,"toy-profile-${mode.name.lowercase()}.txt").writeText(record)
            Log.i("TidyToyProfile",record)
        }
        main { session.modes.request(SpaceMode.Shared) }
        await { main { session.modes.state.value.mode==SpaceMode.Shared && !session.modes.state.value.switching } }
        await { main { !vm.saveInFlight } }
        val saved=checkNotNull(LocalHomeRepository(context).load())
        assertEquals(main { vm.currentGame.playCounts },saved.playCounts)
        assertFalse(main { vm.currentGame.saveError })
    }
}
