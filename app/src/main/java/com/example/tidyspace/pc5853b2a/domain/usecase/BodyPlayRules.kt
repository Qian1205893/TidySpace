package com.example.tidyspace.pc5853b2a.domain.usecase

import com.example.tidyspace.pc5853b2a.domain.model.*
import kotlin.math.abs

/** These whole-body toys share sustained native grabbing and child-only play. */
object BodyPlayRules {
    fun accepts(kind: Furnishing)=kind in setOf(Furnishing.Mochi,Furnishing.Jelly,Furnishing.Tumbler)
    fun soft(kind: Furnishing)=kind==Furnishing.Mochi || kind==Furnishing.Jelly
    fun motion(s: RoomGame,item: RoomItem,d: GrabDelta)=PlaythingRules.motion(s,item,d)
    fun release(s: RoomGame,item: RoomItem): RoomGame? {
        if(!accepts(item.spec.kind) || s.extractionBox!=null ||
            s.origin?.none { it.spec.id==item.spec.id }!=false ||
            s.gestureTurn>=3f ||
            (s.twoHandTurning && s.gestureTurn>1e-5f)) return null
        val origin=checkNotNull(s.origin).first { it.spec.id==item.spec.id }
        // Share the furniture drag threshold: a small intentional placement
        // must stay placed. Net displacement also rejects stationary jitter.
        if((item.position-origin.position).length()>=.016f) return null
        val restored=s.copy(items=checkNotNull(s.origin))
        return when {
            soft(item.spec.kind)->PlaythingRules.award(restored,item,1,
                if(item.spec.kind==Furnishing.Mochi) "呼——团子回弹中" else "果冻慢慢回到原形")
            abs(s.toyGesture.tilt)>.8f || s.gestureDistance>=.004f-1e-6f->
                PlaythingRules.award(restored,item,1,"摇摇晃晃，还是站得稳")
            else->restored.copy(focusId=item.spec.id,message="轻推身体看它摇晃；捏住身体拖远一些就能搬动。")
        }
    }
}
