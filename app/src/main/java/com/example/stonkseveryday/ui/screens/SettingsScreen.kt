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
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentToken: String,
    onNavigateBack: () -> Unit,
    onSaveToken: (String) -> Unit
) {
    var tokenInput by remember { mutableStateOf(currentToken) }
    var showSaveSuccess by remember { mutableStateOf(false) }

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
                        text = "如果留空，系統會自動使用免費的台灣證券交易所官方 API。",
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
                        text = "✓ 設定已儲存",
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

            // Section: API 說明
            Text(
                text = "API 使用說明",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "📊 資料來源策略",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "1. 如果有設定 FinMind Token → 使用 FinMind API",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Text(
                        text = "2. 如果沒有 Token 或 FinMind 失敗 → 自動使用 TWSE 官方 API",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Text(
                        text = "3. 如果兩個 API 都失敗 → 該股票不顯示未實現損益",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "💡 建議",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "• 一般使用者：不需設定 Token，使用 TWSE 官方 API 即可",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Text(
                        text = "• 頻繁使用者：建議註冊 FinMind 以獲得更快的查詢速度",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Text(
                        text = "• 資料安全：Token 僅儲存在您的裝置上，不會上傳到任何伺服器",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    LaunchedEffect(showSaveSuccess) {
        if (showSaveSuccess) {
            kotlinx.coroutines.delay(2000)
            showSaveSuccess = false
        }
    }
}
