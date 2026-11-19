package com.example.tidyspace.pc5853b2a

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import android.view.inspector.WindowInspector
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.tidyspace.pc5853b2a.domain.model.*
import com.example.tidyspace.pc5853b2a.data.repository.RoomPersistence
import com.example.tidyspace.pc5853b2a.platform.LaunchActivity
import com.example.tidyspace.pc5853b2a.ui.sorting.RoomViewModel
import com.example.tidyspace.pc5853b2a.ui.sorting.scene.*
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import com.pico.spatial.core.container.WorldScaleType
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Native meshes, hit regions and all cleaning actions. Does not fake tracked eye/hand input. */
@RunWith(AndroidJUnit4::class)
class CleaningNativeRuntimeTest {
    @Test fun dirtyHouseCleansUnlocksAndRetainsNativeResources(): Unit=runBlocking {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        @Suppress("DEPRECATION")
        val metadata=context.packageManager.getActivityInfo(
            android.content.ComponentName(context,LaunchActivity::class.java),PackageManager.GET_META_DATA).metaData
        assertEquals(WorldScaleType.FIXED.value,metadata.getInt("pico.spatial.windowcontainer.worldscaletype"))
        instrumentation.runOnMainSync { context.startActivity(
            Intent(context,LaunchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        delay(2000)
        withContext(Dispatchers.Main) {
            val parent=node()
            val targets=mutableMapOf<Entity,String>()
            val r=SceneResources()
            val scene=RoomRenovation(targets)
            val vm=RoomViewModel(RoomPersistence.encode(
                com.example.tidyspace.pc5853b2a.domain.usecase.MovingHome.prepare(RoomGame(cleaning=CleaningState()))))
            fun game()=vm.state.value.game
            fun act(a: RoomAction) { vm.onEvent(a);scene.applyCleaning(game()) }
            fun move(p: Point3)=act(RoomAction.Transform(GrabDelta(p-game().cleaning!!.position,Rotation())))
            try {
                scene.build(parent,r)
                scene.synchronize(game())
                assertFalse(game().decorating)
                for(name in listOf("renovation_house","renovation_fireplace","renovation_base")) {
                    val entity=parent.findEntity(name)!!
                    assertFalse(entity.getVisualBounds(entity,true,true).isEmpty())
                }
                CleanTool.entries.forEach { assertTrue(targets.containsValue("@clean:${it.name}")) }
                val broom=checkNotNull(parent.findEntity("clean_Broom"))
                val broomBounds=broom.getVisualBounds(broom,true,true)
                assertTrue("Animated bristle root must retain a floor-safe native bound",broomBounds.min.y>=-.016f)
                assertTrue("Broom native width stays within the existing tool footprint",
                    broomBounds.max.x-broomBounds.min.x<=.174f)
                assertFalse("Floor/wall must not be a room rotation target",targets.containsValue("@room"))
                assertEquals(7,targets.values.count { it.startsWith("@trash:") })
                assertTrue(cleaningMess().all { parent.findEntity(it.id)!!.enabled })
                // Cover optional gathering before vacuuming nearby floor areas,
                // which may already remove loose paper under the new rules.
                for(m in cleaningMess().sortedBy { if(it.kind==MessKind.Debris) 0 else 1 }) {
                    val entity=parent.findEntity(m.id)!!
                    if(game().cleaning!!.amount(m.id)==1f) { assertFalse(entity.enabled);continue }
                    assertTrue(entity.enabled)
                    assertFalse(entity.getVisualBounds(entity,true,true).isEmpty())
                    if(m.kind==MessKind.Dust || m.kind==MessKind.Wall) {
                        val bounds=entity.getVisualBounds(entity,true,true)
                        assertEquals("Visual stain and cleaning contact must have the same width: ${m.id}",
                            m.size.x,bounds.max.x-bounds.min.x,.002f)
                    }
                    when(m.kind) {
                        MessKind.Trash -> {
                            act(RoomAction.Begin("@trash:${m.id}"));move(CLEAN_BIN+Point3(0f,.13f,0f));act(RoomAction.End)
                        }
                        else -> {
                            val tool=when(m.kind) { MessKind.Dust->CleanTool.Vacuum;MessKind.Debris->CleanTool.Broom;
                                MessKind.Spill->CleanTool.Mop;else->CleanTool.Brush }
                            act(RoomAction.Begin("@clean:${tool.name}"))
                            if(m.kind==MessKind.Debris) {
                                val piece=parent.findEntity("${m.id}_piece-0")!!
                                val old=piece.transform.position
                                move(m.position-Point3(.08f,0f,0f))
                                repeat(10) { move(m.position+Point3(if(it%2==0) .08f else -.08f,0f,0f)) }
                                assertEquals(.5f,game().cleaning!!.amount(m.id),0f)
                                assertNotEquals(old,piece.transform.position)
                                assertTrue(entity.enabled)
                                act(RoomAction.End);act(RoomAction.Begin("@clean:Vacuum"))
                                move(m.position-Point3(.08f,0f,0f))
                                repeat(10) { move(m.position+Point3(if(it%2==0) .08f else -.08f,0f,0f)) }
                            } else {
                                val p=CleaningGrid.centre(m,9)
                                move(p)
                                if(tool==CleanTool.Mop) repeat(4) { act(RoomAction.CleanTick(.075f)) }
                                move(p+Point3(.06f,0f,0f));scene.animate(.016)
                                assertTrue(game().cleaning!!.amount(m.id) in .001f.. .8f)
                                val materials=parent.findEntity("${m.id}_mesh")!!.components[ModelComponent::class.java]!!.materials
                                    .map { it as PhysicallyBasedMaterial }
                                assertEquals(CleaningGrid.COUNT,materials.size)
                                materials.forEach { material ->
                                    assertEquals(BlendingMode.TRANSPARENT,material.getBlendingMode())
                                    assertFalse(material.getDepthWrite())
                                    val index=Regex("cell (\\d+)").find(material.getName())!!.groupValues[1].toInt()
                                    assertEquals(1f-CleaningVisibility.cells(game().cleaning!!,m)[index],material.getOpacity(),.001f)
                                }
                                assertEquals(0f,materials.single { it.getName().startsWith("${m.id} cell 0 ") }.getOpacity(),0f)
                                if(m.kind!=MessKind.Spill) assertTrue(materials.single { it.getName().startsWith("${m.id} cell 9 ") }.getOpacity()<.67f)
                                repeat(if(tool==CleanTool.Mop) 3 else 1) { repeat(3) { row ->
                                    val centre=CleaningGrid.centre(m,row*8)
                                    val steps=kotlin.math.ceil(m.size.x/.06f).toInt()
                                    move(centre.copy(x=m.position.x-m.size.x/2))
                                    repeat(steps) { col->move(centre.copy(x=m.position.x-m.size.x/2+(col+1)*m.size.x/steps)) }
                                } }
                                materials.forEach { assertEquals(0f,it.getOpacity(),0f) }
                            }
                            act(RoomAction.End)
                            assertFalse(parent.findEntity("clean_${tool.name}")!!.enabled && game().decorating)
                        }
                    }
                    if(m.kind==MessKind.Trash && !game().decorating) {
                        assertTrue("Collected trash should settle inside the bin",entity.enabled)
                        assertEquals("@clean:Bin",targets[entity])
                    } else assertFalse("Mess still visible after cleaning: ${m.id}",entity.enabled)
                }
                assertTrue(game().decorating)
                assertEquals(20,game().cleaning!!.count)
                assertTrue(game().items.filter { it.spec.kind!=Furnishing.MovingBox }.all { it.packed })
                val inventory=game().boxStock.values.flatten()
                assertEquals(uniqueCatalogue().map { it.id }.toSet(),inventory.toSet())
                assertEquals("Every catalogue item must occur in inventory once",inventory.toSet().size,inventory.size)
                assertEquals(uniqueCatalogue().size,game().remainingGoods)
                assertFalse(parent.findEntity("clean_bin")!!.enabled)
                assertTrue(RoomPersistence.decode(RoomPersistence.encode(game()))!!.decorating)
                val earned=game().items
                act(RoomAction.Add("chair"));assertEquals(earned.size,game().items.size)
                assertEquals("box-furniture",game().previewBox)
                act(RoomAction.CleanAgain)
                assertFalse(game().decorating);assertEquals(earned.size,game().items.size)
                assertTrue(parent.findEntity("clean_bin")!!.enabled)
                assertEquals(20,cleaningMess().count { parent.findEntity(it.id)!!.enabled })
                val views=WindowInspector.getGlobalWindowViews()
                views.filter { it.width>300 && it.height.toFloat()/it.width in 1.45f..1.55f }.forEachIndexed { i,view ->
                    val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
                    view.draw(Canvas(bitmap))
                    File(context.getExternalFilesDir(null),"cleaning-augment-$i.png").outputStream().use {
                        bitmap.compress(Bitmap.CompressFormat.PNG,100,it)
                    }
                    bitmap.recycle()
                }
                Log.i("TidyCleaningTest","PASS native house+fireplace+roof+balcony; 6 organic areas+3 trash; sweep reveal; 3 tool captures; disposal; full clean loop; save; furnishing gate; replay")
            } finally { parent.destroy(true);r.close() }
        }
    }
    @Test fun invisibleLastWallCellAndStationarySuctionMatchNativeMaterialsWithoutRepeatedWrites(): Unit=runBlocking {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync { instrumentation.targetContext.startActivity(
            Intent(instrumentation.targetContext,LaunchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        delay(2000)
        withContext(Dispatchers.Main) {
            val parent=node();val r=SceneResources()
            val scene=RoomRenovation(mutableMapOf())
            val cells=List(CleaningGrid.COUNT) { if(it==0) 0f else 1f }
            val old=RoomGame(cleaning=CleaningState(progress=cleaningMess().associate {
                it.id to if(it.id=="wall-1") cells.average().toFloat() else 1f },
                workTiles=mapOf("wall-1" to cells)))
            val vm=RoomViewModel(RoomPersistence.encode(old))
            fun act(a: RoomAction) { vm.onEvent(a);scene.applyCleaning(vm.currentGame) }
            try {
                scene.build(parent,r);scene.synchronize(vm.currentGame)
                assertTrue(vm.currentGame.decorating)
                assertTrue(cleaningMess().all { !parent.findEntity(it.id)!!.enabled })
                assertFalse(parent.findEntity("clean_bin")!!.enabled)
                assertTrue(vm.currentGame.cleaning!!.progress.values.all { it==1f })
                assertTrue(vm.currentGame.cleaning!!.workTiles.values.all { values->values.all { it==1f } })
                act(RoomAction.CleanAgain)
                val m=cleaningMess().first()
                act(RoomAction.Begin("@clean:Vacuum"))
                act(RoomAction.Transform(GrabDelta(CleaningGrid.centre(m,10)-vm.currentGame.cleaning!!.position,Rotation())))
                repeat(24) { act(RoomAction.CleanTick(.033f)) }
                val materials=parent.findEntity("${m.id}_mesh")!!.components[ModelComponent::class.java]!!.materials
                    .map { it as PhysicallyBasedMaterial }.associateBy {
                        Regex("cell (\\d+)").find(it.getName())!!.groupValues[1].toInt()
                    }
                assertEquals(0f,materials.getValue(10).getOpacity(),0f)
                assertEquals(1f,materials.getValue(22).getOpacity(),0f)
                val writes=scene.grimeWrites
                val start=System.nanoTime()
                repeat(120) { scene.applyCleaning(vm.currentGame.copy(
                    cleaning=vm.currentGame.cleaning!!.copy(sequence=1000L+it))) }
                assertEquals("Unchanged stain opacity must not cause native material writes",writes,scene.grimeWrites)
                Log.i("TidyCleaningTest","PASS invisible-last-wall-cell; local stationary suction; direct native feedback; 120 unchanged-opacity frames writes=0 duration-ms=${(System.nanoTime()-start)/1_000_000.0}")
                act(RoomAction.Cancel)
                assertEquals(1f,materials.getValue(10).getOpacity(),0f)
                assertEquals(0f,vm.currentGame.cleaning!!.amount(m.id),0f)
            } finally { parent.destroy(true);r.close() }
        }
    }
}
