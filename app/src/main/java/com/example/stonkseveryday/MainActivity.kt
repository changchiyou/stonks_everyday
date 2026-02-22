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
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Schedule widget updates
        WidgetUpdateHelper.scheduleWidgetUpdates(this)

        setContent {
            StonksEverydayTheme {
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
    val errorMessage by viewModel.errorMessage.collectAsState()
    val successMessage by viewModel.successMessage.collectAsState()

    var showAddTransaction by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var selectedHolding by remember { mutableStateOf<StockHolding?>(null) }
    var holdingDetails by remember { mutableStateOf<List<com.example.stonkseveryday.data.model.HoldingDetail>>(emptyList()) }
    var showExitDialog by remember { mutableStateOf(false) }

    // Backup launcher
    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { viewModel.backupData(it) }
    }

    // Restore launcher
    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.restoreData(it) }
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
                onNavigateBack = { showAddTransaction = false },
                onSaveTransaction = { transaction ->
                    viewModel.insertTransaction(transaction)
                },
                onFetchStockInfo = { stockCode ->
                    viewModel.fetchStockInfo(stockCode)
                },
                defaultFeeRate = defaultFeeRate,
                defaultStockTaxRate = defaultStockTaxRate,
                defaultEtfTaxRate = defaultEtfTaxRate
            )
        }
        selectedHolding != null -> {
            // 在持股明細頁面，返回鍵回到主頁
            BackHandler { selectedHolding = null }

            HoldingDetailScreen(
                holding = selectedHolding!!,
                holdingDetails = holdingDetails,
                onNavigateBack = { selectedHolding = null },
                onCalculateDividends = {
                    selectedHolding?.let { holding ->
                        viewModel.calculateDividends(
                            stockCode = holding.stockCode,
                            token = currentToken
                        )
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
                onBackup = {
                    val fileName = com.example.stonkseveryday.data.backup.BackupManager(context).generateBackupFileName()
                    backupLauncher.launch(fileName)
                },
                onRestore = {
                    restoreLauncher.launch(arrayOf("application/json"))
                },
                onClearAll = {
                    viewModel.clearAllData()
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
                    scope.launch {
                        val details = viewModel.getHoldingDetails(holding.stockCode, holding.currentPrice)
                        holdingDetails = details
                        selectedHolding = holding
                    }
                },
                onOpenSettings = { showSettings = true },
                showHoldingsView = true
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
