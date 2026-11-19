package com.example.tidyspace.pc5853b2a.domain.model

enum class CleanTool(val title: String,val rack: Point3) {
    Vacuum("吸尘器",RoomLayout.point(Point3(-.65f,-.30f,.48f))),
    Broom("扫把",RoomLayout.point(Point3(-.39f,-.30f,.48f))),
    Brush("墙刷",RoomLayout.point(Point3(-.13f,-.17f,.48f))),
    Mop("湿拖把",Point3(.18f,-.302f,.69f))
}
enum class MessKind { Dust, Debris, Wall, Trash, Spill }
data class Mess(val id: String,val kind: MessKind,val position: Point3,val size: Point3)
data class CleanContact(val tool: CleanTool,val point: Point3,val direction: Point3,val messId: String)
data class CleaningState(
    val progress: Map<String,Float> = emptyMap(),
    val finished: Boolean=false,
    val tool: CleanTool?=null,
    val heldTrash: String?=null,
    val position: Point3=Point3(0f,0f,0f),
    val rotation: Rotation=Rotation(),
    val origin: Map<String,Float>?=null,
    val sequence: Long=0,
    val feedback: String="",
    val workTiles: Map<String,List<Float>> = emptyMap(),
    val originTiles: Map<String,List<Float>>?=null,
    val contact: CleanContact?=null,
    val wetTiles: Map<String,List<Float>> = emptyMap(),
    val originWet: Map<String,List<Float>>?=null,
    val binPosition: Point3=CLEAN_BIN,
    val binRotation: Rotation=Rotation(),
    val heldBin: Boolean=false,
    val binOrigin: Point3?=null,
    val binTurnOrigin: Rotation?=null,
    val binReady: Boolean=false,
    val previousTrash: Point3?=null,
    val disposedId: String?=null,
    val disposedFrom: Point3?=null,
    val disposalSequence: Long=0
) {
    fun rawAmount(id: String)=(progress[id] ?: if(finished) 1f else 0f).coerceIn(0f,1f)
    fun amount(id: String)=CleaningVisibility.amount(this,id)
    val count get()=cleaningMess().count { amount(it.id)>=1f }
    val percent get()=(cleaningMess().sumOf { amount(it.id).toDouble() }/cleaningMess().size*100).toInt()
    val spotless get()=cleaningMess().all { amount(it.id)>=1f }
    /** Canonical finished data remains readable by older v11 clients. */
    fun completed()=copy(finished=true,progress=cleaningMess().associate { it.id to 1f },
        workTiles=workTiles.mapValues { List(CleaningGrid.COUNT) { 1f } })
}
val CLEAN_BIN=Point3(1.276f,-.025f,.609f)
// Broad, irregular areas: accumulated dust by the window, a spill and wall soot.
// All centres are on real surfaces, clear of the window apertures and fireplace.
fun legacyCleaningMess(): List<Mess> = listOf(
    Mess("dust-0",MessKind.Dust,Point3(-.49f,-.3194f,-.59f),Point3(.72f,.007f,.45f)),
    Mess("dust-1",MessKind.Dust,Point3(.43f,-.3194f,-.12f),Point3(.78f,.007f,.46f)),
    Mess("debris-0",MessKind.Debris,Point3(-.24f,-.305f,-.08f),Point3(.53f,.025f,.36f)),
    Mess("debris-1",MessKind.Debris,Point3(.57f,-.305f,-.66f),Point3(.64f,.025f,.31f)),
    Mess("wall-0",MessKind.Wall,Point3(-.48f,-.135f,-.9513f),Point3(.72f,.30f,.005f)),
    Mess("wall-1",MessKind.Wall,Point3(.55f,.25f,-.9513f),Point3(.74f,.53f,.005f)),
    Mess("trash-0",MessKind.Trash,Point3(-.68f,-.265f,-.10f),Point3(.12f,.11f,.10f)),
    Mess("trash-1",MessKind.Trash,Point3(.12f,-.265f,-.54f),Point3(.09f,.11f,.09f)),
    Mess("trash-2",MessKind.Trash,Point3(.66f,-.265f,.18f),Point3(.12f,.11f,.10f)),
).map { it.copy(position=RoomLayout.point(it.position),
    size=if(it.kind==MessKind.Wall) Point3(it.size.x*RoomLayout.scale,it.size.y,it.size.z)
        else if(it.kind==MessKind.Trash) it.size else RoomLayout.footprint(it.size)) }

fun expandedCleaningMess(): List<Mess> = listOf(
    Mess("dust-2",MessKind.Dust,Point3(-.77f,-.3194f,1.32f),Point3(.58f,.007f,.36f)),
    Mess("dust-3",MessKind.Dust,Point3(1f,-.3194f,2.11f),Point3(.55f,.007f,.40f)),
    Mess("spill-0",MessKind.Spill,Point3(-.92f,-.319f,1.93f),Point3(.46f,.003f,.33f)),
    Mess("spill-1",MessKind.Spill,Point3(.46f,-.319f,1.20f),Point3(.42f,.003f,.55f)),
    Mess("debris-2",MessKind.Debris,Point3(.39f,-.305f,2.12f),Point3(.42f,.025f,.26f)),
    Mess("debris-3",MessKind.Debris,Point3(-.61f,-.305f,1.75f),Point3(.44f,.025f,.30f)),
    Mess("debris-4",MessKind.Debris,Point3(1.855f,-.305f,.35f),Point3(.29f,.025f,.44f)),
    Mess("trash-3",MessKind.Trash,Point3(-1.12f,-.26f,1.25f),Point3(.095f,.12f,.095f)),
    Mess("trash-4",MessKind.Trash,Point3(1.33f,-.27f,2.26f),Point3(.08f,.10f,.08f)),
    Mess("trash-5",MessKind.Trash,Point3(-.42f,-.275f,1.92f),Point3(.12f,.08f,.10f)),
    Mess("trash-6",MessKind.Trash,Point3(1.865f,-.27f,-.40f),Point3(.08f,.10f,.08f))
)
fun cleaningMess()=legacyCleaningMess()+expandedCleaningMess()
fun Mess.pieceCount()=if(id in setOf("debris-0","debris-1")) 28 else 12
