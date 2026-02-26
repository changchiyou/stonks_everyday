package com.example.stonkseveryday.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 股價快取
 * 用於在 API 失敗時顯示上次的股價
 */
@Entity(tableName = "stock_price_cache")
data class StockPriceCache(
    @PrimaryKey
    val stockCode: String,
    val currentPrice: Double,
    val previousClose: Double,
    val change: Double,
    val changePercent: Double,
    val lastUpdateTime: Long,  // 最後更新時間（毫秒）
    val isStale: Boolean = false,  // 是否過期（超過1天未更新）
    val askPrice: Double? = null,   // 即時賣一價
    val previousCloseDate: String? = null,  // previousClose 對應的日期 (yyyy-MM-dd)
    val currentPriceDate: String? = null,   // currentPrice 對應的日期 (yyyy-MM-dd)
    val tradeTime: String? = null,  // 最新成交時間 (HH:MM:SS)
    val tradeStatus: String? = null // 交易狀態 (0 = 正常交易, 1 = 試搓)
)
