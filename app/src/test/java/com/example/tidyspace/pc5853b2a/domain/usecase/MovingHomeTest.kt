package com.example.tidyspace.pc5853b2a.domain.usecase

import com.example.tidyspace.pc5853b2a.domain.model.*
import com.example.tidyspace.pc5853b2a.data.repository.RoomPersistence
import com.example.tidyspace.pc5853b2a.ui.sorting.RoomViewModel
import org.junit.Assert.*
import org.junit.Test

class MovingHomeTest {
    private val rules=RoomUseCase()
    private fun fresh()=MovingHome.prepare(RoomGame(),fresh=true)
    private fun take(s: RoomGame,box: String,p: Point3): RoomGame {
        val preview=MovingHome.reveal(s,box)
        val held=rules.reduce(preview,RoomAction.Begin("@unpack:$box"))
        assertNotNull(held.selected)
        return rules.reduce(rules.reduce(held,RoomAction.Transform(
            GrabDelta(p-held.selected!!.position,Rotation()))),RoomAction.End)
    }
    @Test fun fourBoxesContainEveryCatalogueItemExactlyOnce() {
        val s=fresh()
        assertEquals(uniqueCatalogue().size,s.remainingGoods)
        assertEquals(uniqueCatalogue().map { it.id }.toSet(),s.boxStock.values.flatten().toSet())
        assertEquals(4,s.items.count { it.spec.kind==Furnishing.MovingBox })
        assertTrue(s.items.filter { it.spec.kind==Furnishing.MovingBox }.all { RoomPhysics.inside(it) && RoomPhysics.supported(s,it) })
    }
    @Test fun legacyDuplicatesBecomeOneOfEachWithDifferentNewFurniture() {
        val s=RoomGame().copy(items=RoomGame().items+RoomItem(roomCatalogue().first { it.id=="chair" }.copy(
            id="chair~1",assetId="chair"),Point3(.9f,-.175f,.35f)),nextSerial=2,additions=1)
        val migrated=MovingHome.prepare(s)
        assertTrue("chair" in migrated.stock("box-furniture"))
        assertFalse("chair~1" in migrated.stock("box-furniture"))
        assertTrue(migrated.items.filter { it.spec.kind!=Furnishing.MovingBox }.all { it.packed })
        assertEquals(1,migrated.items.count { it.spec.assetId=="chair" })
        assertTrue(listOf("ottoman","gardenbench").all { it in migrated.stock("box-furniture") })
        assertTrue(listOf("plant","book","mug","cushion").all { it in migrated.boxStock.values.flatten() })
    }
    @Test fun uniqueChairCanBeTakenSavedAndRepackedWithoutReintroducingDuplicates() {
        val chair=RoomGame().item("chair")
        val s=MovingHome.prepare(RoomGame(items=RoomGame().items+
            chair.copy(spec=chair.spec.copy(id="chair~1",assetId="chair")),nextSerial=2,additions=1))
            .let { it.copy(boxStock=it.boxStock+("box-furniture" to listOf("chair"))) }
        val done=take(s,"box-furniture",Point3(.1f,.3f,.35f))
        assertFalse(done.item("chair").packed)
        assertTrue(done.stock("box-furniture").isEmpty())
        val restored=RoomPersistence.decode(RoomPersistence.encode(done))!!
        assertEquals(done.items,restored.items)
        val packed=rules.reduce(restored.copy(focusId="chair"),RoomAction.Recycle)
        assertEquals(1,packed.items.count { it.spec.assetId=="chair" })
        assertEquals(listOf("chair"),packed.stock("box-furniture"))
    }
    @Test fun firstLevelCannotUnpackAndCleanCompletionUnlocksOnlyBoxes() {
        val start=MovingHome.prepare(RoomGame(cleaning=CleaningState()))
        assertEquals(start.boxStock,MovingHome.reveal(start,"box-home").boxStock)
        assertNull(MovingHome.begin(start,"box-home").selected)
        val spotless=cleaningMess().associate { it.id to 1f }
        val clean=start.copy(cleaning=CleaningState(progress=spotless,finished=true))
        assertTrue(clean.decorating)
        assertTrue(clean.items.filter { it.spec.kind!=Furnishing.MovingBox }.all { it.packed })
        assertEquals(uniqueCatalogue().size,clean.remainingGoods)
        assertEquals("box-home",MovingHome.reveal(clean,"box-home").previewBox)
        val replay=rules.reduce(clean,RoomAction.CleanAgain)
        assertFalse(replay.decorating)
        assertEquals(start.boxStock,replay.boxStock)
    }
    @Test fun tappingShowsPreviewWhileDraggingMovesTheBox() {
        val s=fresh()
        val tap=rules.reduce(rules.reduce(s,RoomAction.Begin("box-home")),RoomAction.End)
        assertEquals("box-home",tap.previewBox)
        assertEquals(s.item("box-home"),tap.item("box-home"))
        val moved=rules.reduce(rules.reduce(rules.reduce(tap,RoomAction.Begin("box-home")),
            RoomAction.Transform(GrabDelta(Point3(-.03f,.05f,0f),Rotation()))),RoomAction.End)
        assertEquals(.43f*RoomLayout.scale-.03f,moved.item("box-home").position.x,.001f)
        assertEquals(s.boxStock,moved.boxStock)
    }
    @Test fun extractionCancellationAndInvalidReleaseKeepStockUntilValidPlacement() {
        val s=MovingHome.reveal(fresh(),"box-furniture")
        val held=rules.reduce(s,RoomAction.Begin("@unpack:box-furniture"))
        assertEquals(s.boxStock,held.boxStock)
        val cancelled=rules.reduce(held,RoomAction.Cancel)
        assertEquals(s.items,cancelled.items)
        val contact=take(s,"box-furniture",Point3(RoomLayout.left,.1f,RoomLayout.back))
        assertTrue(RoomPhysics.heldClear(contact,setOf("desk")))
        assertTrue(RoomPhysics.supported(contact,contact.item("desk")))
        assertEquals(s.stock("box-furniture"),contact.stock("box-furniture"))
        val valid=take(contact,"box-furniture",Point3(.10f,.3f,.20f))
        assertEquals(s.stock("box-furniture").size-1,valid.stock("box-furniture").size)
        assertEquals(s.items,RoomPersistence.decode(RoomPersistence.encode(held))!!.items)
    }
    @Test fun lastItemOnlyEmptiesTheBoxAfterStablePlacement() {
        val s=fresh().let { it.copy(boxStock=it.boxStock+("box-home" to listOf("mug"))) }
        val done=take(s,"box-home",Point3(.1f,.15f,.35f))
        assertTrue(done.stock("box-home").isEmpty())
        assertNull(done.previewBox)
        assertFalse(done.item("mug").packed)
        assertEquals(-.285f,done.item("mug").position.y,.001f)
        val reissue=rules.reduce(done,RoomAction.Add("mug"))
        assertEquals(done.items,reissue.items)
        val restored=RoomPersistence.decode(RoomPersistence.encode(done))!!
        assertEquals(done.boxStock,restored.boxStock)
        assertEquals(done.items,restored.items)
    }
    @Test fun nearWallDeskContactsSkirtingWithoutManualGap() {
        val s=RoomGame()
        val desk=s.item("desk").copy(position=Point3(-.30f,-.15f,RoomLayout.back+.182f),rotation=Rotation.yaw(8f))
        val snapped=RoomPhysics.wallContact(s,desk)
        assertEquals(0f,snapped.rotation.yaw(),.001f)
        val back=RoomPhysics.boxes(s,snapped).filter { b ->
            snapped.position.y+b.center.y-b.size.y/2<-.247f
        }.minOf { snapped.position.z+it.center.z-it.size.z/2 }
        assertEquals(-.925f*RoomLayout.scale,back,.0001f)
        assertTrue(RoomPhysics.inside(snapped))
    }
    @Test fun lightSettingClampsAndSurvivesSaving() {
        val s=rules.reduce(fresh(),RoomAction.Brightness(.22f))
        assertEquals(.22f,RoomPersistence.decode(RoomPersistence.encode(s))!!.indoorBrightness,0f)
        assertEquals(1f,rules.reduce(s,RoomAction.Brightness(2f)).indoorBrightness,0f)
        assertEquals(s,rules.reduce(s,RoomAction.Brightness(Float.NaN)))
    }
    @Test fun legacyV6MigrationPreservesToyStateCleaningAndAchievements() {
        val base=RoomGame(crateOpen=true,achievements=setOf("guide-1"))
        val encoded=RoomPersistence.encode(base).lines()
        val header=encoded.first().split('\t').take(14).toMutableList().apply { this[0]="tidy-room-6" }
        val legacy=(listOf(header.joinToString("\t"))+encoded.drop(1)).joinToString("\n")
        val decoded=RoomPersistence.decode(legacy)!!
        assertEquals(base.items,decoded.items)
        val migrated=MovingHome.prepare(decoded)
        assertTrue("guide-1" in migrated.achievements)
        assertEquals(migrated.items,RoomPersistence.decode(RoomPersistence.encode(migrated))!!.items)
    }
    @Test fun extractionUndoRedoAndRepackingKeepOneOwnedSpecies() {
        val start=fresh().let { it.copy(boxStock=it.boxStock+("box-home" to listOf("mug"))) }
        val vm=RoomViewModel(RoomPersistence.encode(start))
        fun game()=vm.state.value.game
        fun act(a: RoomAction)=vm.onEvent(a)
        act(RoomAction.Add("mug"));act(RoomAction.Begin("@unpack:box-home"))
        act(RoomAction.Transform(GrabDelta(Point3(.10f,.10f,.35f)-game().selected!!.position,Rotation())))
        act(RoomAction.End)
        assertEquals(0,game().stock("box-home").size)
        act(RoomAction.Undo)
        assertEquals(listOf("mug"),game().stock("box-home"))
        assertTrue(game().item("mug").packed)
        act(RoomAction.Redo)
        assertFalse(game().item("mug").packed)
        assertTrue(game().stock("box-home").isEmpty())
        act(RoomAction.Recycle)
        assertTrue(game().item("mug").packed)
        assertEquals(listOf("mug"),game().stock("box-home"))
        assertEquals(1,game().items.count { it.spec.assetId=="mug" })
    }
    @Test fun vanishedBoxesCannotBlockADrop() {
        val start=fresh().let { it.copy(boxStock=it.boxStock+("box-home" to emptyList())) }
        val mug=start.item("mug").copy(position=Point3(.43f,.25f,1.10f),packed=false)
        val state=start.copy(items=start.items.map { if(it.spec.id=="mug") mug else it },selectedId="mug")
        val result=RoomPhysics.solve(state)
        assertTrue(result.reason,result.valid)
        assertEquals(-.285f,result.item!!.position.y,.001f)
    }
    @Test fun entrySeamAcceptsFurnitureSpanningBothFloors() {
        val start=fresh()
        val cabinet=RoomItem(homeCatalogue().first { it.id=="shoecabinet" },Point3(.68f,.25f,.60f))
        val result=RoomPhysics.solve(start.copy(items=start.items+cabinet,selectedId=cabinet.spec.id))
        assertTrue(result.reason,result.valid)
        assertEquals(-.20f,result.item!!.position.y,.001f)
    }
}
