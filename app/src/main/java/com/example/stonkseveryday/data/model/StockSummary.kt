package com.example.stonkseveryday.data.model

data class StockSummary(
    val totalIncome: Double,
    val dailyProfitLoss: Double,
    val totalProfitLoss: Double,
    val holdings: List<StockHolding>
)

data class StockHolding(
    val stockCode: String,
    val stockName: String,
    val quantity: Int,
    val averageCost: Double,
    val currentPrice: Double,
    val profitLoss: Double,
    val profitLossPercentage: Double
)
