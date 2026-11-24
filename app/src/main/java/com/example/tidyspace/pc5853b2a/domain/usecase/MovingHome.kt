package com.example.tidyspace.pc5853b2a.domain.usecase

import com.example.tidyspace.pc5853b2a.domain.model.*

/** Exactly one of each authored object. Migration preserves progress and placed survivors. */
object MovingHome {
    fun boxFor(spec: RoomSpec)=when {
        spec.kind.playable -> "box-toys"
        spec.kind in setOf(Furnishing.Lamp,Furnishing.TableLamp,Furnishing.GamingLight,Furnishing.StudyLight) -> "box-light"
        spec.kind in setOf(Furnishing.GamingMat,Furnishing.GamingDevice,Furnishing.StudyDecor,
            Furnishing.StudyPlant,Furnishing.StudyRug) -> "box-home"
        spec.kind in setOf(Furnishing.Plant,Furnishing.Picture,Furnishing.Book,Furnishing.Mug,
            Furnishing.Cushion,Furnishing.Rug,Furnishing.WallClock,Furnishing.Blanket,
            Furnishing.Decor,Furnishing.WallMirror,Furnishing.TallPlant) -> "box-home"
        else -> "box-furniture"
    }
    fun prepare(s: RoomGame,fresh: Boolean=false): RoomGame {
        if(s.boxInventoryVersion==2 && s.layoutVersion==RoomLayout.version) {
            val missing=availableCatalogue(s).filter { spec->s.items.none { it.spec.assetId==spec.assetId } }
            if(missing.isEmpty()) return s
            // Add new stock without renormalizing, moving, repacking or replacing existing items.
            return s.copy(items=s.items+missing.map { RoomItem(it,
                position=if(it.id.startsWith("gaming") || it.id.startsWith("study")) Point3(0f,0f,0f) else it.start,packed=true) },
                boxStock=movingBoxes().associate { box ->
                    box.id to (s.stock(box.id)+missing.filter { boxFor(it)==box.id }.map { it.id })
                })
        }
        val expanded=if(s.layoutVersion<RoomLayout.version) s.copy(
            items=s.items.map { i ->
                var root=i
                val visited=mutableSetOf(i.spec.id)
                while(root.support!=null && !root.support.startsWith("@")) {
                    val parent=s.items.find { it.spec.id==root.support } ?: break
                    if(!visited.add(parent.spec.id)) break
                    root=parent
                }
                var p=RoomLayout.point(root.position)
                // Wall mounts retain their thickness and sit flush with the new wall.
                if(root.support=="@back") p=p.copy(z=RoomLayout.back+root.spec.size.z/2)
                if(root.support=="@left") p=p.copy(x=RoomLayout.left+root.spec.size.z/2)
                val moved=if(root.packed) root.copy(position=p)
                    else RoomPhysics.wallContact(s,root.copy(position=p))
                val turn=moved.rotation*root.rotation.inverse()
                i.copy(position=moved.position+turn.rotate(i.position-root.position),rotation=turn*i.rotation)
            },layoutVersion=RoomLayout.version) else s
        return normalize(expanded,pack=s.boxInventoryVersion==0)
    }
    fun packAll(s: RoomGame)=normalize(prepare(s),pack=true)
    private fun normalize(s: RoomGame,pack: Boolean): RoomGame {
        val survivors=availableCatalogue(s).map { spec ->
            val copies=s.items.filter { it.spec.assetId==spec.assetId }
            val source=if(s.gamingVersion>0) copies.firstOrNull { it.spec.id !in s.archivedIds }
                else copies.firstOrNull { it.spec.id==spec.id } ?: copies.firstOrNull()
            (source?.copy(spec=if(s.gamingVersion>0) source.spec else spec) ?: RoomItem(spec,
                position=if(spec.id.startsWith("gaming") || spec.id.startsWith("study")) Point3(0f,0f,0f) else spec.start,packed=true)).let {
                if(pack || source==null) it.copy(packed=true,support=null) else it
            }
        }
        val survivingIds=survivors.map { it.spec.id }.toSet()
        // Objects supported by a removed duplicate return safely to their own box.
        val safe=survivors.map { i ->
            if(i.support!=null && i.support !in survivingIds && !i.support.startsWith("@"))
                i.copy(packed=true,support=null) else i
        }
        val crate=s.items.find { it.spec.kind==Furnishing.Crate }
            ?: RoomItem(roomCatalogue().first { it.kind==Furnishing.Crate })
        val extras=if(s.studyVersion>0) emptyList() else if(s.gamingVersion>0) s.items.filter { it.spec.id in s.archivedIds }
            else s.items.filter { BathroomLayout.owns(it.spec.id) }
        val items=safe+extras+
            crate.copy(packed=true,support=null)+movingBoxes().map { spec ->
            s.items.find { it.spec.id==spec.id } ?: RoomItem(spec,RoomLayout.point(spec.start))
        }
        val stocks=movingBoxes().associate { box ->
            box.id to (safe+extras)
                .filter { it.packed && it.spec.id !in s.archivedIds && boxFor(it.spec)==box.id }.map { it.spec.id }
        }
        val counts=s.playCounts.entries.groupBy { it.key.substringBefore('~') }
            .mapValues { (_,values)->values.sumOf { it.value }.coerceAtMost(1_000_000_000L) }
        val toys=s.toyStates.entries.groupBy { it.key.substringBefore('~') }
            .mapValues { (_,values)->values.maxBy { it.value.rounds }.value }
        return s.copy(items=items,movingHome=true,boxStock=stocks,crateOpen=false,
            boxInventoryVersion=2,previewBox=null,extractionBox=null,selectedId=null,
            origin=null,mechanismOrigin=null,focusId=null,angleFocus=null,playCounts=counts,toyStates=toys,
            achievements=if(s.crateOpen) s.achievements+"guide-1" else s.achievements,
            message=if(s.decorating) "每款只一件。挑选喜欢的家具和小物，慢慢布置你的家。" else s.message)
    }
    fun reveal(s: RoomGame,box: String): RoomGame {
        if(!s.decorating || s.selectedId!=null || s.stock(box).isEmpty()) return s
        val asset=s.stock(box).first()
        return s.copy(previewBox=box,focusId=null,angleFocus=null,
            message="${s.inventorySpec(asset)?.name} · 捏起箱子上方的物件；箱内还剩 ${s.stock(box).size} 件")
    }
    fun revealItem(s: RoomGame,box: String,id: String): RoomGame {
        if(id !in s.stock(box) || s.selectedId!=null || !s.decorating) return s
        return reveal(s.copy(boxStock=s.boxStock+(box to (listOf(id)+s.stock(box).filterNot { it==id }))),box)
    }
    fun begin(s: RoomGame,box: String): RoomGame {
        val asset=s.stock(box).firstOrNull() ?: return s
        if(s.previewBox!=box || !s.decorating) return s
        val spec=s.inventorySpec(asset) ?: return s
        if(s.studyVersion>0 && StudyLayout.retired(spec)) return s
        val existing=s.items.find { it.spec.id==asset && it.packed }
        if(asset in s.archivedIds || s.items.any {
            it.spec.assetId==spec.assetId && it.spec.id!=asset && it.spec.id !in s.archivedIds
        } || s.items.any { it.spec.id==asset && !it.packed }) return s
        val source=existing ?: RoomItem(spec)
        val crate=s.item(box)
        val item=source.copy(position=crate.position+Point3(0f,.17f+spec.size.y/2,0f),
            rotation=Rotation.yaw(crate.rotation.yaw()),support=null,packed=false)
        return s.copy(items=s.items.filter { it.spec.id!=item.spec.id }+item,
            selectedId=item.spec.id,origin=s.items,extractionBox=box,focusId=item.spec.id,angleFocus=null,
            mechanismOrigin=RoomMechanisms(s.roomYaw,s.drawerOpen),gestureDistance=0f,gestureTurn=0f,
            message="拿起${spec.name}，放到喜欢的位置。")
    }
    fun commit(s: RoomGame): RoomGame {
        val box=s.extractionBox ?: return s
        val asset=s.selected?.spec?.id ?: return s
        val remaining=s.stock(box).filterNot { it==asset }
        return s.copy(boxStock=s.boxStock+(box to remaining),
            previewBox=box.takeIf { remaining.isNotEmpty() },
            additions=s.additions+1,nextSerial=s.nextSerial+1,extractionBox=null)
    }
}
