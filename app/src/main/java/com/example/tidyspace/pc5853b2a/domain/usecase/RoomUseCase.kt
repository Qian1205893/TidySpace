package com.example.tidyspace.pc5853b2a.domain.usecase

import com.example.tidyspace.pc5853b2a.domain.model.*
import kotlin.math.*

class RoomUseCase {
    fun reduce(before: RoomGame,a: RoomAction): RoomGame {
        if(a==RoomAction.CleanAgain) return before
        when(a) {
            is RoomAction.CardBegin -> return RoomCardDrop.begin(before,a.id,a.token,a.facing)
            is RoomAction.CardMove -> return RoomCardDrop.move(before,a.token,a.point)
            is RoomAction.CardRelease -> return RoomCardDrop.release(before,a.token)
            is RoomAction.CardCancel -> return RoomCardDrop.cancel(before,a.token)
            else -> Unit
        }
        if(before.cardDrag!=null) {
            if(a==RoomAction.Cancel || a==RoomAction.ToggleControls)
                return RoomCardDrop.cancel(before).let { if(a==RoomAction.ToggleControls)
                    it.copy(controlsVisible=!it.controlsVisible) else it }
            return before
        }
        if(a is RoomAction.SyncCartonFacing) {
            if(!before.decorating || a.rotations.isEmpty()) return before
            return before.copy(items=before.items.map { item ->
                val facing=a.rotations[item.spec.id]
                if(item.spec.kind==Furnishing.MovingBox && before.stock(item.spec.id).isNotEmpty() &&
                    before.selectedId!=item.spec.id && facing?.finite==true)
                    item.copy(rotation=facing.gravityTurn()) else item
            })
        }
        if(a is RoomAction.CleanTick) return if(!before.decorating) CleaningRules.reduce(before,a) else before
        // Refresh the pending pose at actual movement, then consume it once.
        // A later frame or an explicit pair turn must not keep following the head.
        val s=if(a is RoomAction.Transform && before.dragFacing!=null && a.facing?.finite==true)
            before.copy(dragFacing=a.facing.gravityTurn()) else before
        if(a==RoomAction.ToggleControls) return s.copy(controlsVisible=!s.controlsVisible)
        if(a is RoomAction.RotatePlaced) return rotatePlaced(s,a.degrees)
        if(a is RoomAction.Begin && a.id.startsWith("@angle:")) {
            if(!s.decorating || s.angleFocus==null) return s
            return cancel(s).copy(selectedId=a.id,gestureDistance=0f,gestureTurn=0f)
        }
        if(s.selectedId?.startsWith("@angle:")==true) {
            return when(a) {
                is RoomAction.Transform -> s.copy(gestureDistance=s.gestureDistance+a.delta.translation.length(),
                    gestureTurn=s.gestureTurn+abs(a.delta.rotation.yaw()))
                RoomAction.End -> {
                    val degrees=s.selectedId.substringAfter(':').toFloatOrNull()
                    val free=s.copy(selectedId=null)
                    if(degrees!=null && s.gestureDistance<.016f && s.gestureTurn<3f) rotatePlaced(free,degrees) else free
                }
                RoomAction.Cancel -> s.copy(selectedId=null)
                else -> s
            }
        }
        if(a==RoomAction.ToggleJournal) return s.copy(journalOpen=!s.journalOpen)
        if(a==RoomAction.ToggleGuide) return s.copy(guideVisible=!s.guideVisible)
        if(a is RoomAction.Begin && RoomRituals.isTarget(a.id)) {
            val safe=if(!s.decorating && !RoomRituals.isTarget(s.selectedId))
                CleaningRules.reduce(cancel(s),RoomAction.Cancel) else cancel(s)
            return RoomRituals.begin(safe,a)
        }
        if(RoomRituals.isTarget(s.selectedId)) {
            when(a) {
                is RoomAction.Transform->return RoomRituals.motion(s,a.delta)
                RoomAction.End->return RoomProgress.settle(RoomRituals.release(s))
                RoomAction.Cancel->return RoomRituals.cancel(s)
                else->Unit
            }
        }
        if(!s.decorating && (a is RoomAction.Mood || a is RoomAction.Brightness)) return act(s,a)
        if(!s.decorating && !(a is RoomAction.Begin && a.id=="@room") &&
            s.selectedId!="@room") {
            val cleaned=CleaningRules.reduce(s,a)
            return if(cleaned.decorating) cleaned.copy(previewBox=null,
                message="第一关完成！第二关 · 拆箱布置。点入口纸箱，一件件拿出物品。") else cleaned
        }
        val result=act(s,a)
        return if(a==RoomAction.End || a is RoomAction.Add || a==RoomAction.Duplicate ||
            a==RoomAction.Recycle || a is RoomAction.Mood) RoomProgress.settle(result) else result
    }
    private fun act(s: RoomGame,a: RoomAction): RoomGame { return when(a) {
        is RoomAction.CardBegin,is RoomAction.CardMove,is RoomAction.CardRelease,is RoomAction.CardCancel -> s
        is RoomAction.SyncCartonFacing -> s
        is RoomAction.Begin -> {
            val safe=cancel(s)
            if(a.id in safe.archivedIds) return s
            if(a.id.startsWith("@unpack:")) return MovingHome.begin(safe,a.id.removePrefix("@unpack:"))
                .copy(dragFacing=a.facing?.takeIf { it.finite }?.gravityTurn())
            if(safe.movingHome && safe.items.any { it.spec.id==a.id && it.spec.kind==Furnishing.MovingBox } &&
                safe.stock(a.id).isEmpty()) return s
            val play=PlaythingRules.validTarget(safe,a.id)
            if(a.id !in listOf("@room","crate:lid","desk:drawer","lamp:switch") &&
                play==null && safe.items.none { it.spec.id==a.id && (!it.packed || safe.crateOpen) }) s
            else safe.copy(selectedId=a.id,origin=safe.items,actionTravel=0f,gestureDistance=0f,gestureTurn=0f,
                twoHandTurning=false,dragFacing=a.facing?.takeIf { it.finite }?.gravityTurn(),
                crankRadians=0f,toyGesture=ToyGesture(),
                focusId=play?.spec?.id ?: if(safe.items.any { it.spec.id==a.id }) a.id else safe.focusId,
                mechanismOrigin=RoomMechanisms(safe.roomYaw,safe.drawerOpen),
                message=if(play!=null) toyHint(play.spec.kind) else when(a.id) {
                    "@room"->"双手捏合，水平转动小屋；松开任一只手就停下。"
                    "crate:lid"->"向上掀开箱盖，挑一件喜欢的小物。"
                    "desk:drawer"->"向外拉开抽屉，向内推回。"
                    "lamp:switch"->"松开灯的拉绳，切换暖光。"
                    else->"拿起${safe.item(a.id).spec.name} · 单手搬动，双手水平转向"
                })
        }
        is RoomAction.Transform -> transform(s,a.delta)
        RoomAction.End -> release(s)
        RoomAction.Cancel -> cancel(s)
        is RoomAction.Add -> if(s.selectedId!=null || (s.studyVersion>0 &&
            (a.assetId in StudyLayout.retiredAssets || BathroomLayout.owns(a.assetId)))) s else if(s.movingHome) {
            val box=s.boxStock.entries.firstOrNull { a.assetId in it.value }?.key
            if(box!=null) MovingHome.revealItem(s,box,a.assetId)
            else s.copy(message="这件物品已经在你的小屋里，每件都是独一份。")
        } else add(s,a.assetId)
        RoomAction.Duplicate -> if(s.movingHome) s.copy(message="每件物品都是独一份，去入口看看还没拆完的箱子吧。")
        else if(s.selectedId!=null) s else s.items.find { it.spec.id==s.focusId }?.let {
            add(s,it.spec.assetId,it.rotation)
        } ?: s.copy(message="先拿起并放下一件家具，再复制它。")
        RoomAction.Recycle -> recycle(s)
        is RoomAction.Mood -> if(s.selectedId!=null) s else s.copy(mood=a.value,dayHour=RoomTime.hour(a.value),
            message="已换成${a.value.label}氛围，看看你的小屋有了什么变化。")
        is RoomAction.Brightness -> if(!a.value.isFinite() || s.selectedId!=null) s else
            s.copy(indoorBrightness=a.value.coerceIn(0f,1f),lampOn=a.value>0f,message="室内灯光 ${(a.value.coerceIn(0f,1f)*100).toInt()}% · 炉火依然温暖")
        RoomAction.ToggleGuide -> s.copy(guideVisible=!s.guideVisible)
        RoomAction.ToggleJournal -> s.copy(journalOpen=!s.journalOpen)
        RoomAction.ToggleControls -> s.copy(controlsVisible=!s.controlsVisible)
        is RoomAction.RotatePlaced -> rotatePlaced(s,a.degrees)
        RoomAction.Undo,RoomAction.Redo -> s
        RoomAction.CleanAgain -> s
        is RoomAction.CleanTick -> s
    } }
    private fun rotatePlaced(s: RoomGame,degrees: Float): RoomGame {
        if(s.selectedId!=null || !s.decorating || !degrees.isFinite()) return s
        val id=s.angleFocus ?: s.focusId ?: return s
        val attempt=RoomPhysics.rotateInPlace(s,id,degrees)
        return attempt.game?.copy(message="已转到 ${((degrees%360+360)%360).roundToInt()}° · 可以继续微调")
            ?: s.copy(message=attempt.reason)
    }
    private fun add(s: RoomGame,assetId: String,rotation: Rotation=Rotation()): RoomGame {
        if(s.gamingVersion>0 && s.items.any { it.spec.assetId==assetId && it.spec.id !in s.archivedIds })
            return s.copy(message="每款只有一件，已摆放的物品可以自由移动。")
        val source=shopCatalogue().find { it.id==assetId }
            ?: return s.copy(message="这件家具带有专属机关，可以移动；请从家具库选择可添置的款式。")
        if(s.items.size>=ROOM_CAPACITY) return s.copy(message="房间已有 $ROOM_CAPACITY 件物品。先收回一件添置物，再继续创造。")
        val spec=source.copy(id="${source.id}~${s.nextSerial}",packed=false,assetId=source.id)
        for((row,z) in listOf(.43f,.19f,-.07f,-.35f,-.63f,-.81f).withIndex()) for(x in listOf(0f,-.30f,.30f,-.60f,.60f,-.88f,.88f)) {
            val wallKind=source.kind in setOf(Furnishing.Picture,Furnishing.Shelf,Furnishing.WallMirror)
            val item=RoomItem(spec,Point3(x,if(wallKind) listOf(.20f,-.10f,.04f,.40f,.58f,-.20f)[row] else .40f,
                if(wallKind) RoomLayout.back+.092f else z),rotation)
            val attempt=release(s.copy(items=s.items+item,selectedId=spec.id,origin=s.items))
            if(source.kind==Furnishing.Pokeball && attempt.items.any { it.spec.id==spec.id } &&
                !RoomPhysics.toyClear(attempt.copy(toyStates=attempt.toyStates+(spec.id to ToyState(open=true))),spec.id)) continue
            if(attempt.items.any { it.spec.id==spec.id }) return attempt.copy(
                nextSerial=s.nextSerial+1,additions=s.additions+1,focusId=spec.id,
                message="添置了${source.name}！它在房间的空位，捏住它开始布置。")
        }
        return s.copy(message="还没有适合${source.name}的空位。挪开一些家具，或收回添置物后再试。")
    }
    private fun recycle(s: RoomGame): RoomGame {
        if(s.selectedId!=null) return s
        val item=s.items.find { it.spec.id==s.focusId } ?: return s.copy(message="先拿起并放下要收回的添置物。")
        if(item.spec.id in s.archivedIds) return s
        if(!s.movingHome && BathroomLayout.owns(item.spec.id))
            return s.copy(message="卫浴器具可以自由挪动，请把它放到喜欢的位置。")
        if(s.movingHome && item.spec.kind!=Furnishing.MovingBox) {
            if(descendants(s,item.spec.id).size>1) return s.copy(message="先拿走上面的小物，家具就可以重新装箱。")
            val box=MovingHome.boxFor(item.spec)
            return s.copy(items=s.items.map {
                if(it.spec.id==item.spec.id) it.copy(packed=true,support=null) else it
            },boxStock=s.boxStock+(box to (s.stock(box)+item.spec.id).distinct()),
                previewBox=box,focusId=null,message="${item.spec.name}已放回入口的箱子，随时再取。")
        }
        if(item.spec.id==item.spec.assetId) return s.copy(message="这是新家的原有家具。可以自由挪动；家具库添置的物件可以收回。")
        if(descendants(s,item.spec.id).size>1) return s.copy(message="请先移走${item.spec.name}上承托的小物，再收回家具。")
        return s.copy(items=s.items-item,focusId=null,message="${item.spec.name}已收回，空出的位置留给新灵感。")
    }
    fun placementHint(s: RoomGame): String? {
        if(!s.decorating) return null
        if(s.selected==null) return null
        return placement(s).reason
    }
    fun placement(s: RoomGame)=RoomPhysics.solve(s,preserveYaw=preserveHeldHeading(s),
        snapFurnitureToWall=!s.twoHandTurning)
    private fun preserveHeldHeading(s: RoomGame)=s.origin?.any { it.spec.id==s.selectedId }==true
    private fun transform(s: RoomGame,d: GrabDelta): RoomGame {
        if(s.selectedId==null || !d.translation.finite() || !d.rotation.finite) return s
        if(s.selectedId.startsWith("@play:")) {
            val distance=sqrt(d.translation.x*d.translation.x+d.translation.y*d.translation.y+d.translation.z*d.translation.z)
            val angle=2*acos(abs(d.rotation.normalized().w).coerceIn(0f,1f))
            val next=s.copy(gestureDistance=(s.gestureDistance+distance).coerceAtMost(10f),
                crankRadians=(s.crankRadians+angle+sqrt(d.translation.y*d.translation.y+d.translation.z*d.translation.z)/.025f).coerceAtMost(50f))
            return PlaythingRules.validTarget(s,s.selectedId)?.let { PlaythingRules.motion(next,it,d) } ?: next
        }
        val travel=s.actionTravel+d.translation.y
        return when(s.selectedId) {
            "@room" -> s.copy(roomYaw=(s.roomYaw+d.rotation.yaw())%360f,
                gestureTurn=s.gestureTurn+abs(d.rotation.yaw()))
            "crate:lid" -> s.copy(actionTravel=travel,gestureDistance=s.gestureDistance+d.translation.length())
            "lamp:switch" -> s
            "desk:drawer" -> {
                val local=s.item("desk").rotation.inverse().rotate(d.translation)
                val next=s.copy(drawerOpen=(s.drawerOpen+local.z/.10f).coerceIn(0f,1f),
                    gestureDistance=s.gestureDistance+d.translation.length())
                if(RoomPhysics.mechanismsClear(next)) next else s.copy(message="抽屉前有物品挡住了，请先挪开。")
            }
            else -> s.selected?.let { selected ->
                val turning=d.pointerCount>=2
                val translation=if(turning) Point3(0f,0f,0f) else d.translation
                val rotation=if(turning) d.rotation.gravityTurn() else Rotation()
                // Face once on actual single-hand movement, never on a tap or
                // pair-only turn. Later two-hand yaw remains under user control.
                val dragging=!turning && translation.length()>1e-7f
                val faced=if(dragging && s.dragFacing!=null)
                    RoomPhysics.faceForDrag(s,selected,s.dragFacing).let { candidate ->
                        if(abs((candidate.item(selected.spec.id).rotation*s.dragFacing.inverse()).yaw())<.001f)
                            candidate.copy(dragFacing=null) else candidate
                    } else s
                val translated=RoomPhysics.constrainHeld(faced,faced.item(selected.spec.id),translation,rotation,turning)
                val moved=if(dragging && translated.dragFacing!=null) {
                    val oriented=RoomPhysics.faceForDrag(translated,translated.item(selected.spec.id),translated.dragFacing)
                    if(abs((oriented.item(selected.spec.id).rotation*translated.dragFacing.inverse()).yaw())<.001f)
                        oriented.copy(dragFacing=null) else oriented
                } else translated
                val next=moved.copy(angleFocus=null,twoHandTurning=s.twoHandTurning || d.pointerCount>=2,
                    gestureTurn=s.gestureTurn+abs(rotation.yaw()),gestureDistance=s.gestureDistance+translation.length(),
                    items=moved.items.map {
                        if(it.spec.id==selected.spec.id) it.copy(support=null,packed=false) else it
                    })
                if(BodyPlayRules.accepts(selected.spec.kind)) BodyPlayRules.motion(next,selected,d)
                else if(selected.spec.kind==Furnishing.Rattle) PlaythingRules.motion(next,selected,d) else next
            } ?: s
        }
    }
    fun descendants(s: RoomGame,id: String): Set<String> {
        return RoomPhysics.family(s,id)
    }
    private fun moveFamily(s: RoomGame,item: RoomItem,p: Point3,q: Rotation): RoomGame {
        return RoomPhysics.moveFamily(s,item,p,q)
    }
    private fun release(s: RoomGame): RoomGame {
        if(s.selectedId?.startsWith("@play:")==true) return play(s)
        when(s.selectedId) {
            null -> return s
            "@room" -> return clear(s,if(s.decorating) "换个角度，继续布置。" else "换个角度，继续轻松打扫。")
            "crate:lid" -> {
                val next=s.copy(crateOpen=if(abs(s.actionTravel)>.02f) s.actionTravel>0 else !s.crateOpen)
                val dependents=next.items.filter { !it.packed && it.support=="crate" }
                if(dependents.any { !RoomPhysics.supported(next,it) } || !RoomPhysics.mechanismsClear(next))
                    return clear(s,"箱盖被物品挡住了，请先移开盖上或高出箱口的物件。")
                return clear(next,"箱盖已${if(next.crateOpen) "打开" else "合上"}。")
            }
            "lamp:switch" -> return clear(s.copy(lampOn=!s.lampOn),if(s.lampOn) "关灯了。" else "暖光亮起，阅读时间到了。")
            "desk:drawer" -> return clear(s,"抽屉可以随时拉开或推回。")
        }
        val held=s.selected ?: return cancel(s)
        if(held.spec.kind==Furnishing.MovingBox && !s.twoHandTurning && s.gestureDistance<.016f && s.gestureTurn<3f) {
            val restored=cancel(s)
            return MovingHome.reveal(restored,held.spec.id)
        }
        if(s.extractionBox!=null && s.gestureDistance<.016f) return cancel(s)
        BodyPlayRules.release(s,held)?.let { return clear(it,it.message) }
        if(s.extractionBox==null && s.origin?.any { it.spec.id==held.spec.id }==true &&
            !s.twoHandTurning && s.gestureDistance<.016f && s.gestureTurn<3f) {
            // A tap must not apply the viewer-facing grab pose or gravity snapping.
            return cancel(s).copy(focusId=held.spec.id,angleFocus=null,
                message="${held.spec.name} · 单手捏住搬动；加上另一只手，水平转向。")
        }
        val placement=placement(s)
        val next=placement.game ?: return cancel(s).copy(message=placement.reason)
        val candidate=checkNotNull(placement.item)
        val before=goals(s.copy(items=s.origin ?: s.items)).count { it.complete }
        val after=goals(next).count { it.complete }
        val message=if(after>before) "完成一个生活角落！${after} / 3 · 也可以继续自由布置。"
            else "${held.spec.name}放好了${candidate.support?.let { id -> " · 在${next.items.find { it.spec.id==id }?.spec?.name ?: "墙面"}上" } ?: ""}。"
        return clear(MovingHome.commit(next),message).copy(angleFocus=null)
    }
    fun halfExtents(item: RoomItem)=RoomPhysics.half(item)
    private fun play(s: RoomGame): RoomGame {
        val item=PlaythingRules.validTarget(s,s.selectedId ?: "") ?: return cancel(s)
        if(item.spec.kind !in setOf(Furnishing.Woodfish,Furnishing.MusicBox,Furnishing.SingingBowl)) {
            val next=PlaythingRules.release(s,item)
            return clear(next,next.message)
        }
        val beats=if(item.spec.kind==Furnishing.MusicBox) (s.crankRadians/(PI.toFloat()/2)+.0001f).toInt().coerceIn(0,32)
            else if(s.gestureDistance<=.055f) 1 else 0
        if(beats==0) return clear(s,if(item.spec.kind==Furnishing.MusicBox) "再转动一点曲柄，就能奏出旋律。" else "已取消敲击。捏住底座可以搬动物件。")
        val count=((s.playCounts[item.spec.id] ?: 0L)+beats).coerceAtMost(1_000_000_000L)
        val sequence=s.playSequence+1
        val message=when(item.spec.kind) {
            Furnishing.Woodfish->"功德 +1 · 给自己一小会儿宁静"
            Furnishing.MusicBox->"转出 $beats 个音符 · 一点一点，谱出家的旋律"
            else->"清音 +1 · 听一声余韵，慢慢呼吸"
        }
        return clear(s.copy(playCounts=s.playCounts+(item.spec.id to count),playSequence=sequence,
            playEvent=PlayEvent(sequence,item.spec.id,item.spec.kind,count)),message)
    }
    fun goals(s: RoomGame): List<RoomGoal> {
        fun near(a: String,b: String,d: Float): Boolean {
            val p=s.item(a).position-s.item(b).position
            return sqrt(p.x*p.x+p.z*p.z)<d
        }
        val work=s.item("book").support=="desk" && s.item("mug").support=="desk" && near("chair","desk",.34f)
        val read=s.lampOn && near("lamp","sofa",.42f) && s.item("cushion").support=="sofa" && near("coffee","sofa",.39f)
        val plant=s.item("plant")
        val green=RoomProgress.green(plant,s.layoutVersion==RoomLayout.version)
        return listOf(
            RoomGoal("一张好书桌",work,"把手账和茶杯放在书桌上，再把椅子搬近。"),
            RoomGoal("暖光阅读角",read,"靠垫放上沙发，矮桌与落地灯靠近沙发，拉亮灯绳。"),
            RoomGoal("窗边绿意",green,"从箱中取出盆栽，摆在任一扇窗旁。"),
        )
    }
    private fun clear(s: RoomGame,message: String)=s.copy(selectedId=null,origin=null,mechanismOrigin=null,actionTravel=0f,
        gestureDistance=0f,twoHandTurning=false,dragFacing=null,crankRadians=0f,toyGesture=ToyGesture(),extractionBox=null,message=message)
    private fun cancel(s: RoomGame)=RoomRituals.cancel(s).copy(items=s.origin ?: s.items,
        roomYaw=s.mechanismOrigin?.roomYaw ?: s.roomYaw,drawerOpen=s.mechanismOrigin?.drawerOpen ?: s.drawerOpen,
        selectedId=null,origin=null,mechanismOrigin=null,actionTravel=0f,gestureDistance=0f,twoHandTurning=false,dragFacing=null,
        crankRadians=0f,toyGesture=ToyGesture(),extractionBox=null,
        cleaning=s.cleaning?.let { it.copy(progress=it.origin ?: it.progress,
            workTiles=it.originTiles ?: it.workTiles,tool=null,heldTrash=null,origin=null,originTiles=null,contact=null) })
}
