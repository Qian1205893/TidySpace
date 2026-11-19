package com.example.tidyspace.pc5853b2a.data.repository

import com.example.tidyspace.pc5853b2a.domain.model.*
import com.example.tidyspace.pc5853b2a.domain.usecase.RoomTime
import kotlin.math.abs

/** Versioned local snapshot. Held items are never persisted in a half-finished placement. */
object RoomPersistence {
    fun encode(s: RoomGame): String=buildString {
        val hour=s.ritual?.hour ?: s.dayHour
        val brightness=s.ritual?.brightness ?: s.indoorBrightness
        val current=s.movingHome || brightness!=.65f || hour!=RoomTime.hour(s.mood) || s.ritual!=null
        val legacyIds=legacyCleaningMess().map { it.id }.toSet()
        val committedTiles=s.cleaning?.let { it.originTiles ?: it.workTiles }.orEmpty().filterKeys { it in legacyIds }
        val local=committedTiles.isNotEmpty()
        val annex=s.bathroomVersion>0
        val gaming=s.gamingVersion>0
        val study=s.studyVersion>0
        appendLine(listOf(if(study) "tidy-room-14" else if(gaming) "tidy-room-13" else if(annex) "tidy-room-12" else if(local) "tidy-room-11" else if(s.boxInventoryVersion==2) "tidy-room-10" else if(s.boxInventoryVersion==1) "tidy-room-9" else if(current) "tidy-room-8" else "tidy-room-6",s.crateOpen,s.ritual?.lampOn ?: s.lampOn,s.mechanismOrigin?.drawerOpen ?: s.drawerOpen,
            s.mechanismOrigin?.roomYaw ?: s.roomYaw,(if(s.ritual!=null) RoomTime.period(hour) else s.mood).name,s.guideVisible,s.nextSerial,s.additions,
            s.achievements.sorted().joinToString(",").ifEmpty { "-" },
            s.playCounts.toSortedMap().entries.joinToString(",") { "${it.key}:${it.value}" }.ifEmpty { "-" },
            s.toyStates.toSortedMap().entries.joinToString(",") { (id,t)->
                "$id:${t.open}:${t.bubbles}:${t.rounds}:${t.detent}:${t.roundReady}"
            }.ifEmpty { "-" },
            s.cleaning?.finished ?: "-",
            s.cleaning?.let { c -> (c.origin ?: c.progress).filterKeys { it in legacyIds }.toSortedMap().entries.joinToString(",") { "${it.key}:${it.value}" } }?.ifEmpty { "-" } ?: "-",
            s.movingHome,s.boxStock.toSortedMap().entries.joinToString(";") { (id,stock)->"$id:${stock.joinToString(",")}" }.ifEmpty { "-" },
            brightness,hour,s.boxInventoryVersion,s.layoutVersion,
            committedTiles.toSortedMap().entries.joinToString(";") { (id,values)->"$id:${values.joinToString(",")}" }.ifEmpty { "-" },
            s.bathroomVersion,s.gamingVersion,s.archivedIds.sorted().joinToString(",").ifEmpty { "-" },s.studyVersion
        ).let { if(study) it else if(gaming) it.take(24) else if(annex) it.take(22) else if(local) it.take(21) else if(s.boxInventoryVersion==2) it.take(20) else if(s.boxInventoryVersion==1) it.take(19) else if(current) it.take(18) else it.take(14) }.joinToString("\t"))
        (s.origin ?: s.items).forEach { i ->
            appendLine(listOf(i.spec.id,i.position.x,i.position.y,i.position.z,i.rotation.x,
                i.rotation.y,i.rotation.z,i.rotation.w,i.support ?: "-",i.packed,i.spec.assetId).joinToString("\t"))
        }
    }
    fun decode(text: String?,deferCleaningCompletion: Boolean=false): RoomGame?=runCatching {
        require(text!=null)
        val lines=text.trim().lines();val header=lines.first().split('\t')
        val legacy=header[0]=="tidy-room-1"
        val v3=header[0]=="tidy-room-3"
        val v4=header[0]=="tidy-room-4"
        val v5=header[0]=="tidy-room-5"
        val v6=header[0]=="tidy-room-6"
        val v14=header[0]=="tidy-room-14"
        val v13=header[0]=="tidy-room-13" || v14
        val v12=header[0]=="tidy-room-12" || v13
        val v11=header[0]=="tidy-room-11" || v12
        val v10=header[0]=="tidy-room-10" || v11
        val v9=header[0]=="tidy-room-9" || v10
        val v8=header[0]=="tidy-room-8" || v9
        val v7=header[0]=="tidy-room-7" || v8
        require(if(legacy) header.size==5 else if(v14) header.size==25 else if(v13) header.size==24 else if(v12) header.size==22 else if(v11) header.size==21 else if(v10) header.size==20 else if(v9) header.size==19 else if(v8) header.size==18 else if(v7) header.size==17 else if(v5 || v6) header.size==14 else if(v4) header.size==12 else if(v3) header.size==11 else header[0]=="tidy-room-2" && header.size==10)
        val inventoryVersion=if(v11) header[18].toInt().also { require(it in 0..2) } else if(v10) 2 else if(v9) 1 else 0
        val catalogue=(roomCatalogue()+toyCatalogue()+homeCatalogue()+gamingCatalogue()+studyCatalogue()+lifestyleCatalogue()).associateBy { it.id }
        val items=lines.drop(1).map { line ->
            val v=line.split('\t');require(v.size==if(legacy) 10 else 11)
            val source=if(v7 && v[10].startsWith("movingbox")) movingBoxes().first { it.id==v[0] }
                else catalogue.getValue(if(legacy) v[0] else v[10])
            val bath=if(v12) BathroomLayout.fixtures().find { it.spec.id==v[0] && it.spec.assetId==source.id } else null
            val spec=if(bath!=null) bath.spec else if(v[0]==source.id) source else {
                require(v[0].matches(Regex("${source.id}~[1-9][0-9]{0,8}")) && shopCatalogue().any { it.id==source.id })
                source.copy(id=v[0],packed=false,assetId=source.id)
            }
            val p=Point3(v[1].toFloat(),v[2].toFloat(),v[3].toFloat())
            val q=Rotation(v[4].toFloat(),v[5].toFloat(),v[6].toFloat(),v[7].toFloat())
            val scale=RoomLayout.scale
            require(p.finite() && q.finite && p.x in -1.2f*scale..1.5f*scale && p.y in -.35f.. .75f &&
                p.z in -1.1f*scale..(if(v13) GamingLayout.front+.03f else if(v12) BathroomLayout.front+.03f else 1.24f*scale))
            val support=v[8].takeUnless { it=="-" }
            RoomItem(spec,p,q.normalized(),support,v[9].toBooleanStrict())
        }
        val ids=items.map { it.spec.id }.toSet()
        val archived=if(v13 && header[23]!="-") header[23].split(',').toSet() else emptySet()
        if(v14) require(archived.isEmpty() && items.none { StudyLayout.retired(it.spec) })
        require(archived.all { id->items.any { original->
            original.spec.id==id && original.packed && !BathroomLayout.owns(id) &&
                items.any { BathroomLayout.owns(it.spec.id) && it.spec.assetId==original.spec.assetId }
        } })
        require(ids.containsAll(roomCatalogue().map { it.id }) && ids.size==items.size &&
            items.size<=if(v7) ROOM_CAPACITY+uniqueCatalogue().size+movingBoxes().size else ROOM_CAPACITY)
        items.forEach { i ->
            require(i.support==null || i.support in ids || i.support in listOf("@back","@left") ||
                (v12 && i.support==BathroomLayout.wallSupport) || (v13 && i.support==GamingLayout.wallSupport))
            val visited=mutableSetOf(i.spec.id);var parent=i.support
            while(parent in ids) {
                require(visited.add(parent!!))
                parent=items.first { it.spec.id==parent }.support
            }
        }
        val drawer=header[3].toFloat();val yaw=header[4].toFloat()
        require(drawer.isFinite() && yaw.isFinite())
        val serial=if(legacy) 1 else header[7].toInt()
        val additions=if(legacy) 0 else header[8].toInt()
        require(serial>0 && serial<100000000 && additions>=0 && additions<serial)
        require(items.filter { it.spec.id.contains('~') }.all { it.spec.id.substringAfter('~').toInt()<serial })
        val known=(1..11).map { "guide-$it" }.toSet()+setOf("tea","garden","gallery","library","lounge","home","zen","melody","trio","stack")
        val earned=if(legacy || header[9]=="-") emptySet() else header[9].split(',').toSet()
        require(known.containsAll(earned))
        fun validToyId(id: String): Boolean = toyCatalogue().any { spec ->
            (v7 && id==spec.id) || (id.matches(Regex("${spec.id}~[1-9][0-9]{0,8}")) && id.substringAfter('~').toInt()<serial)
        }
        val counts=if((!v3 && !v4 && !v5 && !v6 && !v7) || header[10]=="-") emptyMap() else header[10].split(',').associate { entry ->
            val pair=entry.split(':');require(pair.size==2)
            val id=pair[0];val value=pair[1].toLong()
            require(validToyId(id) && value in 0..1_000_000_000L)
            id to value
        }
        val states=if((!v4 && !v5 && !v6 && !v7) || header[11]=="-") emptyMap() else header[11].split(',').associate { entry ->
            val v=entry.split(':');require(v.size==6 && validToyId(v[0]))
            val state=ToyState(v[1].toBooleanStrict(),v[2].toInt(),v[3].toInt(),v[4].toInt(),v[5].toBooleanStrict())
            require(state.bubbles in 0..63 && state.rounds in 0..1_000_000_000 && state.detent in 0..11)
            v[0] to state
        }
        // Move wall-mounted families with the expanded wall; preserve their local arrangement.
        fun wallOffset(item: RoomItem): Point3 {
            var parent=item.support
            while(parent in ids) parent=items.first { it.spec.id==parent }.support
            return when(parent) { "@back"->Point3(0f,0f,-.3065f); "@left"->Point3(-.3495f,0f,0f);else->Point3(0f,0f,0f) }
        }
        val migrated=if(legacy) items.map { it.copy(position=it.position+wallOffset(it)) } else items
        val cleaning=if(!v5 && !v6 && !v7) CleaningState() else if(header[12]=="-") {
            require(header[13]=="-");null
        } else {
            val progress=if(header[13]=="-") emptyMap() else header[13].split(',').associate { entry ->
                val v=entry.split(':');require(v.size==2 && if(v5)
                    v[0].matches(Regex("(dust|debris|wall|trash)-(1[01]|[0-9])"))
                    else legacyCleaningMess().any { it.id==v[0] })
                val value=v[1].toFloat();require(value.isFinite() && value in 0f..1f)
                v[0] to value
            }
            val finished=header[12].toBooleanStrict()
            if(v5 && finished) require(listOf("dust","debris","wall","trash").all { kind ->
                (0..11).all { progress["$kind-$it"]==1f }
            })
            // Collapse each group of old spots into one broad area; retain fractional work.
            val merged=if(!v5) progress else legacyCleaningMess().associate { mess ->
                val kind=mess.id.substringBefore('-')
                val total=legacyCleaningMess().count { it.kind==mess.kind }
                val index=mess.id.substringAfter('-').toInt()
                val group=12/total
                mess.id to ((index*group until (index+1)*group).sumOf { (progress["$kind-$it"] ?: 0f).toDouble() }/group).toFloat()
            }
            val tiles=if(!v11 || header[20]=="-") emptyMap() else header[20].split(';').map { entry ->
                val v=entry.split(':');require(v.size==2)
                val mess=cleaningMess().first { it.id==v[0] }
                require(mess.kind in setOf(MessKind.Dust,MessKind.Wall))
                val values=v[1].split(',').map { it.toFloat().also { value ->
                    require(value.isFinite() && value in 0f..1f)
                } }
                require(values.size==CleaningGrid.COUNT && abs(values.average().toFloat()-(merged[mess.id] ?: 0f))<.00001f)
                v[0] to values
            }.also { entries->require(entries.map { it.first }.distinct().size==entries.size) }.toMap()
            CleaningState(merged,false,workTiles=tiles).let { state ->
                val coreDone=legacyCleaningMess().all { state.amount(it.id)>=1f }
                require(!finished || coreDone)
                if(finished || (coreDone && !deferCleaningCompletion)) state.completed() else state
            }
        }
        val moving=v7 && header[14].toBooleanStrict()
        val stock=if(!v7 || header[15]=="-") emptyMap() else header[15].split(';').associate { entry ->
            val v=entry.split(':');require(v.size==2 && movingBoxes().any { it.id==v[0] } && v[0] in ids)
            v[0] to if(v[1].isEmpty()) emptyList() else v[1].split(',')
        }
        val goods=stock.values.flatten()
        require(goods.distinct().size==goods.size && goods.none { it in archived } && goods.all { id ->
            if(v9) items.any { it.spec.id==id && it.packed &&
                it.spec.kind !in setOf(Furnishing.MovingBox,Furnishing.Crate) }
            else uniqueCatalogue().any { it.id==id } && items.none { it.spec.assetId==id && !it.packed }
        })
        if(v9 && !v11) require(header[18]==if(v10) "2" else "1")
        if(v10) {
            require(header[19].toInt() in 1..RoomLayout.version)
        }
        if(inventoryVersion==2) {
            val stockItems=items.filter { it.spec.kind !in setOf(Furnishing.Crate,Furnishing.MovingBox) &&
                (if(v13) it.spec.id !in archived else !BathroomLayout.owns(it.spec.id)) }
            require(stockItems.map { it.spec.assetId }.distinct().size==stockItems.size)
        }
        val bathroomVersion=if(v12) header[21].toInt().also {
            require(if(v14) it==0 else it==BathroomLayout.version &&
                ids.containsAll(BathroomLayout.fixtures().map { fixture->fixture.spec.id }))
        } else 0
        val gamingVersion=if(v13) header[22].toInt().also {
            require(it==GamingLayout.version && ids.containsAll(gamingCatalogue().map { spec->spec.id }))
        } else 0
        val studyVersion=if(v14) header[24].toInt().also {
            require(it==StudyLayout.version && ids.containsAll(studyCatalogue().map { spec->spec.id }))
        } else 0
        require(!moving || stock.keys==movingBoxes().map { it.id }.toSet())
        val brightness=if(v7) header[16].toFloat() else .65f
        require(brightness.isFinite() && brightness in 0f..1f)
        val mood=if(legacy) RoomMood.Morning else RoomMood.valueOf(header[5])
        val hour=if(v8) header[17].toFloat() else RoomTime.hour(mood)
        require(hour.isFinite() && hour>=0f && hour<24f)
        RoomGame(items=migrated,crateOpen=header[1].toBooleanStrict(),lampOn=header[2].toBooleanStrict(),
            movingHome=moving,boxStock=stock,indoorBrightness=brightness,
            boxInventoryVersion=inventoryVersion,layoutVersion=if(v10) header[19].toInt() else 1,
            bathroomVersion=bathroomVersion,
            gamingVersion=gamingVersion,studyVersion=studyVersion,archivedIds=archived,
            mood=mood,dayHour=hour,
            guideVisible=if(legacy) true else header[6].toBooleanStrict(),
            nextSerial=serial,additions=additions,achievements=earned,playCounts=counts,toyStates=states,cleaning=cleaning,
            drawerOpen=drawer.coerceIn(0f,1f),roomYaw=yaw%360f,message=if(cleaning!=null && !cleaning.finished)
                "旧屋等待焕新。原来的家具已妥善收好，清洁完会恢复布置。" else "上次布置的小屋已恢复，继续让它变成你的家。")
    }.getOrNull()
}
