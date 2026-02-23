package com.example.stonkseveryday.data.model

data class StockSummary(
    val totalAssets: Double,          // 總資產 (持股現值 + 現金)
    val netAssets: Double,            // 淨資產
    val todayProfitLoss: Double,      // 今日損益
    val todayProfitLossPercent: Double, // 今日損益百分比
    val totalProfitLoss: Double,      // 總未實現損益
    val totalProfitLossPercent: Double, // 總未實現損益百分比
    val holdings: List<StockHolding>
)

data class StockHolding(
    val stockCode: String,
    val stockName: String,
    val quantity: Int,                // 股數
    val averageCost: Double,          // 成本價 (平均)
    val currentPrice: Double,         // 現價
    val currentValue: Double,         // 現值 (quantity * currentPrice)
    val profitLoss: Double,           // 損益金額
    val profitLossPercentage: Double, // 報酬率 %
    val positionRatio: Double,        // 持股比重 %
    val totalDividends: Double = 0.0, // 累計股利
    val isPriceStale: Boolean = false // 股價資料是否過期（API 失敗，使用快取）
)
