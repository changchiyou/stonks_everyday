package com.example.stonkseveryday.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.stonkseveryday.data.backup.BackupManager
import com.example.stonkseveryday.data.database.StockDatabase
import com.example.stonkseveryday.data.model.*
import com.example.stonkseveryday.data.repository.StockRepository
import com.example.stonkseveryday.widget.StockWidget
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class StockViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: StockRepository
    private val appContext = application.applicationContext
    private val backupManager = BackupManager(appContext)
    private val userPreferences = com.example.stonkseveryday.data.preferences.UserPreferences(appContext)

    val allTransactions: Flow<List<StockTransaction>>
    val stockSummary: Flow<StockSummary>

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _lastRefreshTime = MutableStateFlow(System.currentTimeMillis())
    val lastRefreshTime: StateFlow<Long> = _lastRefreshTime.asStateFlow()

    private val _refreshTrigger = MutableStateFlow(0L)

    init {
        val dao = StockDatabase.getDatabase(application).stockTransactionDao()
        repository = StockRepository(dao, appContext)
        allTransactions = repository.allTransactions

        // 結合 includeDividends 設定、交易資料和刷新觸發來計算摘要
        stockSummary = combine(
            userPreferences.includeDividends,
            _refreshTrigger
        ) { includeDividends, _ ->
            android.util.Log.d("StockViewModel", "重新計算摘要: includeDividends=$includeDividends")
            includeDividends
        }.flatMapLatest { includeDividends ->
            repository.calculateSummary(emptyList(), includeDividends)
        }.stateIn(
            viewModelScope,
            SharingStarted.Lazily,
            StockSummary(
                totalAssets = 0.0,
                netAssets = 0.0,
                todayProfitLoss = 0.0,
                todayProfitLossPercent = 0.0,
                totalProfitLoss = 0.0,
                totalProfitLossPercent = 0.0,
                holdings = emptyList()
            )
        )
    }

    fun insertTransaction(transaction: StockTransaction) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                repository.insertTransaction(transaction)
                _errorMessage.value = null
                // Update widget after inserting transaction
                updateWidget()

                // 自動計算該股票的股利
                val token = userPreferences.finmindToken.first()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    repository.calculateAndInsertDividends(transaction.stockCode, token)
                }
            } catch (e: Exception) {
                _errorMessage.value = "新增交易失敗: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateTransaction(transaction: StockTransaction) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                repository.updateTransaction(transaction)
                _errorMessage.value = null
                // Update widget after updating transaction
                updateWidget()

                // 自動計算該股票的股利
                val token = userPreferences.finmindToken.first()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    repository.calculateAndInsertDividends(transaction.stockCode, token)
                }
            } catch (e: Exception) {
                _errorMessage.value = "更新交易失敗: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteTransaction(transaction: StockTransaction) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                repository.deleteTransaction(transaction)
                _errorMessage.value = null
                // Update widget after deleting transaction
                updateWidget()
            } catch (e: Exception) {
                _errorMessage.value = "刪除交易失敗: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteTransaction(transactionId: Long) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                // 找到對應的交易記錄
                val transactions = allTransactions.first()
                val transaction = transactions.find { it.id == transactionId }

                if (transaction != null) {
                    repository.deleteTransaction(transaction)
                    _errorMessage.value = null
                    // Update widget after deleting transaction
                    updateWidget()
                } else {
                    _errorMessage.value = "找不到要刪除的交易記錄"
                }
            } catch (e: Exception) {
                _errorMessage.value = "刪除交易失敗: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    fun clearSuccessMessage() {
        _successMessage.value = null
    }

    /**
     * 備份交易資料到 URI
     */
    fun backupTransactions(uri: Uri) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val success = backupManager.backupTransactionsToUri(uri)
                if (success) {
                    _successMessage.value = "交易資料備份成功"
                    _errorMessage.value = null
                } else {
                    _errorMessage.value = "交易資料備份失敗"
                }
            } catch (e: Exception) {
                _errorMessage.value = "交易資料備份失敗: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 從 URI 恢復交易資料
     */
    fun restoreTransactions(uri: Uri, token: String = "") {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val success = backupManager.restoreTransactionsFromUri(uri, token = token)
                if (success) {
                    _successMessage.value = "交易資料恢復成功"
                    _errorMessage.value = null
                    updateWidget()
                } else {
                    _errorMessage.value = "交易資料恢復失敗"
                }
            } catch (e: Exception) {
                _errorMessage.value = "交易資料恢復失敗: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 備份個人設定到 URI
     */
    fun backupSettings(uri: Uri) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val success = backupManager.backupSettingsToUri(uri)
                if (success) {
                    _successMessage.value = "個人設定備份成功"
                    _errorMessage.value = null
                } else {
                    _errorMessage.value = "個人設定備份失敗"
                }
            } catch (e: Exception) {
                _errorMessage.value = "個人設定備份失敗: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 從 URI 恢復個人設定
     */
    fun restoreSettings(uri: Uri) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val success = backupManager.restoreSettingsFromUri(uri)
                if (success) {
                    _successMessage.value = "個人設定恢復成功"
                    _errorMessage.value = null
                } else {
                    _errorMessage.value = "個人設定恢復失敗"
                }
            } catch (e: Exception) {
                _errorMessage.value = "個人設定恢復失敗: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 備份資料到 URI（舊版相容）
     */
    @Deprecated("請使用 backupTransactions() 或 backupSettings()")
    fun backupData(uri: Uri) {
        backupTransactions(uri)
    }

    /**
     * 從 URI 恢復資料（舊版相容）
     */
    @Deprecated("請使用 restoreTransactions() 或 restoreSettings()")
    fun restoreData(uri: Uri, token: String = "") {
        restoreTransactions(uri, token)
    }

    /**
     * 清除所有資料
     */
    fun clearAllData() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val success = backupManager.clearAllData()
                if (success) {
                    _successMessage.value = "清除成功"
                    _errorMessage.value = null
                    updateWidget()
                } else {
                    _errorMessage.value = "清除失敗"
                }
            } catch (e: Exception) {
                _errorMessage.value = "清除失敗: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 取得股票資訊（包含名稱和是否為 ETF）
     */
    suspend fun fetchStockInfo(stockCode: String): com.example.stonkseveryday.data.api.StockInfoResponse? {
        return try {
            repository.getStockInfo(stockCode)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 取得股票名稱（向後相容）
     */
    suspend fun fetchStockName(stockCode: String): String? {
        return try {
            val stockInfo = repository.getStockInfo(stockCode)
            stockInfo?.stockName
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 取得持股詳細資訊（按交易明細）
     */
    suspend fun getHoldingDetails(stockCode: String, currentPrice: Double): List<HoldingDetail> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val transactions = allTransactions.first().filter {
                it.stockCode == stockCode && it.transactionType == TransactionType.BUY
            }

            transactions.map { tx ->
                val dividends = repository.getDividendsByTransaction(tx.id)
                val totalDividends = dividends.sumOf { it.dividendAmount }

                // Debug: 列印每筆交易的股利明細
                android.util.Log.d("HoldingDetail", "交易ID=${tx.id}, 買入日期=${java.text.SimpleDateFormat("yyyy-MM-dd").format(java.util.Date(tx.transactionDate))}, 股數=${tx.quantity}")
                android.util.Log.d("HoldingDetail", "  股利記錄數量: ${dividends.size}")
                dividends.forEachIndexed { index, div ->
                    android.util.Log.d("HoldingDetail", "  [$index] 除息日=${java.text.SimpleDateFormat("yyyy-MM-dd").format(java.util.Date(div.exDividendDate))}, 每股=${div.dividendPerShare}, 股數=${div.quantity}, 金額=${div.dividendAmount}")
                }
                android.util.Log.d("HoldingDetail", "  累積股利總額: $totalDividends")
                val holdingDays = TimeUnit.MILLISECONDS.toDays(
                    System.currentTimeMillis() - tx.transactionDate
                ).toInt()

                val currentValue = currentPrice * tx.quantity
                val costBasis = tx.totalAmount
                val unrealizedPL = currentValue - costBasis
                val unrealizedPLPercent = if (costBasis > 0) {
                    (unrealizedPL / costBasis) * 100
                } else 0.0

                val plWithDividends = unrealizedPL + totalDividends
                val plWithDividendsPercent = if (costBasis > 0) {
                    (plWithDividends / costBasis) * 100
                } else 0.0

                HoldingDetail(
                    transaction = tx,
                    currentPrice = currentPrice,
                    currentValue = currentValue,
                    unrealizedPL = unrealizedPL,
                    unrealizedPLPercent = unrealizedPLPercent,
                    dividends = dividends,
                    totalDividends = totalDividends,
                    holdingDays = holdingDays,
                    plWithDividends = plWithDividends,
                    plWithDividendsPercent = plWithDividendsPercent
                )
            }
        }

    /**
     * 自動計算並新增股利記錄
     * 會先嘗試計算新股利，成功後才清除舊記錄並插入新記錄
     */
    fun calculateDividends(stockCode: String, token: String = "") {
        viewModelScope.launch {
            try {
                _isLoading.value = true

                // 先嘗試計算（不實際插入資料庫），確認 API 是否正常
                android.util.Log.d("StockViewModel", "開始計算 $stockCode 的股利...")

                // 重新計算並插入股利（內部會先刪除舊記錄）
                val count = repository.calculateAndInsertDividends(stockCode, token)

                if (count > 0) {
                    _successMessage.value = "成功計算 $count 筆股利記錄"
                    _errorMessage.value = null
                } else if (count == 0) {
                    _errorMessage.value = "未找到符合條件的股利資料\n可能原因：買入日期晚於所有除息日，或該股票未配息"
                } else {
                    _errorMessage.value = "計算失敗：未知錯誤"
                }
            } catch (e: java.net.UnknownHostException) {
                _errorMessage.value = "網路連線失敗\n請檢查網路連線後重試"
                android.util.Log.e("StockViewModel", "Network error", e)
            } catch (e: java.net.SocketTimeoutException) {
                _errorMessage.value = "API 請求逾時\n請稍後再試"
                android.util.Log.e("StockViewModel", "Timeout error", e)
            } catch (e: Exception) {
                _errorMessage.value = "計算股利時發生錯誤\n錯誤類型：${e.javaClass.simpleName}\n錯誤訊息：${e.message ?: "未知錯誤"}"
                android.util.Log.e("StockViewModel", "Calculate dividends error", e)
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 計算所有持股的股利
     */
    fun calculateAllDividends(token: String = "") {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                // 取得所有不重複的股票代碼
                val stockCodes = allTransactions.first()
                    .map { it.stockCode }
                    .distinct()

                var totalCount = 0
                for (stockCode in stockCodes) {
                    val count = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        repository.calculateAndInsertDividends(stockCode, token)
                    }
                    totalCount += count
                }

                if (totalCount > 0) {
                    _successMessage.value = "成功計算 ${stockCodes.size} 支股票，新增 $totalCount 筆股利記錄"
                    _errorMessage.value = null
                } else {
                    _successMessage.value = "已完成股利計算，無新增記錄"
                    _errorMessage.value = null
                }
            } catch (e: Exception) {
                _errorMessage.value = "計算股利失敗: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 刷新股票價格和摘要資訊
     */
    fun refreshStockPrices() {
        viewModelScope.launch {
            try {
                _isRefreshing.value = true
                android.util.Log.d("StockViewModel", "開始刷新股價")

                // 先清除所有股價快取，強制重新抓取
                repository.clearPriceCache()
                android.util.Log.d("StockViewModel", "已清除股價快取")

                // 觸發重新計算摘要（會重新呼叫 API 取得最新股價）
                val now = System.currentTimeMillis()
                _refreshTrigger.value = now
                _lastRefreshTime.value = now

                // 等待一下讓 API 呼叫完成
                kotlinx.coroutines.delay(1000)
                android.util.Log.d("StockViewModel", "股價刷新完成")
            } catch (e: Exception) {
                android.util.Log.e("StockViewModel", "刷新股價失敗", e)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    private suspend fun updateWidget() {
        try {
            StockWidget().updateAll(appContext)
        } catch (e: Exception) {
            // Silently fail if widget update fails
        }
    }
}
