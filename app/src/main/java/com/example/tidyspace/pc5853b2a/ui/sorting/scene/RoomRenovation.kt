package com.example.tidyspace.pc5853b2a.ui.sorting.scene

import android.util.Log
import com.example.tidyspace.pc5853b2a.domain.model.*
import com.example.tidyspace.pc5853b2a.domain.usecase.RoomTime
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.DirectionalLightComponent
import com.pico.spatial.core.ecs.GroundShadowComponent
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.PointLightComponent
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import kotlinx.coroutines.delay
import kotlin.math.*

/** Blender geometry is retained once; only tool poses and mess visibility change. */
internal class RoomRenovation(private val targets: MutableMap<Entity,String>,
                              private val library: RoomModelLibrary?=null,
                              private val includeCleaning: Boolean=true) {
    private val entities=mutableMapOf<String,Entity>()
    private val particles=mutableListOf<Entity>()
    private lateinit var asset: Entity
    private lateinit var sun: Entity
    private lateinit var bounce: Entity
    private lateinit var hearthLight: Entity
    private lateinit var ceilingLight: Entity
    private lateinit var pendant: Entity
    private val flames=mutableListOf<Entity>()
    internal val fixtures=mutableMapOf<String,Entity>()
    private val controls=mutableMapOf<String,Entity>()
    private val staticFinishes=mutableListOf<Pair<UnlitMaterial,Color4>>()
    private var finishLight: Pair<Float,Float>?=null
    private val updates=SceneUpdates()
    private var lastFire=Double.NEGATIVE_INFINITY
    private var hour=Float.NaN
    private var state=RoomGame()
    private var clock=0.0
    private var seen=0L
    private var sparkleTime=Double.NEGATIVE_INFINITY
    private var sparklePoint=Point3(0f,0f,0f)
    private var lastContact: CleanContact?=null
    private val pieceOrigins=mutableMapOf<String,Vector3>()
    private val grimeMaterials=mutableMapOf<String,List<PhysicallyBasedMaterial>>()
    private val grimeOpacity=mutableMapOf<String,FloatArray>()
    internal var grimeWrites=0
        private set
    private var lastCleaning: CleaningState?=null
    private var lastDecorating: Boolean?=null
    private val trails=mutableListOf<Entity>()
    private val trailStarts=DoubleArray(8) { -100.0 }
    private var disposalSeen=0L
    private var disposalStart=Double.NEGATIVE_INFINITY
    private var disposing: String?=null
    private var disposalFrom=Point3(0f,0f,0f)
    suspend fun build(parent: Entity,r: SceneResources): Entity {
        asset=Entity.loadSuspend("asset://models/tidy-renovation.glb")
        parent.addChild(asset)
        val study=Entity.loadSuspend("asset://models/tidy-study-room.glb")
        parent.addChild(study)
        val gaming=Entity.loadSuspend("asset://models/tidy-gaming-room.glb")
        parent.addChild(gaming)
        // Replace only the old cube; the large, optimized architecture asset is retained.
        checkNotNull(asset.findEntity("clean_bin")).destroy(true)
        val expanded=if(includeCleaning) Entity.loadSuspend("asset://models/tidy-cleaning-expanded.glb")
            .also { parent.addChild(it) } else null
        if(!includeCleaning) {
            // The shipped route starts in decorating. Remove the cleaning roots
            // embedded in the original asset before registering any components.
            for(name in listOf("clean_stand","clean_Vacuum","clean_Broom","clean_Brush")+
                cleaningMess().map { it.id }) asset.findEntity(name)?.destroy(true)
        }
        fun required(id: String)=checkNotNull(expanded?.findEntity(id) ?: asset.findEntity(id)) { "Missing renovation node: $id" }
        fun shadows(e: Entity,casts: Boolean=true) {
            if(e.components[ModelComponent::class.java]!=null)
                e.components.set(GroundShadowComponent(castsShadowEnabled=casts,receivesShadowEnabled=true))
            e.getChildren().forEach { shadows(it,casts) }
        }
        shadows(asset)
        // The fixed building receives furniture shadows; drawing its enclosing
        // walls and roof again in the shadow pass adds no movable contact detail.
        shadows(required("renovation_house"),casts=false)
        shadows(required("renovation_base"),casts=false)
        shadows(study,casts=false)
        shadows(gaming,casts=false)
        expanded?.let { shadows(it) }
        fun restoreNormalStrength(e: Entity) {
            e.components[ModelComponent::class.java]?.materials?.forEach { material ->
                if(material is PhysicallyBasedMaterial && material.getName().startsWith("Atelier |")) {
                    // Keep quiet finishes free of the normal-map aliasing
                    // confirmed in live 6.1.9 frames. Other surfaces retain
                    // this builder's authored strength (import resets it to 1).
                    val name=material.getName().removePrefix("Atelier | ")
                    val quiet=name in setOf("Warm mineral plaster","Olive mineral paint","Ivory painted joinery","Lime mortar")
                    val fine=name=="Walnut" || name.startsWith("Parquet ") || name=="Natural brush fibres"
                    material.setNormalScale(if(quiet) 0f else if(fine) .10f else .25f)
                }
            }
            e.getChildren().forEach(::restoreNormalStrength)
        }
        restoreNormalStrength(asset)
        // The fixed shell uses textured unlit finishes with baked contact
        // shading. Keep the day and dimmer cues without relighting it per pixel.
        fun registerFinishes(root: Entity) {
            val seen=mutableSetOf<String>()
            fun visit(e: Entity) {
                e.components[ModelComponent::class.java]?.materials?.forEach {
                    if(it is UnlitMaterial && seen.add(it.getName())) staticFinishes+=it to it.getBaseColor()
                }
                e.getChildren().forEach(::visit)
            }
            visit(root)
        }
        registerFinishes(required("renovation_house"))
        registerFinishes(study)
        registerFinishes(gaming)
        RoomBalcony.complete(checkNotNull(asset.findEntity("renovation_balcony")),r)
        val additions=library?.load("tidy-moving-home")
            ?: Entity.loadSuspend("asset://models/tidy-moving-home.glb")
        for(id in listOf("hearth_fire","room_pendant","house_clock","house_dimmer")) {
            val root=checkNotNull(additions.findEntity(id)?.clone(
                cloneOptions=Entity.CloneOptions(recursive=true,shouldShareMaterialInstance=true)))
            shadows(root);parent.addChild(root)
            if(id=="hearth_fire") repeat(7) { n ->
                flames+=checkNotNull(root.findEntity("hearth_flame_$n"))
            }
            if(id=="room_pendant") pendant=checkNotNull(root.findEntity("room_pendant_bulb"))
            if(id.startsWith("house_")) fixtures[id]=root
        }
        fun hit(parent: Entity,id: String,size: Vector3,p: Vector3=Vector3.ZERO) {
            node().also { parent.addChild(it);it.transform.position=p;interactive(it,size);targets[it]=id }
        }
        hit(fixtures.getValue("house_clock"),"@house:clock",Vector3(.27f,.27f,.028f),Vector3(0f,0f,.014f))
        hit(fixtures.getValue("house_dimmer"),"@house:dimmer",Vector3(.095f,.090f,.038f),Vector3(0f,0f,.015f))
        for(name in listOf("house_clock_needle","house_dimmer_knob")) {
            controls[name]=checkNotNull(parent.findEntity(name))
        }
        if(library==null) additions.destroy(true)
        val base=required("renovation_base")
        if(includeCleaning) buildCleaning(parent,r,::required)
        sun=node().apply {
            transform.eulerAngles=EulerAngles(pitch=-48f,yaw=-32f)
            // Keep daylight and furniture grounding, but avoid a full
            // directional shadow-map pass over the miniature room.
            components.set(DirectionalLightComponent(Color4(1f,.92f,.80f,1f),520f,castsShadowEnabled=includeCleaning))
        }
        bounce=node().apply {
            transform.eulerAngles=EulerAngles(pitch=-28f,yaw=135f)
            components.set(DirectionalLightComponent(Color4(.81f,.89f,1f,1f),180f,castsShadowEnabled=false))
        }
        parent.addChild(sun);parent.addChild(bounce)
        hearthLight=node().apply {
            transform.position=RoomLayout.point(Point3(-1.018f,-.225f,.29f)).vector()+Vector3(.082f,0f,0f)
            components.set(PointLightComponent(Color4(1f,.38f,.10f,1f),3.2f,.62f))
        }
        ceilingLight=node().apply {
            transform.position=RoomLayout.point(Point3(.45f,.535f,-.55f)).vector()
            components.set(PointLightComponent(Color4(1f,.81f,.57f,1f),10f,2.20f))
        }
        parent.addChild(hearthLight);parent.addChild(ceilingLight)
        Log.i("TidyRenovation","assets-ready cleaning=$includeCleaning full-floor=true")
        return base
    }
    private suspend fun buildCleaning(parent: Entity,r: SceneResources,required: (String)->Entity) {
        for(tool in CleanTool.entries) {
            val root=required("clean_${tool.name}")
            entities["clean_${tool.name}"]=root
            targets[root]="@clean:${tool.name}"
            // Hit the handle; floor nozzle remains visible and works at root position.
            node().also { hit ->
                root.addChild(hit)
                hit.transform.position=Vector3(0f,if(tool==CleanTool.Brush) .055f else .20f,0f)
                interactive(hit,if(tool==CleanTool.Brush) Vector3(.12f,.17f,.10f) else Vector3(.17f,.44f,.11f))
                targets[hit]="@clean:${tool.name}"
            }
            delay(16)
        }
        entities["clean_bin"]=required("clean_bin")
        entities["bin_mouth_halo"]=required("bin_mouth_halo")
        entities["mop_head"]=required("mop_head")
        entities["clean_stand_extra"]=required("clean_stand_extra")
        node().also { hit ->
            entities.getValue("clean_bin").addChild(hit)
            hit.transform.position=Vector3(0f,-.14f,0f)
            interactive(hit,Vector3(.29f,.29f,.27f))
            targets[hit]="@clean:Bin"
        }
        targets[entities.getValue("clean_bin")]="@clean:Bin"
        entities["clean_stand"]=required("clean_stand")
        for(m in cleaningMess()) {
            val root=required(m.id);entities[m.id]=root
            fun surface(e: Entity) {
                if(e.components[ModelComponent::class.java]!=null)
                    e.components.set(GroundShadowComponent(castsShadowEnabled=false,receivesShadowEnabled=true))
                e.getChildren().forEach(::surface)
            }
            if(m.kind!=MessKind.Trash) surface(root)
            if(m.kind==MessKind.Debris) repeat(8) { i ->
                entities["${m.id}_wipe-$i"]=required("${m.id}_wipe-$i")
            }
            if(m.kind in setOf(MessKind.Dust,MessKind.Wall,MessKind.Spill)) {
                val slots=required("${m.id}_mesh").components[ModelComponent::class.java]!!.materials
                val byCell=slots.associate { material ->
                    val pbr=checkNotNull(material as? PhysicallyBasedMaterial) { "Grime requires public PBR material" }
                    val index=Regex("^${m.id} cell (\\d+) translucent surface dirt").find(pbr.getName())!!.groupValues[1].toInt()
                    pbr.setBlendingMode(BlendingMode.TRANSPARENT)
                    pbr.setDepthWrite(false)
                    index to pbr
                }
                check(byCell.keys== (0 until CleaningGrid.COUNT).toSet()) { "Missing local grime materials: ${m.id}" }
                grimeMaterials[m.id]=List(CleaningGrid.COUNT) { byCell.getValue(it) }
                grimeOpacity[m.id]=FloatArray(CleaningGrid.COUNT) { Float.NaN }
            }
            if(m.kind==MessKind.Debris) repeat(m.pieceCount()) { i ->
                val key="${m.id}_piece-$i"
                entities[key]=required(key);pieceOrigins[key]=entities.getValue(key).transform.position
            }
            if(m.kind==MessKind.Trash) {
                interactive(root,m.size.vector());targets[root]="@trash:${m.id}"
                delay(16)
            }
        }
        for(key in listOf("broom_bristles","brush_bristles")) entities[key]=required(key)
        repeat(16) {
            particles+=r.part(parent,r.sphere(),if(it<8) "cleanFoam" else "cleanDust").apply {
                enabled=false;transform.scaleVector=Vector3(.004f)
                components.set(GroundShadowComponent(castsShadowEnabled=false,receivesShadowEnabled=false))
            }
        }
        repeat(8) { trails+=r.part(parent,r.sphere(),"cleanFoam").apply {
            enabled=false;components.set(GroundShadowComponent(castsShadowEnabled=false,receivesShadowEnabled=false))
        } }
    }
    fun mood(value: RoomMood)=time(RoomTime.hour(value))
    fun time(value: Float) {
        if(hour==value) return
        hour=value
        val light=RoomTime.lighting(value)
        sun.components[DirectionalLightComponent::class.java]?.apply {
            color=Color4(light.color.x,light.color.y,light.color.z,1f)
            intensity=light.sun
        }
        sun.transform.eulerAngles=EulerAngles(pitch=light.pitch,yaw=light.yaw)
        bounce.components[DirectionalLightComponent::class.java]?.apply {
            color=Color4(.81f,.89f,1f,1f)
            intensity=light.bounce
        }
    }
    fun synchronize(game: RoomGame) {
        state=game
        time(game.dayHour)
        val key=game.dayHour to game.indoorBrightness
        if(finishLight!=key && staticFinishes.isNotEmpty()) {
            finishLight=key
            val light=RoomTime.lighting(game.dayHour)
            val daylight=(light.sun/520f).coerceIn(0f,1f)
            val ambient=.40f+.49f*sqrt(daylight)
            val warm=.16f*game.indoorBrightness*(1f-.4f*daylight)
            for((material,base) in staticFinishes) {
                material.setBaseColor(Color4(
                    base.red*(ambient*(.66f+.34f*light.color.x)+warm),
                    base.green*(ambient*(.66f+.34f*light.color.y)+warm*.76f),
                    base.blue*(ambient*(.66f+.34f*light.color.z)+warm*.51f),base.alpha))
            }
        }
        updates.angles(controls.getValue("house_clock_needle"),EulerAngles(roll=-game.dayHour*15f))
        updates.angles(controls.getValue("house_dimmer_knob"),EulerAngles(roll=135f-game.indoorBrightness*270f))
        ceilingLight.components[PointLightComponent::class.java]?.let {
            val intensity=10f*game.indoorBrightness
            if(it.intensity!=intensity) it.intensity=intensity
        }
        updates.enabled(ceilingLight,game.indoorBrightness>0f)
        updates.enabled(pendant,game.indoorBrightness>0f)
        applyCleaning(game)
    }
    /** The same update is called from input, before Compose receives the frame. */
    fun applyCleaning(game: RoomGame) {
        if(!includeCleaning) return
        state=game
        val c=game.cleaning
        if(c==lastCleaning && game.decorating==lastDecorating) return
        lastCleaning=c;lastDecorating=game.decorating
        if(c!=null && c.disposalSequence!=disposalSeen) {
            disposalSeen=c.disposalSequence;disposalStart=clock
            disposing=c.disposedId;disposalFrom=c.disposedFrom ?: c.position
        }
        for(tool in CleanTool.entries) entities.getValue("clean_${tool.name}").let {
            updates.enabled(it,!game.decorating)
            updates.position(it,(if(c?.tool==tool) c.position else tool.rack).vector())
            updates.rotation(it,if(c?.tool==tool) c.rotation else Rotation())
        }
        updates.enabled(entities.getValue("clean_bin"),!game.decorating)
        updates.position(entities.getValue("clean_bin"),(c?.binPosition ?: CLEAN_BIN).vector())
        updates.rotation(entities.getValue("clean_bin"),c?.binRotation ?: Rotation())
        updates.enabled(entities.getValue("bin_mouth_halo"),!game.decorating && c?.heldTrash!=null)
        updates.scale(entities.getValue("bin_mouth_halo"),Vector3(if(c?.binReady==true) 1.08f else .90f))
        updates.enabled(entities.getValue("clean_stand_extra"),!game.decorating)
        updates.enabled(entities.getValue("clean_stand"),!game.decorating)
        for(m in cleaningMess()) entities.getValue(m.id).let {
            val collected=m.kind==MessKind.Trash && c!=null && c.amount(m.id)>=1f
            updates.enabled(it,c!=null && !c.finished && (c.amount(m.id)<1f || collected))
            updates.position(it,(if(c?.heldTrash==m.id) c.position else if(collected) contents(m.id,c!!) else m.position).vector())
            updates.rotation(it,if(c?.heldTrash==m.id) c.rotation else if(collected) c!!.binRotation else Rotation())
            updates.scale(it,Vector3(if(collected) .42f else 1f))
            if(m.kind==MessKind.Trash) targets[it]=if(collected) "@clean:Bin" else "@trash:${m.id}"
            if(m.kind==MessKind.Debris) repeat(8) { i ->
                updates.enabled(entities.getValue("${m.id}_wipe-$i"),true)
            }
            if(m.kind in setOf(MessKind.Dust,MessKind.Wall,MessKind.Spill)) {
                val cells=c?.let { state->CleaningVisibility.cells(state,m) } ?: List(CleaningGrid.COUNT) { 1f }
                val cached=grimeOpacity.getValue(m.id)
                grimeMaterials.getValue(m.id).forEachIndexed { i,material ->
                    val opacity=(1f-cells[i]).coerceIn(0f,1f)
                    if(cached[i]!=opacity) { material.setOpacity(opacity);cached[i]=opacity;grimeWrites++ }
                }
            }
            if(m.kind==MessKind.Debris) repeat(m.pieceCount()) { i ->
                val key="${m.id}_piece-$i";val piece=entities.getValue(key)
                val amount=c?.amount(m.id) ?: 1f
                val gather=(amount*2).coerceIn(0f,1f)
                val old=pieceOrigins.getValue(key)
                val a=i*2.399f;val r=.017f+sqrt(i/m.pieceCount().toFloat())*.026f
                val heap=Vector3(cos(a)*r,.007f+(i%4)*.003f,sin(a)*r)
                updates.position(piece,Vector3(old.x*(1-gather)+heap.x*gather,
                    old.y*(1-gather)+heap.y*gather,old.z*(1-gather)+heap.z*gather))
                updates.enabled(piece,amount<.5f+(i+1)/(m.pieceCount()*2f))
            }
        }
        if(c!=null && c.sequence!=seen) {
            seen=c.sequence
            c.contact?.let { contact ->
                sparkleTime=clock;sparklePoint=contact.point;lastContact=contact
                if(contact.tool==CleanTool.Brush) {
                    val index=(seen%8).toInt()
                    val p=trails[index]
                    updates.position(p,contact.point.vector())
                    updates.scale(p,Vector3(.065f,.040f,.0025f))
                    trailStarts[index]=clock
                }
            }
        }
    }
    fun applyHeld(game: RoomGame) {
        val c=game.cleaning ?: return
        val key=c.tool?.let { "clean_${it.name}" } ?: c.heldTrash ?: if(c.heldBin) "clean_bin" else return
        entities[key]?.let {
            updates.position(it,c.position.vector())
            updates.rotation(it,c.rotation)
        }
    }
    fun heldPosition(game: RoomGame): Vector3? {
        val c=game.cleaning ?: return null
        val key=c.tool?.let { "clean_${it.name}" } ?: c.heldTrash ?: if(c.heldBin) "clean_bin" else return null
        return entities[key]?.transform?.position
    }
    fun animate(seconds: Double) {
        clock=seconds
        if(seconds-lastFire>=.05) {
        lastFire=seconds
        flames.forEachIndexed { index,e ->
            val phase=seconds*(3.1+index*.17)+index*1.79
            e.transform.scaleVector=Vector3(
                (1.0+.08*kotlin.math.sin(phase*1.3)).toFloat(),
                (.86+.16*kotlin.math.sin(phase)).toFloat(),1f)
            e.transform.eulerAngles=EulerAngles(roll=(kotlin.math.sin(phase*.7)*5).toFloat())
        }
        hearthLight.components[PointLightComponent::class.java]?.intensity=
            (3.2+.24*kotlin.math.sin(seconds*5.7)+.13*kotlin.math.sin(seconds*9.3)).toFloat()
        }
        if(!includeCleaning) return
        val age=seconds-sparkleTime
        particles.forEachIndexed { index,p ->
            val contact=lastContact
            val foam=contact?.tool in setOf(CleanTool.Brush,CleanTool.Mop)
            updates.enabled(p,!state.decorating && age in 0.0.. .32 && (index<8)==foam)
            if(p.enabled) {
                val a=index*2.399
                val vacuum=contact?.tool==CleanTool.Vacuum
                val spread=if(foam) .055+age*.06 else if(vacuum) .05*(1-age/.32) else .016+age*.09
                p.transform.position=Vector3(sparklePoint.x+(cos(a)*spread).toFloat(),
                    sparklePoint.y+(if(foam && contact?.tool==CleanTool.Brush) sin(a)*spread else .005+age*.04).toFloat(),
                    sparklePoint.z+(if(foam && contact?.tool==CleanTool.Brush) .004 else sin(a)*spread).toFloat())
                p.transform.scaleVector=Vector3(((if(foam) .006 else .0025)*(1-age/.32)).toFloat())
            }
        }
        trails.forEachIndexed { i,e -> updates.enabled(e,!state.decorating && seconds-trailStarts[i] in 0.0.. .65) }
        val touching=state.cleaning?.contact!=null && age in 0.0.. .12
        entities["broom_bristles"]?.let { updates.angles(it,
            EulerAngles(roll=if(touching && lastContact?.tool==CleanTool.Broom) (sin(seconds*29)*6).toFloat() else 0f)) }
        entities["brush_bristles"]?.let { updates.scale(it,
            Vector3(1f,1f,if(touching && lastContact?.tool==CleanTool.Brush) .72f else 1f)) }
        entities["mop_head"]?.let { updates.scale(it,
            Vector3(1f,if(touching && lastContact?.tool==CleanTool.Mop) .74f else 1f,1f)) }
        val c=state.cleaning
        val id=disposing
        if(c!=null && !c.finished && id!=null) {
            val ageDrop=((seconds-disposalStart)/.34).toFloat().coerceIn(0f,1f)
            val mouth=c.binPosition+Point3(0f,.025f,0f)
            val destination=contents(id,c)
            val p=if(ageDrop<.45f) {
                val t=ageDrop/.45f
                disposalFrom*(1f-t)+mouth*t+Point3(0f,sin(t*PI.toFloat())*.025f,0f)
            } else {
                val t=(ageDrop-.45f)/.55f
                mouth*(1f-t)+destination*t
            }
            updates.position(entities.getValue(id),p.vector())
            if(ageDrop>=1f) disposing=null
        }
    }
    private fun contents(id: String,c: CleaningState): Point3 {
        val i=cleaningMess().filter { it.kind==MessKind.Trash }.indexOfFirst { it.id==id }
        val a=i*2.399f
        return c.binPosition+c.binRotation.rotate(Point3(cos(a)*.053f,-.22f+(i%3)*.024f,sin(a)*.053f))
    }
}
