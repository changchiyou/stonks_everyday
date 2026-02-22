package com.example.stonkseveryday.data.repository

import android.content.Context
import android.util.Log
import com.example.stonkseveryday.data.api.RetrofitInstance
import com.example.stonkseveryday.data.api.StockPriceResponse
import com.example.stonkseveryday.data.database.StockTransactionDao
import com.example.stonkseveryday.data.model.*
import com.example.stonkseveryday.data.preferences.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.*

class StockRepository(
    private val dao: StockTransactionDao,
    private val context: Context
) {
    private val userPreferences = UserPreferences(context)

    val allTransactions: Flow<List<StockTransaction>> = dao.getAllTransactions()
    val todayTransactions: Flow<List<StockTransaction>> = dao.getTodayTransactions()

    suspend fun insertTransaction(transaction: StockTransaction): Long {
        return dao.insertTransaction(transaction)
    }

    suspend fun updateTransaction(transaction: StockTransaction) {
        dao.updateTransaction(transaction)
    }

    suspend fun deleteTransaction(transaction: StockTransaction) {
        dao.deleteTransaction(transaction)
    }

    /**
     * 取得台股即時股價
     * 策略：
     * 1. 如果使用者有設定 FinMind Token，優先使用 FinMind API
     * 2. 如果沒有 Token 或 FinMind 失敗，使用 TWSE 官方 API（完全免費）
     *
     * @param stockCode 股票代碼 (例如: 2330)
     * @return 股價資訊或 null（如果兩個 API 都失敗）
     */
    suspend fun getStockPrice(stockCode: String): StockPriceResponse? {
        // 取得使用者設定的 Token
        val userToken = userPreferences.finmindToken.first()

        // 策略 1: 如果使用者有設定 Token，優先使用 FinMind API
        if (userToken.isNotBlank()) {
            val finMindPrice = getStockPriceFromFinMind(stockCode, userToken)
            if (finMindPrice != null) {
                return finMindPrice
            }
            Log.w("StockRepository", "FinMind API failed for $stockCode, falling back to TWSE API")
        }

        // 策略 2: 使用 TWSE 官方 API（完全免費，無需 Token）
        return getStockPriceFromTwse(stockCode)
    }

    /**
     * 從 FinMind API 取得股價
     * @param userToken 使用者的 FinMind API Token
     */
    private suspend fun getStockPriceFromFinMind(
        stockCode: String,
        userToken: String
    ): StockPriceResponse? = try {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val today = dateFormat.format(Date())

        val response = RetrofitInstance.stockApiService.getTaiwanStockPrice(
            stockCode = stockCode,
            startDate = today,
            token = userToken
        )

        if (response.status == 200 && response.data.isNotEmpty()) {
            val latestData = response.data.last()
            val previousData = response.data.getOrNull(response.data.size - 2)

            val currentPrice = latestData.close
            val previousClose = previousData?.close ?: latestData.open
            val change = currentPrice - previousClose
            val changePercent = if (previousClose != 0.0) {
                (change / previousClose) * 100
            } else 0.0

            StockPriceResponse(
                stockCode = stockCode,
                currentPrice = currentPrice,
                previousClose = previousClose,
                change = change,
                changePercent = changePercent,
                timestamp = System.currentTimeMillis()
            )
        } else {
            Log.e("StockRepository", "FinMind API returned no data for $stockCode")
            null
        }
    } catch (e: Exception) {
        Log.e("StockRepository", "Error with FinMind API for $stockCode", e)
        null
    }

    /**
     * 從 TWSE 官方 API 取得盤中即時股價
     * 完全免費，無限制，交易時間內延遲約 5 秒
     *
     * 股票代碼格式說明：
     * - 上市股票 (TSE)：tse_XXXX.tw (例如：tse_2330.tw)
     * - 上櫃股票 (OTC)：otc_XXXX.tw (例如：otc_6488.tw)
     */
    private suspend fun getStockPriceFromTwse(stockCode: String): StockPriceResponse? = try {
        // 預設為上市股票，可根據需求調整
        val formattedCode = "tse_${stockCode}.tw"

        val response = com.example.stonkseveryday.data.api.TwseRetrofitInstance.twseApiService
            .getStockInfo(stockCode = formattedCode)

        if (response.code == "0000" && !response.data.isNullOrEmpty()) {
            val stockInfo = response.data.first()

            // 解析價格（可能為 "-" 表示尚未交易）
            val currentPrice = stockInfo.currentPrice.replace(",", "").toDoubleOrNull() ?: return null
            val previousClose = stockInfo.previousClose.replace(",", "").toDoubleOrNull() ?: return null

            val change = currentPrice - previousClose
            val changePercent = if (previousClose != 0.0) {
                (change / previousClose) * 100
            } else 0.0

            Log.i("StockRepository", "TWSE API success: $stockCode = $currentPrice (${stockInfo.tradeTime})")

            StockPriceResponse(
                stockCode = stockCode,
                currentPrice = currentPrice,
                previousClose = previousClose,
                change = change,
                changePercent = changePercent,
                timestamp = System.currentTimeMillis()
            )
        } else {
            // 可能是上櫃股票，嘗試 OTC
            val otcCode = "otc_${stockCode}.tw"
            val otcResponse = com.example.stonkseveryday.data.api.TwseRetrofitInstance.twseApiService
                .getStockInfo(stockCode = otcCode)

            if (otcResponse.code == "0000" && !otcResponse.data.isNullOrEmpty()) {
                val stockInfo = otcResponse.data.first()
                val currentPrice = stockInfo.currentPrice.replace(",", "").toDoubleOrNull() ?: return null
                val previousClose = stockInfo.previousClose.replace(",", "").toDoubleOrNull() ?: return null

                val change = currentPrice - previousClose
                val changePercent = if (previousClose != 0.0) {
                    (change / previousClose) * 100
                } else 0.0

                Log.i("StockRepository", "TWSE OTC API success: $stockCode = $currentPrice")

                StockPriceResponse(
                    stockCode = stockCode,
                    currentPrice = currentPrice,
                    previousClose = previousClose,
                    change = change,
                    changePercent = changePercent,
                    timestamp = System.currentTimeMillis()
                )
            } else {
                Log.e("StockRepository", "TWSE API returned no data for $stockCode (tried both TSE and OTC)")
                null
            }
        }
    } catch (e: Exception) {
        Log.e("StockRepository", "Error with TWSE API for $stockCode", e)
        null
    }

    /**
     * 取得多支股票的即時價格
     */
    suspend fun getMultipleStockPrices(stockCodes: List<String>): Map<String, Double> {
        val priceMap = mutableMapOf<String, Double>()
        stockCodes.forEach { code ->
            getStockPrice(code)?.let { response ->
                priceMap[code] = response.currentPrice
            }
        }
        return priceMap
    }

    fun calculateSummary(transactions: List<StockTransaction>): Flow<StockSummary> {
        return allTransactions.map { txList ->
            val holdings = mutableMapOf<String, MutableList<StockTransaction>>()

            // Group transactions by stock code
            txList.forEach { tx ->
                holdings.getOrPut(tx.stockCode) { mutableListOf() }.add(tx)
            }

            // Calculate holdings
            val stockHoldings = holdings.mapNotNull { (code, txs) ->
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

                    // 嘗試從 API 取得即時股價，失敗則不顯示該持股的未實現損益
                    val stockPrice = try {
                        getStockPrice(code)
                    } catch (e: Exception) {
                        Log.e("StockRepository", "Failed to fetch price for $code", e)
                        null
                    }

                    if (stockPrice != null) {
                        val currentPrice = stockPrice.currentPrice
                        val profitLoss = (currentPrice - averageCost) * totalQuantity
                        val profitLossPercentage = ((currentPrice - averageCost) / averageCost) * 100

                        StockHolding(
                            stockCode = code,
                            stockName = txs.first().stockName,
                            quantity = totalQuantity,
                            averageCost = averageCost,
                            currentPrice = currentPrice,
                            profitLoss = profitLoss,
                            profitLossPercentage = profitLossPercentage
                        )
                    } else {
                        // API 失敗，跳過此持股（不顯示錯誤資料）
                        Log.w("StockRepository", "Skipping $code due to API failure")
                        null
                    }
                } else null
            }

            val totalProfitLoss = stockHoldings.sumOf { it.profitLoss }

            // Calculate today's profit/loss
            val todayTx = txList.filter {
                val today = System.currentTimeMillis() / (1000 * 60 * 60 * 24)
                val txDay = it.transactionDate / (1000 * 60 * 60 * 24)
                today == txDay
            }
            val dailyProfitLoss = todayTx.sumOf { tx ->
                when (tx.transactionType) {
                    TransactionType.SELL -> tx.totalAmount
                    TransactionType.BUY -> -tx.totalAmount
                }
            }

            // Total income from all sell transactions
            val totalIncome = txList.filter { it.transactionType == TransactionType.SELL }
                .sumOf { it.totalAmount }

            StockSummary(
                totalIncome = totalIncome,
                dailyProfitLoss = dailyProfitLoss,
                totalProfitLoss = totalProfitLoss,
                holdings = stockHoldings
            )
        }
    }
}
