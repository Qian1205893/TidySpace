package com.example.tidyspace.pc5853b2a.ui.sorting.scene

import com.pico.spatial.ui.foundation.gesture.SpatialPointerInfo
import com.example.tidyspace.pc5853b2a.domain.model.*

internal class SortingSpatialInput(scene: SortingScene,send: (SortingAction)->Unit) {
    private var key=""
    private val input=SharedSpatialInput(
        target={ entity -> if(scene.isBase(entity)) "@room" else (scene.action(entity) as? SortingAction.Pick)?.id },
        pose=scene.volume::pose,
        begin={ key=it;if(it!="@room") send(SortingAction.BeginGrab(it,"SpatialPointer")) },
        transform={ if(key=="@room") scene.volume.turn(it.rotation) else {
            send(SortingAction.Move(it.translation));send(SortingAction.RotateSpatial(it.rotation))
        } },
        end={ if(key!="@room") send(SortingAction.EndGrab) },
        cancelled={ if(key!="@room") send(SortingAction.CancelGrab) },
    )
    fun events(events: List<SpatialPointerInfo>)=input.events(events)
    fun cancel()=input.cancel()
    fun resumeSelection(id: String?) { key=id ?: "";input.resumeSelection(id) }
    fun rebaseline()=input.rebaseline()
}
