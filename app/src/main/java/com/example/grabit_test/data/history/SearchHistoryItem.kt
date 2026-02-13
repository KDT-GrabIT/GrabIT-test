package com.example.grabit_test.data.history

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "search_history")
data class SearchHistoryItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val query: String,
    val classLabel: String,
    val searchedAt: Long,
    val source: String
)