package com.example.tidyspace.pc5853b2a.ui.sorting

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.tidyspace.pc5853b2a.data.repository.*
import com.example.tidyspace.pc5853b2a.domain.model.*
import com.example.tidyspace.pc5853b2a.domain.usecase.*

class RoomViewModelFactory(context: Context): ViewModelProvider.Factory {
    private val context=context.applicationContext
    @Suppress("UNCHECKED_CAST")
    override fun <T: ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(RoomViewModel::class.java))
        val prefs=context.getSharedPreferences("tidy-room",Context.MODE_PRIVATE)
        val restored=CleaningExpansionPersistence.restore(prefs.getString("snapshot",null),
            prefs.getString(CleaningExpansionPersistence.KEY,null))
        val initial=MovingHome.prepare(RoomEntry.prepare(restored ?: RoomGame()),fresh=restored==null)
        val prepared=StudyRenovation.prepare(initial)
        val store=LocalShowroomRepository(context)
        val homes=HomeUseCase(LocalHomeRepository(context))
        val library=homes.initialize(prepared,store.session())
        val active=homes.activeGame()
        val current=RoomEntry.prepare(StudyRenovation.prepare(MovingHome.prepare(active)))
        if(current!=active && !homes.loadError) runCatching { homes.save(current,library.active.session) }
        return RoomViewModel(current.copy(saveError=homes.loadError || homes.migrationError),
            homes.library.active.session,null,{},homes) as T
    }
}
