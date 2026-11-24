package com.example.tidyspace.pc5853b2a.domain.usecase

import com.example.tidyspace.pc5853b2a.data.repository.HomeRepository
import com.example.tidyspace.pc5853b2a.domain.model.*
import java.util.UUID

/** Independent snapshots with a single atomic library transaction. */
class HomeUseCase(
    private val repository: HomeRepository,
    private val clock: ()->Long=System::currentTimeMillis,
    private val identity: ()->String={ "home-${UUID.randomUUID()}" },
) {
    private var current: HomeLibrary?=null
    private var readFailure: Throwable?=null
    var migrationError: Boolean=false
        private set
    private var deleted: SavedHome?=null
    val library get()=checkNotNull(current)
    val undoName get()=deleted?.name

    fun initialize(game: RoomGame,session: ShowroomSession): HomeLibrary {
        if(current!=null) return library
        val loaded=runCatching { repository.load() }
        readFailure=loaded.exceptionOrNull()
        val home=SavedHome("migrated-primary-home",cleanName(session.title).takeUnless {
            it=="我的创意改造"
        } ?: "我的小屋",committed(RoomEntry.prepare(game)),session,savedAt=clock())
        current=RoomEntry.prepare(loaded.getOrNull() ?: HomeLibrary(listOf(home),home.id,
            game.achievements,game.playCounts,game.nextSerial,true))
        if(loaded.isSuccess && loaded.getOrNull()!=current)
            migrationError=runCatching { publish(library) }.isFailure
        return library
    }

    fun activeGame(): RoomGame=withShared(library.active.game,library)
    fun save(game: RoomGame,session: ShowroomSession): HomeLibrary=publish(saved(library,game,session))
    fun saveInteractive(game: RoomGame,session: ShowroomSession,complete: (Result<Unit>)->Unit): HomeLibrary {
        check(readFailure==null) { "已有小屋存档无法读取，原始数据已保留；请先重试读取。" }
        val next=saved(library,game,session)
        // Keep edits available for retry and subsequent interactions while I/O runs.
        current=next
        repository.saveInteractive(next,complete)
        return next
    }
    fun rename(id: String,name: String,game: RoomGame,session: ShowroomSession): HomeLibrary {
        require(validName(name)) { "名称请输入 1–20 个字。" }
        require(library.homes.any { it.id==id })
        val next=saved(library,game,session)
        return publish(next.copy(homes=next.homes.map { if(it.id==id)
            it.copy(name=name.trim(),session=it.session.copy(title=name.trim())) else it }))
    }
    fun switch(id: String,game: RoomGame,session: ShowroomSession): HomeLibrary {
        ready(game)
        val target=library.homes.firstOrNull { it.id==id } ?: error("这间小屋暂时无法打开。")
        val prepared=RoomEntry.prepare(StudyRenovation.prepare(MovingHome.prepare(target.game)))
        val next=saved(library,game,session)
        return publish(next.copy(activeId=id,homes=next.homes.map { if(it.id==id)
            it.copy(game=committed(withShared(prepared,next))) else it }))
    }
    fun create(name: String,template: Showroom?,game: RoomGame,session: ShowroomSession): HomeLibrary {
        ready(game)
        require(validName(name)) { "名称请输入 1–20 个字。" }
        val next=saved(library,game,session)
        var blank=MovingHome.packAll(StudyRenovation.prepare(MovingHome.prepare(RoomGame(),fresh=true))).copy(
                achievements=next.achievements,playCounts=next.playCounts,nextSerial=next.nextSerial,
                lampOn=true,guideVisible=false,toyStates=emptyMap(),roomYaw=game.roomYaw)
        if(template!=null) {
            val result=ShowroomUseCase().apply(blank,template)
            blank=result.game ?: error(result.error ?: "样板间暂时无法创建。")
        }
        val home=SavedHome(identity(),uniqueName(name),committed(blank),
            ShowroomSession(reference=template?.id,title=uniqueName(name)),
            clock(),sourceTemplateId=template?.id,sourceTemplateVersion=template?.version)
        return publish(next.copy(homes=next.homes+home,activeId=home.id))
    }
    fun duplicate(id: String,game: RoomGame,session: ShowroomSession): HomeLibrary {
        ready(game)
        val next=saved(library,game,session)
        val source=next.homes.firstOrNull { it.id==id } ?: error("找不到这间小屋。")
        val suffix=" · 副本"
        val name=uniqueName(source.name.codePoints().toArray().take(20-suffix.codePointCount(0,suffix.length))
            .toIntArray().let { String(it,0,it.size) }+suffix)
        val copy=source.copy(id=identity(),name=name,game=withShared(source.game,next),
            session=source.session.copy(title=name,undo=null,tutorial=null,step=0),savedAt=clock(),revision=1)
        return publish(next.copy(homes=next.homes+copy,activeId=copy.id))
    }
    fun delete(id: String,game: RoomGame,session: ShowroomSession): HomeLibrary {
        require(id!=library.activeId) { "请先进入另一间小屋，再删除这间。" }
        require(library.homes.size>1) { "至少保留一间自己的小屋。" }
        val home=library.homes.firstOrNull { it.id==id } ?: error("找不到这间小屋。")
        val saved=saved(library,game,session)
        val next=publish(saved.copy(homes=saved.homes.filterNot { it.id==id }))
        deleted=home
        return next
    }
    fun undoDelete(game: RoomGame,session: ShowroomSession): HomeLibrary {
        val home=deleted ?: return library
        val next=saved(library,game,session)
        val result=publish(next.copy(homes=next.homes+home))
        deleted=null
        return result
    }
    private fun publish(next: HomeLibrary): HomeLibrary {
        check(readFailure==null) { "已有小屋存档无法读取，原始数据已保留；请先重试读取。" }
        repository.save(next)
        current=next
        migrationError=false
        return next
    }
    fun retryRead(): HomeLibrary {
        if(readFailure==null) return library
        val loaded=repository.load() ?: error("已有小屋存档暂时无法读取。")
        current=RoomEntry.prepare(loaded)
        readFailure=null
        if(current!=loaded) migrationError=runCatching { publish(library) }.isFailure
        return library
    }
    val loadError get()=readFailure!=null
    private fun saved(l: HomeLibrary,game: RoomGame,session: ShowroomSession): HomeLibrary {
        val counts=(l.playCounts.keys+game.playCounts.keys).associateWith {
            maxOf(l.playCounts[it] ?: 0,game.playCounts[it] ?: 0)
        }
        val shared=l.copy(achievements=l.achievements+game.achievements,playCounts=counts,
            nextSerial=maxOf(l.nextSerial,game.nextSerial),tutorialComplete=l.tutorialComplete || game.decorating)
        return shared.copy(homes=l.homes.map { if(it.id==l.activeId)
            it.copy(game=committed(withShared(game,shared)),session=session,savedAt=clock(),revision=it.revision+1) else it })
    }
    private fun withShared(game: RoomGame,l: HomeLibrary)=game.copy(
        achievements=l.achievements+game.achievements,playCounts=l.playCounts,nextSerial=maxOf(l.nextSerial,game.nextSerial))
    private fun ready(game: RoomGame) {
        require(game.selectedId==null && game.cardDrag==null) { "请先放下手中的物品。" }
    }
    private fun committed(game: RoomGame): RoomGame {
        val ready=RoomEntry.prepare(game)
        val stable=if(ready.selectedId!=null || ready.cardDrag!=null) RoomUseCase().reduce(ready,RoomAction.Cancel) else ready
        return stable.copy(
        selectedId=null,origin=null,mechanismOrigin=null,ritual=null,cardDrag=null,
        focusId=null,angleFocus=null,previewBox=null,extractionBox=null,dragFacing=null,
        toyGesture=ToyGesture(),twoHandTurning=false,canUndo=false,canRedo=false,celebration=0,saveError=false)
    }
    fun uniqueName(value: String): String {
        val base=cleanName(value)
        if(library.homes.none { it.name==base }) return base
        var serial=2
        while(true) {
            val suffix=" ${serial++}"
            val points=base.codePoints().toArray().take(20-suffix.length).toIntArray()
            val name=String(points,0,points.size)+suffix
            if(library.homes.none { it.name==name }) return name
        }
    }
    companion object {
        fun validName(value: String)=value.trim().codePointCount(0,value.trim().length) in 1..20
        fun cleanName(value: String)=value.trim().codePoints().toArray().take(20).toIntArray().let {
            String(it,0,it.size)
        }.ifBlank { "我的小屋" }
    }
}
