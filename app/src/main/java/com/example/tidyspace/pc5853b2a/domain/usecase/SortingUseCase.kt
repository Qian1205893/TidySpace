package com.example.tidyspace.pc5853b2a.domain.usecase

import com.example.tidyspace.pc5853b2a.data.repository.SortingRepository
import com.example.tidyspace.pc5853b2a.domain.model.*
import kotlin.math.*

class SortingUseCase(private val repository: SortingRepository) {
    fun initial(index: Int = 0): SortingGame {
        val levels=repository.levels()
        val i=if(levels.isEmpty()) 0 else Math.floorMod(index,levels.size)
        val level=levels.getOrNull(i) ?: SortingLevel("empty","等待小物",false,emptyList())
        return SortingGame(items=level.items.map {
            val pose=ItemPose(it)
            pose.copy(position=tablePosition(pose,it.start,level.wall))
        },levelIndex=i,levelCount=levels.size,levelName=level.name,wall=level.wall)
    }

    fun reduce(state: SortingGame, action: SortingAction): SortingGame = when(action) {
        is SortingAction.Pick -> pick(state,action.id,false,"")
        is SortingAction.BeginGrab -> pick(state,action.id,true,action.input)
        is SortingAction.Move -> state.selected?.let { item ->
            if(!finite(action.delta)) state else {
                val pos=item.position+action.delta
                val surface=if(state.wall && pos.y>.10f && pos.z<-.10f)
                    SortingSurface.Wall else SortingSurface.Table
                updateSelected(state,item.copy(position=pos,surface=surface,placed=false))
            }
        } ?: state
        is SortingAction.Turn -> turn(state,action.degrees)
        is SortingAction.RotateSpatial -> state.selected?.let { item ->
            if(!action.rotation.finite) state else {
                val base=if(item.surface==SortingSurface.Wall) Rotation.axis(Point3(1f,0f,0f),90f) else Rotation()
                val q=action.rotation*(item.rotation ?: (base*Rotation.yaw(item.angle)))
                updateSelected(state,item.copy(rotation=q,angle=((base.inverse()*q).yaw()+360f)%360f))
            }
        } ?: state
        SortingAction.Rotate -> turn(state,90f)
        SortingAction.EndGrab, SortingAction.Release -> release(state)
        SortingAction.CancelGrab -> cancel(state)
        SortingAction.Hint -> {
            val id=state.selectedId ?: state.items.firstOrNull { !it.placed }?.spec?.id
            state.copy(hintId=id,message=if(id==null) "已经全部整理好了。" else
                "发亮的是它的位置；沿桌面或墙面转到长边平行。")
        }
        SortingAction.Recenter -> cancel(state).copy(recenterRevision=state.recenterRevision+1,
            message="收纳场景已回到你的前方。")
        SortingAction.Restart -> initial(state.levelIndex).copy(rounds=state.rounds+1,
            recenterRevision=state.recenterRevision)
        SortingAction.NextLevel -> initial(state.levelIndex+1).copy(
            recenterRevision=state.recenterRevision)
        SortingAction.PreviousLevel -> initial(state.levelIndex-1).copy(
            recenterRevision=state.recenterRevision)
        is SortingAction.Place -> place(state,action)
    }

    private fun pick(state: SortingGame,id: String,dragging: Boolean,input: String): SortingGame {
        // A second pointer on the same item must not overwrite the cancellation snapshot.
        if(state.selectedId==id && state.grabOrigin!=null)
            return state.copy(dragging=dragging || state.dragging,inputName=input.ifEmpty { state.inputName })
        val safe=cancel(state)
        val item=safe.items.find { it.spec.id==id } ?: return state
        return safe.copy(selectedId=id,grabOrigin=item,dragging=dragging,hintId=null,inputName=input,
            items=safe.items.map {
                if(it.spec.id==id) it.copy(placed=false,position=it.position+if(it.surface==SortingSurface.Wall)
                    Point3(0f,0f,.04f) else Point3(0f,.055f,0f)) else it
            },message="拿起了${item.spec.name} · 移动、转向，松手自己摆")
    }

    private fun turn(state: SortingGame,degrees: Float): SortingGame =
        if(!degrees.isFinite()) state else state.selected?.let {
            updateSelected(state,it.copy(angle=((it.angle+degrees)%360f+360f)%360f))
        } ?: state

    private fun updateSelected(state: SortingGame,item: ItemPose)=state.copy(
        items=state.items.map { if(it.spec.id==item.spec.id) item else it })

    private fun fits(item: ItemPose)=!item.spec.angleRequired ||
        min(abs(item.angle%180f),abs(180f-item.angle%180f))<=22.5f

    private fun place(state: SortingGame,action: SortingAction.Place): SortingGame {
        val item=state.selected ?: return state.copy(message="先点选要整理的小物。")
        if(item.spec.id!=action.slotId) return state.copy(mistakes=state.mistakes+1,
            message="这里不是${item.spec.name}的位置。")
        if((action.position ?: item.spec.slot).distance(item.spec.slot)>.065f)
            return state.copy(message="再靠近格位一些。")
        if(!fits(item)) return state.copy(message="朝向还不合适，转到长边平行。")
        return settle(state,item.copy(position=item.spec.slot,angle=0f,placed=true,surface=item.spec.surface,rotation=null),
            "${item.spec.name}归位了。")
    }

    private fun release(state: SortingGame): SortingGame {
        val item=state.selected ?: return state
        // Snap uses the full 3D distance before projecting to a support plane.
        if(item.position.distance(item.spec.slot)<=.065f && fits(item))
            return place(state,SortingAction.Place(item.spec.id,item.position))
        val surface=if(state.wall && item.position.y>.10f && item.position.z<-.10f)
            SortingSurface.Wall else SortingSurface.Table
        val (hx,hz)=extents(item)
        val candidate=if(surface==SortingSurface.Wall) item.copy(
            surface=surface,position=Point3(item.position.x.coerceIn(-.44f+hx,.44f-hx),
                item.position.y.coerceIn(.02f+hz,.54f-hz),
                -.20f+item.spec.size.y/2),placed=false)
        else item.copy(surface=surface,position=tablePosition(item,item.position,state.wall),placed=false)
        if(overlaps(state,candidate)) return cancel(state).copy(
            message="这里已有物件，已放回拿起前的位置。")
        return settle(state,candidate,"${item.spec.name}放下了，想怎么摆都可以。")
    }

    private fun settle(state: SortingGame,item: ItemPose,message: String): SortingGame {
        val next=updateSelected(state,item.copy(rotation=null)).copy(selectedId=null,grabOrigin=null,
            dragging=false,hintId=null,message=message)
        return if(next.completed) next.copy(message="每件小物，都找到家了。") else next
    }

    private fun cancel(state: SortingGame): SortingGame {
        val origin=state.grabOrigin
        val restored=if(origin==null) state else updateSelected(state,origin)
        return restored.copy(selectedId=null,grabOrigin=null,dragging=false,hintId=null)
    }
    private fun extents(item: ItemPose): Pair<Float,Float> {
        val angle=Math.toRadians(item.angle.toDouble())
        val c=abs(cos(angle)).toFloat();val s=abs(sin(angle)).toFloat()
        return (item.spec.size.x*c+item.spec.size.z*s)/2 to
            (item.spec.size.x*s+item.spec.size.z*c)/2
    }
    private fun tablePosition(item: ItemPose,p: Point3,wall: Boolean): Point3 {
        val (hx,hz)=extents(item)
        val z=p.z.coerceIn(-.217f+hz,.217f-hz)
        // Rear receiving plate is 10 mm above the source tray; rest on its top.
        val support=if(!wall && z-hz<0f) .0275f else .0175f
        return Point3(p.x.coerceIn(-.435f+hx,.435f-hx),support+item.spec.size.y/2,z)
    }
    private fun overlaps(state: SortingGame,item: ItemPose): Boolean {
        val (hx,hz)=extents(item)
        return state.items.any {
            if(it.spec.id==item.spec.id || it.surface!=item.surface) false
            else {
                val (ox,oz)=extents(it)
                abs(it.position.x-item.position.x)<hx+ox+.003f &&
                    abs(if(item.surface==SortingSurface.Wall) it.position.y-item.position.y
                        else it.position.z-item.position.z)<hz+oz+.003f
            }
        }
    }
    private fun finite(p: Point3)=p.x.isFinite() && p.y.isFinite() && p.z.isFinite()
}
