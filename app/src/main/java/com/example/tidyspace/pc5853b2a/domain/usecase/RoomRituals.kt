package com.example.tidyspace.pc5853b2a.domain.usecase

import com.example.tidyspace.pc5853b2a.domain.model.*
import kotlin.math.*

/** One 24-hour dial: midnight at top, noon at bottom. All positions are house metres. */
object RoomTime {
    val clock=RoomLayout.point(Point3(.84f,.39f,-.948f))
    val dimmer=RoomLayout.point(Point3(.84f,.15f,-.948f))
    fun wrap(hour: Float)=((hour%24f)+24f)%24f
    fun hour(mood: RoomMood)=when(mood) { RoomMood.Morning->9f;RoomMood.Sunset->17f;RoomMood.Evening->21f }
    fun period(hour: Float)=when(wrap(hour)) {
        in 6f..<16f->RoomMood.Morning
        in 16f..<19f->RoomMood.Sunset
        else->RoomMood.Evening
    }
    fun dial(point: Point3): Float? {
        val p=point-clock
        return if(hypot(p.x,p.y)<.025f) null else
            wrap(Math.toDegrees(atan2(p.x,p.y).toDouble()).toFloat()/15f)
    }
    fun shortestHours(from: Float,to: Float)=((to-from+36f)%24f)-12f
    data class Light(val sun: Float,val bounce: Float,val color: Point3,val pitch: Float,val yaw: Float)
    private val anchors=listOf(
        0f to Light(4f,2f,Point3(.57f,.67f,1f),-54f,-40f),
        5f to Light(6f,3f,Point3(.57f,.67f,1f),-8f,30f),
        // Light local -Z points through the rear/left windows toward the room.
        9f to Light(520f,105f,Point3(1f,.95f,.86f),-38f,-145f),
        13f to Light(620f,120f,Point3(1f,.97f,.91f),-58f,-160f),
        17f to Light(270f,30f,Point3(1f,.67f,.38f),-18f,-120f),
        19f to Light(14f,4f,Point3(.62f,.67f,.93f),-10f,-70f),
        21f to Light(6f,3f,Point3(.57f,.67f,1f),-54f,-40f),
        24f to Light(4f,2f,Point3(.57f,.67f,1f),-54f,-40f),
    )
    fun lighting(hour: Float): Light {
        val h=wrap(hour)
        val (a,b)=anchors.zipWithNext().first { h>=it.first.first && h<=it.second.first }
        val t=(h-a.first)/(b.first-a.first)
        fun mix(x: Float,y: Float)=x+(y-x)*t
        return Light(mix(a.second.sun,b.second.sun),mix(a.second.bounce,b.second.bounce),
            a.second.color+(b.second.color-a.second.color)*t,
            mix(a.second.pitch,b.second.pitch),mix(a.second.yaw,b.second.yaw))
    }
    fun label(hour: Float): String {
        val minutes=(wrap(hour)*60).roundToInt()%1440
        return "%02d:%02d".format(minutes/60,minutes%60)
    }
}

/** Fixed house fixtures never enter the furniture/cleaning placement path. */
object RoomRituals {
    private val ids=setOf("@house:clock","@house:dimmer","@house:undo","@house:redo")
    fun isTarget(id: String?)=id in ids
    fun begin(s: RoomGame,a: RoomAction.Begin)=if(!isTarget(a.id)) s else s.copy(
        selectedId=a.id,ritual=HouseGesture(s.dayHour,s.indoorBrightness,s.lampOn,a.contact?.takeIf { it.finite() }),
        message=when(a.id) {
            "@house:clock"->"沿表盘拨动指针，让小屋从晨光走到星夜。"
            "@house:dimmer"->"转一转调亮暗；轻点开关暖灯。"
            else->"轻点回转箭头，回到喜欢的那一步。"
        })
    private fun twist(d: GrabDelta): Float {
        val q=d.rotation.normalized()
        if(q.z*q.z+q.w*q.w<1e-6f) return 0f
        val radians=2*atan2(q.z,q.w)
        return atan2(sin(radians),cos(radians))
    }
    fun motion(s: RoomGame,d: GrabDelta): RoomGame {
        val g=s.ritual ?: return s
        if(!d.translation.finite() || !d.rotation.finite) return s
        val p=g.point?.plus(d.translation)
        val turn=twist(d)
        val next=g.copy(point=p,travel=g.travel+d.translation.length(),turn=g.turn+abs(turn))
        return when(s.selectedId) {
            "@house:clock"-> {
                val before=g.point?.let(RoomTime::dial);val after=p?.let(RoomTime::dial)
                val hours=if(before!=null && after!=null) RoomTime.shortestHours(before,after)
                    else -turn*12f/PI.toFloat()
                // A tracked jump cannot whirl the day around unexpectedly.
                if(d.translation.length()>.25f) return s.copy(ritual=next)
                val h=RoomTime.wrap(s.dayHour+hours)
                s.copy(dayHour=h,mood=RoomTime.period(h),ritual=next)
            }
            "@house:dimmer"-> {
                val brightness=(s.indoorBrightness-turn/(PI.toFloat()*1.5f)+d.translation.y*2.5f).coerceIn(0f,1f)
                s.copy(indoorBrightness=brightness,lampOn=brightness>0f,ritual=next)
            }
            else->s.copy(ritual=next)
        }
    }
    fun release(s: RoomGame): RoomGame {
        val g=s.ritual ?: return s
        val tapped=g.travel<.016f && g.turn<.07f
        val next=when(s.selectedId) {
            "@house:clock"-> {
                val h=if(tapped) g.point?.let(RoomTime::dial) ?: s.dayHour else s.dayHour
                s.copy(dayHour=h,mood=RoomTime.period(h),message="${RoomTime.label(h)} · 看看窗外的新光景")
            }
            "@house:dimmer"-> {
                val value=if(tapped) (if(g.brightness>0f) 0f else .65f) else s.indoorBrightness
                s.copy(indoorBrightness=value,lampOn=value>0f,
                    message=if(value==0f) "暖灯熄了，炉火还在。" else "暖灯亮度 ${(value*100).roundToInt()}%")
            }
            else->s
        }
        return next.copy(selectedId=null,ritual=null)
    }
    fun cancel(s: RoomGame): RoomGame {
        val g=s.ritual ?: return s
        return s.copy(dayHour=g.hour,mood=RoomTime.period(g.hour),indoorBrightness=g.brightness,
            lampOn=g.lampOn,selectedId=null,ritual=null)
    }
}
