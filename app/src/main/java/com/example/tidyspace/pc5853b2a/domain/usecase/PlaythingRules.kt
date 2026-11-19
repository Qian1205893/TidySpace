package com.example.tidyspace.pc5853b2a.domain.usecase

import com.example.tidyspace.pc5853b2a.domain.model.*
import kotlin.math.*

/** Captured gestures only. No timer awards: motion must cross a physical threshold. */
object PlaythingRules {
    fun detents(degrees: Float)=((abs(degrees)/30+.0001f).toInt())*if(degrees<0) -1 else 1
    fun validTarget(s: RoomGame,target: String): RoomItem? {
        val item=s.items.find { it.spec.id==playId(target) && it.spec.kind.playable && !it.packed } ?: return null
        val suffix=target.removePrefix("@play:").substringAfter(':',"")
        val count=when(item.spec.kind) {
            Furnishing.PopBoard->6;Furnishing.ToggleBox->3;Furnishing.Abacus->5;else->0
        }
        return item.takeIf { if(count>0) suffix in (0 until count).map(Int::toString) else suffix.isEmpty() }
    }
    fun motion(s: RoomGame,item: RoomItem,d: GrabDelta): RoomGame {
        val old=s.toyGesture
        val q=item.rotation.inverse()*d.rotation*item.rotation
        val turn=when(item.spec.kind) {
            Furnishing.Rattle,Furnishing.Ratchet -> q.yaw()
            else -> 2*acos(abs(q.w).coerceIn(0f,1f))*180/PI.toFloat()
        }.coerceIn(-90f,90f)
        val elapsed=old.elapsed+d.seconds.takeIf { it.isFinite() }?.coerceIn(0f,.1f).orZero()
        val local=item.rotation.inverse().rotate(d.translation)
        val drive=if(item.spec.kind==Furnishing.Windmill) turn+local.length()*1800f else turn
        var g=old.copy(elapsed=elapsed,turn=(old.turn+drive).coerceIn(-7200f,7200f),
            slide=(old.slide+local.x).coerceIn(-1f,1f),
            tilt=(old.tilt+local.x*300f+local.z*180f).coerceIn(-24f,24f),
            compression=(old.compression-d.spanDelta.takeIf { it.isFinite() }.orZero()*5).coerceIn(0f,.35f))
        var next=s
        if(item.spec.kind==Furnishing.Rattle && abs(turn)>.25f) {
            val direction=if(turn>0) 1 else -1
            val swing=if(direction==old.direction) old.swing+abs(turn) else abs(turn)
            // First swing arms the mechanism. Only alternating strokes produce a beat.
            val armed=old.pulses==0 && old.lastPulse<0
            if(swing>=18 && (direction!=old.direction || old.swing<18)) {
                if(!armed && elapsed-old.lastPulse>=.09f) {
                    next=award(next,item,1,"咚！鼓点 +1")
                    g=g.copy(lastPulse=elapsed,pulses=old.pulses+1)
                } else if(armed) g=g.copy(lastPulse=elapsed)
            }
            g=g.copy(direction=direction,swing=swing.coerceAtMost(180f))
        }
        if(item.spec.kind==Furnishing.Ratchet) {
            val before=detents(old.turn)
            val after=detents(g.turn)
            val steps=after-before
            if(steps!=0) {
                val state=next.toyStates[item.spec.id] ?: ToyState()
                val detent=(state.detent+steps).mod(12)
                next=next.copy(toyStates=next.toyStates+(item.spec.id to state.copy(detent=detent)))
                next=award(next,item,abs(steps),"咔哒 · 第 ${detent+1} 档")
                g=g.copy(pulses=old.pulses+abs(steps))
            }
        }
        if(item.spec.kind==Furnishing.MagneticSlider) {
            val steps=(g.slide/.018f).toInt()-(old.slide/.018f).toInt()
            val state=next.toyStates[item.spec.id] ?: ToyState(detent=2)
            val slot=(state.detent+steps).coerceIn(0,4)
            if(slot!=state.detent) {
                next=next.copy(toyStates=next.toyStates+(item.spec.id to state.copy(detent=slot)))
                next=award(next,item,abs(slot-state.detent),"咔哒 · 滑块 ${slot+1}/5")
            }
        }
        if(item.spec.kind==Furnishing.Windmill) {
            val beats=(g.turn/90f).toInt()-(old.turn/90f).toInt()
            if(beats>0) next=award(next,item,beats,"风车转起来了")
        }
        return next.copy(toyGesture=g)
    }
    private fun Float?.orZero()=this ?: 0f
    fun release(s: RoomGame,item: RoomItem): RoomGame {
        val old=s.toyStates[item.spec.id] ?: ToyState()
        return when(item.spec.kind) {
            Furnishing.Rattle -> s.copy(message="往复摇转木柄，让两颗珠子轮流敲鼓。")
            Furnishing.Ratchet -> s.copy(message="旋钮停在第 ${old.detent+1} 档。")
            Furnishing.Pokeball -> {
                if(s.gestureDistance>.055f) return s.copy(message="已取消开合；抓住底座可搬动精灵球。")
                val next=s.copy(toyStates=s.toyStates+(item.spec.id to old.copy(open=!old.open)))
                if(!old.open && !RoomPhysics.toyClear(next,item.spec.id))
                    s.copy(message="球盖上方或后面被挡住了，挪出一点空间再打开。")
                else award(next,item,1,if(old.open) "星光收好啦" else "发现一颗小星光 ✦")
            }
            Furnishing.PopBoard -> {
                if(s.gestureDistance>.055f) return s
                val bit=s.selectedId?.substringAfterLast(':')?.toIntOrNull()?.takeIf { it in 0..5 } ?: return s
                val mask=old.bubbles xor (1 shl bit)
                val completed=mask==63 && old.roundReady
                // All six stay pressed; the next round starts after all are released.
                val round=if(completed) (old.rounds+1).coerceAtMost(1_000_000_000) else old.rounds
                val next=s.copy(toyStates=s.toyStates+(item.spec.id to old.copy(bubbles=mask,rounds=round,
                    roundReady=if(completed) false else if(mask==0) true else old.roundReady)))
                award(next,item,1,if(completed) "彩虹完成！第 $round 轮" else
                    "${if(mask and (1 shl bit)!=0) "啵，按下" else "啪，弹回"} · ${Integer.bitCount(mask)}/6")
            }
            Furnishing.Mochi,Furnishing.Jelly -> award(s,item,1,if(item.spec.kind==Furnishing.Mochi) "呼——团子回弹中" else "果冻慢慢回到原形")
            Furnishing.Spinner -> if(s.crankRadians<.12f && s.gestureDistance<.006f)
                s.copy(message="轻拨一下叶片，再松开，陀螺才会转起来。") else award(s,item,1,"转起来了！")
            Furnishing.Tumbler -> if(s.crankRadians<.08f && s.gestureDistance<.004f)
                s.copy(message="轻轻推斜企鹅，再松开。") else award(s,item,1,"摇摇晃晃，还是站得稳")
            Furnishing.ToggleBox,Furnishing.Abacus -> {
                val bit=s.selectedId?.substringAfterLast(':')?.toIntOrNull() ?: return s
                val next=s.copy(toyStates=s.toyStates+(item.spec.id to old.copy(bubbles=old.bubbles xor (1 shl bit))))
                award(next,item,1,if(item.spec.kind==Furnishing.ToggleBox) "嗒 · 拨片切换" else "嗒嗒 · 木珠滑到另一边")
            }
            Furnishing.DeskBell->award(s,item,1,"叮——")
            Furnishing.Hourglass->award(s.copy(toyStates=s.toyStates+(item.spec.id to old.copy(open=!old.open))),item,1,"翻过来，慢慢等一会儿")
            Furnishing.MagneticSlider->s.copy(message="磁吸滑块稳稳停在卡点")
            Furnishing.Windmill->s.copy(message="风车缓缓停下")
            else -> s
        }
    }
    fun award(s: RoomGame,item: RoomItem,amount: Int,text: String): RoomGame {
        val count=((s.playCounts[item.spec.id] ?: 0)+amount).coerceAtMost(1_000_000_000L)
        val sequence=s.playSequence+1
        return s.copy(playCounts=s.playCounts+(item.spec.id to count),playSequence=sequence,
            playEvent=PlayEvent(sequence,item.spec.id,item.spec.kind,count,text),message=text)
    }
}
