package com.example.tidyspace.pc5853b2a.ui.sorting.scene

import android.os.SystemClock
import android.os.Trace
import android.util.Log
import com.example.tidyspace.pc5853b2a.BuildConfig
import com.example.tidyspace.pc5853b2a.domain.model.*
import com.pico.spatial.ui.foundation.gesture.SpatialPointerInfo

/** Debug-only observation; never changes gestures, scene poses or saved game data. */
internal inline fun <T> dragTrace(name: String, block: () -> T): T {
    if (!BuildConfig.DEBUG) return block()
    Trace.beginSection(name)
    return try { block() } finally { Trace.endSection() }
}

internal class DragDiagnostics {
    private var frames = 0
    private var lastFrame = 0L
    private var frameGapMaxMs = 0L
    private var lastInput = 0L
    private var lastReport = 0L
    private var batches = 0
    private var downs = 0
    private var ups = 0
    private var lost = 0
    private var sourceChanges = 0
    private var stationaryRaw = 0
    private var stationaryView = 0
    private var invalidRaw = 0
    private var gapMaxMs = 0L
    private var ageMaxMs = 0L
    private var costMaxUs = 0L
    private val sources = linkedMapOf<Long, String>()
    private val raw = mutableMapOf<Long, Point3>()
    private val view = mutableMapOf<Long, Point3>()

    fun frame() {
        if (!BuildConfig.DEBUG) return
        val now = SystemClock.uptimeMillis()
        if (lastFrame != 0L) frameGapMaxMs = maxOf(frameGapMaxMs, now - lastFrame)
        lastFrame = now
        frames++
    }

    fun input(
        events: List<SpatialPointerInfo>,
        before: RoomGame,
        read: () -> RoomGame,
        model: () -> Point3?,
        block: () -> Boolean,
    ): Boolean {
        if (!BuildConfig.DEBUG) return block()
        val started = System.nanoTime()
        val now = SystemClock.uptimeMillis()
        val result = dragTrace("Tidy.input") { block() }
        val after = read()
        val trackedEvents = events.filter { it.pressed || it.isUpEvent() || it.pointerId.value in sources }
        if (trackedEvents.isEmpty() &&
            before.selectedId == null && after.selectedId == null) return result
        if (trackedEvents.any { it.isDownEvent() } && sources.isEmpty()) reset(now)
        if (lastInput != 0L) gapMaxMs = maxOf(gapMaxMs, now - lastInput)
        lastInput = now
        batches++
        costMaxUs = maxOf(costMaxUs, (System.nanoTime() - started) / 1000)
        trackedEvents.forEach { e ->
            ageMaxMs = maxOf(ageMaxMs, (now - e.uptimeMillis).coerceAtLeast(0))
            if (e.isDownEvent()) downs++
            if (e.isUpEvent()) ups++
            if (!e.pressed && !e.isUpEvent() && e.pointerId.value in sources) lost++
            val id = e.pointerId.value
            val source = e.kind.name
            if (sources[id]?.let { it != source } == true) sourceChanges++
            sources[id] = source
            val r = e.inputDevicePose.rawPosition.let { Point3(it.x, it.y, it.z) }
            val v = Point3(e.x, e.y, e.z)
            if (!r.finite()) invalidRaw++
            if (e.pressed && !e.isDownEvent()) {
                if (raw[id] == r) stationaryRaw++
                if (view[id] == v) stationaryView++
            }
            raw[id] = r
            view[id] = v
        }
        val terminal = trackedEvents.any { it.isUpEvent() || !it.pressed }
        if (terminal || now - lastReport >= 200L) {
            val e = trackedEvents.firstOrNull()
            val native = model()
            val expected = after.selected?.position
            val error = if (native != null && expected != null) (native - expected).length() else null
            Log.i("TidyDragDiag", "bootNs=${SystemClock.elapsedRealtimeNanos()} " +
                "selected=${before.selectedId}->${after.selectedId} consumed=$result " +
                "batches=$batches down=$downs up=$ups lost=$lost sources=${sources.values.toSet()} " +
                "switches=$sourceChanges rawStill=$stationaryRaw viewStill=$stationaryView invalidRaw=$invalidRaw " +
                "inputGapMaxMs=$gapMaxMs eventAgeMaxMs=$ageMaxMs callbackMaxUs=$costMaxUs " +
                "frames=$frames frameGapMaxMs=$frameGapMaxMs " +
                "view=${e?.position3D} raw=${e?.inputDevicePose?.rawPosition} " +
                "game=$expected model=$native modelErrorM=$error terminal=$terminal")
            lastReport = now
        }
        trackedEvents.filter { !it.pressed }.forEach {
            sources.remove(it.pointerId.value)
            raw.remove(it.pointerId.value)
            view.remove(it.pointerId.value)
        }
        return result
    }

    private fun reset(now: Long) {
        frames = 0; lastFrame = 0L; frameGapMaxMs = 0L
        lastInput = 0L; lastReport = now - 200L
        batches = 0; downs = 0; ups = 0; lost = 0; sourceChanges = 0
        stationaryRaw = 0; stationaryView = 0; invalidRaw = 0
        gapMaxMs = 0L; ageMaxMs = 0L; costMaxUs = 0L
        sources.clear(); raw.clear(); view.clear()
    }
}
