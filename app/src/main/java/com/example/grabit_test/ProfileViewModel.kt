package com.example.grabit_test

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.grabit_test.data.AppDatabase
import com.example.grabit_test.data.SearchHistoryRepository
import com.example.grabit_test.data.history.FrequentSearchItem
import com.example.grabit_test.data.history.SearchHistoryItem
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SearchHistoryRepository(AppDatabase.getInstance(application))

    val recentSearches: StateFlow<List<SearchHistoryItem>> = repository
        .getRecentSearches()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val frequentSearches: StateFlow<List<FrequentSearchItem>> = repository
        .getFrequentSearches()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
