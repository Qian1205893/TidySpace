package com.example.tidyspace.pc5853b2a.domain.usecase

import com.example.tidyspace.pc5853b2a.domain.model.*
import kotlin.math.*

/** Surface contact drives strokes and bounded suction; rendering never awards progress. */
object CleaningRules {
    fun hint(c: CleaningState): String {
        if(c.finished) return "旧屋焕新完成！现在开始布置你的家。"
        if(c.spotless) return "已经打扫干净，松手就可以开始布置。"
        val remaining=cleaningMess().filter { c.amount(it.id)<1f }
        return when {
            remaining.any { it.kind==MessKind.Trash }->"捏起垃圾，靠近圆形桶口后松手即可。垃圾桶也能搬到身边。"
            remaining.any { it.kind==MessKind.Debris }->"纸屑可以直接吸走，也可以先用扫把扫成小堆。吸头贴近碎屑即可。"
            remaining.any { it.kind==MessKind.Dust }->"吸头靠近积尘，扫过或稍停一会就能吸干净。"
            remaining.any { it.kind==MessKind.Spill }->"湿拖把贴住茶渍、鞋印，先润湿，再来回擦几下。"
            else->"墙刷贴近有污渍的地方擦过，泡沫会带走污渍。"
        }
    }
    fun reduce(s: RoomGame,a: RoomAction): RoomGame {
        val c=s.cleaning ?: return s
        return when(a) {
            is RoomAction.Begin -> {
                val safe=cancel(s)
                val base=checkNotNull(safe.cleaning)
                val tool=CleanTool.entries.firstOrNull { a.id=="@clean:${it.name}" }
                val trash=cleaningMess().find { it.id==a.id.removePrefix("@trash:") && it.kind==MessKind.Trash && c.amount(it.id)<1f }
                val bin=a.id=="@clean:Bin"
                if(tool==null && trash==null && !bin) return safe
                safe.copy(selectedId=a.id,twoHandTurning=false,dragFacing=a.facing?.takeIf { it.finite }?.gravityTurn(),
                    cleaning=base.copy(tool=tool,heldTrash=trash?.id,heldBin=bin,
                    position=tool?.rack ?: trash?.position ?: base.binPosition,contact=null,binReady=false,previousTrash=null,
                    rotation=if(bin) base.binRotation else Rotation(),origin=base.progress,originTiles=base.workTiles,
                    originWet=base.wetTiles,binOrigin=if(bin) base.binPosition else null,
                    binTurnOrigin=if(bin) base.binRotation else null),
                    message=if(tool!=null) when(tool) {
                        CleanTool.Vacuum->"积尘和纸屑都能直接吸走；吸头靠近污渍，扫过或稍停即可。"
                        CleanTool.Broom->"贴地扫拢叶片与纸屑，聚成小堆后更容易吸走。"
                        CleanTool.Brush->"贴近墙上污渍擦过，泡沫会带走污渍。"
                        CleanTool.Mop->"拖头贴住茶渍或鞋印，先润湿，再来回擦净。"
                    } else if(bin) "把垃圾桶搬到身边，松手会稳稳放回地面。" else "靠近桶口，桶沿亮起后松手即可。")
            }
            is RoomAction.Transform -> motion(s,a.delta)
            is RoomAction.CleanTick -> {
                if(c.tool !in setOf(CleanTool.Vacuum,CleanTool.Mop) || s.selectedId!="@clean:${c.tool?.name}" ||
                    s.twoHandTurning || !a.seconds.isFinite() || a.seconds<=0f) s
                else work(s,c,a.seconds.coerceAtMost(.075f))
            }
            RoomAction.Cancel -> cancel(s)
            RoomAction.End -> release(s)
            else->s.copy(message="先把小屋打扫干净，家具和手把件会在验收后解锁。")
        }
    }
    private fun cancel(s: RoomGame): RoomGame {
        val c=s.cleaning ?: return s
        return s.copy(selectedId=null,twoHandTurning=false,dragFacing=null,cleaning=c.copy(progress=c.origin ?: c.progress,
            workTiles=c.originTiles ?: c.workTiles,wetTiles=c.originWet ?: c.wetTiles,originWet=null,
            binPosition=c.binOrigin ?: c.binPosition,binRotation=c.binTurnOrigin ?: c.binRotation,
            heldBin=false,binOrigin=null,binTurnOrigin=null,binReady=false,previousTrash=null,
            tool=null,heldTrash=null,origin=null,originTiles=null,contact=null))
    }
    private fun motion(s: RoomGame,d: GrabDelta): RoomGame {
        val c=s.cleaning ?: return s
        if((c.tool==null && c.heldTrash==null && !c.heldBin) || !d.translation.finite() || !d.rotation.finite) return s
        val turning=d.pointerCount>=2
        val end=c.position+if(turning) Point3(0f,0f,0f) else d.translation
        val face=!turning && d.translation.length()>1e-7f && s.dragFacing!=null
        val rotation=if(turning) d.rotation.gravityTurn()*Rotation.yaw(c.rotation.yaw())
            else if(face) checkNotNull(s.dragFacing) else c.rotation
        val game=s.copy(twoHandTurning=turning,dragFacing=if(face) null else s.dragFacing)
        // Match catalogue preview freedom. Contact grants cleaning progress;
        // it must not clamp the visible tool, trash or bin to a room surface.
        var next=c.copy(position=end,rotation=rotation,contact=null,
            previousTrash=if(c.heldTrash!=null) c.position else null)
        if(c.heldBin) next=next.copy(binPosition=end,binRotation=rotation)
        if(c.heldTrash!=null) next=next.copy(binReady=CleaningBin.captures(next),
            feedback=if(CleaningBin.captures(next)) "松手即可入桶" else "靠近桶口")
        val distance=length(next.position-c.position)
        if(distance<.0001f || distance>.28f || c.tool==null) return game.copy(cleaning=next)
        return work(game,next,0f)
    }
    /** Exported model bounds about each nozzle/body root, for the outer window
     * envelope and bin release only; never a floor constraint during dragging. */
    fun heldBounds(c: CleaningState,rotation: Rotation=c.rotation): Pair<Point3,Point3> {
        val (lo,hi)=when(c.tool) {
            CleanTool.Vacuum->Point3(-.080f,-.0165f,-.0801f) to Point3(.080f,.3638f,.0416f)
            CleanTool.Broom->Point3(-.0858f,-.0146f,-.0553f) to Point3(.0854f,.4116f,.0264f)
            CleanTool.Brush->Point3(-.0526f,-.0276f,-.0191f) to Point3(.0526f,.1327f,.0618f)
            CleanTool.Mop->Point3(-.088f,-.017f,-.040f) to Point3(.088f,.39f,.040f)
            null->if(c.heldBin) Point3(-.139f,-.295f,-.206f) to Point3(.139f,.245f,.156f)
                else (cleaningMess().first { it.id==c.heldTrash }.size*.5f).let { it*(-1f) to it }
        }
        val corners=listOf(lo.x,hi.x).flatMap { x -> listOf(lo.y,hi.y).flatMap { y ->
            listOf(lo.z,hi.z).map { z -> rotation.rotate(Point3(x,y,z)) }
        } }
        val minCorner=Point3(corners.minOf { it.x },corners.minOf { it.y },corners.minOf { it.z })
        val maxCorner=Point3(corners.maxOf { it.x },corners.maxOf { it.y },corners.maxOf { it.z })
        return minCorner to maxCorner
    }
    private fun work(s: RoomGame,next: CleaningState,seconds: Float): RoomGame {
        val c=s.cleaning ?: return s
        val tool=next.tool ?: return s
        val distance=length(next.position-c.position)
        val accepted=when(tool) { CleanTool.Vacuum->MessKind.Dust;CleanTool.Broom->MessKind.Debris;
            CleanTool.Brush->MessKind.Wall;CleanTool.Mop->MessKind.Spill }
        // Work area is the brush/nozzle face at the root, not the tall handle.
        val normal=next.rotation.rotate(if(accepted==MessKind.Wall) Point3(0f,0f,-1f) else Point3(0f,-1f,0f))
        val aligned=if(accepted==MessKind.Wall) normal.z<-.65f else normal.y<-.65f
        if(!aligned) return s.copy(cleaning=next)
        val progress=c.progress.toMutableMap()
        val tiles=c.workTiles.toMutableMap()
        val wetTiles=c.wetTiles.toMutableMap()
        var workContact: CleanContact?=null
        var hits=0
        for(mess in cleaningMess().filter { (it.kind==accepted ||
            (tool==CleanTool.Vacuum && it.kind==MessKind.Debris)) && c.amount(it.id)<1f }) {
            val v=next.position-c.position
            val t=if(distance<.0001f) 0f else (dot(mess.position-c.position,v)/dot(v,v)).coerceIn(0f,1f)
            val point=c.position+v*t
            val contact=if(accepted==MessKind.Wall)
                abs(point.z-mess.position.z)<CleaningGrid.NORMAL_TOLERANCE &&
                    abs(point.x-mess.position.x)<mess.size.x/2+.075f &&
                    abs(point.y-mess.position.y)<mess.size.y/2+.075f
            else abs(point.y-mess.position.y)<CleaningGrid.NORMAL_TOLERANCE &&
                abs(point.x-mess.position.x)<mess.size.x/2+.095f &&
                abs(point.z-mess.position.z)<mess.size.z/2+.095f
            if(contact) {
                val tangential=if(accepted==MessKind.Wall) hypot(v.x,v.y) else hypot(v.x,v.z)
                // Only the part of a segment actually near the surface does work.
                val startNormal=if(accepted==MessKind.Wall) c.position.z-mess.position.z else c.position.y-mess.position.y
                val normalTravel=if(accepted==MessKind.Wall) v.z else v.y
                val fraction=if(abs(normalTravel)<.0001f) 1f else {
                    val a=(-CleaningGrid.NORMAL_TOLERANCE-startNormal)/normalTravel
                    val b=(CleaningGrid.NORMAL_TOLERANCE-startNormal)/normalTravel
                    (min(1f,max(a,b))-max(0f,min(a,b))).coerceIn(0f,1f)
                }
                val amount=if(mess.kind==MessKind.Debris) {
                    val cap=if(tool==CleanTool.Broom) .5f else 1f
                    val pileContact=tool!=CleanTool.Vacuum || c.rawAmount(mess.id)<.5f ||
                        hypot(point.x-mess.position.x,point.z-mess.position.z)<.18f
                    if(!pileContact) c.amount(mess.id) else
                        (c.rawAmount(mess.id)+tangential*fraction/.32f+seconds/.80f)
                            .let { if(it>=.95f) 1f else it }.coerceAtMost(cap).coerceAtLeast(c.rawAmount(mess.id))
                } else {
                    val old=CleaningGrid.cells(c,mess)
                    val wet=c.wetTiles[mess.id] ?: List(CleaningGrid.COUNT) { 0f }
                    if(tool==CleanTool.Mop) wetTiles[mess.id]=wet.mapIndexed { i,value ->
                        (value+CleaningGrid.work(mess,i,c.position,next.position,.11f)/.08f+
                            if(CleaningGrid.touches(mess,i,next.position,.11f)) seconds/.12f else 0f).coerceAtMost(1f)
                    }
                    val updated=old.mapIndexed { i,value ->
                        (value+if(tool==CleanTool.Mop && wet[i]<.25f) 0f else CleaningGrid.work(mess,i,c.position,next.position,
                            if(tool==CleanTool.Brush) .075f else if(tool==CleanTool.Mop) .11f else .095f)/
                            (if(tool==CleanTool.Brush) .085f else if(tool==CleanTool.Mop) .055f else .075f)+
                            if(tool==CleanTool.Vacuum && seconds>0f && CleaningGrid.touches(mess,i,next.position,.095f)) seconds/.70f else 0f)
                            .coerceAtMost(1f)
                    }
                    if(updated!=old) tiles[mess.id]=updated
                    updated.average().toFloat()
                }
                progress[mess.id]=amount
                if(amount>c.rawAmount(mess.id) || (tool==CleanTool.Mop && wetTiles[mess.id]!=c.wetTiles[mess.id])) workContact=CleanContact(tool,
                    if(mess.kind==MessKind.Wall) point.copy(z=mess.position.z+.003f)
                    else point.copy(y=mess.position.y+.007f),if(distance>.0001f) v*(1f/distance) else Point3(0f,0f,0f),mess.id)
                if(amount>=1f) hits++
            }
        }
        val worked=workContact!=null
        val result=next.copy(progress=progress,workTiles=tiles,wetTiles=wetTiles,contact=workContact,
            sequence=if(worked) c.sequence+1 else c.sequence,
            feedback=if(hits>0) "这片干净了" else if(worked && c.tool==CleanTool.Broom) "碎屑正聚拢"
                else if(worked && tool==CleanTool.Mop) "润湿后再擦过，水渍就会褪去"
                else if(worked) "擦过的地方亮起来了" else c.feedback)
        return s.copy(cleaning=result,message=if(result.spotless) hint(result)
            else if(worked) "${result.percent}% · "+
                if(tool==CleanTool.Broom) "碎屑聚拢后更容易吸走。" else "沿着还脏的部分继续。" else s.message)
    }
    private fun release(s: RoomGame): RoomGame {
        val c=s.cleaning ?: return s
        var next=c
        c.heldTrash?.let { id ->
            val inside=CleaningBin.captures(c)
            next=if(inside) c.copy(progress=c.progress+(id to 1f),sequence=c.sequence+1,feedback="垃圾已入桶",
                disposedId=id,disposedFrom=c.position,disposalSequence=c.disposalSequence+1)
                else c.copy(feedback="没有投入桶口，垃圾已放回原位")
        }
        if(c.heldBin) {
            val (lo,hi)=heldBounds(c)
            val landed=RoomFloor.constrain(c.position.copy(y=-.32f+CleaningBin.floorOffset),lo,hi)
            next=next.copy(binPosition=landed ?: c.binOrigin ?: c.binPosition,
                binRotation=if(landed!=null) c.rotation else c.binTurnOrigin ?: c.binRotation)
        }
        val completed=next.spotless
        if(completed) next=next.completed()
        next=next.copy(finished=completed,tool=null,heldTrash=null,heldBin=false,binOrigin=null,binTurnOrigin=null,
            binReady=false,previousTrash=null,origin=null,originTiles=null,originWet=null,contact=null)
        return s.copy(selectedId=null,twoHandTurning=false,dragFacing=null,cleaning=next,celebration=if(completed) s.celebration+1 else s.celebration,
            message=if(completed) "小屋焕然一新！家具已搬回，开始布置喜欢的家吧。"
                else if(c.heldTrash!=null) next.feedback else hint(next))
    }
    private fun dot(a: Point3,b: Point3)=a.x*b.x+a.y*b.y+a.z*b.z
    private fun length(a: Point3)=sqrt(dot(a,a))
}
