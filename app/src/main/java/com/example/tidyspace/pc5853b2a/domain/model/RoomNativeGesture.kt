package com.example.tidyspace.pc5853b2a.domain.model

/**
 * Receives incremental frames from SpatialUI's sustained transform recognizer.
 * Captures one target for its whole lifetime; no raw ray/hand pose subtraction.
 */
class RoomNativeGesture(
    private val begin: (String)->Unit,
    private val transform: (GrabDelta)->Unit,
    private val end: ()->Unit,
    private val cancelled: ()->Unit,
    private val tap: (String)->Unit={},
    private val captureImmediately: (String)->Boolean={ false },
    private val allowCompression: (String)->Boolean={ false },
) {
    var active=false
        private set
    private var target: String?=null
    val captured get()=target!=null
    private var count=0
    private var pendingTarget: String?=null
    private var pendingTap: String?=null
    private var tapEligible=true
    fun start() {
        cancel()
        active=true
    }
    fun frame(id: String?,delta: GrabDelta,tapId: String?=id) {
        if(!active || !delta.translation.finite() || !delta.rotation.finite) return
        val hands=delta.pointerCount.coerceIn(1,2)
        if(hands==2) tapEligible=false
        if(pendingTarget==null && id!=null) {
            pendingTarget=id;pendingTap=tapId
        }
        // Track contacts even before capture (including a pair-only scale).
        // Otherwise releasing its second hand could leak a midpoint jump into
        // the first single-hand drag frame.
        val changed=count!=0 && count!=hands
        count=hands
        if(changed) return
        if(target==null) {
            // A recognizer may emit a stationary first frame. Leave that tap
            // available to box/toy actions rather than capturing their bodies.
            val moving=if(hands==1) delta.translation.length()>1e-7f
                else kotlin.math.abs(delta.rotation.yaw())>1e-5f
            val pending=pendingTarget ?: return
            if(!moving && !captureImmediately(pending) &&
                !(hands==2 && allowCompression(pending) && delta.spanDelta.isFinite() &&
                    kotlin.math.abs(delta.spanDelta)>1e-7f)) return
            target=pendingTarget ?: return
            begin(checkNotNull(target))
        }
        // Joining/releasing a hand belongs to the recognizer's new baseline.
        // Discard that transition frame rather than mixing translation and yaw.
        transform(if(hands==1) delta.copy(rotation=Rotation(),spanDelta=0f,pointerCount=1)
            else delta.copy(translation=Point3(0f,0f,0f),
                rotation=responsiveTurn(delta.rotation),
                spanDelta=if(allowCompression(checkNotNull(target)) && delta.spanDelta.isFinite())
                    delta.spanDelta else 0f,pointerCount=2))
    }
    fun finish() {
        if(target!=null) end() else if(tapEligible) pendingTap?.let(tap)
        reset()
    }
    fun cancel() {
        if(target!=null) cancelled()
        reset()
    }
    private fun reset() {
        active=false;target=null;count=0;pendingTarget=null;pendingTap=null;tapEligible=true
    }
    companion object {
        const val TURN_GAIN=1.75f
        /** Continuous gain keeps sub-degree adjustments responsive. */
        fun responsiveTurn(rotation: Rotation)=Rotation.yaw(rotation.gravityTurn().yaw()*TURN_GAIN)
        /** SpatialUI view pixels are Y-down; Entity room metres are Y-up. */
        fun roomDelta(pixels: Point3,metersPerPixel: Float,fitScale: Float,roomYaw: Float): Point3 {
            if(!pixels.finite() || !metersPerPixel.isFinite() || metersPerPixel<=0f ||
                !fitScale.isFinite() || fitScale<=0f || !roomYaw.isFinite()) return Point3(0f,0f,0f)
            val meters=Point3(pixels.x,-pixels.y,pixels.z)*(metersPerPixel/fitScale)
            return Rotation.yaw(-roomYaw).rotate(meters)
        }
    }
}
