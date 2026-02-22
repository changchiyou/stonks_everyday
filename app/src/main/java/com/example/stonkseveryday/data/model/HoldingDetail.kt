package com.example.stonkseveryday.data.model

/**
 * 單筆交易的詳細持倉資訊
 * 用於顯示每筆交易的未實現損益、持有天數、股利等資訊
 */
data class HoldingDetail(
    val transaction: StockTransaction,
    val currentPrice: Double,
    val currentValue: Double,
    val unrealizedPL: Double,
    val unrealizedPLPercent: Double,
    val dividends: List<Dividend>,
    val totalDividends: Double,
    val holdingDays: Int,
    val plWithDividends: Double,
    val plWithDividendsPercent: Double
)
