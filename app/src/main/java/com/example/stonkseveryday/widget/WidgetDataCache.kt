package com.example.stonkseveryday.widget

import android.content.Context
import com.example.stonkseveryday.data.database.StockDatabase
import com.example.stonkseveryday.data.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 小工具資料快取
 * 避免多個小工具實例同時計算造成 database 競爭
 */
object WidgetDataCache {
    private var cachedData: WidgetData? = null
    private var lastCalculateTime = 0L
    private val cacheDuration = 5000L // 5 秒內重複使用快取
    private val mutex = Mutex()

    data class WidgetData(
        val dailyProfitLoss: Double,
        val totalProfitLoss: Double,
        val totalAssets: Double
    )

    /**
     * 取得小工具資料
     * @param forceRefresh 強制重新計算（刷新按鈕使用）
     */
    suspend fun getData(context: Context, forceRefresh: Boolean = false): WidgetData {
        mutex.withLock {
            val now = System.currentTimeMillis()

            // 如果 5 秒內有快取且不強制刷新，直接返回
            if (!forceRefresh && cachedData != null && (now - lastCalculateTime) < cacheDuration) {
                android.util.Log.d("WidgetDataCache", "✓ 使用快取資料（${(now - lastCalculateTime)}ms 前）")
                return cachedData!!
            }

            // 重新計算
            val reason = if (forceRefresh) "強制刷新" else "快取過期"
            android.util.Log.i("WidgetDataCache", "開始重新計算小工具資料（原因: $reason）")
            val startTime = System.currentTimeMillis()
            val newData = calculateWidgetData(context)
            val duration = System.currentTimeMillis() - startTime
            android.util.Log.i(
                "WidgetDataCache",
                "✓ 計算完成，耗時 ${duration}ms\n" +
                "  - 今日損益: ${String.format("%.2f", newData.dailyProfitLoss)}\n" +
                "  - 總損益: ${String.format("%.2f", newData.totalProfitLoss)}\n" +
                "  - 總資產: ${String.format("%.2f", newData.totalAssets)}"
            )
            cachedData = newData
            lastCalculateTime = now
            return newData
        }
    }

    /**
     * 判斷交易時段，決定是否應該顯示今日損益
     *
     * @param tradeTime 交易時間 (HH:MM:SS)
     * @return true 表示應該顯示今日損益（盤中或盤後），false 表示顯示 0（開盤前）
     */
    private fun shouldShowTodayProfitLoss(tradeTime: String?): Boolean {
        // 如果沒有時間資訊，使用本地時間判斷
        if (tradeTime.isNullOrEmpty()) {
            val calendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Taipei"))
            val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
            val minute = calendar.get(java.util.Calendar.MINUTE)
            val timeInMinutes = hour * 60 + minute

            // 09:00 之前不顯示今日損益
            return timeInMinutes >= (9 * 60)
        }

        // 解析時間字串 (HH:MM:SS)
        val timeParts = tradeTime.split(":")
        if (timeParts.size < 2) {
            return false
        }

        val hour = timeParts[0].toIntOrNull() ?: 0
        val minute = timeParts[1].toIntOrNull() ?: 0
        val timeValue = hour * 10000 + minute * 100 + (timeParts.getOrNull(2)?.toIntOrNull() ?: 0)

        // 未開盤 (< 08:30)
        if (timeValue < 83000) {
            return false
        }

        // 開盤前試搓 (08:30-09:00)
        if (timeValue >= 83000 && timeValue < 90000) {
            return false
        }

        // 09:00 之後（包含盤中、收盤、盤後）都顯示今日損益
        return timeValue >= 90000
    }

    /**
     * 智能計算今日損益
     * 實作跨日檢測、日期驗證等邏輯
     *
     * @param code 股票代碼
     * @param currentPrice 現價
     * @param previousClose 昨收價
     * @param quantity 持股數量
     * @param cachedPrice 快取的股價資料（包含日期資訊）
     * @return 今日損益
     */
    private fun calculateTodayProfitLoss(
        code: String,
        currentPrice: Double,
        previousClose: Double,
        quantity: Int,
        cachedPrice: com.example.stonkseveryday.data.model.StockPriceCache?
    ): Double {
        // 檢查交易時段：只在盤中和盤後顯示今日損益
        if (!shouldShowTodayProfitLoss(cachedPrice?.tradeTime)) {
            android.util.Log.d(
                "WidgetDataCache",
                "$code: 開盤前時段（時間:${cachedPrice?.tradeTime}），今日損益設為 0"
            )
            return 0.0
        }
        // 如果沒有快取資料，無法判斷，返回簡單計算結果
        if (cachedPrice == null) {
            android.util.Log.d("WidgetDataCache", "$code: 無快取資料，使用基本計算")
            return (currentPrice - previousClose) * quantity
        }

        // 取得今天的日期（台灣時區）
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        dateFormat.timeZone = java.util.TimeZone.getTimeZone("Asia/Taipei")
        val today = dateFormat.format(java.util.Date())

        // 檢查 1: previousClose 和 currentPrice 的日期
        if (cachedPrice.previousCloseDate != null && cachedPrice.currentPriceDate != null) {
            // 異常情況：previousClose 和 currentPrice 是同一天
            if (cachedPrice.previousCloseDate == cachedPrice.currentPriceDate) {
                android.util.Log.w(
                    "WidgetDataCache",
                    "$code: previousClose 和 currentPrice 同日 (${cachedPrice.currentPriceDate})，今日損益設為 0"
                )
                return 0.0
            }

            // 檢查 2: 跨日檢測 - currentPrice 不是今天的資料
            if (cachedPrice.currentPriceDate != today) {
                android.util.Log.w(
                    "WidgetDataCache",
                    "$code: currentPrice 不是今天的資料 (${cachedPrice.currentPriceDate} vs $today)，今日損益設為 0"
                )
                return 0.0
            }

            // 檢查 3: previousClose 應該是昨天或之前的日期
            val calendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Taipei"))
            calendar.add(java.util.Calendar.DAY_OF_YEAR, -1)
            val yesterday = dateFormat.format(calendar.time)

            // previousClose 的日期應該在今天之前
            if (cachedPrice.previousCloseDate >= today) {
                android.util.Log.w(
                    "WidgetDataCache",
                    "$code: previousClose 日期異常 (${cachedPrice.previousCloseDate} >= $today)，今日損益設為 0"
                )
                return 0.0
            }
        }

        // 檢查 4: 時間戳記檢測 - 資料太舊（超過 24 小時）
        val ageInHours = (System.currentTimeMillis() - cachedPrice.lastUpdateTime) / (1000 * 60 * 60)
        if (ageInHours > 24) {
            android.util.Log.w(
                "WidgetDataCache",
                "$code: 快取資料過舊 (${ageInHours}h)，今日損益可能不準確"
            )
            // 不返回 0，但記錄警告
        }

        // 檢查 5: 盤前/收盤後檢測 - 如果現價等於昨收（使用昨收作為現價）
        if (currentPrice == previousClose) {
            android.util.Log.d(
                "WidgetDataCache",
                "$code: currentPrice == previousClose，可能是盤前或收盤後，今日損益設為 0"
            )
            return 0.0
        }

        // 所有檢查通過，計算今日損益
        val todayPL = (currentPrice - previousClose) * quantity
        android.util.Log.d(
            "WidgetDataCache",
            "$code: 今日損益計算正常 = ($currentPrice - $previousClose) × $quantity = $todayPL"
        )
        return todayPL
    }

    /**
     * 計算小工具所需的所有資料
     * 注意：此方法從 stockPriceCacheDao 讀取已快取的股價，不會呼叫 API
     * RefreshWidgetCallback 應該先呼叫 repository.getStockPrice() 更新快取，再呼叫此方法
     */
    private suspend fun calculateWidgetData(context: Context): WidgetData {
        val database = StockDatabase.getDatabase(context)
        val dao = database.stockTransactionDao()
        val dividendDao = database.dividendDao()
        val stockPriceCacheDao = database.stockPriceCacheDao()
        val userPreferences = com.example.stonkseveryday.data.preferences.UserPreferences(context)

        // 使用快取資料計算總資產和總損益（快取應該已由 repository.getStockPrice() 更新）
        val allTransactions = dao.getAllTransactions().first()
        val includeDividends = userPreferences.includeDividends.first()

        val holdings = mutableMapOf<String, MutableList<com.example.stonkseveryday.data.model.StockTransaction>>()
        allTransactions.forEach { tx ->
            holdings.getOrPut(tx.stockCode) { mutableListOf() }.add(tx)
        }

        var totalAssets = 0.0
        var totalProfitLoss = 0.0
        var dailyProfitLoss = 0.0

        holdings.forEach { (code, txs) ->
            var totalQuantity = 0
            var totalCost = 0.0

            txs.forEach { tx ->
                when (tx.transactionType) {
                    TransactionType.BUY -> {
                        totalQuantity += tx.quantity
                        totalCost += tx.totalAmount
                    }
                    TransactionType.SELL -> {
                        totalQuantity -= tx.quantity
                        totalCost -= tx.totalAmount
                    }
                }
            }

            if (totalQuantity > 0) {
                val averageCost = totalCost / totalQuantity

                // 從快取取得股價（不呼叫 API）
                val cachedPrice = stockPriceCacheDao.getPriceCache(code)
                val currentPrice = cachedPrice?.currentPrice ?: averageCost
                val previousClose = cachedPrice?.previousClose ?: averageCost

                val currentValue = currentPrice * totalQuantity
                totalAssets += currentValue

                // 計算今日損益（智能跨日檢測）
                val todayPL = calculateTodayProfitLoss(
                    code = code,
                    currentPrice = currentPrice,
                    previousClose = previousClose,
                    quantity = totalQuantity,
                    cachedPrice = cachedPrice
                )
                dailyProfitLoss += todayPL

                val baseProfitLoss = (currentPrice - averageCost) * totalQuantity

                // 取得股利
                val totalDividends = dividendDao.getTotalDividendsByStockCode(code) ?: 0.0

                val profitLoss = if (includeDividends) {
                    baseProfitLoss + totalDividends
                } else {
                    baseProfitLoss
                }

                totalProfitLoss += profitLoss
            }
        }

        return WidgetData(
            dailyProfitLoss = dailyProfitLoss,
            totalProfitLoss = totalProfitLoss,
            totalAssets = totalAssets
        )
    }
}
