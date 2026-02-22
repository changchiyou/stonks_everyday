package com.example.stonkseveryday.data.api

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

interface StockApiService {
    /**
     * 取得台股即時股價
     * FinMind API: https://api.finmindtrade.com/api/v4/data
     * dataset: TaiwanStockPrice
     */
    @GET("data")
    suspend fun getTaiwanStockPrice(
        @Query("dataset") dataset: String = "TaiwanStockPrice",
        @Query("data_id") stockCode: String,
        @Query("start_date") startDate: String = "",
        @Query("token") token: String = ""
    ): FinMindResponse
}

data class FinMindResponse(
    @SerializedName("msg")
    val message: String,
    @SerializedName("status")
    val status: Int,
    @SerializedName("data")
    val data: List<TaiwanStockData>
)

data class TaiwanStockData(
    @SerializedName("date")
    val date: String,
    @SerializedName("stock_id")
    val stockId: String,
    @SerializedName("Trading_Volume")
    val tradingVolume: Long,
    @SerializedName("Trading_money")
    val tradingMoney: Long,
    @SerializedName("open")
    val open: Double,
    @SerializedName("max")
    val max: Double,
    @SerializedName("min")
    val min: Double,
    @SerializedName("close")
    val close: Double,
    @SerializedName("spread")
    val spread: Double,
    @SerializedName("Trading_turnover")
    val tradingTurnover: Long
)

// 簡化的股價回應，用於內部使用
data class StockPriceResponse(
    val stockCode: String,
    val currentPrice: Double,
    val previousClose: Double,
    val change: Double,
    val changePercent: Double,
    val timestamp: Long
)
