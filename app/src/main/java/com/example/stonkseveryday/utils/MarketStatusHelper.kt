package com.example.stonkseveryday.utils

import com.example.stonkseveryday.data.api.TwseStockInfoResponse
import java.text.SimpleDateFormat
import java.util.*

/**
 * 市場開盤狀態輔助類
 * 使用 TWSE API 回應來判斷市場狀態
 */
object MarketStatusHelper {

    /**
     * 判斷今天是否為交易日
     * 原理：比較 API 的系統日期(今天) 和 最新成交日期
     * - 如果相同 = 今天有開盤（交易日）
     * - 如果不同 = 今天沒開盤（休市日）
     *
     * @param response TWSE API 回應
     * @return true 表示今天是交易日，false 表示休市
     */
    fun isTodayTradingDay(response: TwseStockInfoResponse?): Boolean {
        if (response == null || response.queryTime == null || response.data.isNullOrEmpty()) {
            // 無法判斷時，保守起見使用本地時間判斷（週一到週五）
            val calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Taipei"))
            val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
            return dayOfWeek in Calendar.MONDAY..Calendar.FRIDAY
        }

        val sysDate = response.queryTime.sysDate  // 今天的日期 (yyyyMMdd)
        val tradeDate = response.data.firstOrNull()?.date  // 最新成交日期 (yyyyMMdd)

        // 如果系統日期 = 成交日期，表示今天有交易
        return sysDate == tradeDate
    }

    /**
     * 取得市場狀態描述
     * @param response TWSE API 回應
     * @return 市場狀態字串（如：「開盤中」、「休市」、「盤前」等）
     */
    fun getMarketStatus(response: TwseStockInfoResponse?): String {
        // 先判斷是否為交易日
        if (!isTodayTradingDay(response)) {
            return "休市"
        }

        // 交易日的時段判斷
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Taipei"))

        // 優先使用 API 的系統時間（如果有的話）
        val timeInMinutes = if (response?.queryTime?.sysTime != null) {
            try {
                val timeParts = response.queryTime.sysTime.split(":")
                val hour = timeParts[0].toInt()
                val minute = timeParts[1].toInt()
                hour * 60 + minute
            } catch (e: Exception) {
                // 解析失敗，使用本地時間
                val hour = calendar.get(Calendar.HOUR_OF_DAY)
                val minute = calendar.get(Calendar.MINUTE)
                hour * 60 + minute
            }
        } else {
            // 沒有 API 時間，使用本地時間
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val minute = calendar.get(Calendar.MINUTE)
            hour * 60 + minute
        }

        return when {
            timeInMinutes < 8 * 60 + 30 -> "盤前"              // 08:30 前
            timeInMinutes < 9 * 60 -> "集合競價"               // 08:30 - 09:00
            timeInMinutes < 13 * 60 + 30 -> "開盤中"           // 09:00 - 13:30
            timeInMinutes < 14 * 60 + 30 -> "盤後零股交易"     // 13:30 - 14:30
            else -> "已收盤"                                   // 14:30 後
        }
    }

    /**
     * 判斷是否應該顯示今日損益
     * - 休市日：不顯示（返回 false）
     * - 交易日盤前（09:00前）：不顯示（返回 false）
     * - 交易日盤中/盤後：顯示（返回 true）
     *
     * @param response TWSE API 回應
     * @return true 表示應該顯示今日損益
     */
    fun shouldShowTodayProfitLoss(response: TwseStockInfoResponse?): Boolean {
        // 休市日不顯示今日損益
        if (!isTodayTradingDay(response)) {
            return false
        }

        // 交易日的時段判斷
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Taipei"))

        val timeInMinutes = if (response?.queryTime?.sysTime != null) {
            try {
                val timeParts = response.queryTime.sysTime.split(":")
                val hour = timeParts[0].toInt()
                val minute = timeParts[1].toInt()
                hour * 60 + minute
            } catch (e: Exception) {
                val hour = calendar.get(Calendar.HOUR_OF_DAY)
                val minute = calendar.get(Calendar.MINUTE)
                hour * 60 + minute
            }
        } else {
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val minute = calendar.get(Calendar.MINUTE)
            hour * 60 + minute
        }

        // 09:00 之前不顯示今日損益（包含集合競價）
        return timeInMinutes >= 9 * 60
    }
}
