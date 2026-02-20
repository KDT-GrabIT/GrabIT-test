package com.example.grabit_test.data.product

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ProductDimensionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(dimensions: List<ProductDimension>)

    @Query("SELECT * FROM product_dimension WHERE barcd = :barcd LIMIT 1")
    suspend fun getByBarcd(barcd: String): ProductDimension?

    @Query("SELECT * FROM product_dimension")
    suspend fun getAll(): List<ProductDimension>
}
