package com.example.tidyspace.pc5853b2a.domain.usecase

import com.example.tidyspace.pc5853b2a.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class OpenAnnexTest {
    private fun game()=StudyRenovation.prepare(MovingHome.prepare(RoomGame())).let {
        it.copy(items=it.items.map { item->item.copy(packed=true,support=null) })
    }
    @Test fun heldFurnitureCrossesFormerStudyGamingPartition() {
        val spec=studyCatalogue().first { it.id=="studystool" }.copy(id="probe")
        val item=RoomItem(spec,Point3(.20f,-.2475f,2.0f))
        val state=game().let { it.copy(items=it.items+item) }
        val moved=RoomPhysics.constrainHeld(state,item,Point3(-.75f,0f,0f),Rotation(),false)
        assertEquals(-.55f,moved.item("probe").position.x,.001f)
        assertTrue(RoomPhysics.heldClear(moved,setOf("probe")))
    }
    @Test fun formerAnnexWallsDoNotSnapOrBlockFurnitureButFloorEdgesRemain() {
        val state=game()
        val spec=studyCatalogue().first { it.id=="studychair" }
        for(position in listOf(Point3(-1.415f,-.1525f,1.7f),Point3(1.44f,-.1525f,2.0f),
                Point3(.025f,-.1525f,2.0f))) {
            val chair=RoomItem(spec,position)
            assertTrue(RoomPhysics.heldClear(state.copy(items=state.items+chair),setOf(spec.id)))
            assertEquals(chair,RoomPhysics.wallContact(state,chair))
        }
        assertFalse(RoomPhysics.inside(RoomItem(spec,Point3(-1.53f,-.1525f,1.7f))))
        assertFalse(RoomPhysics.inside(RoomItem(spec,Point3(1.57f,-.1525f,2.0f))))
    }
}
