package com.example.tidyspace.pc5853b2a

import android.content.Intent
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.tidyspace.pc5853b2a.data.repository.CleaningExpansionPersistence
import com.example.tidyspace.pc5853b2a.domain.model.*
import com.example.tidyspace.pc5853b2a.domain.usecase.MovingHome
import com.example.tidyspace.pc5853b2a.domain.usecase.StudyRenovation
import com.example.tidyspace.pc5853b2a.platform.LaunchActivity
import com.example.tidyspace.pc5853b2a.ui.sorting.RoomViewModel
import com.example.tidyspace.pc5853b2a.ui.sorting.scene.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Stage measurements against the current user's room; an isolated VM never saves. */
@RunWith(AndroidJUnit4::class)
class DragLatencyNativeRuntimeTest {
    @Test fun replayUserRoomMeasuresRealInputStageCosts(): Unit=runBlocking {
        val i=InstrumentationRegistry.getInstrumentation()
        i.runOnMainSync { i.targetContext.startActivity(Intent(i.targetContext,LaunchActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        delay(1800)
        withContext(Dispatchers.Main) {
            val prefs=i.targetContext.getSharedPreferences("tidy-room",0)
            val saved=checkNotNull(CleaningExpansionPersistence.restore(
                prefs.getString("snapshot",null),
                prefs.getString(CleaningExpansionPersistence.KEY,null)))
            val initial=StudyRenovation.prepare(MovingHome.prepare(saved,fresh=false))
            var vm=RoomViewModel(initial) {}
            val scene=RoomScene()
            val facingTimes=mutableListOf<Long>()
            val reducerTimes=mutableListOf<Long>()
            val modelTimes=mutableListOf<Long>()
            fun dispatch(a: RoomAction) {
                val t0=System.nanoTime()
                dragTrace("Tidy.replay.cartons") {
                    val rotations=scene.cartonFacing()
                    if(rotations.isNotEmpty()) vm.onEvent(RoomAction.SyncCartonFacing(rotations))
                }
                val t1=System.nanoTime()
                dragTrace("Tidy.replay.reduce") { vm.onEvent(a) }
                val t2=System.nanoTime()
                scene.applyInput(vm.currentGame)
                val t3=System.nanoTime()
                if(a is RoomAction.Transform) {
                    facingTimes+=t1-t0;reducerTimes+=t2-t1;modelTimes+=t3-t2
                }
            }
            fun summary(name: String,values: List<Long>) {
                val sorted=values.sorted()
                Log.i("TidyReplay","stage=$name samples=${sorted.size} " +
                    "p50us=${sorted[sorted.size/2]/1000} " +
                    "p95us=${sorted[(sorted.size*.95).toInt()]/1000} maxus=${sorted.last()/1000}")
            }
            // Real GazePinch raw-position deltas from the 14:50 mug capture:
            // .0004 room metres per raw view pixel at the measured room fit.
            val raw=listOf(
                Point3(2704.7f,1470.3f,8964.9f),Point3(2862.9f,2088.9f,8431.4f),
                Point3(2703.2f,2109.6f,8536.8f),Point3(2712f,2082.9f,8552.1f),
                Point3(2918.2f,2010.1f,8465.2f),Point3(3320.3f,1868.5f,8511.1f),
                Point3(3450.9f,1830.1f,8590.6f),Point3(3415.3f,1795.3f,8637.3f),
                Point3(2901.9f,1967.2f,8545f),Point3(2511.8f,2114.1f,8829.3f),
                Point3(2427f,2131.6f,9005f),Point3(2411.8f,2128.5f,9066.5f),
                Point3(2712.7f,2228.1f,9033.8f),Point3(3221.3f,2356.5f,8959f),
                Point3(3567.4f,2450.6f,9043.4f),Point3(3840.2f,2449f,9211.6f),
                Point3(3989.7f,2384.3f,9504.5f),Point3(3710.4f,2271.4f,10161f),
                Point3(3481.3f,2162.8f,9214.9f),Point3(3114.3f,2111.4f,8620.6f),
                Point3(2958f,2101.6f,8381.3f),Point3(2771.2f,2109.1f,8300.6f),
                Point3(2597.8f,2092.6f,8217.2f),Point3(2737.2f,1959.6f,8008.4f))
            try {
                scene.bindState { vm.currentGame };scene.build(vm.currentGame)
                for(id in listOf("mug","chair","studybanker")) {
                    assertNotNull(initial.items.find { it.spec.id==id && !it.packed })
                    repeat(4) {
                        vm=RoomViewModel(initial) {}
                        scene.synchronize(vm.currentGame)
                        dispatch(RoomAction.Begin(id,scene.viewerFacing(id)))
                        val origin=checkNotNull(vm.currentGame.origin)
                        for(frame in 1 until raw.size) {
                            val d=raw[frame]-raw[frame-1]
                            val steps=if(frame==1) 1 else 6
                            repeat(steps) {
                                dispatch(scene.dragFrame(GrabDelta(
                                    Point3(d.x*.0004f/steps,-d.y*.0004f/steps,d.z*.0004f/steps),Rotation())))
                                assertEquals(id,vm.currentGame.selectedId)
                                assertEquals(vm.currentGame.selected!!.position.vector(),scene.inputPosition())
                                // Interpolate 200ms telemetry at the observed 30Hz
                                // cadence; retain the full initial tracking jump.
                                delay(if(frame==1) 299 else 33)
                            }
                        }
                        dispatch(RoomAction.Cancel)
                        assertEquals(origin,vm.currentGame.items)
                    }
                    summary("$id.cartons",facingTimes);summary("$id.reducer",reducerTimes)
                    summary("$id.model",modelTimes)
                    facingTimes.clear();reducerTimes.clear();modelTimes.clear()
                }
                Log.i("TidyReplay","PASS current user room; actual recorded deltas; all model poses exact; cancel restores isolated VM; no prefs writes")
            } finally { scene.destroy() }
        }
    }
}
