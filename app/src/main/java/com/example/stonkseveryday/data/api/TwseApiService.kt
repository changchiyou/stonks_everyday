package com.example.stonkseveryday.data.api

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

/**
 * 台灣證券交易所 (TWSE) 公開 API
 * 完全免費，無需 Token，無請求限制
 *
 * 資料來源：台灣證券交易所官方網站
 * 網址：https://www.twse.com.tw/
 *
 * 注意：此 API 提供盤中即時資訊（延遲約 5 秒）
 */
interface TwseApiService {

    /**
     * 取得盤中即時個股資訊
     * 此 API 在交易時間內會即時更新（延遲約 5 秒）
     * 盤後顯示當日收盤價
     *
     * @param response 固定值 "json"
     * @param ex_ch 股票代碼，格式: "tse_2330.tw" 或 "otc_2330.tw"
     * @param json 固定值 "1"
     * @param delay 固定值 "0"
     */
    @GET("stock/api/getStockInfo.jsp")
    suspend fun getStockInfo(
        @Query("response") response: String = "json",
        @Query("ex_ch") stockCode: String,
        @Query("json") json: String = "1",
        @Query("delay") delay: String = "0"
    ): TwseStockInfoResponse
}

/**
 * TWSE 盤中即時資訊回應
 */
data class TwseStockInfoResponse(
    @SerializedName("msgArray")
    val data: List<TwseStockInfo>?,
    @SerializedName("rtmessage")
    val message: String,
    @SerializedName("rtcode")
    val code: String // "0000" 表示成功
)

data class TwseStockInfo(
    @SerializedName("c") // 股票代碼
    val stockCode: String,
    @SerializedName("n") // 股票名稱
    val stockName: String,
    @SerializedName("z") // 最新成交價
    val currentPrice: String,
    @SerializedName("y") // 昨收價
    val previousClose: String,
    @SerializedName("o") // 開盤價
    val openPrice: String,
    @SerializedName("h") // 最高價
    val highPrice: String,
    @SerializedName("l") // 最低價
    val lowPrice: String,
    @SerializedName("v") // 累積成交量
    val volume: String,
    @SerializedName("t") // 最新成交時間 (HH:MM:SS)
    val tradeTime: String,
    @SerializedName("d") // 當日日期 (yyyyMMdd)
    val date: String
)

object TwseRetrofitInstance {
    // TWSE 盤中即時資訊 API
    private const val BASE_URL = "https://mis.twse.com.tw/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val twseApiService: TwseApiService = retrofit.create(TwseApiService::class.java)
}
