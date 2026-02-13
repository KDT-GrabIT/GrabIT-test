package com.example.grabit_test.data.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** 자주 찾는 상품 조회용 (classLabel + 검색 횟수) */
data class FrequentSearchItem(
    val classLabel: String,
    val searchCount: Int
)

@Dao
interface SearchHistoryDao {

    @Insert
    suspend fun insert(item: SearchHistoryItem)

    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC LIMIT 100")
    fun getAllOrderedByRecent(): Flow<List<SearchHistoryItem>>

    @Query("SELECT classLabel, COUNT(*) as searchCount FROM search_history GROUP BY classLabel ORDER BY searchCount DESC LIMIT 50")
    fun getFrequentClassLabels(): Flow<List<FrequentSearchItem>>
}