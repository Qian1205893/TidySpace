package com.example.tidyspace.pc5853b2a.domain.usecase

import com.example.tidyspace.pc5853b2a.domain.model.*
import com.example.tidyspace.pc5853b2a.data.repository.RoomPersistence
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class LifestyleTest {
    private fun base()=MovingHome.prepare(RoomGame())
    @Test fun latestInventoryMigrationOnlyAppendsMissingLifestyleStock() {
        val all=base()
        val old=all.copy(items=all.items.filterNot { LifestyleModels.contains(it.spec) },
            boxStock=all.boxStock.mapValues { (_,ids)->ids.filterNot { it.startsWith("life_") } },
            achievements=setOf("guide-1"),playCounts=mapOf("woodfish" to 23L))
        val next=MovingHome.prepare(old)
        assertEquals(old,next.copy(items=next.items.take(old.items.size),boxStock=old.boxStock))
        assertEquals(72,next.items.size-old.items.size)
        assertTrue(next.items.takeLast(72).all { it.packed })
        old.boxStock.forEach { (box,ids)->assertEquals(ids,next.stock(box).take(ids.size)) }
        assertEquals(next,MovingHome.prepare(next))
        val decoded=checkNotNull(RoomPersistence.decode(RoomPersistence.encode(next)))
        assertEquals(next.items,decoded.items)
        assertEquals(next.boxStock,decoded.boxStock)
        assertEquals(old.playCounts,decoded.playCounts)
        assertEquals(old.achievements,decoded.achievements)
    }
    @Test fun everyLifestyleObjectHasUniqueModelAndStableFloorPlacement() {
        val stock=lifestyleCatalogue()
        assertEquals(72,stock.map { it.assetId }.toSet().size)
        assertEquals(12,stock.map { LifestyleModels.family(it) }.toSet().size)
        stock.forEach { spec ->
            val item=RoomItem(spec,Point3(.1f,-.32f+spec.size.y/2,.12f),packed=false)
            val game=base().let { s->s.copy(items=s.items.map { if(it.spec.id==spec.id) item else it.copy(packed=true) }) }
            assertNull(spec.id,RoomPhysics.validateLayout(game))
            val held=RoomUseCase().reduce(game,RoomAction.Begin(spec.id,Rotation()))
            assertEquals(spec.id,held.selectedId)
            val moved=RoomUseCase().reduce(held,RoomAction.Transform(GrabDelta(Point3(.06f,.09f,.07f),Rotation())))
            assertEquals(spec.id,held.item(spec.id).position+Point3(.06f,.09f,.07f),moved.item(spec.id).position)
            val cancelled=RoomUseCase().reduce(moved,RoomAction.Cancel)
            assertEquals(spec.id,game.items,cancelled.items)
        }
    }
    @Test fun undoCreatedBeforeExpansionRetainsAllNewStock() {
        val current=base()
        val old=current.copy(items=current.items.filterNot { LifestyleModels.contains(it.spec) },
            boxStock=current.boxStock.mapValues { (_,ids)->ids.filterNot { it.startsWith("life_") } })
        val restored=ShowroomUseCase().restore(current,old)
        assertEquals(current.items,restored.items)
        assertEquals(current.boxStock,restored.boxStock)
        assertNotNull(RoomPersistence.decode(RoomPersistence.encode(restored)))
    }
    @Test fun measuredWorktopsSupportObjectsAndRejectOverhang() {
        lifestyleCatalogue().filter { LifestyleModels.top(it)!=null }.forEach { spec ->
            val top=checkNotNull(LifestyleModels.top(spec))
            val table=RoomItem(spec,Point3(.1f,-.32f+spec.size.y/2,.1f),packed=false)
            val prop=RoomItem(roomCatalogue().first { it.id=="mug" },
                table.position+Point3(0f,top.y+.035f,top.z),support=spec.id,packed=false)
            val game=base().let { s->s.copy(items=s.items.map {
                when(it.spec.id) { spec.id->table;"mug"->prop;else->it.copy(packed=true) }
            }) }
            assertNull(spec.id,RoomPhysics.validateLayout(game))
            assertFalse(RoomPhysics.supported(game,prop.copy(position=prop.position+Point3(top.width,0f,0f))))
        }
    }
    @Test fun cushionsRestOnSeatsInsteadOfFloatingAboveBackrests() {
        lifestyleCatalogue().filter { LifestyleModels.seat(it)!=null }.forEach { spec ->
            val seat=checkNotNull(LifestyleModels.seat(spec))
            val chair=RoomItem(spec,Point3(.1f,-.32f+spec.size.y/2,.1f),packed=false)
            val cushion=RoomItem(roomCatalogue().first { it.id=="cushion" },
                chair.position+Point3(0f,seat.y+.021f,seat.z),support=spec.id,packed=false)
            val game=base().let { s->s.copy(items=s.items.map {
                when(it.spec.id) { spec.id->chair;"cushion"->cushion;else->it.copy(packed=true) }
            }) }
            assertTrue(spec.id,RoomPhysics.supported(game,cushion))
            assertFalse(spec.id,RoomPhysics.intersects(game,chair,cushion))
        }
    }
    @Test fun allShippedTemplatesHaveStableLayoutsAndSixExclusiveModels() {
        // Parse the checked-in authored targets; device tests also run the production JSON reader.
        val file=listOf(File("src/main/assets/showrooms.json"),File("app/src/main/assets/showrooms.json")).first { it.exists() }
        val text=file.readText()
        val roomPattern=Regex(""""id": "([^"]+)",\s*"title": "([^"]+)".*?"items": \[(.*?)\],\s*"zones":""",RegexOption.DOT_MATCHES_ALL)
        val itemPattern=Regex("""\{\s*"id": "([^"]+)".*?"position": \[(.*?)\],\s*"yaw": ([-\d.]+),\s*"support": (null|"[^"]+"),\s*"zone": "([^"]+)".*?\}""",RegexOption.DOT_MATCHES_ALL)
        val rooms=roomPattern.findAll(text).map { match ->
            val targets=itemPattern.findAll(match.groupValues[3]).map { item ->
                val p=item.groupValues[2].split(',').map { it.trim().toFloat() }
                ShowroomTarget(item.groupValues[1],Point3(p[0],p[1],p[2]),item.groupValues[3].toFloat(),
                    item.groupValues[4].takeUnless { it=="null" }?.trim('"'),item.groupValues[5])
            }.toList()
            Showroom(match.groupValues[1],match.groupValues[2],"","入门","","","",RoomMood.Morning,10f,.7f,
                targets,emptyList(),emptyList(),emptyList())
        }.toList()
        assertEquals(12,rooms.size)
        for(room in rooms) {
            val result=ShowroomUseCase().apply(base(),room)
            assertNull("${room.id}: ${result.error}",result.error)
            assertEquals(6,room.targets.count { it.asset.startsWith("life_${room.id}_") })
        }
        val ids=rooms.map { r->r.targets.map { it.asset }.toSet() }
        assertTrue(ids.reduce { a,b->a intersect b }.isEmpty())
        ids.forEachIndexed { i,a->ids.drop(i+1).forEach { b->
            assertTrue((a intersect b).size.toFloat()/(a union b).size<.38f)
        } }
    }
}
