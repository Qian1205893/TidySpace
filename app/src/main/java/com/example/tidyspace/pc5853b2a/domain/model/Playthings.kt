package com.example.tidyspace.pc5853b2a.domain.model

data class ToyState(val open: Boolean=false,val bubbles: Int=0,val rounds: Int=0,val detent: Int=0,val roundReady: Boolean=true)
data class ToyGesture(val elapsed: Float=0f,val turn: Float=0f,val direction: Int=0,
    val swing: Float=0f,val lastPulse: Float=-1f,val pulses: Int=0,val compression: Float=0f,
    val slide: Float=0f,val tilt: Float=0f)

val Furnishing.playable get()=this in setOf(Furnishing.Woodfish,Furnishing.MusicBox,Furnishing.SingingBowl,
    Furnishing.Rattle,Furnishing.Pokeball,Furnishing.Mochi,Furnishing.Jelly,Furnishing.PopBoard,
    Furnishing.Spinner,Furnishing.Tumbler,Furnishing.Ratchet,Furnishing.MagneticSlider,
    Furnishing.ToggleBox,Furnishing.Abacus,Furnishing.DeskBell,Furnishing.Hourglass,Furnishing.Windmill)
fun playId(target: String?)=target?.takeIf { it.startsWith("@play:") }?.removePrefix("@play:")?.substringBefore(':')
fun toyHint(kind: Furnishing): String=when(kind) {
    Furnishing.Rattle->"抓住木柄，左右往复摇转；珠子随节奏击鼓"
    Furnishing.Pokeball->"轻捏正面按钮，打开星光；再捏一次合上"
    Furnishing.Mochi->"捏住团子，慢慢压扁；松手看它弹回来"
    Furnishing.Jelly->"捏住果冻，双手靠近压得更扁；松开慢慢回弹"
    Furnishing.PopBoard->"逐个捏按六颗彩泡；全按下解锁一轮彩虹"
    Furnishing.Spinner->"捏住叶片拨转，松开看它旋转、慢慢停下"
    Furnishing.Tumbler->"捏住企鹅轻轻推斜，松手看它摇摆回正"
    Furnishing.Ratchet->"捏住金属旋钮转手腕，每过一档咔哒一声"
    Furnishing.Woodfish->"轻敲上方小槌，松手「功德 +1」"
    Furnishing.MusicBox->"捏住侧面曲柄画圈，转出自己的旋律"
    Furnishing.SingingBowl->"轻敲钵沿短槌，听一声悠长余韵"
    Furnishing.MagneticSlider->"捏住滑块左右推，每过一格磁吸咔哒"
    Furnishing.ToggleBox->"逐个拨动三枚开关，感受清脆回弹"
    Furnishing.Abacus->"捏按或推拨木珠，让珠子来回碰撞"
    Furnishing.DeskBell->"按住顶端压下铃钮，松手叮一声"
    Furnishing.Hourglass->"捏住沙漏翻转，或轻点一次，看细沙缓缓落下"
    Furnishing.Windmill->"捏住侧面曲柄转动，风车会跟着转；松手慢慢停"
    else->"捏住拿起，转动手腕调整，松手放下"
}
