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
        val totalAssets: Double,
        val lastRefreshTime: Long
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
                android.util.Log.d("WidgetDataCache", "使用快取資料（${(now - lastCalculateTime)}ms 前）")
                return cachedData!!
            }

            // 重新計算
            android.util.Log.d("WidgetDataCache", "重新計算小工具資料（forceRefresh=$forceRefresh）")
            val startTime = System.currentTimeMillis()
            val newData = calculateWidgetData(context)
            val duration = System.currentTimeMillis() - startTime
            android.util.Log.d("WidgetDataCache", "計算完成，耗時 ${duration}ms，今日損益=${newData.dailyProfitLoss}, 總損益=${newData.totalProfitLoss}, 總資產=${newData.totalAssets}")
            cachedData = newData
            lastCalculateTime = now
            return newData
        }
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

                // 計算今日損益
                val todayPL = (currentPrice - previousClose) * totalQuantity
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

        // 讀取最後刷新時間
        val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        val lastRefreshTime = prefs.getLong("last_refresh_time", System.currentTimeMillis())

        return WidgetData(
            dailyProfitLoss = dailyProfitLoss,
            totalProfitLoss = totalProfitLoss,
            totalAssets = totalAssets,
            lastRefreshTime = lastRefreshTime
        )
    }
}
