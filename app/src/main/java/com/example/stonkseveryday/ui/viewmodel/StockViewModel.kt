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

    val allTransactions: Flow<List<StockTransaction>>
    val stockSummary: Flow<StockSummary>

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    init {
        val dao = StockDatabase.getDatabase(application).stockTransactionDao()
        repository = StockRepository(dao, appContext)
        allTransactions = repository.allTransactions
        stockSummary = repository.calculateSummary(emptyList())
            .stateIn(
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

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    fun clearSuccessMessage() {
        _successMessage.value = null
    }

    /**
     * 備份資料到 URI
     */
    fun backupData(uri: Uri) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val success = backupManager.backupToUri(uri)
                if (success) {
                    _successMessage.value = "備份成功"
                    _errorMessage.value = null
                } else {
                    _errorMessage.value = "備份失敗"
                }
            } catch (e: Exception) {
                _errorMessage.value = "備份失敗: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 從 URI 恢復資料
     */
    fun restoreData(uri: Uri) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val success = backupManager.restoreFromUri(uri)
                if (success) {
                    _successMessage.value = "恢復成功"
                    _errorMessage.value = null
                    updateWidget()
                } else {
                    _errorMessage.value = "恢復失敗"
                }
            } catch (e: Exception) {
                _errorMessage.value = "恢復失敗: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
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
    suspend fun getHoldingDetails(stockCode: String, currentPrice: Double): List<HoldingDetail> {
        val transactions = allTransactions.first().filter {
            it.stockCode == stockCode && it.transactionType == TransactionType.BUY
        }

        return transactions.map { tx ->
            val dividends = repository.getDividendsByTransaction(tx.id)
            val totalDividends = dividends.sumOf { it.dividendAmount }
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
     */
    fun calculateDividends(stockCode: String, token: String = "") {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val count = repository.calculateAndInsertDividends(stockCode, token)
                if (count > 0) {
                    _successMessage.value = "成功新增 $count 筆股利記錄"
                    _errorMessage.value = null
                } else {
                    _errorMessage.value = "未找到符合條件的股利資料"
                }
            } catch (e: Exception) {
                _errorMessage.value = "計算股利失敗: ${e.message}"
            } finally {
                _isLoading.value = false
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
