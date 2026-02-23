package com.example.stonkseveryday.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.stonkseveryday.data.model.StockSummary
import com.example.stonkseveryday.data.model.StockTransaction
import com.example.stonkseveryday.data.model.TransactionType
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlinx.coroutines.delay

/**
 * 格式化金額
 * - >= 100,000,000: 顯示為 M (百萬)，統一 .2f
 * - >= 10,000: 顯示為 K (千)，統一 .1f
 * - < 10,000: 直接顯示整數
 */
private fun formatCurrency(amount: Double): String {
    val absAmount = abs(amount)
    val sign = if (amount < 0) "-" else ""

    return when {
        absAmount >= 100_000_000 -> String.format("%s$%.2fM", sign, absAmount / 1_000_000)
        absAmount >= 10_000 -> String.format("%s$%.1fK", sign, absAmount / 1_000)
        else -> String.format("%s$%.0f", sign, absAmount)
    }
}

/**
 * 格式化刷新時間為相對時間（精確到分鐘）
 */
private fun formatRefreshTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diffMinutes = ((now - timestamp) / 60000).toInt()

    return when {
        diffMinutes == 0 -> "剛剛更新"
        diffMinutes < 60 -> "${diffMinutes}分鐘前"
        else -> {
            val hours = diffMinutes / 60
            val minutes = diffMinutes % 60
            if (minutes == 0) "${hours}小時前"
            else "${hours}小時${minutes}分鐘前"
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    transactions: List<StockTransaction>,
    summary: StockSummary,
    onAddTransaction: () -> Unit,
    onTransactionClick: (StockTransaction) -> Unit,
    onHoldingClick: (com.example.stonkseveryday.data.model.StockHolding) -> Unit,
    onOpenSettings: () -> Unit,
    showHoldingsView: Boolean = true,
    includeDividends: Boolean = true,
    onIncludeDividendsChange: (Boolean) -> Unit = {},
    isRefreshing: Boolean = false,
    lastRefreshTime: Long = System.currentTimeMillis(),
    onRefresh: () -> Unit = {}
) {
    // 自動刷新：每 60 秒刷新一次
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000) // 60 秒
            onRefresh()
        }
    }

    // 用於強制重新計算刷新時間文字（每分鐘更新一次）
    var refreshTimeTrigger by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000) // 每分鐘更新一次文字
            refreshTimeTrigger++
        }
    }

    val refreshTimeText = remember(lastRefreshTime, refreshTimeTrigger) {
        formatRefreshTime(lastRefreshTime)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("股票交易記錄")
                        Text(
                            text = refreshTimeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onRefresh,
                        enabled = !isRefreshing
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "刷新",
                            modifier = if (isRefreshing) Modifier else Modifier
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "設定")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddTransaction,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "新增交易")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                SummaryCard(summary = summary)
            }

            if (showHoldingsView) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "持股明細 (${summary.holdings.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        // 股利開關
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "含股利",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            Switch(
                                checked = includeDividends,
                                onCheckedChange = onIncludeDividendsChange,
                                modifier = Modifier.height(24.dp)
                            )
                        }
                    }
                }

                items(summary.holdings) { holding ->
                    HoldingItem(
                        holding = holding,
                        onClick = {
                            println("持股卡片被點擊: ${holding.stockCode} ${holding.stockName}")
                            onHoldingClick(holding)
                        },
                        includeDividends = includeDividends
                    )
                }
            } else {
                item {
                    Text(
                        text = "交易記錄",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                items(transactions) { transaction ->
                    TransactionItem(
                        transaction = transaction,
                        onClick = { onTransactionClick(transaction) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@Composable
fun SummaryCard(summary: StockSummary) {
    val configuration = LocalConfiguration.current
    val isSmallScreen = configuration.screenWidthDp < 360

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isSmallScreen) 12.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (isSmallScreen) 8.dp else 12.dp)
        ) {
            // 第一行：預估總市值、今日損益
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "預估總市值",
                        style = if (isSmallScreen) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = formatCurrency(summary.totalAssets),
                        style = if (isSmallScreen) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(
                        text = "今日損益",
                        style = if (isSmallScreen) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "${if (summary.todayProfitLoss >= 0) "+" else ""}${formatCurrency(summary.todayProfitLoss)}",
                        style = if (isSmallScreen) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (summary.todayProfitLoss >= 0)
                            MaterialTheme.colorScheme.error  // 賺錢：紅色（台股習慣）
                        else
                            MaterialTheme.colorScheme.tertiary  // 虧錢：綠色（台股習慣）
                    )
                    Text(
                        text = "${if (summary.todayProfitLossPercent >= 0) "+" else ""}${"%.2f".format(summary.todayProfitLossPercent)}%",
                        style = if (isSmallScreen) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
                        color = if (summary.todayProfitLoss >= 0)
                            MaterialTheme.colorScheme.error  // 賺錢：紅色（台股習慣）
                        else
                            MaterialTheme.colorScheme.tertiary  // 虧錢：綠色（台股習慣）
                    )
                }
            }

            HorizontalDivider()

            // 第二行：總未實現損益
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "總未實現損益",
                    style = MaterialTheme.typography.bodyMedium
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${if (summary.totalProfitLoss >= 0) "+" else ""}${formatCurrency(summary.totalProfitLoss)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (summary.totalProfitLoss >= 0)
                            MaterialTheme.colorScheme.error  // 賺錢：紅色（台股習慣）
                        else
                            MaterialTheme.colorScheme.tertiary  // 虧錢：綠色（台股習慣）
                    )
                    Text(
                        text = "${if (summary.totalProfitLossPercent >= 0) "+" else ""}${"%.2f".format(summary.totalProfitLossPercent)}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (summary.totalProfitLoss >= 0)
                            MaterialTheme.colorScheme.error  // 賺錢：紅色（台股習慣）
                        else
                            MaterialTheme.colorScheme.tertiary  // 虧錢：綠色（台股習慣）
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionItem(
    transaction: StockTransaction,
    onClick: () -> Unit
) {
    val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = transaction.stockName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = transaction.stockCode,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = dateFormat.format(Date(transaction.transactionDate)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "${transaction.quantity} 股 @ ${"$%.0f".format(transaction.pricePerShare)}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Surface(
                    color = if (transaction.transactionType == TransactionType.BUY)
                        MaterialTheme.colorScheme.tertiaryContainer  // 買入：綠色背景
                    else
                        MaterialTheme.colorScheme.errorContainer,  // 賣出：紅色背景
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = if (transaction.transactionType == TransactionType.BUY) "買入" else "賣出",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (transaction.transactionType == TransactionType.BUY)
                            MaterialTheme.colorScheme.onTertiaryContainer
                        else
                            MaterialTheme.colorScheme.onErrorContainer
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = formatCurrency(transaction.totalAmount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (transaction.transactionType == TransactionType.BUY)
                        MaterialTheme.colorScheme.tertiary  // 買入：綠色（支出）
                    else
                        MaterialTheme.colorScheme.error  // 賣出：紅色（收入）
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HoldingItem(
    holding: com.example.stonkseveryday.data.model.StockHolding,
    onClick: () -> Unit,
    includeDividends: Boolean = true
) {
    val configuration = LocalConfiguration.current
    val isSmallScreen = configuration.screenWidthDp < 360

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isSmallScreen) 12.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (isSmallScreen) 6.dp else 8.dp)
        ) {
            // 第一行：股票代碼、股票名稱、損益百分比
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = holding.stockCode,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = holding.stockName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                Text(
                    text = "${if (holding.profitLossPercentage >= 0) "+" else ""}${"%.2f".format(holding.profitLossPercentage)}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (holding.profitLoss >= 0)
                        MaterialTheme.colorScheme.tertiary
                    else
                        MaterialTheme.colorScheme.error
                )
            }

            HorizontalDivider()

            // 第二行：股數、成本價、現價
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "股數",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "${holding.quantity}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }

                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "成本價",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "${"%.2f".format(holding.averageCost)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }

                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(
                        text = "現價",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "${"%.2f".format(holding.currentPrice)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // 第三行：現值、損益、持股比重
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "現值",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = formatCurrency(holding.currentValue),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }

                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "損益",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "${if (holding.profitLoss >= 0) "+" else ""}${formatCurrency(holding.profitLoss)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (holding.profitLoss >= 0)
                            MaterialTheme.colorScheme.error  // 賺錢：紅色（台股習慣）
                        else
                            MaterialTheme.colorScheme.tertiary  // 虧錢：綠色（台股習慣）
                    )
                }

                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(
                        text = "持股比重",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "${"%.2f".format(holding.positionRatio)}%",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            // 主畫面不顯示累積股利，只在持股明細頁面顯示
        }
    }
}
