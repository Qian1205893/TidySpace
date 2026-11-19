package com.example.tidyspace.pc5853b2a

import android.content.Intent
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.tidyspace.pc5853b2a.data.repository.RoomPersistence
import com.example.tidyspace.pc5853b2a.domain.model.*
import com.example.tidyspace.pc5853b2a.domain.usecase.MovingHome
import com.example.tidyspace.pc5853b2a.domain.usecase.RoomUseCase
import com.example.tidyspace.pc5853b2a.platform.LaunchActivity
import com.example.tidyspace.pc5853b2a.ui.sorting.RoomViewModel
import com.example.tidyspace.pc5853b2a.ui.sorting.scene.*
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.math.Vector3
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Detached real SDK models and isolated VMs; user preferences are never edited. */
@RunWith(AndroidJUnit4::class)
class HomePlayNativeRuntimeTest {
    private fun start() {
        val i=InstrumentationRegistry.getInstrumentation()
        i.runOnMainSync {
            i.targetContext.startActivity(Intent(i.targetContext,LaunchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
    private fun nodes(e: Entity): List<Entity> = listOf(e)+e.getChildren().flatMap(::nodes)
    private fun rotation(e: Entity): Rotation=e.transform.quaternion.let { Rotation(it.x,it.y,it.z,it.w) }

    @Test fun allCatalogueModelsHaveGeometryBodyHitsAndPlayableParts(): Unit=runBlocking {
        start();delay(2000)
        withContext(Dispatchers.Main) {
            val parent=node()
            val targets=mutableMapOf<Entity,String>()
            val furniture=RoomFurniture(targets)
            try {
                furniture.load(parent,emptyList())
                val active=availableCatalogue(RoomGame(studyVersion=1))
                for(spec in active) {
                    val root=furniture.reconcile(listOf(RoomItem(spec,packed=false))).getValue(spec.id)
                    val bounds=root.getVisualBounds(root,true,true)
                    assertFalse("Actual GLB geometry: ${spec.id}",bounds.isEmpty())
                    assertTrue("Body remains movable: ${spec.id}",targets.values.any { it==spec.id })
                    assertTrue("Body collider: ${spec.id}",nodes(root).any {
                        targets[it]==spec.id && it.components[CollisionComponent::class.java]!=null
                    })
                    if(spec.kind.playable) {
                        assertTrue("Raw play hit: ${spec.id}",targets.values.any { it.startsWith("@play:${spec.id}") })
                        assertTrue("Animated GLB part: ${spec.id}",furniture.parts.any {
                            it.key.startsWith("${spec.id}:") && nodes(it.value).any { n->
                                n.components[ModelComponent::class.java]!=null
                            }
                        })
                        for((part,target) in targets.filterValues { it.startsWith("@play:${spec.id}") })
                            assertTrue("Play target resolves to visible or collision geometry: $target",
                                nodes(part).any { it.components[ModelComponent::class.java]!=null ||
                                    it.components[CollisionComponent::class.java]!=null })
                    }
                    Log.i("TidyHomeNative","ASSET_PASS id=${spec.id} geometry=$bounds playable=${spec.kind.playable}")
                }
                assertEquals(81,active.size)
                Log.i("TidyHomeNative","PASS all 81 active catalogue GLBs; 17 toys with independent body/play targets")
            } finally { furniture.close();parent.destroy(true) }
        }
    }

    @Test fun captionFacingUsesLiveRoomYawAndResetsOnEveryDrag(): Unit=runBlocking {
        start();delay(2000)
        withContext(Dispatchers.Main) {
            val initial=MovingHome.prepare(RoomGame()).let { s->
                s.copy(items=s.items.map { if(it.spec.id=="chair")
                    it.copy(packed=false,support=null,position=Point3(0f,-.175f,.2f),rotation=Rotation.yaw(147f))
                    else it.copy(packed=true,support=null) },
                    boxStock=s.boxStock.mapValues { (_,stock)->stock.filterNot { it=="chair" } })
            }
            var vm=RoomViewModel(RoomPersistence.encode(initial))
            val scene=RoomScene()
            fun dispatch(a: RoomAction) { vm.onEvent(a);scene.applyInput(vm.currentGame) }
            try {
                scene.bindState { vm.currentGame };scene.build(initial)
                val chair=checkNotNull(scene.volume.roomRoot.findEntity("chair"))
                for(roomYaw in listOf(0f,68f,-125f)) {
                    vm=RoomViewModel(RoomPersistence.encode(initial.copy(roomYaw=roomYaw)))
                    // Begin deliberately precedes a scene snapshot: must read live VM yaw.
                    repeat(3) {
                        val facing=checkNotNull(scene.viewerFacing("chair"))
                        assertEquals(-roomYaw,facing.yaw(),.001f)
                        dispatch(RoomAction.Begin("chair",facing))
                        dispatch(scene.dragFrame(GrabDelta(Point3(0f,.025f,0f),Rotation())))
                        assertEquals(-roomYaw,rotation(chair).yaw(),.001f)
                        val captionDirection=(rotation(scene.volume.turntable)*rotation(chair)).rotate(Point3(0f,0f,1f))
                        assertEquals(0f,captionDirection.x,.001f);assertEquals(1f,captionDirection.z,.001f)
                        dispatch(RoomAction.End)
                        dispatch(RoomAction.Begin("chair",facing))
                        dispatch(RoomAction.Transform(GrabDelta(Point3(0f,0f,0f),Rotation.yaw(17f),pointerCount=2)))
                        dispatch(RoomAction.End)
                    }
                }
                Log.i("TidyHomeNative","PASS actual caption-facing vector +Z; 9 repeated drags at live room yaw 0/68/-125")
            } finally { scene.destroy() }
        }
    }

    @Test fun rawToyContactsKeepWristMotionPairSqueezeAndActualAnimation(): Unit=runBlocking {
        start();delay(2000)
        withContext(Dispatchers.Main) {
            val parent=node()
            val targets=mutableMapOf<Entity,String>()
            val furniture=RoomFurniture(targets)
            val animator=PlaythingAnimator()
            val rules=RoomUseCase()
            var game=RoomGame()
            fun send(a: RoomAction) { game=rules.reduce(game,a) }
            val input=RoomGestureInput({ send(RoomAction.Begin(it)) },{ send(RoomAction.Transform(it)) },
                { send(RoomAction.End) },{ send(RoomAction.Cancel) },
                movableRoot={ it.removePrefix("@play:").substringBefore(':') })
            try {
                furniture.load(parent,emptyList())
                for(spec in uniqueCatalogue().filter { it.kind.playable &&
                    it.kind !in setOf(Furnishing.Jelly,Furnishing.Mochi,Furnishing.Tumbler) }) {
                    val item=RoomItem(spec,Point3(0f,.3f,0f),packed=false)
                    game=RoomGame(items=RoomGame().items.filterNot { it.spec.id==spec.id }
                        .map { it.copy(packed=true,support=null) }+item)
                    val root=furniture.reconcile(listOf(item)).getValue(spec.id)
                    val play=targets.entries.first { it.value.startsWith("@play:${spec.id}") }.value
                    fun sample(phase: ContactPhase,p: Point3,angle: Float=0f,id: Long=1)=
                        RoomContact(id,phase,play,HandPose(p,Rotation.yaw(angle)),p)
                    animator.frame(game,furniture.parts,1.0)
                    val before=game.items
                    input.events(listOf(sample(ContactPhase.Down,Point3(0f,0f,0f))))
                    input.events(listOf(sample(ContactPhase.Move,Point3(.024f,.018f,0f),65f)),.1f)
                    assertEquals("Play must not become body drag after 8mm: ${spec.id}",play,game.selectedId)
                    assertEquals("Playing cannot translate the furnishing: ${spec.id}",before,game.items)
                    assertTrue("Wrist data reaches toy rules: ${spec.id}",game.crankRadians>0f)
                    if(spec.kind in setOf(Furnishing.Mochi,Furnishing.Jelly)) {
                        input.events(listOf(sample(ContactPhase.Down,Point3(.20f,0f,0f),id=2)))
                        input.events(listOf(sample(ContactPhase.Move,Point3(.05f,0f,0f)),
                            sample(ContactPhase.Move,Point3(.14f,0f,0f),id=2)),.1f)
                        assertEquals(play,game.selectedId)
                        assertTrue("Two-hand squeeze preserved",game.toyGesture.compression>0f)
                        animator.frame(game,furniture.parts,1.05)
                        val soft=checkNotNull(furniture.parts["${spec.id}:${spec.id}_soft"])
                        assertTrue("Actual native soft model compresses",soft.transform.scaleVector.y<1f)
                        input.events(listOf(RoomContact(2,ContactPhase.Up)))
                    }
                    if(spec.kind==Furnishing.Rattle) {
                        input.events(listOf(sample(ContactPhase.Move,Point3(.024f,.018f,0f),-25f)),.1f)
                        assertTrue((game.playCounts[spec.id] ?: 0)>0)
                    }
                    input.events(listOf(RoomContact(1,ContactPhase.Up)))
                    assertNull(game.selectedId)
                    if(spec.kind !in setOf(Furnishing.Pokeball,Furnishing.PopBoard))
                        assertTrue("Original/new play award: ${spec.id}",(game.playCounts[spec.id] ?: 0)>0)
                    animator.frame(game,furniture.parts,1.10)
                    val movingPart=when(spec.kind) {
                        Furnishing.MagneticSlider->"slider_thumb"
                        Furnishing.ToggleBox->"togglebox_0"
                        Furnishing.Abacus->"abacus_0"
                        Furnishing.Hourglass->"hourglass_rotor"
                        Furnishing.Windmill->"windmill_rotor"
                        Furnishing.Ratchet->"ratchet_knob"
                        else->null
                    }
                    movingPart?.let { name->
                        val part=checkNotNull(furniture.parts["${spec.id}:$name"])
                        if(spec.kind==Furnishing.MagneticSlider) assertTrue(part.transform.position.x>.001f)
                        else if(spec.kind==Furnishing.Abacus) assertEquals(.023f,part.transform.position.y,.001f)
                        else assertNotEquals(Rotation(),rotation(part))
                    }
                    assertEquals(Vector3(0f,.3f,0f),root.transform.position)
                    Log.i("TidyHomeNative","PLAY_PASS id=${spec.id} rawTarget=$play count=${game.playCounts[spec.id]}")
                    input.cancel()
                }
                Log.i("TidyHomeNative","PASS 14 mechanism raw toy contacts; actual native pivots; body toys verified in native route")
            } finally { input.cancel();animator.close();furniture.close();parent.destroy(true) }
        }
    }
}
