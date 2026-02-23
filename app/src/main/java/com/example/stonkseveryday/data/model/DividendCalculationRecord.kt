package com.example.stonkseveryday.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 股利計算記錄
 * 記錄每支股票最後一次計算股利的時間，避免過度頻繁呼叫 API
 */
@Entity(tableName = "dividend_calculation_records")
data class DividendCalculationRecord(
    @PrimaryKey
    val stockCode: String,
    val lastCalculationTime: Long, // 最後計算時間（毫秒）
    val recordCount: Int = 0       // 當次計算新增的股利記錄數量
)
