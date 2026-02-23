package com.example.stonkseveryday.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentToken: String,
    defaultFeeRate: Double,
    defaultStockTaxRate: Double,
    defaultEtfTaxRate: Double,
    onNavigateBack: () -> Unit,
    onSaveToken: (String) -> Unit,
    onSaveFeeRate: (Double) -> Unit,
    onSaveStockTaxRate: (Double) -> Unit,
    onSaveEtfTaxRate: (Double) -> Unit,
    onBackup: () -> Unit = {},
    onRestore: () -> Unit = {},
    onClearAll: () -> Unit = {},
    onCalculateAllDividends: () -> Unit = {}
) {
    var tokenInput by remember { mutableStateOf(currentToken) }
    var feeRateInput by remember { mutableStateOf(defaultFeeRate.toString()) }
    var stockTaxRateInput by remember { mutableStateOf(defaultStockTaxRate.toString()) }
    var etfTaxRateInput by remember { mutableStateOf(defaultEtfTaxRate.toString()) }
    var showSaveSuccess by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("設定") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section: API 設定
            Text(
                text = "API 設定",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "FinMind API Token（選填）",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "如果您有註冊 FinMind 帳號並取得 API Token，可以在此輸入以獲得更即時的股價資料。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )

                    Text(
                        text = "如果留空，系統會依序嘗試 FinMind 免費版 API 或台灣證券交易所官方 API（股利資料可能無法取得）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                    )
                }
            }

            OutlinedTextField(
                value = tokenInput,
                onValueChange = { tokenInput = it },
                label = { Text("FinMind API Token") },
                placeholder = { Text("貼上您的 Token 或留空") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = {
                    Text("註冊網址: https://finmindtrade.com/")
                }
            )

            if (showSaveSuccess) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Text(
                        text = "設定已儲存",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        onSaveToken(tokenInput.trim())
                        showSaveSuccess = true
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("儲存")
                }

                OutlinedButton(
                    onClick = {
                        tokenInput = ""
                        onSaveToken("")
                        showSaveSuccess = true
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("清除")
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Section: 費率設定
            Text(
                text = "費率設定",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "預設手續費率（%）",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedTextField(
                        value = feeRateInput,
                        onValueChange = { feeRateInput = it },
                        label = { Text("手續費率") },
                        placeholder = { Text("0.1425") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        supportingText = {
                            Text("預設 0.1425%，券商優惠請自行調整")
                        }
                    )

                    Text(
                        text = "預設證交稅率（%）",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedTextField(
                        value = stockTaxRateInput,
                        onValueChange = { stockTaxRateInput = it },
                        label = { Text("一般股票證交稅率") },
                        placeholder = { Text("0.3") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        supportingText = {
                            Text("一般股票賣出適用，預設 0.3%")
                        }
                    )

                    OutlinedTextField(
                        value = etfTaxRateInput,
                        onValueChange = { etfTaxRateInput = it },
                        label = { Text("ETF 證交稅率") },
                        placeholder = { Text("0.1") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        supportingText = {
                            Text("ETF 賣出適用，預設 0.1%")
                        }
                    )

                    Button(
                        onClick = {
                            feeRateInput.toDoubleOrNull()?.let { onSaveFeeRate(it) }
                            stockTaxRateInput.toDoubleOrNull()?.let { onSaveStockTaxRate(it) }
                            etfTaxRateInput.toDoubleOrNull()?.let { onSaveEtfTaxRate(it) }
                            showSaveSuccess = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("儲存費率設定")
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Section: 資料管理
            Text(
                text = "資料管理",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "股利計算",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "重新計算所有持股的股利資料。股利會在交易記錄變更時自動計算，通常不需要手動執行。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )

                    Button(
                        onClick = onCalculateAllDividends,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("計算所有股利")
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "備份與恢復",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "備份您的交易記錄和股利資料，以便在需要時恢復。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onBackup,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("備份資料")
                        }

                        OutlinedButton(
                            onClick = onRestore,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("恢復資料")
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "清除所有資料",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )

                    Text(
                        text = "警告：此操作將永久刪除所有交易記錄和股利資料，無法復原。請先備份資料。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                    )

                    Button(
                        onClick = { showClearConfirmDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("清除所有資料")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // 清除確認對話框
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("確認清除所有資料") },
            text = {
                Text("此操作將永久刪除所有交易記錄和股利資料，無法復原。\n\n您確定要繼續嗎？")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearConfirmDialog = false
                        onClearAll()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("確定清除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    LaunchedEffect(showSaveSuccess) {
        if (showSaveSuccess) {
            delay(2000)
            showSaveSuccess = false
        }
    }
}

//@Preview
//@Composable
//private fun SettingScreenPreview() {
//    SettingsScreen(
//        currentToken = "",
//        defaultFeeRate = 0.0,
//        defaultStockTaxRate = 0.0,
//        defaultEtfTaxRate = 0.0,
//        onNavigateBack = {},
//        onSaveToken = {},
//        onSaveFeeRate = {},
//        onSaveStockTaxRate = {},
//        onSaveEtfTaxRate = {},
//        onBackup = {},
//        onRestore = {},
//        onClearAll = {}
//    )
//}
