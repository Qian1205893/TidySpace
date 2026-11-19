package com.example.tidyspace.pc5853b2a.ui.sorting.scene

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.example.tidyspace.pc5853b2a.domain.model.*

/** Bounded overlapping one-shots. Pool disposal stops every voice on leaving the room. */
internal class RoomAudio(context: Context) {
    private val pool=SoundPool.Builder().setMaxStreams(6).setAudioAttributes(
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()).build()
    private val sounds=mutableMapOf<String,Int>()
    private val loaded=java.util.Collections.synchronizedSet(mutableSetOf<Int>())
    private var motor=0
    private var lastFoley=0L
    private var heardClean=0L
    private var heardDisposal=0L
    init {
        pool.setOnLoadCompleteListener { _,id,status->if(status==0) loaded+=id }
        (listOf("woodfish","bowl","rattle","pop","click","squish","spark",
            "clean_sweep","clean_scrub","clean_motor","clean_suction")+(0..7).map { "music$it" }).forEach { name ->
            context.assets.openFd("sounds/$name.wav").use { sounds[name]=pool.load(it,1) }
        }
    }
    private fun play(name: String) {
        sounds[name]?.takeIf { it in loaded }?.let { pool.play(it,.65f,.65f,1,0,1f) }
    }
    fun strike(event: PlayEvent) {
        when(event.kind) {
            Furnishing.Woodfish->play("woodfish")
            Furnishing.SingingBowl->play("bowl")
            Furnishing.MusicBox->note((event.count%8).toInt())
            Furnishing.Rattle->play("rattle")
            Furnishing.PopBoard->play("pop")
            Furnishing.Ratchet,Furnishing.Spinner->play("click")
            Furnishing.Pokeball->play("spark")
            Furnishing.Mochi,Furnishing.Jelly->play("squish")
            Furnishing.Tumbler->note(0)
            Furnishing.MagneticSlider,Furnishing.ToggleBox,Furnishing.Abacus->play("click")
            Furnishing.DeskBell->play("bowl")
            Furnishing.Hourglass->play("squish")
            Furnishing.Windmill->note((event.count%8).toInt())
            else->Unit
        }
    }
    fun note(index: Int)=play("music${index.mod(8)}")
    fun cleaning(c: CleaningState?) {
        if(c!=null && c.disposalSequence!=heardDisposal) {
            heardDisposal=c.disposalSequence
            play("pop")
        }
        val powered=c?.tool==CleanTool.Vacuum && !c.finished
        if(powered && motor==0) sounds["clean_motor"]?.takeIf { it in loaded }?.let {
            motor=pool.play(it,.22f,.22f,0,-1,1f)
        }
        if(!powered && motor!=0) { pool.stop(motor);motor=0 }
        if(c!=null && c.sequence!=heardClean) {
            heardClean=c.sequence
            val now=android.os.SystemClock.uptimeMillis()
            if(c.contact!=null && now-lastFoley>=120) {
                lastFoley=now
                play(when(c.contact.tool) {
                    CleanTool.Vacuum->"clean_suction";CleanTool.Broom->"clean_sweep";
                    CleanTool.Brush,CleanTool.Mop->"clean_scrub"
                })
            }
        }
    }
    fun close() { pool.setOnLoadCompleteListener(null);pool.release();loaded.clear();sounds.clear() }
}
