package com.example.tidyspace.pc5853b2a.domain.usecase

import com.example.tidyspace.pc5853b2a.domain.model.*
import kotlin.math.abs

/** Packed inventory commits on accepted release; the scene owns the temporary held mesh. */
object RoomCardDrop {
    data class Preview(val item: RoomItem?,val valid: Boolean,val reason: String)
    fun inventory(s: RoomGame)=s.items.filter { item ->
        item.packed && item.spec.id !in s.archivedIds &&
            item.spec.kind !in setOf(Furnishing.Crate,Furnishing.MovingBox) &&
            (!s.movingHome || s.boxStock.values.any { item.spec.id in it }) &&
            !(s.studyVersion>0 && StudyLayout.retired(item.spec))
    }
    private fun attempt(s: RoomGame,drag: CardDrag): RoomPhysics.Placement {
        val source=inventory(s).find { it.spec.id==drag.id }
            ?: return RoomPhysics.Placement(null,null,"物品已被取走")
        val p=drag.point?.takeIf { it.finite() }
            ?: return RoomPhysics.Placement(null,null,"拖入房间，松手放置")
        val held=source.copy(packed=false,support=null,position=p,rotation=drag.facing)
        return RoomPhysics.solve(s.copy(
            items=s.items.map { if(it.spec.id==drag.id) held else it },
            selectedId=drag.id,origin=s.items),preserveYaw=true,snapFurnitureToWall=true)
    }
    fun preview(s: RoomGame): Preview?=s.cardDrag?.let {
        if(it.point==null) Preview(null,false,"拖入房间，松手放置")
        else attempt(s,it).let { result -> Preview(result.item,result.valid,result.reason) }
    }
    fun begin(s: RoomGame,id: String,token: Long,facing: Rotation): RoomGame {
        if(!s.decorating || s.selectedId!=null || s.cardDrag!=null || token<=s.cardSequence ||
            !facing.finite || inventory(s).none { it.spec.id==id }) return s
        return s.copy(cardDrag=CardDrag(token,id,null,facing.gravityTurn()),cardSequence=token,
            previewBox=null)
    }
    fun move(s: RoomGame,token: Long,point: Point3?): RoomGame {
        val drag=s.cardDrag?.takeIf { it.token==token } ?: return s
        return s.copy(cardDrag=drag.copy(point=point?.takeIf { it.finite() }))
    }
    fun cancel(s: RoomGame,token: Long?=null): RoomGame =
        if(token==null || s.cardDrag?.token==token) s.copy(cardDrag=null) else s
    fun release(s: RoomGame,token: Long): RoomGame {
        val drag=s.cardDrag?.takeIf { it.token==token } ?: return s
        if(!s.decorating) return cancel(s,token)
        val attempt=attempt(s,drag)
        val next=attempt.game ?: return cancel(s,token).copy(message=attempt.reason)
        val stock=s.boxStock.mapValues { (_,ids) -> ids.filterNot { it==drag.id } }
        return RoomProgress.settle(next.copy(selectedId=null,origin=null,mechanismOrigin=null,
            cardDrag=null,previewBox=null,extractionBox=null,boxStock=stock,
            focusId=drag.id,angleFocus=null,additions=s.additions+1,nextSerial=s.nextSerial+1,
            message="${next.item(drag.id).spec.name}已放好"))
    }

    /** Screen/ray placement: intersect the actual floor/supports before asking the contact solver.
     * No depth motion is required. Never fall through the first visible support on the ray. */
    fun project(s: RoomGame,spec: RoomSpec,origin: Point3,through: Point3): Point3? {
        if(!origin.finite() || !through.finite()) return null
        val direction=through-origin
        if(direction.length()<.0001f) return null
        data class Hit(val t: Float,val p: Point3)
        val hits=mutableListOf<Hit>()
        if(direction.y<-.0001f) {
            val t=(-.32f-origin.y)/direction.y
            val p=origin+direction*t
            if(t>0 && RoomFloor.point(p.x,p.z)) hits+=Hit(t,p)
            for(parent in s.items.filter { !it.packed && it.spec.id !in s.archivedIds }) {
                val inv=parent.rotation.inverse()
                val localOrigin=inv.rotate(origin-parent.position)
                val localDirection=inv.rotate(direction)
                if(localDirection.y>=-.0001f) continue
                for(surface in RoomPhysics.surfaces(s,parent)) {
                    val st=(surface.center.y-localOrigin.y)/localDirection.y
                    val at=localOrigin+localDirection*st-surface.center
                    val inside=if(surface.ellipse) (at.x/(surface.width/2)).let { it*it }+
                        (at.z/(surface.depth/2)).let { it*it }<=1f
                        else abs(at.x)<=surface.width/2 && abs(at.z)<=surface.depth/2
                    if(st>0 && inside) hits+=Hit(st,origin+direction*st)
                }
            }
        }
        if(spec.kind in setOf(Furnishing.Picture,Furnishing.Shelf,Furnishing.WallClock,Furnishing.WallMirror)) {
            fun wall(axis: Char,coordinate: Float) {
                val d=if(axis=='x') direction.x else direction.z
                if(abs(d)<.0001f) return
                val t=(coordinate-(if(axis=='x') origin.x else origin.z))/d
                val p=origin+direction*t
                if(t>0 && p.y in -.22f.. .60f && RoomFloor.point(p.x,p.z))
                    hits+=Hit(t,p)
            }
            wall('z',RoomLayout.back+.025f)
            wall('x',RoomLayout.left+.025f)
        }
        val hit=hits.minByOrNull { it.t } ?: return null
        return hit.p.copy(y=if(hit.p.y>-.319f &&
            spec.kind in setOf(Furnishing.Picture,Furnishing.Shelf,Furnishing.WallClock,Furnishing.WallMirror))
            hit.p.y else hit.p.y+spec.size.y/2+.025f)
    }
}
