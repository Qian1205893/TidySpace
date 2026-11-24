package com.example.tidyspace.pc5853b2a.ui.sorting.scene

import android.util.Log
import androidx.compose.runtime.withFrameNanos
import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.HoverEffectComponent
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.ViewCoordinateSpace
import com.example.tidyspace.pc5853b2a.domain.model.*
import kotlin.math.abs
import kotlinx.coroutines.yield

class SortingScene {
    val resources = SceneResources()
    internal val volume=SharedVolume()
    val placementOwner = volume.owner
    val workbenchRoot = volume.roomRoot
    private var base: Entity?=null
    fun isBase(entity: Entity?)=entity!=null && entity==base
    private val objects = mutableMapOf<String, Entity>()
    private val renderedAngles=mutableMapOf<String,Float>()
    private val slots = mutableMapOf<String, Entity>()
    private val cues = mutableMapOf<String, Entity>()
    private val targets = mutableMapOf<Entity, SortingAction>()
    private var lastRecenter = -1
    private var built = false
    val isBuilt get()=built
    var buildCount=0
        private set
    private var panel: Entity?=null
    private var hoverId: String? = null
    private var currentGame = SortingGame()
    private var viewContent: SpatialViewContent? = null
    fun bind(content: SpatialViewContent) { viewContent=content;volume.content=content }
    fun detachHost() {
        panel?.removeFromParent();panel=null
        viewContent?.removeEntity(placementOwner)
        viewContent=null;volume.content=null
    }
    fun localPointer(x: Float,y: Float,z: Float): Point3? =
        viewContent?.convertPosition(Vector3(x,y,z),ViewCoordinateSpace.Local,workbenchRoot)
            ?.let { Point3(it.x,it.y,it.z) }
    fun wallInteraction() = currentGame.selected?.surface == SortingSurface.Wall

    suspend fun build(game: SortingGame) {
        if(built) return
        buildCount++
        base=RoomArchitecture().build(workbenchRoot,resources,game.wall,game.levelIndex).also {
            interactive(it,Vector3(1.5f,.06f,1.12f))
        }
        resources.part(workbenchRoot,resources.box(Vector3(.92f,.035f,.48f),.01f),"trayOak")
        // The tabletop remains the independently defined gameplay support.
        if(game.wall) {
            resources.part(workbenchRoot,resources.box(Vector3(.92f,.56f,.025f),.008f),
                "panelGreen",Vector3(0f,.28f,-.2125f))
        } else buildReceivingPlate(game.items.map { it.spec })
        for(pose in game.items) {
            yield()
            val spec = pose.spec
            val entity = Entity().apply { components.set(TransformComponent()) }
            workbenchRoot.addChild(entity)
            buildSortingObject(entity,spec,resources)
            makeTarget(entity,Vector3(spec.size.x+.015f,spec.size.y+.02f,spec.size.z+.015f))
            objects[spec.id] = entity; targets[entity] = SortingAction.Pick(spec.id)
            renderedAngles[spec.id]=pose.angle
            applyPose(pose,pose.position.vector(),pose.angle)
            val slot = Entity().apply { components.set(TransformComponent()) }
            workbenchRoot.addChild(slot)
            slot.components[TransformComponent::class.java]!!.apply {
                position=if(game.wall) Vector3(spec.slot.x,spec.slot.y,-.19875f)
                    else Vector3(spec.slot.x,.01875f,spec.slot.z)
                eulerAngles=EulerAngles(pitch=if(game.wall) 90f else 0f)
            }
            resources.part(slot,resources.box(Vector3(spec.size.x+.016f,.0025f,spec.size.z+.016f)),"trayCavity")
            // A shallow recognizable object silhouette sits on the cavity floor.
            val silhouette = Entity()
            slot.addChild(silhouette)
            buildSortingObject(silhouette,spec,resources,ghost=true)
            silhouette.components[TransformComponent::class.java]!!.apply {
                scaleVector = Vector3(.82f,.035f,.82f)
                position = Vector3(0f,.002f,0f)
            }
            makeTarget(slot,Vector3(spec.size.x+.016f,.024f,spec.size.z+.016f))
            slots[spec.id] = slot; targets[slot] = SortingAction.Place(spec.id)
            val cue = Entity().apply { components.set(TransformComponent()); enabled = false }
            workbenchRoot.addChild(cue)
            cue.components[TransformComponent::class.java]!!.apply {
                position=if(game.wall) Vector3(spec.slot.x,spec.slot.y,-.190f)
                    else Vector3(spec.slot.x,.029f,spec.slot.z)
                eulerAngles=EulerAngles(pitch=if(game.wall) 90f else 0f)
            }
            resources.part(cue,resources.box(Vector3(spec.size.x+.012f,.0015f,spec.size.z+.012f)),"slotGlow")
            cues[spec.id] = cue
        }
        built = true
        currentGame=game
        volume.fit(VolumeEnvelope(radiusX=1.12f,radiusZ=1.12f,lowY=-.568f,highY=.93f))
        Log.i("TidyScene","scene-ready level=${game.levelIndex} wall=${game.wall} objects=${objects.size} slots=${slots.size} unit=m")
    }
    private fun makeTarget(entity: Entity,size: Vector3) {
        entity.components.set(CollisionComponent(listOf(ShapeResource.createBox(size)),PhysicsMaterialResource()))
        entity.components.set(InteractableComponent())
        entity.components.set(HoverEffectComponent())
    }
    /** Tiled upper plate has real rectangular cavities; floors are independent of occupants. */
    private suspend fun buildReceivingPlate(items: List<ItemSpec>) {
        val xs = (listOf(-.44f,.44f)+items.flatMap { listOf(it.slot.x-it.size.x/2-.008f,it.slot.x+it.size.x/2+.008f) }).distinct().sorted()
        val zs = (listOf(-.222f,0f)+items.flatMap { listOf(it.slot.z-it.size.z/2-.008f,it.slot.z+it.size.z/2+.008f) }).distinct().sorted()
        val cells = mutableListOf<Pair<Vector3,Vector3>>()
        for((x1,x2) in xs.zipWithNext()) for((z1,z2) in zs.zipWithNext()) {
            val x = (x1+x2)/2; val z = (z1+z2)/2
            val hole = items.any { abs(x-it.slot.x)<it.size.x/2+.008f && abs(z-it.slot.z)<it.size.z/2+.008f }
            if(!hole && z < 0f) cells.add(Vector3(x,.0225f,z) to Vector3(x2-x1,.01f,z2-z1))
        }
        resources.part(workbenchRoot,resources.plate(cells),"trayOak")
    }
    fun action(target: Entity?): SortingAction? = targets[target]
    fun isTarget(entity: Entity) = targets.containsKey(entity)
    fun hover(target: Entity?) {
        if(!built) return
        val pick = targets[target] as? SortingAction.Pick
        val id = pick?.id?.takeIf { it != currentGame.selectedId &&
            currentGame.items.any { pose -> pose.spec.id == it } }
        if(id == hoverId) return
        hoverId?.let { old ->
            if(old != currentGame.selectedId) objects[old]?.components?.get(TransformComponent::class.java)?.apply {
                position = currentGame.items.first { it.spec.id==old }.position.vector()
            }
        }
        hoverId = id
        id?.let { objects.getValue(it).components[TransformComponent::class.java]!!.apply {
            val pose=currentGame.items.first { pose -> pose.spec.id==it }
            position=pose.position.vector()+if(pose.surface==SortingSurface.Wall)
                Vector3(0f,0f,.012f) else Vector3(0f,.012f,0f)
        } }
    }
    fun attach(panel: Entity, position: Vector3) {
        this.panel=panel
        volume.attach(panel)
    }
    suspend fun synchronize(game: SortingGame) {
        if(!built) return
        currentGame = game
        val starts = objects.mapValues { (_,entity) -> entity.components[TransformComponent::class.java]!!.position }
        val angles=renderedAngles.toMap()
        cues.forEach { (id,entity) -> entity.enabled = id == game.hintId && game.items.none { it.spec.id == id && it.placed } }
        if(game.dragging) {
            for(pose in game.items) applyPose(pose,pose.position.vector(),pose.angle)
            return
        }
        val start = withFrameNanos { it }
        do {
            val now = withFrameNanos { it }
            val f = ((now-start)/240_000_000f).coerceIn(0f,1f)
            val t = f*f*(3f-2f*f)
            for(pose in game.items) {
                val entity = objects.getValue(pose.spec.id)
                val lift=if(hoverId==pose.spec.id && game.selectedId!=pose.spec.id) .012f else 0f
                val target=pose.position.vector()+if(pose.surface==SortingSurface.Wall)
                    Vector3(0f,0f,lift) else Vector3(0f,lift,0f)
                val from = starts.getValue(pose.spec.id)
                val a=angles.getValue(pose.spec.id)
                val delta=((pose.angle-a+540f)%360f)-180f
                applyPose(pose,from+(target-from)*t,a+delta*t)
            }
        } while(f < 1f)
        validateSupport(game)
    }
    private fun applyPose(pose: ItemPose,p: Vector3,angle: Float) {
        objects.getValue(pose.spec.id).components[TransformComponent::class.java]!!.apply {
            position=p
            // Canonical props use +Y thickness; +90 pitch maps thickness toward the wall normal.
            quaternion=pose.rotation?.quat() ?: if(pose.surface==SortingSurface.Wall)
                EulerAngles(pitch=90f).toQuat()*EulerAngles(yaw=angle).toQuat()
                else EulerAngles(yaw=angle).toQuat()
        }
        renderedAngles[pose.spec.id]=angle
    }
    private fun validateSupport(game: SortingGame) {
        for(pose in game.items.filter { it.spec.id != game.selectedId && it.spec.id != hoverId }) {
            val b = objects.getValue(pose.spec.id).getVisualBounds(workbenchRoot,true,true)
            val wall=pose.surface==SortingSurface.Wall
            val surface=if(wall) -.20f else if(pose.placed) .020f
                else if(!game.wall && b.min.z<0f) .0275f else .0175f
            val pass=!b.isEmpty() && abs((if(wall) b.min.z else b.min.y)-surface)<.003f &&
                b.min.x>=-.445f && b.max.x<=.445f &&
                if(wall) b.min.y>=-.003f && b.max.y<=.563f
                else b.min.z>=-.222f && b.max.z<=.222f
            Log.i("TidyBounds","id=${pose.spec.id} owner=workbench-root min=${b.min} max=${b.max} support=$surface numeric=${if(pass) "PASS" else "FAIL"} visual=UNOBSERVED")
        }
    }
    fun destroy() {
        viewContent=null
        placementOwner.destroy(true)
        objects.clear(); slots.clear(); cues.clear(); targets.clear(); resources.close()
    }
}

internal fun Point3.vector() = Vector3(x,y,z)
