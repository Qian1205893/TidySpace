package com.example.tidyspace.pc5853b2a.domain.model

import kotlin.math.sqrt

data class Point3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(other: Point3)=Point3(x+other.x,y+other.y,z+other.z)
    fun distance(other: Point3): Float =
        sqrt((x-other.x)*(x-other.x)+(y-other.y)*(y-other.y)+(z-other.z)*(z-other.z))
}

data class ItemSpec(
    val id: String, val name: String, val color: String,
    val size: Point3, val start: Point3, val slot: Point3,
    val startAngle: Float, val angleRequired: Boolean,
    val surface: SortingSurface = SortingSurface.Table,
)

enum class SortingSurface { Table, Wall }
data class SortingLevel(val id: String,val name: String,val wall: Boolean,val items: List<ItemSpec>)
data class ItemPose(val spec: ItemSpec, val position: Point3 = spec.start,
                    val angle: Float = spec.startAngle, val placed: Boolean = false,
                    val surface: SortingSurface = SortingSurface.Table,
                    val rotation: Rotation? = null)

data class SortingGame(
    val items: List<ItemPose> = emptyList(), val selectedId: String? = null,
    val hintId: String? = null, val mistakes: Int = 0, val rounds: Int = 1,
    val recenterRevision: Int = 0,
    val levelIndex: Int = 0, val levelCount: Int = 5, val levelName: String = "文具桌面",
    val wall: Boolean = false, val dragging: Boolean = false, val grabOrigin: ItemPose? = null,
    val inputName: String = "",
    val message: String = "捏住小物移动，转动手腕或双手转向；松手放下，捏住底座查看另一面。",
) {
    val placedCount get() = items.count { it.placed }
    val completed get() = items.isNotEmpty() && placedCount == items.size
    val selected get() = items.find { it.spec.id == selectedId }
}

sealed interface SortingAction {
    data class Pick(val id: String): SortingAction
    data class BeginGrab(val id: String,val input: String): SortingAction
    data class Move(val delta: Point3): SortingAction
    data class Turn(val degrees: Float): SortingAction
    data class RotateSpatial(val rotation: Rotation): SortingAction
    data object EndGrab: SortingAction
    data object CancelGrab: SortingAction
    data object NextLevel: SortingAction
    data object PreviousLevel: SortingAction
    data class Place(val slotId: String, val position: Point3? = null): SortingAction
    data object Rotate: SortingAction
    data object Release: SortingAction
    data object Hint: SortingAction
    data object Restart: SortingAction
    data object Recenter: SortingAction
}
