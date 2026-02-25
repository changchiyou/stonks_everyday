package com.example.stonkseveryday

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.stonkseveryday.data.model.StockHolding
import com.example.stonkseveryday.data.model.StockSummary
import com.example.stonkseveryday.data.preferences.UserPreferences
import com.example.stonkseveryday.ui.screens.*
import com.example.stonkseveryday.ui.theme.StonksEverydayTheme
import com.example.stonkseveryday.ui.viewmodel.StockViewModel
import com.example.stonkseveryday.widget.WidgetUpdateHelper
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Schedule widget updates
        WidgetUpdateHelper.scheduleWidgetUpdates(this)

        // 強制更新所有 widget（解決 IDE Run 不觸發 MY_PACKAGE_REPLACED 的問題）
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                android.util.Log.i("MainActivity", "啟動時更新所有 widget - 開始")
                val startTime = System.currentTimeMillis()

                com.example.stonkseveryday.widget.StockWidget().updateAll(applicationContext)
                android.util.Log.i("MainActivity", "標準 widget 更新完成")

                com.example.stonkseveryday.widget.CompactStockWidget().updateAll(applicationContext)
                android.util.Log.i("MainActivity", "緊湊 widget 更新完成")

                val duration = System.currentTimeMillis() - startTime
                android.util.Log.i("MainActivity", "所有 widget 更新完成，耗時 ${duration}ms")
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "更新 widget 失敗", e)
            }
        }

        // 在背景更新過期的股利資料
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val dao = com.example.stonkseveryday.data.database.StockDatabase.getDatabase(applicationContext).stockTransactionDao()
                val repository = com.example.stonkseveryday.data.repository.StockRepository(dao, applicationContext)
                val userPreferences = com.example.stonkseveryday.data.preferences.UserPreferences(applicationContext)
                val token = userPreferences.finmindToken.first()

                android.util.Log.i("MainActivity", "開始背景更新股利資料")
                val count = repository.updateStaleDividends(token)
                android.util.Log.i("MainActivity", "背景更新完成，更新了 $count 支股票")
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "背景更新股利失敗", e)
            }
        }

        setContent {
            val userPreferences = remember { UserPreferences(this) }
            val lightColors by userPreferences.lightColorCustomization.collectAsState(
                initial = com.example.stonkseveryday.data.model.ColorCustomization.defaultLight()
            )
            val darkColors by userPreferences.darkColorCustomization.collectAsState(
                initial = com.example.stonkseveryday.data.model.ColorCustomization.defaultDark()
            )

            StonksEverydayTheme(
                lightColors = lightColors,
                darkColors = darkColors
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    StockTradingApp()
                }
            }
        }
    }
}

@Composable
fun StockTradingApp() {
    val context = LocalContext.current
    val viewModel: StockViewModel = viewModel()
    val userPreferences = remember { UserPreferences(context) }
    val scope = rememberCoroutineScope()

    val transactions by viewModel.allTransactions.collectAsState(initial = emptyList())
    val summary by viewModel.stockSummary.collectAsState(initial = StockSummary(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, emptyList()))
    val currentToken by userPreferences.finmindToken.collectAsState(initial = "")
    val defaultFeeRate by userPreferences.defaultFeeRate.collectAsState(initial = 0.1425)
    val defaultStockTaxRate by userPreferences.defaultStockTaxRate.collectAsState(initial = 0.3)
    val defaultEtfTaxRate by userPreferences.defaultEtfTaxRate.collectAsState(initial = 0.1)
    val includeDividends by userPreferences.includeDividends.collectAsState(initial = true)
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val lastRefreshTime by viewModel.lastRefreshTime.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val successMessage by viewModel.successMessage.collectAsState()

    var showAddTransaction by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showColorSettings by remember { mutableStateOf(false) }
    var selectedHolding by remember { mutableStateOf<StockHolding?>(null) }
    var holdingDetails by remember { mutableStateOf<List<com.example.stonkseveryday.data.model.HoldingDetail>>(emptyList()) }
    var showExitDialog by remember { mutableStateOf(false) }
    var transactionToEdit by remember { mutableStateOf<com.example.stonkseveryday.data.model.StockTransaction?>(null) }

    // 顏色設定
    val lightColors by userPreferences.lightColorCustomization.collectAsState(
        initial = com.example.stonkseveryday.data.model.ColorCustomization.defaultLight()
    )
    val darkColors by userPreferences.darkColorCustomization.collectAsState(
        initial = com.example.stonkseveryday.data.model.ColorCustomization.defaultDark()
    )

    // Transactions backup launcher
    val backupTransactionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { viewModel.backupTransactions(it) }
    }

    // Transactions restore launcher
    val restoreTransactionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.restoreTransactions(it, currentToken) }
    }

    // Settings backup launcher
    val backupSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { viewModel.backupSettings(it) }
    }

    // Settings restore launcher
    val restoreSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.restoreSettings(it) }
    }

    // Show error/success messages
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            // You can show a snackbar or toast here
        }
    }

    LaunchedEffect(successMessage) {
        successMessage?.let {
            // You can show a snackbar or toast here
        }
    }

    // 處理返回鍵/手勢
    when {
        showAddTransaction -> {
            // 在新增交易頁面，返回鍵回到主頁
            BackHandler { showAddTransaction = false }

            AddTransactionScreen(
                onNavigateBack = {
                    showAddTransaction = false
                    transactionToEdit = null
                },
                onSaveTransaction = { transaction ->
                    viewModel.insertTransaction(transaction)
                    transactionToEdit = null
                },
                onFetchStockInfo = { stockCode ->
                    viewModel.fetchStockInfo(stockCode)
                },
                defaultFeeRate = defaultFeeRate,
                defaultStockTaxRate = defaultStockTaxRate,
                defaultEtfTaxRate = defaultEtfTaxRate,
                editingTransaction = transactionToEdit
            )
        }
        selectedHolding != null -> {
            // 在持股明細頁面，返回鍵回到主頁
            BackHandler { selectedHolding = null }

            HoldingDetailScreen(
                holding = selectedHolding!!,
                holdingDetails = holdingDetails,
                onNavigateBack = { selectedHolding = null },
                onTransactionClick = { transactionId ->
                    // 找到對應的交易記錄並進入編輯模式
                    transactions.find { it.id == transactionId }?.let { transaction ->
                        transactionToEdit = transaction
                        showAddTransaction = true
                        selectedHolding = null
                    }
                },
                onDeleteTransaction = { transactionId ->
                    viewModel.deleteTransaction(transactionId)
                }
            )
        }
        showColorSettings -> {
            // 在顏色設定頁面，返回鍵回到設定頁
            BackHandler {
                showColorSettings = false
                showSettings = true
            }

            ColorCustomizationScreen(
                lightColors = lightColors,
                darkColors = darkColors,
                onNavigateBack = {
                    showColorSettings = false
                    showSettings = true
                },
                onSaveLightColors = { colors ->
                    scope.launch {
                        userPreferences.saveLightColorCustomization(colors)
                    }
                },
                onSaveDarkColors = { colors ->
                    scope.launch {
                        userPreferences.saveDarkColorCustomization(colors)
                    }
                },
                onResetLightColors = {
                    scope.launch {
                        userPreferences.resetLightColors()
                    }
                },
                onResetDarkColors = {
                    scope.launch {
                        userPreferences.resetDarkColors()
                    }
                }
            )
        }
        showSettings -> {
            // 在設定頁面，返回鍵回到主頁
            BackHandler { showSettings = false }

            SettingsScreen(
                currentToken = currentToken,
                defaultFeeRate = defaultFeeRate,
                defaultStockTaxRate = defaultStockTaxRate,
                defaultEtfTaxRate = defaultEtfTaxRate,
                onNavigateBack = { showSettings = false },
                onSaveToken = { token ->
                    scope.launch {
                        userPreferences.saveFinmindToken(token)
                    }
                },
                onSaveFeeRate = { rate ->
                    scope.launch {
                        userPreferences.saveDefaultFeeRate(rate)
                    }
                },
                onSaveStockTaxRate = { rate ->
                    scope.launch {
                        userPreferences.saveDefaultStockTaxRate(rate)
                    }
                },
                onSaveEtfTaxRate = { rate ->
                    scope.launch {
                        userPreferences.saveDefaultEtfTaxRate(rate)
                    }
                },
                onBackupTransactions = {
                    val fileName = com.example.stonkseveryday.data.backup.BackupManager(context).generateTransactionsBackupFileName()
                    backupTransactionsLauncher.launch(fileName)
                },
                onRestoreTransactions = {
                    restoreTransactionsLauncher.launch(arrayOf("application/json"))
                },
                onBackupSettings = {
                    val fileName = com.example.stonkseveryday.data.backup.BackupManager(context).generateSettingsBackupFileName()
                    backupSettingsLauncher.launch(fileName)
                },
                onRestoreSettings = {
                    restoreSettingsLauncher.launch(arrayOf("application/json"))
                },
                onClearAll = {
                    viewModel.clearAllData()
                },
                onCalculateAllDividends = {
                    viewModel.calculateAllDividends(currentToken)
                },
                onOpenColorSettings = {
                    showSettings = false
                    showColorSettings = true
                }
            )
        }
        else -> {
            // 在主頁面，返回鍵顯示退出確認對話框
            BackHandler { showExitDialog = true }

            MainScreen(
                transactions = transactions,
                summary = summary,
                onAddTransaction = { showAddTransaction = true },
                onTransactionClick = { /* 可以在這裡加入編輯功能 */ },
                onHoldingClick = { holding ->
                    println("[CLICK] 持股卡片被點擊: ${holding.stockCode}")
                    scope.launch {
                        println("[LAUNCH] Coroutine 啟動")
                        try {
                            println("[BEFORE] 準備呼叫 getHoldingDetails")
                            val details = viewModel.getHoldingDetails(holding.stockCode, holding.currentPrice)
                            println("[AFTER] 取得 ${details.size} 筆明細")
                            holdingDetails = details
                            selectedHolding = holding
                            println("[DONE] selectedHolding = ${selectedHolding?.stockCode}")
                        } catch (e: Exception) {
                            println("[ERROR] ${e.message}")
                            e.printStackTrace()
                        }
                    }
                },
                onOpenSettings = { showSettings = true },
                showHoldingsView = true,
                includeDividends = includeDividends,
                onIncludeDividendsChange = { include ->
                    scope.launch {
                        userPreferences.saveIncludeDividends(include)
                    }
                },
                isRefreshing = isRefreshing,
                lastRefreshTime = lastRefreshTime,
                onRefresh = {
                    viewModel.refreshStockPrices()
                }
            )
        }
    }

    // 退出確認對話框
    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("離開應用程式") },
            text = { Text("確定要離開應用程式嗎？") },
            confirmButton = {
                Button(
                    onClick = {
                        // 關閉 Activity，退出應用程式
                        (context as? ComponentActivity)?.finish()
                    }
                ) {
                    Text("離開")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}
