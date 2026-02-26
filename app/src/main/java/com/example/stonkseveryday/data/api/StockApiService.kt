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
        @Query("end_date") endDate: String = "",
        @Query("token") token: String = ""
    ): FinMindResponse

    /**
     * 取得台股股票資訊（包含股票名稱）
     * FinMind API: https://api.finmindtrade.com/api/v4/data
     * dataset: TaiwanStockInfo
     */
    @GET("data")
    suspend fun getTaiwanStockInfo(
        @Query("dataset") dataset: String = "TaiwanStockInfo",
        @Query("data_id") stockCode: String = "",
        @Query("token") token: String = ""
    ): FinMindStockInfoResponse

    /**
     * 取得台股股利資料
     * FinMind API: https://api.finmindtrade.com/api/v4/data
     * dataset: TaiwanStockDividend
     */
    @GET("data")
    suspend fun getTaiwanStockDividend(
        @Query("dataset") dataset: String = "TaiwanStockDividend",
        @Query("data_id") stockCode: String,
        @Query("start_date") startDate: String = "",
        @Query("end_date") endDate: String = "",
        @Query("token") token: String = ""
    ): FinMindDividendResponse
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

// FinMind 股票資訊回應
data class FinMindStockInfoResponse(
    @SerializedName("msg")
    val message: String,
    @SerializedName("status")
    val status: Int,
    @SerializedName("data")
    val data: List<TaiwanStockInfoData>
)

data class TaiwanStockInfoData(
    @SerializedName("stock_id")
    val stockId: String,
    @SerializedName("stock_name")
    val stockName: String,
    @SerializedName("industry_category")
    val industry: String,
    @SerializedName("type")
    val type: String
)

// 簡化的股價回應，用於內部使用
data class StockPriceResponse(
    val stockCode: String,
    val currentPrice: Double,
    val previousClose: Double,
    val change: Double,
    val changePercent: Double,
    val timestamp: Long,
    val isStale: Boolean = false,  // 資料是否過期（來自快取且超過24小時）
    val askPrice: Double? = null,   // 即時賣一價（委託價第一檔），僅 TWSE API 提供
    val previousCloseDate: String? = null,  // previousClose 對應的日期 (yyyy-MM-dd)
    val currentPriceDate: String? = null,   // currentPrice 對應的日期 (yyyy-MM-dd)
    val tradeTime: String? = null,  // 最新成交時間 (HH:MM:SS)
    val tradeStatus: String? = null // 交易狀態 (0 = 正常交易, 1 = 試搓)
)

// 股票基本資訊回應
data class StockInfoResponse(
    val stockCode: String,
    val stockName: String,
    val industry: String = "",
    val type: String = "",
    val isEtf: Boolean = false
)

// FinMind 股利資料回應
data class FinMindDividendResponse(
    @SerializedName("msg")
    val message: String,
    @SerializedName("status")
    val status: Int,
    @SerializedName("data")
    val data: List<TaiwanStockDividendData>
)

data class TaiwanStockDividendData(
    @SerializedName("stock_id")
    val stockId: String,
    @SerializedName("date")
    val date: String,  // 發放日
    @SerializedName("CashExDividendTradingDate")
    val cashExDividendDate: String? = "",  // 現金股利除息交易日
    @SerializedName("StockExDividendTradingDate")
    val stockExDividendDate: String? = "",  // 股票股利除權交易日
    @SerializedName("year")
    val dividendYear: String? = "",
    @SerializedName("CashEarningsDistribution")
    val cashDividend: Double? = 0.0,  // 現金股利
    @SerializedName("StockEarningsDistribution")
    val stockDividend: Double? = 0.0  // 股票股利
)
