package com.example.stonkseveryday.ui.viewmodel

import android.app.Application
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.stonkseveryday.data.database.StockDatabase
import com.example.stonkseveryday.data.model.StockSummary
import com.example.stonkseveryday.data.model.StockTransaction
import com.example.stonkseveryday.data.repository.StockRepository
import com.example.stonkseveryday.widget.StockWidget
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class StockViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: StockRepository
    private val appContext = application.applicationContext

    val allTransactions: Flow<List<StockTransaction>>
    val stockSummary: Flow<StockSummary>

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        val dao = StockDatabase.getDatabase(application).stockTransactionDao()
        repository = StockRepository(dao, appContext)
        allTransactions = repository.allTransactions
        stockSummary = repository.calculateSummary(emptyList())
            .stateIn(viewModelScope, SharingStarted.Lazily, StockSummary(0.0, 0.0, 0.0, emptyList()))
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

    private suspend fun updateWidget() {
        try {
            StockWidget().updateAll(appContext)
        } catch (e: Exception) {
            // Silently fail if widget update fails
        }
    }
}
