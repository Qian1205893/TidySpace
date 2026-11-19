package com.example.tidyspace.pc5853b2a.domain.usecase

import com.example.tidyspace.pc5853b2a.domain.model.*

/** Remove retired fixtures completely while preserving unrelated player state. */
object StudyRenovation {
    fun prepare(s: RoomGame): RoomGame {
        if(s.studyVersion==StudyLayout.version) return s
        val prior=GamingRenovation.prepare(s)
        val removed=prior.items.filter { StudyLayout.retired(it.spec) }.map { it.spec.id }.toSet()
        val carried=removed.flatMap { RoomPhysics.family(prior,it) }.toSet()-removed
        val equipment=StudyLayout.equipment()
        val newIds=equipment.map { it.spec.id }.toSet()
        val initial=prior.copy(items=prior.items.filterNot {
            it.spec.id in removed || it.spec.assetId in newIds
        }.map { if(it.spec.id in carried) it.copy(packed=true,support=null) else it }+equipment,
            bathroomVersion=0,studyVersion=StudyLayout.version,archivedIds=prior.archivedIds-removed)
        // A player-owned obstruction keeps its pose; only the new assembly goes in its box.
        val blocked=equipment.filter { i->initial.items.any { old->
            old.spec.id !in newIds && RoomPhysics.intersects(initial,i,old)
        } }.flatMap { RoomPhysics.family(initial,it.spec.id) }.toSet()
        val next=initial.copy(items=initial.items.map { if(it.spec.id in blocked)
            it.copy(packed=true,support=null) else it })
        val stocks=movingBoxes().associate { box ->
            val retained=prior.stock(box.id).filter { id->
                id !in removed && id !in newIds && next.items.any { it.spec.id==id && it.packed }
            }
            box.id to (retained+next.items.filter {
                it.packed && it.spec.id in carried+blocked && MovingHome.boxFor(it.spec)==box.id
            }.map { it.spec.id }).distinct()
        }
        return next.copy(boxStock=stocks,previewBox=null,extractionBox=null,
            selectedId=null,origin=null,mechanismOrigin=null,
            focusId=next.focusId?.takeUnless { it in removed },
            angleFocus=next.angleFocus?.takeUnless { it in removed })
    }
}
