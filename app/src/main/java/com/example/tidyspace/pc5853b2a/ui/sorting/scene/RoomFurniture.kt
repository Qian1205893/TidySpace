package com.example.tidyspace.pc5853b2a.ui.sorting.scene

import android.util.Log
import com.example.tidyspace.pc5853b2a.domain.model.*
import com.example.tidyspace.pc5853b2a.domain.usecase.RoomPhysics
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.GroundShadowComponent
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.PointLightComponent
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Vector3
import kotlinx.coroutines.delay

/**
 * Blender-authored GLB in gameplay metres. One load shares all PBR textures.
 * Articulation pivots and hollow-body hit regions stay independent of the mesh.
 */
internal class RoomFurniture(private val targets: MutableMap<Entity,String>,
                             private val sharedLibrary: RoomModelLibrary?=null) {
    val parts=mutableMapOf<String,Entity>()
    private val library=sharedLibrary ?: RoomModelLibrary()
    private val updates=SceneUpdates()
    private val objects=mutableMapOf<String,Entity>()
    private lateinit var owner: Entity
    private var preview: Entity?=null
    private var previewKey: String?=null
    private var previewHit: Entity?=null
    private var promotedHit: Entity?=null
    private var cardModel: Entity?=null
    private var cardToken: Long?=null
    private var cardId: String?=null

    /** A real mesh under the room root, independent of packed inventory until release. */
    fun updateCardModel(game: RoomGame,floatingPosition: Point3?) {
        val drag=game.cardDrag
        if(cardToken!=drag?.token) {
            clearCardModel()
            if(drag!=null) {
                val spec=game.items.find { it.spec.id==drag.id }?.spec ?: return
                val source=library.cachedSource(spec) ?: return
                cardModel=checkNotNull(source.clone(
                    cloneOptions=Entity.CloneOptions(recursive=true,shouldShareMaterialInstance=true)))
                    .also { root ->
                        root.setName("@card-model:${drag.id}")
                        owner.addChild(root)
                    }
                cardToken=drag.token;cardId=drag.id
                Log.i("TidyCard","model-visible id=${drag.id} token=${drag.token} mesh=true")
            }
        }
        val root=cardModel ?: return
        val item=game.items.find { it.spec.id==drag?.id } ?: return
        // Contact prediction only controls the landing ring. The held mesh
        // follows all three pointer axes until the release transaction.
        val p=drag?.point ?: floatingPosition ?: item.position
        updates.position(root,p.vector())
        updates.rotation(root,checkNotNull(drag).facing)
        updates.enabled(root,true)
    }
    /** Promote the same Entity on a valid drop: no disappearance or second model load. */
    fun finishCardModel(game: RoomGame): Entity? {
        if(game.cardDrag!=null) return null
        val id=cardId ?: return null
        val item=game.items.find { it.spec.id==id && !it.packed }
        if(item==null) { clearCardModel();return null }
        val root=cardModel ?: return null
        root.setName(id)
        targets[root]=id
        colliders(root,item.spec);toyParts(root,item.spec);mechanisms(root,item.spec)
        objects[id]=root
        cardModel=null;cardToken=null;cardId=null
        return root
    }
    private fun clearCardModel() {
        cardModel?.let { unbind(it);it.destroy(true) }
        cardModel=null;cardToken=null;cardId=null
    }

    /** The root owns both the mesh and body colliders; preview inherits this pose. */
    fun cartonRotation(id: String): Rotation? {
        val entity=objects[id] ?: return null
        return entity.transform.quaternion.let { Rotation(it.x,it.y,it.z,it.w).gravityTurn() }
    }
    fun updatePreviewFacing(game: RoomGame) {
        val box=game.previewBox ?: return
        val root=preview ?: return
        cartonRotation(box)?.let { updates.rotation(root,it) }
    }

    /** Keep the captured preview Entity alive while it becomes a real furnishing. */
    fun promotePreview(item: RoomItem): Entity? {
        if(previewKey?.substringAfter(':')!=item.spec.id) return null
        val root=preview ?: return null
        targets[root]=item.spec.id
        previewHit?.let { targets[it]=item.spec.id;promotedHit=it }
        preview=null;previewKey=null;previewHit=null
        colliders(root,item.spec);toyParts(root,item.spec);mechanisms(root,item.spec)
        objects[item.spec.id]=root
        return root
    }
    fun finishPromotion() {
        promotedHit?.let { targets.remove(it);it.destroy(true) }
        promotedHit=null
    }

    suspend fun load(parent: Entity,items: List<RoomItem>): Map<String,Entity> {
        owner=parent
        reconcile(items)
        Log.i("TidyAssets","GLB ready items=${objects.size} lazy-packed=true shared-libraries=true PBR=base+normal+roughness")
        return objects
    }
    suspend fun reconcile(items: List<RoomItem>,stillWanted: (RoomItem)->Boolean={true}): Map<String,Entity> {
        val wanted=items.map { it.spec.id }.toSet()
        for(id in (objects.keys-wanted)) {
            val root=objects.remove(id)!!
            unbind(root);root.destroy(true)
            parts.keys.filter { it.startsWith("$id:") }.toList().forEach(parts::remove)
        }
        for(item in items) if(item.spec.id !in objects) {
            if(promotePreview(item)!=null) continue
            val source=library.source(item.spec)
            // Undo or cancellation can happen while a family loads. Do not publish that stale model.
            if(!stillWanted(item)) continue
            val root=checkNotNull(source.clone(
                cloneOptions=Entity.CloneOptions(recursive=true,shouldShareMaterialInstance=true))) { "Cannot clone ${item.spec.id}" }
            root.transform.position=item.position.vector()
            root.transform.quaternion=item.rotation.quat()
            root.enabled=!item.packed
            fun shadows(entity: Entity) {
                if(entity.components[ModelComponent::class.java]!=null)
                    entity.components.set(GroundShadowComponent(castsShadowEnabled=true,receivesShadowEnabled=true))
                entity.getChildren().forEach(::shadows)
            }
            shadows(root)
            // SDK hits may name the model/mesh rather than our collision proxy.
            // Bind its stable root so every descendant resolves to this item.
            targets[root]=item.spec.id
            colliders(root,item.spec)
            toyParts(root,item.spec)
            mechanisms(root,item.spec)
            owner.addChild(root)
            objects[item.spec.id]=root
            Log.i("TidyAssets","cloned id=${item.spec.id} asset=${item.spec.assetId}")
        }
        return objects
    }
    suspend fun inventory(game: RoomGame) {
        for(box in game.items.filter { it.spec.kind==Furnishing.MovingBox }) {
            objects[box.spec.id]?.let { updates.enabled(it,game.decorating && game.stock(box.spec.id).isNotEmpty()) }
            val total=availableCatalogue(game).count {
                com.example.tidyspace.pc5853b2a.domain.usecase.MovingHome.boxFor(it)==box.spec.id
            }.coerceAtLeast(1)
            parts["${box.spec.id}:fill"]?.let { updates.scale(it,Vector3(1f,
                (.08f+.92f*game.stock(box.spec.id).size/total).coerceIn(.08f,1f),1f)) }
            repeat(12) { n -> parts["${box.spec.id}:tally-$n"]?.let { updates.enabled(it,n<game.stock(box.spec.id).size) } }
        }
        val asset=game.previewAsset
        val box=game.items.find { it.spec.id==game.previewBox }
        val key=if(asset!=null && box!=null && game.decorating && game.extractionBox==null) "${box.spec.id}:${game.previewId}" else null
        if(key!=previewKey) {
            preview?.let { unbind(it);it.destroy(true) }
            preview=null;previewHit=null;previewKey=key
            if(key!=null) {
                val spec=checkNotNull(game.inventorySpec(checkNotNull(game.previewId)))
                preview=checkNotNull(library.source(spec).clone(
                    cloneOptions=Entity.CloneOptions(recursive=true,shouldShareMaterialInstance=true))).also {
                    targets[it]="@unpack:${checkNotNull(box).spec.id}"
                    previewHit=hit(it,"@unpack:${checkNotNull(box).spec.id}",spec.size.vector())
                    owner.addChild(it)
                }
            }
        }
        preview?.let { root ->
            val spec=checkNotNull(game.inventorySpec(checkNotNull(game.previewId)))
            updates.position(root,checkNotNull(box).position.vector()+Vector3(0f,.17f+spec.size.y/2,0f))
            updates.rotation(root,cartonRotation(box.spec.id) ?: Rotation.yaw(box.rotation.yaw()))
        }
        for(item in game.items.filter { it.spec.kind in setOf(Furnishing.Lamp,Furnishing.TableLamp,Furnishing.StudyLight) }) {
            val on=game.decorating && !item.packed && game.lampOn && game.indoorBrightness>0f
            parts["${item.spec.id}:light"]?.let {
                updates.enabled(it,on)
                if(on) it.components[PointLightComponent::class.java]?.let { light ->
                    val intensity=(if(item.spec.kind==Furnishing.Lamp) 12f else 6f)*game.indoorBrightness
                    if(light.intensity!=intensity) light.intensity=intensity
                }
            }
            parts["${item.spec.id}:bulb"]?.let { updates.enabled(it,on) }
        }
    }
    private fun unbind(e: Entity) {
        if(e==promotedHit) promotedHit=null
        targets.remove(e);updates.forget(e);e.getChildren().forEach(::unbind)
    }
    private fun mechanisms(root: Entity,s: RoomSpec) {
        if(LifestyleModels.contains(s)) {
            if(s.kind in setOf(Furnishing.Lamp,Furnishing.TableLamp)) {
                parts["${s.id}:light"]=node().also {
                    root.addChild(it)
                    it.transform.position=Vector3(0f,s.size.y*.20f,0f)
                    it.components.set(PointLightComponent(Color4(1f,.84f,.64f,1f),6f,.65f))
                }
            }
            return
        }
        fun part(name: String,key: String)=checkNotNull(root.findEntity(name)).also { parts["${s.id}:$key"]=it }
        when(s.kind) {
            Furnishing.Desk -> {
                val drawer=part("desk_drawer","drawer")
                targets[drawer]="${s.id}:drawer"
                hit(drawer,"${s.id}:drawer",Vector3(.394f,.055f,.025f),Vector3(0f,0f,.096f))
                hit(drawer,"${s.id}:drawer",Vector3(.07f,.025f,.028f),Vector3(0f,0f,.113f))
            }
            Furnishing.Crate -> {
                val lid=part("crate_lid","lid")
                targets[lid]="${s.id}:lid"
                hit(lid,"${s.id}:lid",Vector3(.25f,.016f,.23f),Vector3(0f,.001f,.115f))
            }
            Furnishing.MovingBox -> {
                part("${s.assetId}_fill","fill")
                repeat(12) { n -> part("${s.assetId}_tally_$n","tally-$n") }
            }
            Furnishing.Lamp,Furnishing.TableLamp -> {
                part(if(s.kind==Furnishing.Lamp) "lamp_bulb" else "tablelamp_bulb","bulb")
                if(s.kind==Furnishing.Lamp) {
                    val switch=checkNotNull(root.findEntity("lamp_switch"))
                    targets[switch]="lamp:switch"
                    hit(switch,"lamp:switch",Vector3(.03f,.04f,.03f))
                }
                parts["${s.id}:light"]=node().also {
                    root.addChild(it)
                    it.transform.position=Vector3(0f,if(s.kind==Furnishing.Lamp) .195f else .022f,0f)
                    it.components.set(PointLightComponent(Color4(1f,.79f,.52f,1f),8f,
                        if(s.kind==Furnishing.Lamp) 1.1f else .65f))
                }
            }
            Furnishing.StudyLight -> {
                parts["${s.id}:light"]=node().also {
                    root.addChild(it)
                    it.transform.position=Vector3(0f,if(s.assetId=="studybanker") .044f else .115f,0f)
                    it.components.set(PointLightComponent(Color4(1f,.79f,.52f,1f),6f,
                        if(s.assetId=="studybanker") .48f else .80f))
                }
            }
            else -> Unit
        }
    }
    fun close() {
        clearCardModel()
        finishPromotion()
        preview?.let { unbind(it);it.destroy(true) };preview=null;previewKey=null
        objects.values.forEach { unbind(it);it.destroy(true) }
        objects.clear();parts.clear();updates.close()
        if(sharedLibrary==null) library.close()
    }

    private fun hit(parent: Entity,id: String,size: Vector3,position: Vector3=Vector3.ZERO): Entity =
        node().also {
            parent.addChild(it)
            it.transform.position=position
            interactive(it,size)
            targets[it]=id
        }

    private fun colliders(root: Entity,s: RoomSpec) {
        if(LifestyleModels.contains(s)) {
            hit(root,s.id,s.size.vector())
            return
        }
        fun box(x:Float,y:Float,z:Float,px:Float=0f,py:Float=0f,pz:Float=0f)=
            hit(root,s.id,Vector3(x,y,z),Vector3(px,py,pz))
        when(s.kind) {
            Furnishing.Bed,Furnishing.DiningTable,Furnishing.DiningChair,Furnishing.Bookcase->
                RoomPhysics.boxes(RoomGame(),RoomItem(s)).forEach { b->
                    hit(root,s.id,b.size.vector(),b.center.vector())
                }
            Furnishing.MagneticSlider,Furnishing.ToggleBox,Furnishing.Abacus,Furnishing.DeskBell,
            Furnishing.Hourglass,Furnishing.Windmill->{
                box(s.size.x,.014f,s.size.z,py=-s.size.y/2+.007f)
                when(s.kind) {
                    Furnishing.MagneticSlider->hit(checkNotNull(root.findEntity("slider_thumb")),"@play:${s.id}",Vector3(.045f,.035f,.055f))
                    Furnishing.ToggleBox->repeat(3) { n->hit(checkNotNull(root.findEntity("togglebox_$n")),"@play:${s.id}:$n",Vector3(.034f,.04f,.056f)) }
                    Furnishing.Abacus->repeat(5) { n->hit(checkNotNull(root.findEntity("abacus_$n")),"@play:${s.id}:$n",Vector3(.023f,.04f,.038f)) }
                    Furnishing.DeskBell->hit(checkNotNull(root.findEntity("bell_button")),"@play:${s.id}",Vector3(.055f,.029f,.055f))
                    Furnishing.Hourglass->hit(checkNotNull(root.findEntity("hourglass_rotor")),"@play:${s.id}",Vector3(.065f,.125f,.052f))
                    Furnishing.Windmill->hit(checkNotNull(root.findEntity("windmill_crank")),"@play:${s.id}",Vector3(.032f,.07f,.05f))
                    else->Unit
                }
            }
            Furnishing.Desk -> {
                box(.48f,.025f,.25f,py=.1575f)
                for(x in listOf(-.202f,.202f)) {
                    box(.016f,.055f,.192f,x,.111f)
                    for(z in listOf(-.092f,.092f)) box(.023f,.315f,.023f,x,-.0125f,z)
                }
                box(.413f,.056f,.012f,py=.111f,pz=-.096f)
            }
            Furnishing.Chair -> {
                box(.174f,.045f,.17f,py=-.006f)
                box(.165f,.13f,.026f,py=.072f,pz=-.067f)
                for(x in listOf(-.068f,.068f)) for(z in listOf(-.064f,.064f))
                    box(.019f,.123f,.020f,x,-.084f,z)
            }
            Furnishing.Sofa -> {
                box(.398f,.035f,.213f,py=-.089f)
                box(.37f,.056f,.174f,py=-.038f,pz=.014f)
                box(.406f,.182f,.060f,py=.033f,pz=-.083f)
                for(x in listOf(-.191f,.191f)) box(.048f,.157f,.232f,x,-.005f)
            }
            Furnishing.CoffeeTable -> {
                box(.31f,.028f,.20f,py=.061f)
                box(.22f,.012f,.122f,py=-.035f)
                for(x in listOf(-.111f,.111f)) for(z in listOf(-.063f,.063f))
                    box(.018f,.123f,.018f,x,-.0135f,z)
            }
            Furnishing.Shelf -> {
                for(y in listOf(-.119f,0f,.119f)) box(.27f,.012f,.11f,py=y)
                for(x in listOf(-.129f,.129f)) box(.012f,.238f,.105f,px=x)
            }
            Furnishing.Lamp -> {
                box(.13f,.019f,.13f,py=-.2605f)
                box(.012f,.44f,.012f,py=-.031f)
                box(.127f,.093f,.127f,py=.223f)
            }
            Furnishing.Crate -> {
                // No whole-crate hit box: open contents remain reachable from above.
                box(.25f,.015f,.23f,py=-.0925f)
                for(z in listOf(-.108f,.108f)) box(.25f,.186f,.014f,py=-.0065f,pz=z)
                for(x in listOf(-.118f,.118f)) box(.014f,.186f,.207f,px=x,py=-.0065f)
            }
            Furnishing.Woodfish -> {
                box(.15f,.02f,.12f,py=-.04f)
                box(.115f,.046f,.10f,py=-.008f)
                hit(root,"@play:${s.id}",Vector3(.080f,.035f,.072f),Vector3(.030f,.043f,.018f))
            }
            Furnishing.MusicBox -> {
                box(.136f,.07f,.11f,py=-.0225f)
                hit(root,"@play:${s.id}",Vector3(.035f,.05f,.04f),Vector3(.083f,-.010f,0f))
            }
            Furnishing.SingingBowl -> {
                box(.12f,.035f,.12f,py=-.0175f)
                hit(root,"@play:${s.id}",Vector3(.065f,.033f,.052f),Vector3(.025f,.035f,.022f))
            }
            Furnishing.Rattle -> {
                box(.047f,.016f,.047f,py=-.102f)
                hit(root,"@play:${s.id}",Vector3(.12f,.18f,.05f),Vector3(0f,.017f,0f))
            }
            Furnishing.Pokeball -> {
                box(.12f,.06f,.12f,py=-.032f)
                hit(root,"@play:${s.id}",Vector3(.036f,.036f,.018f),Vector3(0f,.002f,.075f))
            }
            Furnishing.Mochi,Furnishing.Jelly -> {
                box(s.size.x,.012f,s.size.z,py=-s.size.y/2+.006f)
                hit(root,"@play:${s.id}",Vector3(.102f,.078f,.092f),Vector3(0f,.008f,0f))
            }
            Furnishing.PopBoard -> {
                box(.20f,.027f,.17f,py=-.0065f)
                repeat(6) { index ->
                    hit(root,"@play:${s.id}:$index",Vector3(.043f,.023f,.043f),
                        Vector3((index%3-1)*.056f,.024f,(index/3-.5f)*.068f))
                }
            }
            Furnishing.Spinner -> {
                box(.041f,.035f,.041f)
                for(index in 0..2) {
                    val a=index*kotlin.math.PI*2/3
                    hit(checkNotNull(root.findEntity("spinner_rotor")),"@play:${s.id}",Vector3(.043f,.022f,.043f),
                        Vector3(.048f*kotlin.math.cos(a).toFloat(),0f,.048f*kotlin.math.sin(a).toFloat()))
                }
            }
            Furnishing.Tumbler -> {
                box(.10f,.04f,.10f,py=-.058f)
                hit(root,"@play:${s.id}",Vector3(.092f,.11f,.09f),Vector3(0f,.017f,0f))
            }
            Furnishing.Ratchet -> {
                box(.115f,.026f,.115f,py=-.017f)
                hit(root,"@play:${s.id}",Vector3(.082f,.038f,.082f),Vector3(0f,.015f,0f))
            }
            else -> box(s.size.x,s.size.y,s.size.z)
        }
    }
    private fun toyParts(root: Entity,s: RoomSpec) {
        val names=when(s.kind) {
            Furnishing.MagneticSlider->listOf("slider_thumb")
            Furnishing.ToggleBox->(0..2).map { "togglebox_$it" }
            Furnishing.Abacus->(0..4).map { "abacus_$it" }
            Furnishing.DeskBell->listOf("bell_button","bell_dome")
            Furnishing.Hourglass->listOf("hourglass_rotor","hourglass_sand_top","hourglass_sand_bottom","hourglass_stream")
            Furnishing.Windmill->listOf("windmill_rotor","windmill_crank")
            Furnishing.Woodfish->listOf("woodfish_hammer")
            Furnishing.MusicBox->listOf("musicbox_crank","musicbox_rotor")
            Furnishing.SingingBowl->listOf("bowl_striker")
            Furnishing.Rattle->listOf("rattle_left","rattle_right")
            Furnishing.Pokeball->listOf("pokeball_lid","pokeball_star")
            Furnishing.Mochi->listOf("mochi_soft")
            Furnishing.Jelly->listOf("jelly_soft")
            Furnishing.PopBoard->(0..5).map { "popboard_$it" }
            Furnishing.Spinner->listOf("spinner_rotor")
            Furnishing.Tumbler->listOf("tumbler_body")
            Furnishing.Ratchet->listOf("ratchet_knob")
            else->emptyList()
        }
        names.forEach { name ->
            val mechanism=checkNotNull(root.findEntity(name)) { "Missing instrument mechanism: $name" }
            parts["${s.id}:$name"]=mechanism
            val target=if(s.kind in setOf(Furnishing.PopBoard,Furnishing.ToggleBox,Furnishing.Abacus)) "@play:${s.id}:${name.substringAfterLast('_')}"
                else "@play:${s.id}"
            // Actual rendered descendants and collision proxies must resolve identically.
            targets[mechanism]=target
        }
        if(s.kind==Furnishing.Rattle) root.findEntity("rattle_mesh")?.let { targets[it]="@play:${s.id}" }
    }
}
