package com.example.grabit_test.data

import com.example.grabit_test.data.history.FrequentSearchItem
import com.example.grabit_test.data.history.SearchHistoryItem
import kotlinx.coroutines.flow.Flow

class SearchHistoryRepository(private val db: AppDatabase) {

    private val dao = db.searchHistoryDao()

    suspend fun insert(item: SearchHistoryItem) {
        dao.insert(item)
    }

    fun getRecentSearches(): Flow<List<SearchHistoryItem>> =
        dao.getAllOrderedByRecent()

    fun getFrequentSearches(): Flow<List<FrequentSearchItem>> =
        dao.getFrequentClassLabels()
}
