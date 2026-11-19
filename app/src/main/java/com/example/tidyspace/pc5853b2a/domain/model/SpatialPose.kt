package com.example.tidyspace.pc5853b2a.domain.model

import kotlin.math.*

/** SDK-independent unit quaternion, in the room owner's right-handed coordinates. */
data class Rotation(val x: Float=0f,val y: Float=0f,val z: Float=0f,val w: Float=1f) {
    val finite get()=listOf(x,y,z,w).all { it.isFinite() } && x*x+y*y+z*z+w*w>1e-10f
    fun normalized(): Rotation {
        if(!finite) return Rotation()
        val n=sqrt(x*x+y*y+z*z+w*w)
        return Rotation(x/n,y/n,z/n,w/n)
    }
    fun inverse()=normalized().let { Rotation(-it.x,-it.y,-it.z,it.w) }
    operator fun times(b: Rotation)=Rotation(
        w*b.x+x*b.w+y*b.z-z*b.y,w*b.y-x*b.z+y*b.w+z*b.x,
        w*b.z+x*b.y-y*b.x+z*b.w,w*b.w-x*b.x-y*b.y-z*b.z).normalized()
    fun rotate(v: Point3): Point3 {
        val q=normalized();val u=Point3(q.x,q.y,q.z)
        return v+u.cross(v)*(2*q.w)+u.cross(u.cross(v))*2f
    }
    fun yaw(): Float {
        val forward=rotate(Point3(0f,0f,1f))
        return Math.toDegrees(atan2(forward.x,forward.z).toDouble()).toFloat()
    }
    /** Twist about gravity, discarding wrist pitch/roll without a 180-degree flip. */
    fun gravityTurn(): Rotation {
        val q=normalized()
        return if(q.y*q.y+q.w*q.w<1e-6f) Rotation() else Rotation(0f,q.y,0f,q.w).normalized()
    }
    companion object {
        fun axis(axis: Point3,degrees: Float): Rotation {
            val n=axis.length()
            if(n<1e-6f || !degrees.isFinite()) return Rotation()
            val a=Math.toRadians(degrees.toDouble()).toFloat()/2
            val s=sin(a)/n
            return Rotation(axis.x*s,axis.y*s,axis.z*s,cos(a))
        }
        fun yaw(degrees: Float)=axis(Point3(0f,1f,0f),degrees)
        fun between(a: Point3,b: Point3): Rotation {
            if(a.length()<.005f || b.length()<.005f) return Rotation()
            val u=a*(1/a.length());val v=b*(1/b.length());val dot=u.dot(v).coerceIn(-1f,1f)
            if(dot < -.9999f) {
                val perpendicular=u.cross(if(abs(u.x)<.8f) Point3(1f,0f,0f) else Point3(0f,1f,0f))
                return axis(perpendicular,180f)
            }
            val c=u.cross(v)
            return Rotation(c.x,c.y,c.z,1f+dot).normalized()
        }
    }
}
operator fun Point3.minus(other: Point3)=Point3(x-other.x,y-other.y,z-other.z)
operator fun Point3.times(value: Float)=Point3(x*value,y*value,z*value)
fun Point3.dot(b: Point3)=x*b.x+y*b.y+z*b.z
fun Point3.cross(b: Point3)=Point3(y*b.z-z*b.y,z*b.x-x*b.z,x*b.y-y*b.x)
fun Point3.length()=sqrt(dot(this))
fun Point3.finite()=x.isFinite() && y.isFinite() && z.isFinite()

data class HandPose(val position: Point3,val rotation: Rotation)
data class GrabDelta(val translation: Point3,val rotation: Rotation,val seconds: Float=1f/60,
    val spanDelta: Float=0f,val pointerCount: Int=1)

/** Rebaselines on contact count changes; two contacts rotate about their midpoint. */
class GrabTracker(private val uprightPair: Boolean=false) {
    private val hands=linkedMapOf<Long,HandPose>()
    private var baselineValid=true
    val count get()=hands.size
    fun contains(id: Long)=id in hands
    fun down(id: Long,pose: HandPose) {
        if(pose.position.finite() && pose.rotation.finite) { hands[id]=pose;baselineValid=true }
    }
    fun up(id: Long) { hands.remove(id);baselineValid=true }
    fun clear() { hands.clear();baselineValid=true }
    fun update(updates: Map<Long,HandPose>): GrabDelta? {
        if(hands.isEmpty()) return null
        val samplesValid=updates.filterKeys { it in hands }.values.all { it.position.finite() && it.rotation.finite }
        val before=hands.values.take(2)
        updates.forEach { (id,pose) ->
            if(id in hands && pose.position.finite() && pose.rotation.finite) hands[id]=pose
        }
        val after=hands.values.take(2)
        val measure=baselineValid && samplesValid
        baselineValid=samplesValid
        if(!measure) return GrabDelta(Point3(0f,0f,0f),Rotation(),pointerCount=hands.size)
        val oldCenter=before.map { it.position }.reduce { a,b->a+b }*(1f/before.size)
        val newCenter=after.map { it.position }.reduce { a,b->a+b }*(1f/after.size)
        val oldSpan=if(before.size==2) before[1].position-before[0].position else Point3(0f,0f,0f)
        val newSpan=if(after.size==2) after[1].position-after[0].position else Point3(0f,0f,0f)
        val rotation=if(before.size==1) after[0].rotation*before[0].rotation.inverse()
            else if(uprightPair) {
                if(hypot(oldSpan.x,oldSpan.z)<.05f || hypot(newSpan.x,newSpan.z)<.05f) Rotation()
                else Rotation.yaw(Math.toDegrees(atan2(
                    oldSpan.z*newSpan.x-oldSpan.x*newSpan.z,
                    oldSpan.x*newSpan.x+oldSpan.z*newSpan.z).toDouble()).toFloat())
            } else Rotation.between(oldSpan,newSpan)
        val span=if(before.size==2) (after[1].position-after[0].position).length()-
            (before[1].position-before[0].position).length() else 0f
        // Turning a placed item with two hands keeps its pivot and support in place.
        return GrabDelta(if(uprightPair && before.size==2) Point3(0f,0f,0f) else newCenter-oldCenter,
            rotation,spanDelta=span,pointerCount=before.size)
    }
}
