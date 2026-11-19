package com.example.tidyspace.pc5853b2a.ui.sorting.scene

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import com.example.tidyspace.pc5853b2a.domain.model.ItemSpec

/** Simplified real 3D archetypes, used only after the documented Editor recovery failure. */
suspend fun buildSortingObject(parent: Entity, item: ItemSpec, r: SceneResources, ghost: Boolean = false) {
    suspend fun box(x:Float,y:Float,z:Float,token:String,px:Float=0f,py:Float=0f,pz:Float=0f) =
        r.part(parent,r.box(Vector3(x,y,z)),if(ghost) "trayEdge" else token,Vector3(px,py,pz))
    val w=item.size.x;val h=item.size.y;val d=item.size.z
    suspend fun b(x:Float,y:Float,z:Float,token:String=item.color,
                  px:Float=0f,py:Float=0f,pz:Float=0f)=box(w*x,h*y,d*z,token,w*px,h*py,d*pz)
    when(item.id) {
        "notebook" -> {
            box(.144f,.020f,.100f,"paperCream")
            box(.15f,.0025f,.105f,item.color,py=.01125f)
            box(.15f,.0025f,.105f,item.color,py=-.01125f)
            box(.004f,.025f,.105f,item.color,px=-.073f)
            box(.045f,.0008f,.022f,"paperCream",px=.026f,py=.0129f,pz=-.019f)
        }
        "pencil" -> {
            box(.15f,.015f,.015f,"goldObject",px=-.015f)
            val tip = r.part(parent,r.cone(.030f,.0075f),if(ghost) "trayCavity" else "paperCream",Vector3(.075f,0f,0f))
            tip.components[TransformComponent::class.java]!!.eulerAngles = EulerAngles(roll=-90f)
            val lead = r.part(parent,r.cone(.008f,.0023f),if(ghost) "trayCavity" else "inkObject",Vector3(.086f,0f,0f))
            lead.components[TransformComponent::class.java]!!.eulerAngles = EulerAngles(roll=-90f)
            box(.014f,.0151f,.0151f,"coralObject",px=-.082f)
        }
        "eraser" -> {
            box(.065f,.03f,.042f,"paperCream")
            box(.032f,.0304f,.0424f,"skyObject",px=-.008f)
        }
        "tape" -> {
            r.part(parent,r.ring(.045f,.023f,.035f),if(ghost) "trayCavity" else "mintObject")
            r.part(parent,r.ring(.023f,.020f,.034f),if(ghost) "trayCavity" else "paperCream")
        }
        "key" -> {
            r.part(parent,r.ring(.0225f,.012f,.014f),if(ghost) "trayCavity" else "goldObject",Vector3(-.03f,0f,0f))
            box(.065f,.014f,.011f,"goldObject",px=.02f)
            box(.009f,.014f,.022f,"goldObject",px=.043f,pz=.0055f)
            box(.008f,.014f,.019f,"goldObject",px=.026f,pz=.004f)
        }
        "clip" -> {
            box(.07f,.022f,.04f,"skyObject",py=-.0065f)
            for(z in listOf(-.0235f,.0235f)) {
                box(.043f,.003f,.003f,"paperCream",py=.016f,pz=z)
                for(x in listOf(-.02f,.02f))
                    box(.003f,.018f,.003f,"paperCream",px=x,py=.0085f,pz=z)
            }
        }
        "bottle" -> {
            b(1f,.74f,1f,py=-.13f)
            b(.32f,.15f,.40f,"goldObject",py=.315f)
            b(.40f,.11f,.48f,"goldObject",py=.445f)
            b(.55f,.28f,.02f,"paperCream",py=-.09f,pz=.501f)
        }
        "jar" -> {
            r.part(parent,r.ring(w/2,w*.36f,h*.82f),
                if(ghost) "trayEdge" else item.color,Vector3(0f,-h*.09f,0f))
            b(.98f,.18f,.98f,"paperCream",py=.41f)
            b(.60f,.02f,.60f,item.color,py=.51f)
        }
        "brush" -> {
            b(.56f,.40f,.32f,px=-.22f,py=-.30f)
            b(.44f,.45f,1f,px=.28f,py=-.275f)
            for(i in 0..5) b(.025f,.55f,.92f,"paperCream",
                px=.09f+i*.07f,py=.225f)
        }
        "lipstick" -> {
            b(.65f,1f,1f,"goldObject",px=-.175f)
            b(.35f,.74f,.74f,"coralObject",px=.325f)
        }
        "mirror","board" -> {
            b(1f,1f,1f)
            b(.86f,.05f,.84f,if(item.id=="mirror") "skyObject" else "trayOak",py=.525f)
            if(item.id=="board") b(.04f,.04f,.70f,"trayEdge",px=-.42f,py=.55f)
        }
        "pouch" -> {
            b(1f,.85f,1f,py=-.075f)
            b(.80f,.10f,.08f,"goldObject",py=.40f)
            b(.18f,.05f,.14f,"paperCream",px=.30f,py=.475f)
        }
        "mug" -> {
            r.part(parent,r.ring(d/2,d*.34f,h),if(ghost) "trayEdge" else item.color,Vector3(-w*.125f,0f,0f))
            b(.35f,.075f,.80f,"paperCream",px=.325f,py=.24f)
            b(.35f,.075f,.80f,"paperCream",px=.325f,py=-.24f)
            b(.06f,.55f,.075f,"paperCream",px=.47f)
            b(.60f,.05f,.60f,py=-.475f,px=-.125f)
        }
        "spoon","spatula","whisk" -> {
            b(.66f,.70f,.22f,px=-.17f,py=-.15f)
            if(item.id=="whisk") {
                for(z in listOf(-.40f,.40f))
                    b(.34f,.10f,.05f,"goldObject",px=.33f,pz=z)
                for(x in listOf(.18f,.48f))
                    b(.025f,1f,.80f,"goldObject",px=x)
            } else {
                b(.34f,1f,1f,px=.33f)
                if(item.id=="spatula") for(z in listOf(-.27f,0f,.27f))
                    b(.20f,.04f,.055f,"inkObject",px=.34f,py=.52f,pz=z)
            }
        }
        "hammer" -> {
            b(.83f,.65f,.20f,px=-.085f,py=-.175f)
            b(.17f,1f,1f,"inkObject",px=.415f)
        }
        "wrench" -> {
            b(.65f,1f,.25f,px=-.13f)
            for(z in listOf(-.37f,.37f)) b(.35f,1f,.26f,px=.325f,pz=z)
            b(.12f,1f,.48f,px=.21f)
        }
        "screwdriver","umbrella" -> {
            b(.62f,.36f,.36f,if(item.id=="screwdriver") "paperCream" else item.color,px=.19f,py=-.32f)
            b(.38f,1f,1f,px=-.31f)
            if(item.id=="umbrella") b(.035f,.03f,1.01f,"goldObject",px=-.10f,py=.515f)
        }
        "scissors" -> {
            for(z in listOf(-.19f,.19f)) {
                r.part(parent,r.ring(d*.24f,d*.16f,h),if(ghost) "trayEdge" else item.color,
                    Vector3(-w*.29f,0f,d*z))
                b(.64f,.36f,.13f,"paperCream",px=.18f,pz=z*.55f)
            }
            b(.07f,1f,.10f,"goldObject",px=-.03f)
        }
        "glasses" -> {
            for(x in listOf(-.27f,.27f)) {
                b(.42f,.20f,.05f,px=x,py=-.40f,pz=.31f)
                b(.42f,.20f,.05f,px=x,py=-.40f,pz=-.31f)
                for(z in listOf(-.20f,.20f)) b(.03f,.20f,.35f,px=x-.20f,py=-.40f,pz=z)
            }
            b(.15f,.20f,.035f,py=-.40f)
            for(x in listOf(-.47f,.47f)) b(.04f,1f,.45f,px=x,pz=-.275f)
        }
        "cap" -> {
            b(.73f,.80f,.78f,py=-.10f,pz=-.11f)
            b(1f,.20f,.55f,py=-.40f,pz=.225f)
            b(.06f,.20f,.06f,"goldObject",py=.40f,pz=-.11f)
        }
    }
}
