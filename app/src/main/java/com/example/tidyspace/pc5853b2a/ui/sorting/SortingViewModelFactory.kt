package com.example.tidyspace.pc5853b2a.ui.sorting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.tidyspace.pc5853b2a.data.repository.LocalSortingRepository
import com.example.tidyspace.pc5853b2a.domain.usecase.SortingUseCase

class SortingViewModelFactory: ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T: ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(SortingViewModel::class.java))
        return SortingViewModel(SortingUseCase(LocalSortingRepository())) as T
    }
}
