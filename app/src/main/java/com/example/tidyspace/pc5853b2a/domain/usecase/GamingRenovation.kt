package com.example.tidyspace.pc5853b2a.domain.usecase

import com.example.tidyspace.pc5853b2a.domain.model.*

/** Idempotent installation. Archived rows retain identity/pose for save compatibility. */
object GamingRenovation {
    fun prepare(s: RoomGame): RoomGame {
        if(s.gamingVersion==GamingLayout.version) return s
        val bathroom=BathroomLayout.prepare(s)
        val kept=BathroomLayout.fixtures().map { bathroom.item(it.spec.id) }
        val archives=bathroom.items.filter { original->
            kept.any { it.spec.assetId==original.spec.assetId && it.spec.id!=original.spec.id }
        }.map { it.spec.id }.toSet()
        val carried=archives.flatMap { RoomPhysics.family(bathroom,it) }.toSet()-archives
        val equipment=GamingLayout.equipment()
        // If an old item occupies the extension, keep its pose and stock the new
        // assembly instead. The default layout never displaces the player's work.
        val initial=bathroom.copy(items=bathroom.items.filterNot { old->
            equipment.any { it.spec.id==old.spec.id }
        }.map { i->if(i.spec.id in archives+carried) i.copy(packed=true,support=null) else i }+equipment,
            archivedIds=bathroom.archivedIds+archives,gamingVersion=GamingLayout.version)
        val blocked=equipment.filter { i->initial.items.any { old->
            old.spec.id !in equipment.map { it.spec.id } && RoomPhysics.intersects(initial,i,old)
        } }.flatMap { RoomPhysics.family(initial,it.spec.id) }.toSet()
        val next=initial.copy(items=initial.items.map { if(it.spec.id in blocked)
            it.copy(packed=true,support=null) else it })
        val stock=movingBoxes().associate { box->
            val retained=next.stock(box.id).filter { id->next.items.any {
                it.spec.id==id && it.packed && id !in next.archivedIds
            } }
            box.id to (retained+next.items.filter {
                it.packed && it.spec.id !in next.archivedIds &&
                    it.spec.id in carried+blocked && MovingHome.boxFor(it.spec)==box.id
            }.map { it.spec.id }).distinct()
        }
        return next.copy(boxStock=stock,previewBox=null,extractionBox=null)
    }
}
