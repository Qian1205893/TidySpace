package com.example.tidyspace.pc5853b2a.domain.model

enum class Furnishing { Desk, Chair, Sofa, CoffeeTable, Shelf, Rug, Lamp, Crate, Plant, Picture, Book, Mug, Cushion, Woodfish, MusicBox, SingingBowl, Rattle, Pokeball, Mochi, Jelly, PopBoard, Spinner, Tumbler, Ratchet, MovingBox, ShoeCabinet, CoatRack, SideTable, TableLamp, WallClock, Blanket, Ottoman, GardenBench,
    Bed, Cabinet, DiningTable, DiningChair, Bookcase, Appliance, Sanitary, WallMirror, Decor, TallPlant,
    MagneticSlider, ToggleBox, Abacus, DeskBell, Hourglass, Windmill,
    GamingDesk, GamingChair, GamingDevice, GamingMat, GamingStorage, GamingLight,
    StudyDesk, StudyChair, StudyBookcase, StudyArmchair, StudyStool, StudySide, StudyLight,
    StudyDecor, StudyPlant, StudyRug }
data class RoomSpec(val id: String,val name: String,val kind: Furnishing,val size: Point3,
                    val start: Point3,val packed: Boolean=false,val assetId: String=id)
data class RoomItem(val spec: RoomSpec,val position: Point3=spec.start,val rotation: Rotation=Rotation(),
                    val support: String?=null,val packed: Boolean=spec.packed)
data class RoomGoal(val title: String,val complete: Boolean,val hint: String)
data class RoomMechanisms(val roomYaw: Float,val drawerOpen: Float)
enum class RoomMood(val label: String) { Morning("晨光"), Sunset("夕照"), Evening("夜读") }
data class RoomTask(val id: String,val title: String,val hint: String,val done: Int,val total: Int,val xp: Int=100) {
    val complete get()=done>=total
}
data class GuideStep(val id: String,val title: String,val instructions: List<String>,
                     val target: String?,val destination: Point3?,val complete: Boolean,val xp: Int=30)
data class RoomGame(
    val items: List<RoomItem> = roomCatalogue().map {
        RoomItem(it,support=if(it.packed) "crate" else if(it.kind in listOf(Furnishing.Shelf,Furnishing.Picture)) "@back" else null)
    },
    val selectedId: String?=null,val origin: List<RoomItem>?=null,
    val mechanismOrigin: RoomMechanisms?=null,
    val crateOpen: Boolean=false,val lampOn: Boolean=false,val drawerOpen: Float=0f,
    val roomYaw: Float=0f,val actionTravel: Float=0f,
    val message: String="捏住箱盖向上掀开；家具和小物都能直接拿起、转动、放下。",
    val saveError: Boolean=false,
    val focusId: String?=null,
    val mood: RoomMood=RoomMood.Morning,
    val guideVisible: Boolean=true,
    val nextSerial: Int=1,
    val additions: Int=0,
    val achievements: Set<String> = emptySet(),
    val celebration: Int=0,
    val canUndo: Boolean=false,val canRedo: Boolean=false,
    val playCounts: Map<String,Long> = emptyMap(),
    val playEvent: PlayEvent?=null,
    val playSequence: Long=0,
    val gestureDistance: Float=0f,
    val crankRadians: Float=0f,
    val toyStates: Map<String,ToyState> = emptyMap(),
    val toyGesture: ToyGesture=ToyGesture(),
    val cleaning: CleaningState?=null,
    val movingHome: Boolean=false,
    val boxStock: Map<String,List<String>> = emptyMap(),
    val previewBox: String?=null,
    val extractionBox: String?=null,
    val indoorBrightness: Float=.65f,
    val dayHour: Float=when(mood) { RoomMood.Morning->9f;RoomMood.Sunset->17f;RoomMood.Evening->21f },
    val journalOpen: Boolean=false,
    val ritual: HouseGesture?=null,
    val boxInventoryVersion: Int=0,
    val layoutVersion: Int=1,
    val bathroomVersion: Int=0,
    val gamingVersion: Int=0,
    val studyVersion: Int=0,
    val archivedIds: Set<String> = emptySet(),
    val controlsVisible: Boolean=true,
    val angleFocus: String?=null,
    val gestureTurn: Float=0f,
    val twoHandTurning: Boolean=false,
    val dragFacing: Rotation?=null,
    val cardDrag: CardDrag?=null,
    val cardSequence: Long=0,
) {
    val decorating get()=cleaning==null || cleaning.finished
    /** Guidance belongs to an active interaction; completed actions leave the room clear. */
    val cueVisible get()=saveError || (guideVisible && selectedId!=null)
    val selected get()=items.find { it.spec.id==selectedId }
    fun item(id: String)=items.first { it.spec.id==id }
    val unpackedCount get()=items.count { it.spec.packed && !it.packed }
    val xp: Int get()=achievements.count { it.startsWith("guide-") }*30+achievements.count { !it.startsWith("guide-") }*100
    val rank get()=when { xp>=900->"空间造梦师";xp>=500->"生活设计师";xp>=210->"布置达人";else->"新家探索者" }
    val addedItems get()=items.count { it.spec.id.contains('~') }
    val merit get()=playCounts.filterKeys { it=="woodfish" || it.startsWith("woodfish~") }.values.sum()
    val remainingGoods get()=boxStock.values.sumOf { it.size }
    fun stock(id: String)=boxStock[id].orEmpty()
    val previewId get()=previewBox?.let { stock(it).firstOrNull() }
    fun inventorySpec(id: String)=items.find { it.spec.id==id }?.spec
        ?: availableCatalogue(this).find { it.id==id }
    val previewAsset get()=previewId?.let { inventorySpec(it)?.assetId }
}
data class PlayEvent(val sequence: Long,val id: String,val kind: Furnishing,val count: Long,val feedback: String="")
data class CardDrag(val token: Long,val id: String,val point: Point3?,val facing: Rotation)
sealed interface RoomAction {
    data class CardBegin(val id: String,val token: Long,val facing: Rotation): RoomAction
    data class CardMove(val token: Long,val point: Point3?): RoomAction
    data class CardRelease(val token: Long): RoomAction
    data class CardCancel(val token: Long): RoomAction
    /** Runtime native carton poses, sampled before an interaction; does not commit a save. */
    data class SyncCartonFacing(val rotations: Map<String,Rotation>): RoomAction
    data class Begin(val id: String,val facing: Rotation?=null,val contact: Point3?=null): RoomAction
    data class Transform(val delta: GrabDelta,val facing: Rotation?=null): RoomAction
    data class CleanTick(val seconds: Float): RoomAction
    data object End: RoomAction
    data object Cancel: RoomAction
    data class Add(val assetId: String): RoomAction
    data object Duplicate: RoomAction
    data object Recycle: RoomAction
    data class Mood(val value: RoomMood): RoomAction
    data class Brightness(val value: Float): RoomAction
    data object ToggleGuide: RoomAction
    data object ToggleJournal: RoomAction
    data object ToggleControls: RoomAction
    data class RotatePlaced(val degrees: Float): RoomAction
    data object Undo: RoomAction
    data object Redo: RoomAction
    data object CleanAgain: RoomAction
}
data class HouseGesture(val hour: Float,val brightness: Float,val lampOn: Boolean,
    val point: Point3?,val travel: Float=0f,val turn: Float=0f)
const val ROOM_CAPACITY=96
fun shopCatalogue()=roomCatalogue().filter { it.kind !in setOf(Furnishing.Desk,Furnishing.Lamp,Furnishing.Crate) }+toyCatalogue()+homeCatalogue()+gamingCatalogue()+studyCatalogue()+lifestyleCatalogue()
fun uniqueCatalogue()=roomCatalogue().filter { it.kind!=Furnishing.Crate }+toyCatalogue()+homeCatalogue()+gamingCatalogue()+studyCatalogue()+lifestyleCatalogue()
fun availableCatalogue(s: RoomGame)=uniqueCatalogue().filterNot { s.studyVersion>0 && StudyLayout.retired(it) }
fun movingBoxes()=listOf(
    RoomSpec("box-furniture","家具箱",Furnishing.MovingBox,Point3(.25f,.20f,.23f),Point3(.43f,-.22f,.79f),assetId="movingbox-furniture"),
    RoomSpec("box-light","灯光箱",Furnishing.MovingBox,Point3(.25f,.20f,.23f),Point3(.86f,-.22f,.79f),assetId="movingbox-light"),
    RoomSpec("box-home","生活箱",Furnishing.MovingBox,Point3(.25f,.20f,.23f),Point3(.43f,-.22f,1.10f),assetId="movingbox-home"),
    RoomSpec("box-toys","快乐箱",Furnishing.MovingBox,Point3(.25f,.20f,.23f),Point3(.86f,-.22f,1.10f),assetId="movingbox-toys"),
)
fun homeCatalogue()=listOf(
    RoomSpec("shoecabinet","玄关鞋柜",Furnishing.ShoeCabinet,Point3(.28f,.24f,.146f),Point3(0f,0f,0f)),
    RoomSpec("coatrack","木衣帽架",Furnishing.CoatRack,Point3(.15f,.42f,.15f),Point3(0f,0f,0f)),
    RoomSpec("sidetable","橡木边几",Furnishing.SideTable,Point3(.17f,.22f,.17f),Point3(0f,0f,0f)),
    RoomSpec("tablelamp","陶瓷台灯",Furnishing.TableLamp,Point3(.10f,.18f,.10f),Point3(0f,0f,0f)),
    RoomSpec("wallclock","暖木挂钟",Furnishing.WallClock,Point3(.14f,.14f,.025f),Point3(0f,0f,0f)),
    RoomSpec("blanket","卷叠毛毯",Furnishing.Blanket,Point3(.15f,.055f,.10f),Point3(0f,0f,0f)),
    RoomSpec("ottoman","圆绒脚凳",Furnishing.Ottoman,Point3(.19f,.135f,.19f),Point3(0f,0f,0f)),
    RoomSpec("gardenbench","阳台木长凳",Furnishing.GardenBench,Point3(.34f,.235f,.145f),Point3(0f,0f,0f)),
)+homeExpansion()
fun toyCatalogue()=listOf(
    RoomSpec("woodfish","功德木鱼",Furnishing.Woodfish,Point3(.15f,.10f,.12f),Point3(0f,0f,0f)),
    RoomSpec("musicbox","手摇音乐盒",Furnishing.MusicBox,Point3(.18f,.115f,.11f),Point3(0f,0f,0f)),
    RoomSpec("bowl","清音钵",Furnishing.SingingBowl,Point3(.12f,.07f,.12f),Point3(0f,0f,0f)),
    RoomSpec("rattle","拨浪鼓",Furnishing.Rattle,Point3(.18f,.22f,.06f),Point3(0f,0f,0f)),
    RoomSpec("pokeball","精灵球",Furnishing.Pokeball,Point3(.13f,.14f,.14f),Point3(0f,0f,0f)),
    RoomSpec("mochi","团子捏捏",Furnishing.Mochi,Point3(.13f,.10f,.13f),Point3(0f,0f,0f)),
    RoomSpec("jelly","果冻方块",Furnishing.Jelly,Point3(.12f,.105f,.12f),Point3(0f,0f,0f)),
    RoomSpec("popboard","彩虹按泡",Furnishing.PopBoard,Point3(.20f,.04f,.17f),Point3(0f,0f,0f)),
    RoomSpec("spinner","指尖陀螺",Furnishing.Spinner,Point3(.15f,.035f,.15f),Point3(0f,0f,0f)),
    RoomSpec("tumbler","企鹅不倒翁",Furnishing.Tumbler,Point3(.11f,.16f,.11f),Point3(0f,0f,0f)),
    RoomSpec("ratchet","咔哒旋钮",Furnishing.Ratchet,Point3(.115f,.06f,.115f),Point3(0f,0f,0f)),
)+handheldExpansion()
fun roomCatalogue()=listOf(
    RoomSpec("desk","橡木书桌",Furnishing.Desk,Point3(.48f,.34f,.25f),Point3(-.36f,-.15f,-.43f)),
    RoomSpec("chair","弧背椅",Furnishing.Chair,Point3(.18f,.29f,.18f),Point3(-.34f,-.175f,.07f)),
    RoomSpec("sofa","棉麻沙发",Furnishing.Sofa,Point3(.43f,.27f,.24f),Point3(.34f,-.185f,-.43f)),
    RoomSpec("coffee","圆角矮桌",Furnishing.CoffeeTable,Point3(.31f,.15f,.20f),Point3(.10f,-.245f,.30f)),
    RoomSpec("shelf","开放书架",Furnishing.Shelf,Point3(.27f,.25f,.11f),Point3(.47f,.17f,-.8635f)),
    RoomSpec("rug","编织地毯",Furnishing.Rug,Point3(.63f,.008f,.52f),Point3(.30f,-.316f,-.29f)),
    RoomSpec("lamp","暖光落地灯",Furnishing.Lamp,Point3(.13f,.54f,.13f),Point3(-.58f,-.05f,.22f)),
    RoomSpec("crate","搬家木箱",Furnishing.Crate,Point3(.25f,.20f,.23f),Point3(.49f,-.22f,.24f)),
    RoomSpec("plant","绿叶盆栽",Furnishing.Plant,Point3(.095f,.16f,.095f),Point3(.49f,-.215f,.24f),true),
    RoomSpec("picture","山间日落",Furnishing.Picture,Point3(.22f,.17f,.015f),Point3(.065f,.28f,-.9125f)),
    RoomSpec("book","旅行手账",Furnishing.Book,Point3(.11f,.018f,.075f),Point3(.49f,-.282f,.24f),true),
    RoomSpec("mug","陶瓷茶杯",Furnishing.Mug,Point3(.064f,.07f,.05f),Point3(.55f,-.255f,.28f),true),
    RoomSpec("cushion","柔软靠垫",Furnishing.Cushion,Point3(.12f,.042f,.105f),Point3(.44f,-.27f,.22f),true),
)
