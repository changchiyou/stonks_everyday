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
    val recordCount: Int = 0,       // 當次計算新增的股利記錄數量
    val queryStatus: DividendQueryStatus = DividendQueryStatus.SUCCESS // 查詢狀態
)

/**
 * 股利查詢狀態
 */
enum class DividendQueryStatus {
    SUCCESS,        // 查詢成功（可能有股利或無股利）
    NOT_FOUND,      // FinMind 查不到該股票資料
    API_ERROR       // API 錯誤（網路問題、逾時等）
}
