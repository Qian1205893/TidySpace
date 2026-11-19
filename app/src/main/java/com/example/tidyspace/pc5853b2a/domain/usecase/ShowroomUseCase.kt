package com.example.tidyspace.pc5853b2a.domain.usecase

import com.example.tidyspace.pc5853b2a.domain.model.*
import kotlin.math.abs

/** Whole-layout transaction. Reuses the real inventory instances, including migrated aliases. */
class ShowroomUseCase {
    data class Result(val game: RoomGame?=null,val error: String?=null)
    fun filter(rooms: List<Showroom>,style: String,level: String,query: String): List<Showroom> =
        rooms.filter { (style=="全部风格" || it.style==style) && (level=="全部难度" || it.difficulty==level) &&
            (query.isBlank() || listOf(it.title,it.style,it.brief).any { value->value.contains(query.trim(),true) } ||
                it.targets.any { t->uniqueCatalogue().find { c->c.assetId==t.asset }?.name?.contains(query.trim(),true)==true }) }

    fun source(s: RoomGame,asset: String): RoomItem? =
        s.items.singleOrNull { it.spec.assetId==asset && it.spec.id !in s.archivedIds &&
            availableCatalogue(s).any { c->c.assetId==asset } }

    fun missing(s: RoomGame,r: Showroom)=r.targets.filter { source(s,it.asset)==null }.map { it.asset }

    fun apply(s: RoomGame,r: Showroom): Result {
        if(!s.decorating) return Result(error="先完成第一关清洁，再套用样板间。")
        if(s.selectedId!=null || s.cardDrag!=null) return Result(error="先放下手中的物品，再套用。")
        if(r.targets.map { it.asset }.distinct().size!=r.targets.size) return Result(error="样板间重复使用同一物品。")
        val unavailable=missing(s,r)
        if(unavailable.isNotEmpty()) return Result(error="当前缺少 ${unavailable.size} 件物品，暂时无法套用。")
        val ids=r.targets.associate { it.asset to checkNotNull(source(s,it.asset)).spec.id }
        val targets=r.targets.associateBy { ids.getValue(it.asset) }
        if(r.targets.any { it.support!=null && !it.support.startsWith("@") && it.support !in ids })
            return Result(error="承托物品缺失，暂时无法套用。")
        val next=s.copy(items=s.items.map { i ->
            val t=targets[i.spec.id]
            when {
                t!=null -> i.copy(position=t.position,rotation=Rotation.yaw(t.yaw),
                    support=t.support?.let { ids[it] ?: it },packed=false)
                i.spec.kind==Furnishing.MovingBox -> i.copy(packed=true,support=null)
                else -> i.copy(packed=true,support=null)
            }
        },mood=r.mood,dayHour=r.hour,indoorBrightness=r.brightness,lampOn=true,
            crateOpen=false,drawerOpen=0f,previewBox=null,extractionBox=null,
            focusId=null,angleFocus=null,origin=null,mechanismOrigin=null,ritual=null,
            toyGesture=ToyGesture(),dragFacing=null,twoHandTurning=false,
            message="已套用${r.title}。每件物品都可以继续自由拖动。").let { g ->
            g.copy(boxStock=movingBoxes().associate { box ->
                box.id to g.items.filter { it.packed && it.spec.kind !in setOf(Furnishing.Crate,Furnishing.MovingBox) &&
                    it.spec.id !in g.archivedIds && MovingHome.boxFor(it.spec)==box.id }.map { it.spec.id }
            })
        }
        val error=RoomPhysics.validateLayout(next,targets.keys)
        return if(error!=null) Result(error=error) else Result(next)
    }

    /** Only layout and light are restored. New cleaning, achievements and toy progress survive. */
    fun restore(current: RoomGame,old: RoomGame): RoomGame =
        current.copy(items=current.items.map { item ->
            old.items.find { it.spec.id==item.spec.id }?.let { prior ->
                item.copy(position=prior.position,rotation=prior.rotation,support=prior.support,packed=prior.packed)
            } ?: item
        },boxStock=old.boxStock,crateOpen=old.crateOpen,drawerOpen=old.drawerOpen,
            roomYaw=old.roomYaw,mood=old.mood,dayHour=old.dayHour,indoorBrightness=old.indoorBrightness,
            lampOn=old.lampOn,previewBox=null,extractionBox=null,selectedId=null,origin=null,
            mechanismOrigin=null,focusId=null,angleFocus=null,ritual=null,cardDrag=null,
            dragFacing=null,toyGesture=ToyGesture(),twoHandTurning=false,
            message="已恢复套用前的小屋，新的成就和玩法进度已保留。").let { restored ->
            // A saved undo may predate a catalogue expansion. Keep new packed stock reachable.
            val priorIds=old.items.map { it.spec.id }.toSet()
            restored.copy(boxStock=movingBoxes().associate { box ->
                box.id to (old.stock(box.id)+restored.items.filter {
                    it.packed && it.spec.id !in priorIds && it.spec.id !in restored.archivedIds &&
                        it.spec.kind !in setOf(Furnishing.Crate,Furnishing.MovingBox) &&
                        MovingHome.boxFor(it.spec)==box.id
                }.map { it.spec.id }).distinct()
            })
        }

    fun placed(s: RoomGame,r: Showroom,t: ShowroomTarget): Boolean {
        val i=source(s,t.asset) ?: return false
        val support=t.support?.let { if(it.startsWith("@")) it else source(s,it)?.spec?.id }
        val yaw=abs(((i.rotation.yaw()-t.yaw+540f)%360f)-180f)
        return !i.packed && i.spec.id!=s.selectedId && i.support==support &&
            (i.position-t.position).length()<.16f && yaw<40f &&
            RoomPhysics.validateLayout(s,setOf(i.spec.id))==null
    }
    fun pending(s: RoomGame,r: Showroom,step: Int): List<ShowroomTarget> {
        val assets=r.steps.getOrNull(step)?.assets.orEmpty()
        return r.targets.filter { it.asset in assets && !placed(s,r,it) }
    }
    fun changed(s: RoomGame,r: Showroom): List<String> {
        val byAsset=r.targets.associateBy { it.asset }
        return s.items.filter { it.spec.kind !in setOf(Furnishing.Crate,Furnishing.MovingBox) &&
            it.spec.id !in s.archivedIds }.filter { i ->
            val t=byAsset[i.spec.assetId]
            if(t==null) !i.packed else i.packed || (i.position-t.position).length()>.035f ||
                abs(((i.rotation.yaw()-t.yaw+540f)%360f)-180f)>8f
        }.map { it.spec.name }
    }
}
