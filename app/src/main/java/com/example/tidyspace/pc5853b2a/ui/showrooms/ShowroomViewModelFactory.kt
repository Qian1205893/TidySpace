package com.example.tidyspace.pc5853b2a.ui.showrooms

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.tidyspace.pc5853b2a.data.repository.LocalShowroomRepository
import com.example.tidyspace.pc5853b2a.data.repository.ShowroomExporter
import com.example.tidyspace.pc5853b2a.ui.sorting.RoomViewModel

class ShowroomViewModelFactory(private val context: Context,private val room: RoomViewModel,
    private val initialPage: ShowroomPage=ShowroomPage.Gallery): ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T: ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ShowroomViewModel::class.java))
        val exporter=ShowroomExporter(context)
        return ShowroomViewModel(LocalShowroomRepository(context).rooms(),room,initialPage) { game,reference,title,note ->
            exporter.share(exporter.create(game,reference,title,note)).absolutePath
        } as T
    }
}
