package com.example.tidyspace.pc5853b2a.ui.sorting.scene

import com.pico.spatial.core.ecs.BoundingBox
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.GroundShadowComponent
import com.pico.spatial.core.ecs.resource.Material
import com.pico.spatial.core.ecs.resource.MeshModel
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import com.pico.spatial.core.ecs.resource.TextureResource
import android.graphics.Bitmap
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.core.math.Vector2
import com.example.tidyspace.pc5853b2a.ui.theme.SortingPalette
import kotlin.math.*
import kotlinx.coroutines.delay
import com.example.tidyspace.pc5853b2a.domain.model.RoomMood

/** Scene-lifetime native resource registry. No resource construction in update/recomposition. */
class SceneResources {
    private val meshes = mutableMapOf<String, MeshResource>()
    private val materials = mutableMapOf<String, Material>()
    private val textures = mutableMapOf<String,TextureResource>()
    private val bitmaps = mutableListOf<Bitmap>()
    fun material(token: String): Material = materials.getOrPut(token) {
        val hex = roomColors[token] ?: SortingPalette.hex.getValue(token)
        fun linear(channel: Int): Float {
            val s = channel / 255f
            return if(s <= .04045f) s / 12.92f else ((s+.055f)/1.055f).pow(2.4f)
        }
        PhysicallyBasedMaterial.create().apply {
            setBaseColor(Color4(linear(hex shr 16 and 255),linear(hex shr 8 and 255),linear(hex and 255),1f))
            setRoughness(if(token == "goldObject") .38f else .76f)
            setMetallic(if(token == "goldObject") .25f else 0f)
            if(token in listOf("furnitureWood","linen","rugFabric")) setBaseColorTexture(texture(token))
            if(token=="lampGlow") setEmissiveColor(Color4(1f,.66f,.25f,1f))
        }
    }
    fun mood(mood: RoomMood) {
        val colors=when(mood) {
            RoomMood.Morning -> mapOf("plaster" to 0xE4D9C4,"sageWall" to 0x8E9E88,"panelGreen" to 0x5D766C,"panelInset" to 0x50665D)
            RoomMood.Sunset -> mapOf("plaster" to 0xE8C2A5,"sageWall" to 0xBC8A73,"panelGreen" to 0x79605A,"panelInset" to 0x684F4B)
            RoomMood.Evening -> mapOf("plaster" to 0xA6B6C6,"sageWall" to 0x657D94,"panelGreen" to 0x3B5269,"panelInset" to 0x30465A)
        }
        fun linear(c: Int): Float { val v=c/255f;return if(v<=.04045f) v/12.92f else ((v+.055f)/1.055f).pow(2.4f) }
        colors.forEach { (token,hex) ->
            (material(token) as PhysicallyBasedMaterial).setBaseColor(
                Color4(linear(hex shr 16 and 255),linear(hex shr 8 and 255),linear(hex and 255),1f))
        }
    }
    private val roomColors = mapOf(
        "oak0" to 0xBDA17B, "oak1" to 0xC5AA84, "oak2" to 0xB59A76,
        "oak3" to 0xC9AF8B, "oak4" to 0xC1A780, "oak5" to 0xBCA17D,
        "endGrain" to 0x907653, "woodGrain" to 0xAF936F,
        "plaster" to 0xE4D9C4, "sageWall" to 0x8E9E88,
        "wallPattern" to 0xB0BAA3, "creamTrim" to 0xF1E4C9,
        "panelGreen" to 0x5D766C, "panelInset" to 0x50665D,
        "iron" to 0x424D48, "tile" to 0xD0C5AB,
        "furnitureWood" to 0xC69D72, "linen" to 0xC8BBA0, "rugFabric" to 0x697E75,
        "leaf" to 0x527750, "leafLight" to 0x90A96C, "soil" to 0x4C372A,
        "terracotta" to 0xB97555, "lampGlow" to 0xFFE6AC, "ceramic" to 0x98B2AC,
        "sunset" to 0xE4AA79, "mountain" to 0x7B9690, "cushion" to 0xC98D67,
        "cleanFoam" to 0xF1EFE1,"cleanDust" to 0xB5A589,
    )
    private fun texture(token: String)=textures.getOrPut(token) {
        val n=128
        val pixels=IntArray(n*n) { index ->
            val x=index%n;val y=index/n
            val shade=if(token=="furnitureWood") {
                val wave=sin(x*.49+sin(y*.055)*2.7)+.4*sin(x*1.6+y*.026)
                (231+wave*13).toInt()
            } else 228+(if(x%4<2) 14 else -7)+(if(y%4<2) 7 else -7)
            val c=shade.coerceIn(0,255)
            (255 shl 24) or (c shl 16) or (c shl 8) or c
        }
        val bitmap=Bitmap.createBitmap(pixels,n,n,Bitmap.Config.ARGB_8888)
        bitmaps+=bitmap
        TextureResource.create(bitmap)
    }
    /** Smooth surface of revolution; profile includes the interior for genuinely hollow ceramics. */
    fun lathe(name: String,profile: List<Pair<Float,Float>>): MeshResource=meshes.getOrPut("lathe:$name") {
        val positions=mutableListOf<Vector3>();val normals=mutableListOf<Vector3>()
        val uv=mutableListOf<Vector2>();val indices=mutableListOf<Int>();val segments=40
        for(row in profile.indices) {
            val (r,y)=profile[row]
            val before=profile[(row-1).coerceAtLeast(0)];val after=profile[(row+1).coerceAtMost(profile.lastIndex)]
            val dr=after.first-before.first;val dy=after.second-before.second
            val length=sqrt(dr*dr+dy*dy).coerceAtLeast(.000001f)
            for(i in 0..segments) {
                val a=(i*2*PI/segments).toFloat()
                positions+=Vector3(r*cos(a),y,r*sin(a))
                normals+=Vector3(dy*cos(a)/length,-dr/length,dy*sin(a)/length)
                uv+=Vector2(i.toFloat()/segments,row.toFloat()/profile.lastIndex)
            }
        }
        for(row in 0 until profile.lastIndex) for(i in 0 until segments) {
            val a=row*(segments+1)+i;val b=a+segments+1
            indices.addAll(listOf(a,b,a+1,a+1,b,b+1))
        }
        val maxR=profile.maxOf { it.first };val lo=profile.minOf { it.second };val hi=profile.maxOf { it.second }
        MeshResource.createWithMeshModel(MeshModel(positions,indices,normals,uv0=uv),
            BoundingBox(Vector3(0f,(lo+hi)/2,0f),Vector3(maxR,(hi-lo)/2,maxR)),name)
    }
    fun sphere()=lathe("soft-sphere",(0..20).map {
        val t=(-PI/2+it*PI/20).toFloat();cos(t).coerceAtLeast(0f) to sin(t)
    })
    fun mesh(name: String, model: MeshModel, bounds: BoundingBox): MeshResource =
        meshes.getOrPut(name) { MeshResource.createWithMeshModel(model,bounds,name) }
    fun box(size: Vector3, radius: Float = .002f) = meshes.getOrPut("box:$size:$radius") {
        MeshResource.createBox(size, radius.coerceAtMost(min(size.x,min(size.y,size.z)) / 3f))
    }
    fun cone(height: Float, radius: Float) = meshes.getOrPut("cone:$height:$radius") {
        MeshResource.createCone(height, radius)
    }
    /** Many plate cells share a single mesh/native upload, retaining the actual cutouts. */
    fun plate(cells: List<Pair<Vector3, Vector3>>): MeshResource = meshes.getOrPut("receiving-plate") {
        val positions = mutableListOf<Vector3>()
        val normals = mutableListOf<Vector3>()
        val indices = mutableListOf<Int>()
        fun face(a: Vector3,b: Vector3,c: Vector3,d: Vector3,n: Vector3) {
            val i = positions.size
            positions.addAll(listOf(a,b,c,d)); repeat(4) { normals.add(n) }
            indices.addAll(listOf(i,i+1,i+2,i,i+2,i+3))
        }
        for((center,size) in cells) {
            val l=center.x-size.x/2; val r=center.x+size.x/2
            val b=center.y-size.y/2; val t=center.y+size.y/2
            val f=center.z+size.z/2; val k=center.z-size.z/2
            face(Vector3(l,t,f),Vector3(r,t,f),Vector3(r,t,k),Vector3(l,t,k),Vector3(0f,1f,0f))
            face(Vector3(l,b,k),Vector3(r,b,k),Vector3(r,b,f),Vector3(l,b,f),Vector3(0f,-1f,0f))
            face(Vector3(l,b,f),Vector3(r,b,f),Vector3(r,t,f),Vector3(l,t,f),Vector3(0f,0f,1f))
            face(Vector3(r,b,k),Vector3(l,b,k),Vector3(l,t,k),Vector3(r,t,k),Vector3(0f,0f,-1f))
            face(Vector3(r,b,f),Vector3(r,b,k),Vector3(r,t,k),Vector3(r,t,f),Vector3(1f,0f,0f))
            face(Vector3(l,b,k),Vector3(l,b,f),Vector3(l,t,f),Vector3(l,t,k),Vector3(-1f,0f,0f))
        }
        MeshResource.createWithMeshModel(MeshModel(positions,indices,normals),
            BoundingBox(Vector3(0f,.0225f,-.111f),Vector3(.44f,.005f,.111f)),"receiving-plate")
    }
    /** Open annular cylinder, +Y up, exact outer dimensions; flat normals for crisp edges. */
    fun ring(outer: Float, inner: Float, height: Float): MeshResource =
        meshes.getOrPut("ring:$outer:$inner:$height") {
            val positions = mutableListOf<Vector3>()
            val normals = mutableListOf<Vector3>()
            val indices = mutableListOf<Int>()
            fun quad(a: Vector3,b: Vector3,c: Vector3,d: Vector3,n: Vector3) {
                val index = positions.size
                positions.addAll(listOf(a,b,c,d)); repeat(4) { normals.add(n) }
                indices.addAll(listOf(index,index+1,index+2,index,index+2,index+3))
            }
            val h = height/2f
            for(i in 0 until 48) {
                val a = (i*2.0*PI/48).toFloat()
                val b = ((i+1)*2.0*PI/48).toFloat()
                fun p(r:Float,y:Float,t:Float) = Vector3(r*cos(t),y,r*sin(t))
                quad(p(outer,h,a),p(inner,h,a),p(inner,h,b),p(outer,h,b),Vector3(0f,1f,0f))
                quad(p(outer,-h,b),p(inner,-h,b),p(inner,-h,a),p(outer,-h,a),Vector3(0f,-1f,0f))
                quad(p(outer,-h,a),p(outer,h,a),p(outer,h,b),p(outer,-h,b),
                    Vector3(cos((a+b)/2),0f,sin((a+b)/2)))
                quad(p(inner,-h,b),p(inner,h,b),p(inner,h,a),p(inner,-h,a),
                    Vector3(-cos((a+b)/2),0f,-sin((a+b)/2)))
            }
            MeshResource.createWithMeshModel(
                model = MeshModel(positions, indices, normals),
                bounds = BoundingBox(Vector3(0f), Vector3(outer,h,outer)), name = "annular-cylinder")
        }
    suspend fun part(parent: Entity, mesh: MeshResource, token: String, position: Vector3 = Vector3(0f)): Entity =
        part(parent,mesh,material(token),position)
    suspend fun part(parent: Entity, mesh: MeshResource, finish: Material, position: Vector3 = Vector3(0f)): Entity {
        // Native mesh/material registration may wait for the renderer. Let Android handle
        // focus/input and render the loading panel between individual registrations.
        delay(16)
        return Entity().apply {
            if(components[TransformComponent::class.java]==null) components.set(TransformComponent())
            components.set(ModelComponent(mesh, finish))
            components.set(GroundShadowComponent(castsShadowEnabled = true, receivesShadowEnabled = true))
            parent.addChild(this)
            components[TransformComponent::class.java]!!.position = position
        }
    }
    fun close() {
        meshes.values.forEach { it.close() }; materials.values.forEach { it.close() }
        textures.values.forEach { it.close() };textures.clear()
        bitmaps.forEach { it.recycle() };bitmaps.clear()
        meshes.clear(); materials.clear()
    }
}
