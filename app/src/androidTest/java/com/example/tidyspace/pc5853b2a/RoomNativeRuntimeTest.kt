package com.example.tidyspace.pc5853b2a

import android.content.Intent
import android.util.Log
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.inspector.WindowInspector
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.tidyspace.pc5853b2a.data.repository.RoomPersistence
import com.example.tidyspace.pc5853b2a.domain.model.*
import com.example.tidyspace.pc5853b2a.platform.LaunchActivity
import com.example.tidyspace.pc5853b2a.ui.sorting.RoomViewModel
import com.example.tidyspace.pc5853b2a.ui.sorting.scene.RoomAudio
import com.example.tidyspace.pc5853b2a.ui.sorting.scene.RoomFurniture
import com.example.tidyspace.pc5853b2a.ui.sorting.scene.node
import com.example.tidyspace.pc5853b2a.ui.sorting.scene.transform
import com.example.tidyspace.pc5853b2a.ui.sorting.scene.PlaythingAnimator
import com.pico.spatial.core.ecs.Entity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Device-side native resource and state integration. Does not simulate tracked hands. */
@RunWith(AndroidJUnit4::class)
class RoomNativeRuntimeTest {
    @Test fun toysLoadPlayPersistAndReleaseNativeTargets(): Unit = runBlocking {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        instrumentation.startActivitySync(Intent(context,LaunchActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        // The Spatial service binds asynchronously after the activity starts.
        delay(12_000)
        withContext(Dispatchers.Main) {
            val vm=RoomViewModel(RoomPersistence.encode(RoomGame()))
            fun action(a: RoomAction)=vm.onEvent(a)
            fun game()=vm.state.value.game
            fun tap(id: String) { action(RoomAction.Begin("@play:$id"));action(RoomAction.End) }
            fun place(id: String,x: Float,z: Float,support: String) {
                action(RoomAction.Begin(id))
                val held=game().selected!!
                action(RoomAction.Transform(GrabDelta(Point3(x,.35f,z)-held.position,Rotation())))
                action(RoomAction.End)
                assertEquals(game().message,support,game().item(id).support)
            }
            action(RoomAction.Begin("crate:lid"));action(RoomAction.End)
            assertTrue(game().crateOpen)
            place("plant",-.53f,-.43f,"desk")
            action(RoomAction.Add("woodfish"))
            val fish=game().focusId!!
            place(fish,-.33f,-.43f,"desk")
            action(RoomAction.Add("musicbox"))
            val music=game().focusId!!
            place(music,.10f,.30f,"coffee")
            action(RoomAction.Add("bowl"))
            val bowl=game().focusId!!
            val targets=mutableMapOf<Entity,String>()
            val furniture=RoomFurniture(targets)
            val parent=node()
            val audio=RoomAudio(context)
            try {
                val models=furniture.load(parent,game().items)
                assertEquals(16,models.size)
                listOf(fish,music,bowl).forEach { id ->
                    assertFalse(models.getValue(id).getVisualBounds(models.getValue(id),true,true).isEmpty())
                    assertTrue(targets.containsValue(id))
                    assertTrue(targets.containsValue("@play:$id"))
                }
                delay(500)
                repeat(12) {
                    tap(fish)
                    audio.strike(game().playEvent!!)
                    delay(130)
                }
                assertEquals(12L,game().merit)
                action(RoomAction.Begin("@play:$fish"));action(RoomAction.Cancel)
                assertEquals(12L,game().merit)
                tap(bowl);audio.strike(game().playEvent!!)
                action(RoomAction.Begin("@play:$music"))
                repeat(4) {
                    action(RoomAction.Transform(GrabDelta(Point3(0f,0f,0f),Rotation.axis(Point3(1f,0f,0f),90f))))
                    audio.note(it);delay(100)
                }
                action(RoomAction.End)
                assertEquals(4L,game().playCounts[music])
                val saved=RoomPersistence.encode(game())
                val restored=RoomPersistence.decode(saved)!!
                assertEquals(game().items,restored.items)
                assertEquals(game().playCounts,restored.playCounts)
                File(context.getExternalFilesDir(null),"interaction-runtime-fixture.txt").writeText(saved)
                furniture.reconcile(game().items.filter { it.spec.id!=bowl })
                assertFalse(targets.containsValue(bowl))
                assertFalse(targets.containsValue("@play:$bowl"))
                assertTrue(furniture.parts.keys.none { it.startsWith("$bowl:") })
                furniture.reconcile(restored.items)
                assertTrue(targets.containsValue("@play:$bowl"))
                val newIds=mutableListOf<String>()
                for(spec in toyCatalogue().drop(3)) {
                    action(RoomAction.Add(spec.id))
                    val id=game().focusId!!
                    assertEquals(spec.id,game().item(id).spec.assetId)
                    newIds+=id
                }
                place(newIds.first { it.startsWith("pokeball") },-.19f,-.43f,"desk")
                val all=furniture.reconcile(game().items)
                assertEquals(24,all.size)
                for(id in newIds) {
                    val root=all.getValue(id)
                    assertFalse(root.getVisualBounds(root,true,true).isEmpty())
                    assertTrue(targets.containsValue(id))
                    assertTrue(furniture.parts.keys.any { it.startsWith("$id:") })
                }
                val animator=PlaythingAnimator()
                var clock=1.0
                fun frame() { clock+=.016;animator.frame(game(),furniture.parts,clock) }
                for(id in newIds) {
                    val kind=game().item(id).spec.kind
                    val capture=if(kind==Furnishing.Rattle) id else "@play:$id${if(kind==Furnishing.PopBoard) ":0" else ""}"
                    action(RoomAction.Begin(capture))
                    repeat(40) {
                        action(RoomAction.Transform(GrabDelta(Point3(0f,0f,0f),
                            Rotation.yaw(if(kind==Furnishing.Rattle && it%2==0) -25f else 25f),.1f)))
                        frame()
                    }
                    if(kind==Furnishing.Mochi || kind==Furnishing.Jelly) {
                        val name=if(kind==Furnishing.Mochi) "mochi_soft" else "jelly_soft"
                        assertTrue(furniture.parts.getValue("$id:$name").transform.scaleVector.y<.8f)
                        assertEquals(1f,all.getValue(id).transform.scaleVector.y,.001f)
                    }
                    action(RoomAction.End);frame()
                    assertTrue("No play feedback for $kind: ${game().message}",(game().playCounts[id] ?: 0)>0)
                    game().playEvent?.let(audio::strike)
                }
                repeat(600) { frame() }
                for(id in newIds.filter { it.startsWith("mochi") || it.startsWith("jelly") }) {
                    val node=if(id.startsWith("mochi")) "mochi_soft" else "jelly_soft"
                    assertEquals(1f,furniture.parts.getValue("$id:$node").transform.scaleVector.y,.001f)
                }
                val final=RoomPersistence.encode(game())
                assertEquals(game().toyStates,RoomPersistence.decode(final)!!.toyStates)
                File(context.getExternalFilesDir(null),"playthings-runtime-fixture.txt").writeText(final)
                val removed=newIds.toSet()
                furniture.reconcile(game().items.filter { it.spec.id !in removed })
                assertTrue(targets.values.none { value->newIds.any { value==it || value.startsWith("@play:$it") } })
                assertTrue(furniture.parts.keys.none { key->newIds.any { key.startsWith("$it:") } })
                animator.close()
                Log.i("TidyNativeTest","PASS: plant+woodfish on desk; musicbox on coffee; 3 toy assets; 12 merits; cancel; audio; save; clone/remove/recreate")
                Log.i("TidyNativeTest","PASS: 8 new native models + pivots + targets; all mechanisms; soft-only deformation + reset; v4 persistence; native cleanup")
                // Public Android window roots: capture the actually composed Augment for inspection.
                val roots=WindowInspector.getGlobalWindowViews()
                Log.i("TidyNativeTest","windowRoots="+roots.joinToString { "${it.javaClass.simpleName}:${it.width}x${it.height}" })
                roots.filter { it.width>300 && it.height.toFloat()/it.width in 1.45f..1.55f }.forEachIndexed { index,view ->
                    val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
                    view.draw(Canvas(bitmap))
                    File(context.getExternalFilesDir(null),"augment-panel-$index.png").outputStream().use {
                        bitmap.compress(Bitmap.CompressFormat.PNG,100,it)
                    }
                    bitmap.recycle()
                }
                Unit
            } finally {
                audio.close()
                furniture.close()
                parent.destroy(true)
            }
        }
    }
}
