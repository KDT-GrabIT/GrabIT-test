package com.example.grabit_test.data.product

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 상품 메타(meta.xml)의 상단 div_cd 내 치수만 저장.
 * - annotation/size(이미지 픽셀)는 저장하지 않음.
 */
@Entity(tableName = "product_dimension")
data class ProductDimension(
    @PrimaryKey
    val barcd: String,
    val itemNo: String?,
    val imgProdNm: String?,
    /** 상품 가로 치수 (div_cd 내 첫 번째 width만) */
    val widthCm: Float?,
    /** 상품 세로 치수 (div_cd 내 첫 번째 length만) */
    val lengthCm: Float?,
    /** 상품 높이 치수 (div_cd 내 첫 번째 height만, 비어 있으면 null) */
    val heightCm: Float?
)
