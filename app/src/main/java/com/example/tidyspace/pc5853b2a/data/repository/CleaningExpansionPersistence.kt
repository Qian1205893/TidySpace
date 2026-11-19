package com.example.tidyspace.pc5853b2a.data.repository

import com.example.tidyspace.pc5853b2a.domain.model.*
import kotlin.math.abs

/** Sidecar keeps all new IDs out of the strict legacy furniture snapshot. */
object CleaningExpansionPersistence {
    const val KEY="cleaning-expansion-v1"
    private val extra=expandedCleaningMess().map { it.id }.toSet()
    fun encode(s: RoomGame): String? {
        val c=s.cleaning?.takeUnless { it.finished } ?: return null
        fun maps(values: Map<String,List<Float>>) = values.filterKeys { it in extra }.toSortedMap()
            .entries.joinToString(";") { (id,v)->"$id:${v.joinToString(",")}" }.ifEmpty { "-" }
        val bin=c.binOrigin ?: c.binPosition
        val turn=c.binTurnOrigin ?: c.binRotation
        return listOf("tidy-cleaning-expansion-1",
            (c.origin ?: c.progress).filterKeys { it in extra }.toSortedMap().entries
                .joinToString(";") { "${it.key}:${it.value}" }.ifEmpty { "-" },
            maps(c.originTiles ?: c.workTiles),maps(c.originWet ?: c.wetTiles),
            "${bin.x},${bin.y},${bin.z}", "${turn.x},${turn.y},${turn.z},${turn.w}").joinToString("\t")
    }
    fun merge(game: RoomGame,text: String?): RoomGame {
        val c=game.cleaning?.takeUnless { it.finished } ?: return game
        val restored=runCatching {
            require(text!=null)
            val fields=text.split('\t')
            require(fields.size==6 && fields[0]=="tidy-cleaning-expansion-1")
            fun floats(s: String)=s.split(',').map { it.toFloat().also { v->require(v.isFinite()) } }
            fun entries(s: String): List<Pair<String,String>> = if(s=="-") emptyList() else s.split(';').map {
                val p=it.split(':');require(p.size==2 && p[0] in extra);p[0] to p[1]
            }.also { require(it.map { p->p.first }.distinct().size==it.size) }
            val progress=entries(fields[1]).associate { (id,v)->
                id to v.toFloat().also { require(it.isFinite() && it in 0f..1f) }
            }
            fun cells(s: String,wet: Boolean)=entries(s).associate { (id,v)->
                require(expandedCleaningMess().first { it.id==id }.kind in
                    if(wet) setOf(MessKind.Spill) else setOf(MessKind.Dust,MessKind.Spill))
                val list=floats(v)
                require(list.size==CleaningGrid.COUNT && list.all { it in 0f..1f })
                if(!wet) require(abs(list.average().toFloat()-(progress[id] ?: 0f))<.00001f)
                id to list
            }
            val p=floats(fields[4]);val q=floats(fields[5])
            require(p.size==3 && q.size==4)
            val bin=Point3(p[0],p[1],p[2])
            val turn=Rotation(q[0],q[1],q[2],q[3]).normalized().gravityTurn()
            val corners=listOf(-.139f,.139f).flatMap { x ->
                listOf(-.206f,.156f).map { z -> turn.rotate(Point3(x,0f,z)) }
            }
            val validBin=RoomFloor.contains(bin.x+corners.minOf { it.x },bin.x+corners.maxOf { it.x },
                bin.z+corners.minOf { it.z },bin.z+corners.maxOf { it.z }) &&
                abs(bin.y-(-.32f+CleaningBin.floorOffset))<.001f
            c.copy(progress=c.progress+progress,workTiles=c.workTiles+cells(fields[2],false),
                wetTiles=cells(fields[3],true),binPosition=if(validBin) bin else CLEAN_BIN,
                binRotation=if(validBin) turn else Rotation())
        }.getOrNull()
        val merged=restored ?: run {
            val inherited=legacyCleaningMess().map { c.amount(it.id) }.average().toFloat()
            c.copy(progress=c.progress+extra.associateWith { inherited })
        }
        return game.copy(cleaning=if(merged.spotless) merged.completed() else merged)
    }
    fun restore(main: String?,extra: String?): RoomGame?=
        RoomPersistence.decode(main,deferCleaningCompletion=true)?.let { merge(it,extra) }
}
