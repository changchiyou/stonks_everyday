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
    private val dividendCalculationRecordDao = database.dividendCalculationRecordDao()
    private val stockPriceCacheDao = database.stockPriceCacheDao()

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

    suspend fun deleteDividendsByStockCode(stockCode: String) {
        // 取得該股票的所有股利記錄並刪除
        val dividends = dividendDao.getDividendsByStockCode(stockCode).first()
        dividends.forEach { dividend ->
            dividendDao.deleteDividend(dividend)
        }
        // 同時清除股利計算記錄，讓下次可以重新計算
        dividendCalculationRecordDao.deleteRecord(stockCode)
    }

    suspend fun deleteAllDividends() {
        dividendDao.deleteAllDividends()
        dividendCalculationRecordDao.deleteAll()
    }

    suspend fun getDividendsByTransaction(transactionId: Long): List<com.example.stonkseveryday.data.model.Dividend> {
        return dividendDao.getDividendsByTransactionId(transactionId).first()
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
     * 1. 優先使用 TWSE 官方 API（有真正的即時價格和昨收，適合計算今日損益）
     * 2. 如果 TWSE 失敗，使用 FinMind API（歷史收盤價）
     *
     * @param stockCode 股票代碼 (例如: 2330)
     * @param forceRefresh 是否強制刷新（忽略快取），預設 false
     * @return 股價資訊或 null（如果兩個 API 都失敗）
     */
    suspend fun getStockPrice(stockCode: String, forceRefresh: Boolean = false): StockPriceResponse? {
        Log.d("StockRepository", "getStockPrice for $stockCode (forceRefresh=$forceRefresh)")

        // 策略 0: 先檢查快取是否新鮮（5分鐘內），除非強制刷新
        if (!forceRefresh) {
            val cachedPrice = stockPriceCacheDao.getPriceCache(stockCode)
            if (cachedPrice != null) {
                val ageInMinutes = (System.currentTimeMillis() - cachedPrice.lastUpdateTime) / (1000 * 60)
                if (ageInMinutes < 5) {
                    Log.d("StockRepository", "Using fresh cache for $stockCode (age: ${ageInMinutes}m)")
                    return StockPriceResponse(
                        stockCode = cachedPrice.stockCode,
                        currentPrice = cachedPrice.currentPrice,
                        previousClose = cachedPrice.previousClose,
                        change = cachedPrice.change,
                        changePercent = cachedPrice.changePercent,
                        timestamp = cachedPrice.lastUpdateTime,
                        isStale = false,
                        askPrice = cachedPrice.askPrice
                    )
                }
            }
        } else {
            Log.d("StockRepository", "Force refresh enabled, skipping cache for $stockCode")
        }

        // 策略 0.5: 如果強制刷新，仍然讀取快取用於後續 fallback
        val cachedPrice = if (forceRefresh) stockPriceCacheDao.getPriceCache(stockCode) else null

        // 策略 1: 快取過期，嘗試 TWSE API（有即時價格和昨收，適合計算今日損益）
        val twsePrice = getStockPriceFromTwse(stockCode)
        if (twsePrice != null) {
            Log.i("StockRepository", "TWSE API success: $stockCode")
            // 成功時更新快取
            cacheStockPrice(twsePrice)
            return twsePrice
        }

        // 策略 2: TWSE 失敗時使用 FinMind API（可能是非交易時段或網路問題）
        val userToken = userPreferences.finmindToken.first()
        if (userToken.isNotBlank()) {
            Log.d("StockRepository", "TWSE failed, trying FinMind API")
            val finMindPrice = getStockPriceFromFinMind(stockCode, userToken)
            if (finMindPrice != null) {
                Log.i("StockRepository", "FinMind API success: $stockCode")
                // 成功時更新快取
                cacheStockPrice(finMindPrice)
                return finMindPrice
            }
        }

        // 策略 3: 兩個 API 都失敗，使用快取的股價（即使過期）
        val fallbackCache = cachedPrice ?: stockPriceCacheDao.getPriceCache(stockCode)
        if (fallbackCache != null) {
            val ageInHours = (System.currentTimeMillis() - fallbackCache.lastUpdateTime) / (1000 * 60 * 60)
            Log.w("StockRepository", "Both APIs failed for $stockCode, using stale cache (age: ${ageInHours}h)")
            return StockPriceResponse(
                stockCode = fallbackCache.stockCode,
                currentPrice = fallbackCache.currentPrice,
                previousClose = fallbackCache.previousClose,
                change = fallbackCache.change,
                changePercent = fallbackCache.changePercent,
                timestamp = fallbackCache.lastUpdateTime,
                isStale = true,  // 標記為過期
                askPrice = fallbackCache.askPrice
            )
        }

        Log.e("StockRepository", "No data available for $stockCode (APIs failed and no cache)")
        return null
    }

    /**
     * 快取股價到資料庫
     */
    private suspend fun cacheStockPrice(price: StockPriceResponse) {
        val cache = com.example.stonkseveryday.data.model.StockPriceCache(
            stockCode = price.stockCode,
            currentPrice = price.currentPrice,
            previousClose = price.previousClose,
            change = price.change,
            changePercent = price.changePercent,
            lastUpdateTime = System.currentTimeMillis(),
            isStale = false,
            askPrice = price.askPrice
        )
        stockPriceCacheDao.insertOrUpdate(cache)
    }

    /**
     * 清除所有股價快取
     * 用於手動刷新時強制重新抓取最新價格
     */
    suspend fun clearPriceCache() {
        Log.d("StockRepository", "清除所有股價快取")
        stockPriceCacheDao.deleteAll()
    }

    /**
     * 清除特定股票的價格快取
     */
    suspend fun clearPriceCacheForStock(stockCode: String) {
        Log.d("StockRepository", "清除 $stockCode 的股價快取")
        stockPriceCacheDao.delete(stockCode)
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

        val calendar = java.util.Calendar.getInstance()
        calendar.add(java.util.Calendar.DAY_OF_YEAR, -30)  // 查詢最近 30 天（考慮農曆新年等長假）
        val startDate = dateFormat.format(calendar.time)

        Log.d("StockRepository", "FinMind API 查詢: code=$stockCode, startDate=$startDate, endDate=$today")

        val response = RetrofitInstance.stockApiService.getTaiwanStockPrice(
            stockCode = stockCode,
            startDate = startDate,
            endDate = today,
            token = userToken
        )

        Log.d("StockRepository", "FinMind API 回應: status=${response.status}, data.size=${response.data.size}")

        if (response.status == 200 && response.data.isNotEmpty()) {
            val latestData = response.data.last()
            val previousData = response.data.getOrNull(response.data.size - 2)

            // FinMind 只有歷史收盤價，沒有即時價格
            // currentPrice = 最新一筆的收盤價（可能是今天或最近交易日）
            // previousClose = 前一個交易日的收盤價
            val currentPrice = latestData.close
            val previousClose = previousData?.close ?: latestData.close
            val change = currentPrice - previousClose
            val changePercent = if (previousClose != 0.0) {
                (change / previousClose) * 100
            } else 0.0

            Log.d("StockRepository", "FinMind: current=$currentPrice, previous=$previousClose, date=${latestData.date}")

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
     *
     * Fallback 優先級（按照證券所常見處理方式）：
     * 1. z (最新成交價) - 盤中有成交時
     * 2. s (試撮價) - 開盤前試撮時段
     * 3. a (賣一價) - 盤中無成交但有委託時，優先使用賣一價
     * 4. b (買一價) - 如果沒有賣價，才使用買一價
     * 5. o (開盤價) - 開盤後但無其他價格時
     * 6. y (昨收價) - 最後的選擇
     */
    private suspend fun getStockPriceFromTwse(stockCode: String): StockPriceResponse? = try {
        // 預設為上市股票，可根據需求調整
        val formattedCode = "tse_${stockCode}.tw"

        val response = com.example.stonkseveryday.data.api.TwseRetrofitInstance.twseApiService
            .getStockInfo(stockCode = formattedCode)

        if (response.code == "0000" && !response.data.isNullOrEmpty()) {
            val stockInfo = response.data.first()
            parseTwseStockPrice(stockCode, stockInfo)
        } else {
            // 可能是上櫃股票，嘗試 OTC
            val otcCode = "otc_${stockCode}.tw"
            val otcResponse = com.example.stonkseveryday.data.api.TwseRetrofitInstance.twseApiService
                .getStockInfo(stockCode = otcCode)

            if (otcResponse.code == "0000" && !otcResponse.data.isNullOrEmpty()) {
                val stockInfo = otcResponse.data.first()
                parseTwseStockPrice(stockCode, stockInfo)
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
     * 解析 TWSE API 回傳的股價資料，使用 fallback 機制
     *
     * @param stockCode 股票代碼
     * @param stockInfo TWSE API 回傳的股票資訊
     * @return 股價回應或 null
     */
    private fun parseTwseStockPrice(
        stockCode: String,
        stockInfo: com.example.stonkseveryday.data.api.TwseStockInfo
    ): StockPriceResponse? {
        // 昨收價（必須有，否則無法計算漲跌）
        val previousClose = stockInfo.previousClose.replace(",", "").toDoubleOrNull()
        if (previousClose == null) {
            Log.e("StockRepository", "TWSE: $stockCode 沒有昨收價，無法計算")
            return null
        }

        var currentPrice: Double? = null
        var priceSource = ""

        // 策略 1: 優先使用最新成交價 (z)
        stockInfo.currentPrice.replace(",", "").toDoubleOrNull()?.let {
            currentPrice = it
            priceSource = "成交價"
        }

        // 策略 2: 試撮價格 (s) - 開盤前試撮時段
        if (currentPrice == null && !stockInfo.trialMatchPrice.isNullOrEmpty()) {
            stockInfo.trialMatchPrice.replace(",", "").toDoubleOrNull()?.let {
                if (it > 0) {
                    currentPrice = it
                    priceSource = "試撮價"
                }
            }
        }

        // 提取賣一價（不論是否用於現價，都需要記錄）
        val askPrice = parseFirstPrice(stockInfo.askPrices)

        // 策略 3: 委託價 (a/b) - 盤中無成交但有委託
        // 按照證券所常見處理方式，優先使用賣一價作為即時現價
        if (currentPrice == null) {
            val bidPrice = parseFirstPrice(stockInfo.bidPrices) // 買一價

            when {
                askPrice != null -> {
                    // 優先使用賣一價（證券所常見做法）
                    currentPrice = askPrice
                    priceSource = "賣一價"
                }
                bidPrice != null -> {
                    // 如果沒有賣價，才使用買一價
                    currentPrice = bidPrice
                    priceSource = "買一價"
                }
            }
        }

        // 策略 4: 開盤價 (o) - 今日已開盤但無其他價格
        if (currentPrice == null && !stockInfo.openPrice.isEmpty() && stockInfo.openPrice != "-") {
            stockInfo.openPrice.replace(",", "").toDoubleOrNull()?.let {
                if (it > 0) {
                    currentPrice = it
                    priceSource = "開盤價"
                }
            }
        }

        // 策略 5: 昨收價 (y) - 最後的選擇（開盤前或無任何資料）
        if (currentPrice == null) {
            currentPrice = previousClose
            priceSource = "昨收價"
        }

        // 計算漲跌
        val change = currentPrice!! - previousClose
        val changePercent = if (previousClose != 0.0) {
            (change / previousClose) * 100
        } else 0.0

        Log.i(
            "StockRepository",
            "TWSE: $stockCode = $currentPrice [$priceSource] (昨收:$previousClose, 賣一:${askPrice ?: "N/A"}, 時間:${stockInfo.tradeTime})"
        )

        return StockPriceResponse(
            stockCode = stockCode,
            currentPrice = currentPrice,
            previousClose = previousClose,
            change = change,
            changePercent = changePercent,
            timestamp = System.currentTimeMillis(),
            askPrice = askPrice  // 新增賣一價
        )
    }

    /**
     * 解析 TWSE 委託價格字串的第一檔價格
     * 格式: "1930.0000_1935.0000_1940.0000_1945.0000_1950.0000_"
     *
     * @param pricesString 委託價格字串
     * @return 第一檔價格或 null
     */
    private fun parseFirstPrice(pricesString: String?): Double? {
        if (pricesString.isNullOrEmpty() || pricesString == "-") return null

        return pricesString
            .split("_")
            .firstOrNull()
            ?.replace(",", "")
            ?.toDoubleOrNull()
            ?.takeIf { it > 0 }
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

    fun calculateSummary(transactions: List<StockTransaction>, includeDividends: Boolean = true): Flow<StockSummary> {
        Log.d("StockRepository", "calculateSummary called with includeDividends = $includeDividends")
        return allTransactions.map { txList ->
            val holdings = mutableMapOf<String, MutableList<StockTransaction>>()

            // Group transactions by stock code
            txList.forEach { tx ->
                holdings.getOrPut(tx.stockCode) { mutableListOf() }.add(tx)
            }

            // 用於計算今日損益的資料
            data class HoldingWithPrice(
                val holding: StockHolding,
                val previousClose: Double
            )

            // Calculate holdings
            val stockHoldingsWithPrice = holdings.mapNotNull { (code, txs) ->
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

                    // 嘗試從 API 取得即時股價
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

                    // 獲取股利查詢狀態
                    val dividendQueryStatus = try {
                        dividendCalculationRecordDao.getRecord(code)?.queryStatus
                            ?: com.example.stonkseveryday.data.model.DividendQueryStatus.SUCCESS
                    } catch (e: Exception) {
                        Log.e("StockRepository", "Failed to fetch dividend query status for $code", e)
                        com.example.stonkseveryday.data.model.DividendQueryStatus.SUCCESS
                    }

                    // API 成功時使用即時股價，失敗時使用成本價（讓使用者至少能看到持股）
                    val currentPrice = stockPrice?.currentPrice ?: averageCost
                    val previousClose = stockPrice?.previousClose ?: averageCost

                    if (stockPrice != null || totalQuantity > 0) {  // 只要有持股就顯示
                        val currentValue = currentPrice * totalQuantity
                        val baseProfitLoss = (currentPrice - averageCost) * totalQuantity

                        // 根據開關決定損益是否含股利
                        val profitLoss = if (includeDividends) {
                            baseProfitLoss + totalDividends
                        } else {
                            baseProfitLoss
                        }
                        Log.d("StockRepository", "$code: basePL=$baseProfitLoss, dividends=$totalDividends, include=$includeDividends, finalPL=$profitLoss")

                        val costBasis = averageCost * totalQuantity

                        // 調整後成本（扣除股利，可能為負）
                        val adjustedCostBasis = if (includeDividends) {
                            costBasis - totalDividends
                        } else {
                            costBasis
                        }

                        // 是否達成零成本
                        val isZeroCost = includeDividends && (adjustedCostBasis <= 0)

                        // 報酬率計算：成本 > 0 時才計算，否則為 0（UI 會特殊顯示）
                        val profitLossPercentage = if (adjustedCostBasis > 0) {
                            (profitLoss / adjustedCostBasis) * 100
                        } else {
                            0.0
                        }

                        val holding = StockHolding(
                            stockCode = code,
                            stockName = txs.first().stockName,
                            quantity = totalQuantity,
                            averageCost = averageCost,
                            currentPrice = currentPrice,
                            currentValue = currentValue,
                            profitLoss = profitLoss,
                            profitLossPercentage = profitLossPercentage,
                            positionRatio = 0.0, // 會在後面計算
                            totalDividends = totalDividends,
                            isPriceStale = stockPrice?.isStale ?: (stockPrice == null),  // API 失敗或使用過期快取
                            adjustedCost = adjustedCostBasis,
                            isZeroCost = isZeroCost,
                            dividendQueryStatus = dividendQueryStatus,
                            askPrice = stockPrice?.askPrice,  // 賣一價
                            todayChangePercent = stockPrice?.changePercent ?: 0.0  // 今日漲跌幅
                        )

                        HoldingWithPrice(holding, previousClose)
                    } else {
                        // API 失敗，跳過此持股（不顯示錯誤資料）
                        Log.w("StockRepository", "Skipping $code due to API failure")
                        null
                    }
                } else null
            }

            val stockHoldings = stockHoldingsWithPrice.map { it.holding }

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

            // 計算總損益（已經在個別持股計算時處理了股利）
            val totalProfitLoss = updatedHoldings.sumOf { it.profitLoss }

            // 計算調整後總成本（扣除股利，可能為負）
            val adjustedTotalCost = if (includeDividends) {
                totalCostBasis - totalDividendsSum
            } else {
                totalCostBasis
            }

            // 是否達成零成本投資組合
            val isPortfolioZeroCost = includeDividends && (adjustedTotalCost <= 0)

            // 總報酬率計算：成本 > 0 時才計算，否則為 0（UI 會特殊顯示）
            val totalProfitLossPercent = if (adjustedTotalCost > 0) {
                (totalProfitLoss / adjustedTotalCost) * 100
            } else {
                0.0
            }

            // 計算今日損益：(現價 - 昨收) * 持股數
            val todayProfitLoss = stockHoldingsWithPrice.sumOf { hwp ->
                (hwp.holding.currentPrice - hwp.previousClose) * hwp.holding.quantity
            }

            // 計算昨日總市值
            val yesterdayTotalValue = stockHoldingsWithPrice.sumOf { hwp ->
                hwp.previousClose * hwp.holding.quantity
            }

            val todayProfitLossPercent = if (yesterdayTotalValue > 0) {
                (todayProfitLoss / yesterdayTotalValue) * 100
            } else 0.0

            StockSummary(
                totalAssets = totalCurrentValue,
                netAssets = totalCurrentValue,
                todayProfitLoss = todayProfitLoss,
                todayProfitLossPercent = todayProfitLossPercent,
                totalProfitLoss = totalProfitLoss,
                totalProfitLossPercent = totalProfitLossPercent,
                holdings = updatedHoldings,
                adjustedTotalCost = adjustedTotalCost,
                isPortfolioZeroCost = isPortfolioZeroCost
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
                Log.w("StockRepository", "$stockCode 沒有買入交易記錄")
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
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

            Log.d("StockRepository", "查詢股利: code=$stockCode, startDate=$startDate, endDate=$today, token=${if(token.isNotBlank()) "有" else "無"}")

            val dividendResponse = RetrofitInstance.stockApiService
                .getTaiwanStockDividend(
                    stockCode = stockCode,
                    startDate = startDate,
                    endDate = today,
                    token = token
                )

            Log.d("StockRepository", "股利查詢結果: status=${dividendResponse.status}, data.size=${dividendResponse.data.size}")

            // 判斷查詢狀態
            val queryStatus = when {
                dividendResponse.status != 200 -> {
                    Log.w("StockRepository", "API 回應異常: status=${dividendResponse.status}")
                    com.example.stonkseveryday.data.model.DividendQueryStatus.API_ERROR
                }
                dividendResponse.data.isEmpty() -> {
                    // 可能是「該股票從未配息」或「FinMind 查不到該股票」
                    // 檢查 message 來判斷
                    if (dividendResponse.message.contains("stock not found", ignoreCase = true) ||
                        dividendResponse.message.contains("查無此股票", ignoreCase = true)) {
                        Log.w("StockRepository", "FinMind 查不到該股票: $stockCode")
                        com.example.stonkseveryday.data.model.DividendQueryStatus.NOT_FOUND
                    } else {
                        Log.w("StockRepository", "該股票歷年無股利資料")
                        com.example.stonkseveryday.data.model.DividendQueryStatus.SUCCESS
                    }
                }
                else -> com.example.stonkseveryday.data.model.DividendQueryStatus.SUCCESS
            }

            // 如果是 API 錯誤或查不到股票，記錄狀態後返回 0
            if (queryStatus != com.example.stonkseveryday.data.model.DividendQueryStatus.SUCCESS) {
                val record = com.example.stonkseveryday.data.model.DividendCalculationRecord(
                    stockCode = stockCode,
                    lastCalculationTime = System.currentTimeMillis(),
                    recordCount = 0,
                    queryStatus = queryStatus
                )
                dividendCalculationRecordDao.insertOrUpdate(record)
                Log.d("StockRepository", "已記錄 $stockCode 的查詢狀態: $queryStatus")
                return 0
            }

            // data 為空但查詢成功，表示該股票歷年無股利
            if (dividendResponse.data.isEmpty()) {
                val record = com.example.stonkseveryday.data.model.DividendCalculationRecord(
                    stockCode = stockCode,
                    lastCalculationTime = System.currentTimeMillis(),
                    recordCount = 0,
                    queryStatus = com.example.stonkseveryday.data.model.DividendQueryStatus.SUCCESS
                )
                dividendCalculationRecordDao.insertOrUpdate(record)
                Log.d("StockRepository", "已記錄 $stockCode 無股利資料")
                return 0
            }

            // 4. API 成功取得資料後，才刪除舊的股利記錄
            Log.d("StockRepository", "準備刪除 $stockCode 的舊股利記錄...")
            deleteDividendsByStockCode(stockCode)
            Log.d("StockRepository", "已清除 $stockCode 的舊股利記錄")

            // 列印第一筆資料來檢查欄位
            if (dividendResponse.data.isNotEmpty()) {
                val firstData = dividendResponse.data.first()
                Log.d("StockRepository", "第一筆股利完整資料: stockId=${firstData.stockId}, date=${firstData.date}, year=${firstData.dividendYear}, cash=${firstData.cashDividend}, stock=${firstData.stockDividend}")
            }

            var insertedCount = 0

            Log.d("StockRepository", "開始處理 ${transactions.size} 筆交易記錄")

            // 4. 為每筆符合條件的交易建立股利記錄
            for (transaction in transactions) {
                val transactionCalendar = java.util.Calendar.getInstance()
                transactionCalendar.timeInMillis = transaction.transactionDate
                // 將時間歸零到當天 00:00:00，避免時分秒影響日期比較
                transactionCalendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                transactionCalendar.set(java.util.Calendar.MINUTE, 0)
                transactionCalendar.set(java.util.Calendar.SECOND, 0)
                transactionCalendar.set(java.util.Calendar.MILLISECOND, 0)
                val txDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(transaction.transactionDate))

                // 取得該交易已有的股利記錄，避免重複新增
                val existingDividends = getDividendsByTransaction(transaction.id)
                val existingDividendDates = existingDividends.map { it.exDividendDate }.toSet()

                Log.d("StockRepository", "交易 ${transaction.id} (買入日: $txDateStr, 股數: ${transaction.quantity}, 已有股利: ${existingDividends.size} 筆)")
                Log.d("StockRepository", "  已存在的除息日期: ${existingDividendDates.map { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it)) }}")

                for (dividendData in dividendResponse.data) {
                    // 先列印原始資料來除錯
                    Log.d("StockRepository", "  原始股利資料: payDate=${dividendData.date}, cashExDate=${dividendData.cashExDividendDate}, stockExDate=${dividendData.stockExDividendDate}, cash=${dividendData.cashDividend}, stock=${dividendData.stockDividend}")

                    val cashDividend = dividendData.cashDividend ?: 0.0
                    val stockDividend = dividendData.stockDividend ?: 0.0
                    val dividendYear = dividendData.dividendYear ?: ""

                    // 處理現金股利
                    if (cashDividend > 0 && !dividendData.cashExDividendDate.isNullOrEmpty()) {
                        val exDividendDateStr = dividendData.cashExDividendDate
                        val exDividendCalendar = java.util.Calendar.getInstance()
                        val dateParts = exDividendDateStr.split("-")
                        if (dateParts.size != 3) continue

                        exDividendCalendar.set(
                            dateParts[0].toInt(),
                            dateParts[1].toInt() - 1,
                            dateParts[2].toInt(),
                            0, 0, 0  // 時、分、秒都設為 0
                        )
                        exDividendCalendar.set(java.util.Calendar.MILLISECOND, 0)

                        // 判斷：買入日期必須在除息交易日之前（同一天不算）
                        if (transactionCalendar.timeInMillis < exDividendCalendar.timeInMillis) {
                            // 檢查是否已經有這個日期的股利記錄
                            if (exDividendCalendar.timeInMillis in existingDividendDates) {
                                Log.d("StockRepository", "  現金股利 $exDividendDateStr 已存在，跳過")
                                continue
                            }

                            Log.d("StockRepository", "  現金股利 $exDividendDateStr: 每股=$cashDividend, 股數=${transaction.quantity}, 總額=${cashDividend * transaction.quantity}")

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
                            Log.i("StockRepository", "  ✓ 新增現金股利: $exDividendDateStr")
                            insertedCount++
                        }
                    }

                    // 處理股票股利
                    if (stockDividend > 0 && !dividendData.stockExDividendDate.isNullOrEmpty()) {
                        val exDividendDateStr = dividendData.stockExDividendDate
                        val exDividendCalendar = java.util.Calendar.getInstance()
                        val dateParts = exDividendDateStr.split("-")
                        if (dateParts.size != 3) continue

                        exDividendCalendar.set(
                            dateParts[0].toInt(),
                            dateParts[1].toInt() - 1,
                            dateParts[2].toInt(),
                            0, 0, 0  // 時、分、秒都設為 0
                        )
                        exDividendCalendar.set(java.util.Calendar.MILLISECOND, 0)

                        // 判斷：買入日期必須在除權交易日之前（同一天不算）
                        if (transactionCalendar.timeInMillis < exDividendCalendar.timeInMillis) {
                            // 檢查是否已經有這個日期的股利記錄
                            if (exDividendCalendar.timeInMillis in existingDividendDates) {
                                Log.d("StockRepository", "  股票股利 $exDividendDateStr 已存在，跳過")
                                continue
                            }

                            Log.d("StockRepository", "  股票股利 $exDividendDateStr: 每股配${stockDividend}股")

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
                            Log.i("StockRepository", "  ✓ 新增股票股利: $exDividendDateStr")
                            insertedCount++
                        }
                    }
                }
            }

            Log.i("StockRepository", "股利計算完成，共新增 $insertedCount 筆記錄")

            // Debug: 列印所有交易的最終股利統計
            for (transaction in transactions) {
                val finalDividends = getDividendsByTransaction(transaction.id)
                val finalTotal = finalDividends.sumOf { it.dividendAmount }
                Log.i("StockRepository", "【最終統計】交易ID=${transaction.id}, 股利記錄數=${finalDividends.size}, 累積股利=$finalTotal")
            }

            // 記錄計算時間（成功）
            val record = com.example.stonkseveryday.data.model.DividendCalculationRecord(
                stockCode = stockCode,
                lastCalculationTime = System.currentTimeMillis(),
                recordCount = insertedCount,
                queryStatus = com.example.stonkseveryday.data.model.DividendQueryStatus.SUCCESS
            )
            dividendCalculationRecordDao.insertOrUpdate(record)
            Log.d("StockRepository", "已記錄 $stockCode 的股利計算時間")

            return insertedCount
        } catch (e: java.net.UnknownHostException) {
            Log.e("StockRepository", "網路連線失敗", e)
            throw e  // 讓 ViewModel 處理
        } catch (e: java.net.SocketTimeoutException) {
            Log.e("StockRepository", "連線逾時", e)
            throw e  // 讓 ViewModel 處理
        } catch (e: Exception) {
            Log.e("StockRepository", "計算股利時發生錯誤: ${e.javaClass.simpleName} - ${e.message}", e)
            e.printStackTrace()
            throw e  // 讓 ViewModel 處理
        }
    }

    /**
     * 檢查股票是否需要更新股利
     * @param stockCode 股票代碼
     * @return true 表示需要更新（超過 1 天沒更新、從未計算過、或上次 API 錯誤）
     */
    suspend fun shouldUpdateDividends(stockCode: String): Boolean {
        val record = dividendCalculationRecordDao.getRecord(stockCode)
        if (record == null) {
            Log.d("StockRepository", "$stockCode 從未計算過股利，需要更新")
            return true
        }

        // 如果上次查詢是 API_ERROR，立即重試（每次 app 啟動或刷新都會重試）
        if (record.queryStatus == com.example.stonkseveryday.data.model.DividendQueryStatus.API_ERROR) {
            Log.d("StockRepository", "$stockCode 上次查詢發生錯誤，需要重試")
            return true
        }

        // 其他情況（SUCCESS 或 NOT_FOUND）檢查時間，每天更新一次
        val daysSinceLastCalculation = (System.currentTimeMillis() - record.lastCalculationTime) / (1000 * 60 * 60 * 24)
        val shouldUpdate = daysSinceLastCalculation >= 1
        Log.d("StockRepository", "$stockCode 距上次計算 $daysSinceLastCalculation 天，查詢狀態=${record.queryStatus}，${if (shouldUpdate) "需要" else "不需要"}更新")
        return shouldUpdate
    }

    /**
     * 更新所有過期的股利資料
     * 在 app 啟動時呼叫
     */
    suspend fun updateStaleDividends(token: String = ""): Int {
        Log.i("StockRepository", "開始檢查並更新過期的股利資料")

        // 取得所有不重複的股票代碼
        val stockCodes = allTransactions.first()
            .map { it.stockCode }
            .distinct()

        var totalUpdated = 0
        for (stockCode in stockCodes) {
            if (shouldUpdateDividends(stockCode)) {
                val count = calculateAndInsertDividends(stockCode, token)
                if (count > 0) {
                    totalUpdated++
                }
            }
        }

        Log.i("StockRepository", "股利更新完成，共更新 $totalUpdated 支股票")
        return totalUpdated
    }
}
