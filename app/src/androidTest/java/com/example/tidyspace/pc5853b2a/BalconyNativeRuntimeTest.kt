package com.example.tidyspace.pc5853b2a

import android.content.Intent
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.example.tidyspace.pc5853b2a.domain.model.RoomLayout
import com.example.tidyspace.pc5853b2a.domain.model.RoomGame
import com.example.tidyspace.pc5853b2a.domain.usecase.MovingHome
import com.example.tidyspace.pc5853b2a.platform.LaunchActivity
import com.example.tidyspace.pc5853b2a.ui.sorting.scene.*
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

/** Measure actual native meshes in the authored balcony's metre coordinates. */
class BalconyNativeRuntimeTest {
    @Test fun perimeterJoinsHaveFloorContactAndInnerPassageStaysOpen(): Unit=runBlocking {
        val i=InstrumentationRegistry.getInstrumentation()
        i.runOnMainSync { i.targetContext.startActivity(Intent(i.targetContext,LaunchActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        delay(2000)
        withContext(Dispatchers.Main) {
            val scene=RoomScene()
            try {
                scene.build(MovingHome.prepare(RoomGame()))
                val balcony=checkNotNull(scene.volume.roomRoot.findEntity("renovation_balcony"))
                val completion=checkNotNull(balcony.findEntity("balcony_railing_completion"))
                assertEquals(31,completion.getChildren().size)
                fun bounds(name: String)=checkNotNull(completion.findEntity(name)).getVisualBounds(balcony,true,true)
                val back=RoomLayout.balconyRailBack
                val front=RoomLayout.balconyRailFront
                val outer=1.49f*RoomLayout.scale
                val lower=bounds("balcony_lower_outer")
                assertFalse(lower.isEmpty())
                assertTrue(lower.min.z<back && lower.max.z>front)
                assertTrue(lower.min.x<=outer && lower.max.x>=outer)
                for(side in listOf("back","front")) for(edge in listOf("inner","outer")) {
                    val post=bounds("balcony_corner_${side}_$edge")
                    val upper=bounds("balcony_upper_$side")
                    val bottom=bounds("balcony_lower_$side")
                    assertEquals("Post is grounded",-.32f,post.min.y,.0001f)
                    assertTrue("Upper rail joins post",upper.min.y<post.max.y && upper.max.y>post.min.y)
                    assertTrue("Lower rail joins post",bottom.min.y<post.max.y && bottom.max.y>post.min.y)
                    assertTrue(upper.min.x<=post.max.x && upper.max.x>=post.min.x)
                    assertTrue(bottom.min.x<=post.max.x && bottom.max.x>=post.min.x)
                    Log.i("TidyBalconyNative","CORNER_PASS $side/$edge bounds=$post upper=$upper lower=$bottom")
                }
                // No new long member crosses the open room-facing edge.
                for(e in completion.getChildren()) {
                    val b=e.getVisualBounds(balcony,true,true)
                    if(b.max.z-b.min.z>.3f) assertTrue(b.min.x>RoomLayout.right+.3f)
                    val finish=checkNotNull(e.components[ModelComponent::class.java]).materials.single()
                    assertTrue(finish is PhysicallyBasedMaterial)
                    assertEquals("Atelier | Ivory painted joinery",(finish as PhysicallyBasedMaterial).getName())
                }
                val count=completion.getChildren().size
                scene.applyInput(RoomGame())
                scene.synchronize(MovingHome.prepare(RoomGame()))
                assertEquals("Reconciliation cannot rebuild static rails",count,completion.getChildren().size)
                assertNull(scene.volume.roomRoot.findEntity("house_journal"))
                Log.i("TidyBalconyNative","PASS 31 native pieces; continuous upper/lower edges; four grounded joints; open inner passage; existing finish; no HOME model")
            } finally { scene.destroy() }
        }
    }
}
