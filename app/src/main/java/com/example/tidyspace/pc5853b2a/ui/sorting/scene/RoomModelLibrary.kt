package com.example.tidyspace.pc5853b2a.ui.sorting.scene

import android.os.SystemClock
import android.util.Log
import com.example.tidyspace.pc5853b2a.domain.model.*
import com.pico.spatial.core.ecs.Entity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Scene-owned source meshes: lazy family loading, one copy of each shared PBR atlas. */
internal class RoomModelLibrary {
    private val assets=mutableMapOf<String,Entity>()
    private val loading=Mutex()
    suspend fun load(name: String): Entity = loading.withLock {
        assets[name] ?: run {
            val started=SystemClock.uptimeMillis()
            Entity.loadSuspend("asset://models/$name.glb").also {
                assets[name]=it
                Log.i("TidyAssets","source-ready family=$name loadMs=${SystemClock.uptimeMillis()-started} cached=true")
            }
        }
    }
    private fun family(spec: RoomSpec)=LifestyleModels.family(spec) ?: when {
            studyCatalogue().any { it.id==spec.assetId }->"tidy-study"
            gamingCatalogue().any { it.id==spec.assetId }->"tidy-gaming"
            (homeExpansion()+handheldExpansion()).any { it.id==spec.assetId }->"tidy-home-expansion"
            spec.kind==Furnishing.MovingBox || homeCatalogue().any { it.id==spec.assetId }->"tidy-moving-home"
            spec.kind in setOf(Furnishing.Woodfish,Furnishing.MusicBox,Furnishing.SingingBowl)->"tidy-toys"
            toyCatalogue().any { it.id==spec.assetId }->"tidy-playthings"
            else->"tidy-furniture"
        }
    fun cachedSource(spec: RoomSpec): Entity?=assets[family(spec)]?.findEntity(spec.assetId)
    suspend fun source(spec: RoomSpec): Entity {
        return checkNotNull(load(family(spec)).findEntity(spec.assetId)) { "Missing model ${spec.assetId}" }
    }
    fun close() { assets.values.forEach { it.destroy(true) };assets.clear() }
}
