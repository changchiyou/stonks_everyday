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
    private val database = com.example.stonkseveryday.data.database.StockDatabase.getDatabase(context)
    private val dividendDao = database.dividendDao()

    val allTransactions: Flow<List<StockTransaction>> = dao.getAllTransactions()
    val todayTransactions: Flow<List<StockTransaction>> = dao.getTodayTransactions()
    val allDividends: Flow<List<com.example.stonkseveryday.data.model.Dividend>> = dividendDao.getAllDividends()

    suspend fun insertTransaction(transaction: StockTransaction): Long {
        return dao.insertTransaction(transaction)
    }

    suspend fun updateTransaction(transaction: StockTransaction) {
        dao.updateTransaction(transaction)
    }

    suspend fun deleteTransaction(transaction: StockTransaction) {
        dao.deleteTransaction(transaction)
    }

    suspend fun insertDividend(dividend: com.example.stonkseveryday.data.model.Dividend): Long {
        return dividendDao.insertDividend(dividend)
    }

    suspend fun deleteDividend(dividend: com.example.stonkseveryday.data.model.Dividend) {
        dividendDao.deleteDividend(dividend)
    }

    suspend fun getDividendsByTransaction(transactionId: Long): List<com.example.stonkseveryday.data.model.Dividend> {
        val flow = dividendDao.getDividendsByTransactionId(transactionId)
        var result = listOf<com.example.stonkseveryday.data.model.Dividend>()
        flow.collect { result = it }
        return result
    }

    /**
     * 根據股票代碼判斷是否為 ETF
     * 台灣 ETF 代碼規律：
     * - 00 開頭（如 0050, 0056）
     * - 部分 5 開頭（如 5000）
     */
    private fun isEtfByCode(stockCode: String): Boolean {
        return stockCode.startsWith("00") ||
               (stockCode.startsWith("5") && stockCode.length == 4)
    }

    /**
     * 取得股票名稱
     * 策略：
     * 1. 優先使用 TWSE API（會同時返回股價和名稱）
     * 2. 如果失敗，嘗試使用 FinMind API
     *
     * @param stockCode 股票代碼 (例如: 2330)
     * @return 股票資訊或 null（包含是否為 ETF 的判斷）
     */
    suspend fun getStockInfo(stockCode: String): com.example.stonkseveryday.data.api.StockInfoResponse? {
        // 策略 1: 使用 TWSE API（同時取得名稱和價格）
        val twseInfo = getStockInfoFromTwse(stockCode)
        if (twseInfo != null) {
            return twseInfo
        }

        // 策略 2: 使用 FinMind API
        return getStockInfoFromFinMind(stockCode)
    }

    /**
     * 從 TWSE API 取得股票資訊
     */
    private suspend fun getStockInfoFromTwse(stockCode: String): com.example.stonkseveryday.data.api.StockInfoResponse? = try {
        val formattedCode = "tse_${stockCode}.tw"
        val response = com.example.stonkseveryday.data.api.TwseRetrofitInstance.twseApiService
            .getStockInfo(stockCode = formattedCode)

        if (response.code == "0000" && !response.data.isNullOrEmpty()) {
            val stockInfo = response.data.first()
            com.example.stonkseveryday.data.api.StockInfoResponse(
                stockCode = stockCode,
                stockName = stockInfo.stockName,
                isEtf = isEtfByCode(stockCode)
            )
        } else {
            // 嘗試 OTC
            val otcCode = "otc_${stockCode}.tw"
            val otcResponse = com.example.stonkseveryday.data.api.TwseRetrofitInstance.twseApiService
                .getStockInfo(stockCode = otcCode)

            if (otcResponse.code == "0000" && !otcResponse.data.isNullOrEmpty()) {
                val stockInfo = otcResponse.data.first()
                com.example.stonkseveryday.data.api.StockInfoResponse(
                    stockCode = stockCode,
                    stockName = stockInfo.stockName,
                    isEtf = isEtfByCode(stockCode)
                )
            } else {
                null
            }
        }
    } catch (e: Exception) {
        Log.e("StockRepository", "Error fetching stock info from TWSE for $stockCode", e)
        null
    }

    /**
     * 從 FinMind API 取得股票資訊
     */
    private suspend fun getStockInfoFromFinMind(stockCode: String): com.example.stonkseveryday.data.api.StockInfoResponse? = try {
        val userToken = userPreferences.finmindToken.first()
        val response = RetrofitInstance.stockApiService.getTaiwanStockInfo(
            stockCode = stockCode,
            token = userToken
        )

        if (response.status == 200 && response.data.isNotEmpty()) {
            val stockData = response.data.find { it.stockId == stockCode }
            stockData?.let {
                // 從 FinMind API 的 type 欄位判斷是否為 ETF
                val isEtfFromApi = it.type.contains("ETF", ignoreCase = true)
                com.example.stonkseveryday.data.api.StockInfoResponse(
                    stockCode = it.stockId,
                    stockName = it.stockName,
                    industry = it.industry,
                    type = it.type,
                    isEtf = isEtfFromApi || isEtfByCode(stockCode)
                )
            }
        } else {
            null
        }
    } catch (e: Exception) {
        Log.e("StockRepository", "Error fetching stock info from FinMind for $stockCode", e)
        null
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

                    // 獲取該股票的累計股利
                    val totalDividends = try {
                        dividendDao.getTotalDividendsByStockCode(code) ?: 0.0
                    } catch (e: Exception) {
                        Log.e("StockRepository", "Failed to fetch dividends for $code", e)
                        0.0
                    }

                    if (stockPrice != null) {
                        val currentPrice = stockPrice.currentPrice
                        val currentValue = currentPrice * totalQuantity
                        val profitLoss = (currentPrice - averageCost) * totalQuantity
                        val profitLossPercentage = ((currentPrice - averageCost) / averageCost) * 100

                        StockHolding(
                            stockCode = code,
                            stockName = txs.first().stockName,
                            quantity = totalQuantity,
                            averageCost = averageCost,
                            currentPrice = currentPrice,
                            currentValue = currentValue,
                            profitLoss = profitLoss,
                            profitLossPercentage = profitLossPercentage,
                            positionRatio = 0.0, // 會在後面計算
                            totalDividends = totalDividends
                        )
                    } else {
                        // API 失敗，跳過此持股（不顯示錯誤資料）
                        Log.w("StockRepository", "Skipping $code due to API failure")
                        null
                    }
                } else null
            }

            // 計算總資產和持股比重
            val totalCurrentValue = stockHoldings.sumOf { it.currentValue }
            val totalCostBasis = stockHoldings.sumOf { it.averageCost * it.quantity }
            val totalDividendsSum = stockHoldings.sumOf { it.totalDividends }

            // 更新持股比重
            val updatedHoldings = stockHoldings.map { holding ->
                holding.copy(
                    positionRatio = if (totalCurrentValue > 0) {
                        (holding.currentValue / totalCurrentValue) * 100
                    } else 0.0
                )
            }

            // 計算總損益（含股利）
            val totalProfitLoss = stockHoldings.sumOf { it.profitLoss } + totalDividendsSum
            val totalProfitLossPercent = if (totalCostBasis > 0) {
                (totalProfitLoss / totalCostBasis) * 100
            } else 0.0

            // 計算今日損益
            // 這裡假設有保存昨日收盤價的機制，暫時簡化為 0
            val todayProfitLoss = 0.0
            val todayProfitLossPercent = 0.0

            StockSummary(
                totalAssets = totalCurrentValue,
                netAssets = totalCurrentValue,
                todayProfitLoss = todayProfitLoss,
                todayProfitLossPercent = todayProfitLossPercent,
                totalProfitLoss = totalProfitLoss,
                totalProfitLossPercent = totalProfitLossPercent,
                holdings = updatedHoldings
            )
        }
    }

    /**
     * 自動推算並新增股利記錄
     * 根據交易記錄的買入日期，查詢該股票的歷史股利資料，
     * 並為符合條件的交易自動建立股利記錄
     *
     * @param stockCode 股票代碼
     * @param token FinMind API Token (選填)
     * @return 成功新增的股利記錄數量
     */
    suspend fun calculateAndInsertDividends(stockCode: String, token: String = ""): Int {
        try {
            // 1. 取得該股票的所有買入交易記錄
            val transactions = allTransactions.first().filter {
                it.stockCode == stockCode && it.transactionType == com.example.stonkseveryday.data.model.TransactionType.BUY
            }

            if (transactions.isEmpty()) {
                return 0
            }

            // 2. 找出最早的買入日期
            val earliestTransaction = transactions.minByOrNull { it.transactionDate }
            val startDate = earliestTransaction?.let {
                val calendar = java.util.Calendar.getInstance()
                calendar.timeInMillis = it.transactionDate
                String.format(
                    "%04d-%02d-%02d",
                    calendar.get(java.util.Calendar.YEAR),
                    calendar.get(java.util.Calendar.MONTH) + 1,
                    calendar.get(java.util.Calendar.DAY_OF_MONTH)
                )
            } ?: return 0

            // 3. 從 FinMind API 取得股利資料
            val dividendResponse = RetrofitInstance.stockApiService
                .getTaiwanStockDividend(
                    stockCode = stockCode,
                    startDate = startDate,
                    token = token
                )

            if (dividendResponse.status != 200 || dividendResponse.data.isEmpty()) {
                return 0
            }

            var insertedCount = 0

            // 4. 為每筆符合條件的交易建立股利記錄
            for (transaction in transactions) {
                val transactionCalendar = java.util.Calendar.getInstance()
                transactionCalendar.timeInMillis = transaction.transactionDate

                // 取得該交易已有的股利記錄，避免重複新增
                val existingDividends = getDividendsByTransaction(transaction.id)
                val existingDividendDates = existingDividends.map { it.exDividendDate }.toSet()

                for (dividendData in dividendResponse.data) {
                    // 解析除息日
                    val exDividendDateStr = dividendData.date
                    val exDividendCalendar = java.util.Calendar.getInstance()
                    val dateParts = exDividendDateStr.split("-")
                    if (dateParts.size != 3) continue

                    exDividendCalendar.set(
                        dateParts[0].toInt(),
                        dateParts[1].toInt() - 1,
                        dateParts[2].toInt()
                    )

                    // 判斷：買入日期必須在除息日之前
                    if (transactionCalendar.timeInMillis < exDividendCalendar.timeInMillis) {
                        // 檢查是否已經有這個日期的股利記錄
                        if (exDividendCalendar.timeInMillis in existingDividendDates) {
                            continue
                        }

                        // 計算股利金額
                        val cashDividend = dividendData.cashDividend ?: 0.0
                        val stockDividend = dividendData.stockDividend ?: 0.0
                        val dividendYear = dividendData.dividendYear ?: ""

                        // 只有當有現金股利或股票股利時才新增記錄
                        if (cashDividend > 0 || stockDividend > 0) {
                            // 現金股利
                            if (cashDividend > 0) {
                                val dividend = com.example.stonkseveryday.data.model.Dividend(
                                    transactionId = transaction.id,
                                    stockCode = stockCode,
                                    dividendType = com.example.stonkseveryday.data.model.DividendType.CASH,
                                    exDividendDate = exDividendCalendar.timeInMillis,
                                    dividendPerShare = cashDividend,
                                    quantity = transaction.quantity,
                                    dividendAmount = cashDividend * transaction.quantity,
                                    note = if (dividendYear.isNotEmpty()) "${dividendYear}年度現金股利" else "現金股利"
                                )
                                insertDividend(dividend)
                                insertedCount++
                            }

                            // 股票股利
                            if (stockDividend > 0) {
                                val dividend = com.example.stonkseveryday.data.model.Dividend(
                                    transactionId = transaction.id,
                                    stockCode = stockCode,
                                    dividendType = com.example.stonkseveryday.data.model.DividendType.STOCK,
                                    exDividendDate = exDividendCalendar.timeInMillis,
                                    dividendPerShare = stockDividend,
                                    quantity = transaction.quantity,
                                    dividendAmount = 0.0,
                                    note = if (dividendYear.isNotEmpty()) "${dividendYear}年度股票股利 (配${stockDividend}股)" else "股票股利 (配${stockDividend}股)"
                                )
                                insertDividend(dividend)
                                insertedCount++
                            }
                        }
                    }
                }
            }

            return insertedCount
        } catch (e: Exception) {
            e.printStackTrace()
            return 0
        }
    }
}
