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
 * 格式化刷新時間為絕對時間（日期+時間，精確到分鐘）
 * 格式：MM/dd HH:mm（例如：02/23 14:35）
 */
private fun formatRefreshTime(timestamp: Long): String {
    val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
    return dateFormat.format(Date(timestamp))
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

    // 刷新時間文字（絕對時間，不需要定期更新）
    val refreshTimeText = remember(lastRefreshTime) {
        formatRefreshTime(lastRefreshTime)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Stonks Everyday")
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
                        text = "總今日損益",
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
                    // 報酬率顯示：零成本投資組合時顯示特殊提示
                    if (summary.isPortfolioZeroCost) {
                        Text(
                            text = "✨ 零成本投資組合",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
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

            // 第三行：調整後總成本（含股利開關開啟時才顯示）
            if (summary.adjustedTotalCost != 0.0) {
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "調整後總成本",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = formatCurrency(summary.adjustedTotalCost),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (summary.adjustedTotalCost < 0)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onPrimaryContainer
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
            // 第一行：股票代碼、股票名稱、今日損益
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

                // 今日損益：(現價 - 昨收價) × 持股數量
                // 昨收價 = 現價 / (1 + 今日漲跌幅%)
                val previousClose = holding.currentPrice / (1 + holding.todayChangePercent / 100)
                val todayProfitLoss = (holding.currentPrice - previousClose) * holding.quantity

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "今日損益",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "${if (todayProfitLoss >= 0) "+" else ""}${formatCurrency(todayProfitLoss)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            todayProfitLoss > 0 -> MaterialTheme.colorScheme.error  // 上漲：紅色
                            todayProfitLoss < 0 -> MaterialTheme.colorScheme.tertiary  // 下跌：綠色
                            else -> MaterialTheme.colorScheme.onSurface  // 平盤：黑色
                        }
                    )
                }
            }

            HorizontalDivider()

            // 第二行：股數、成本價、持股比重、報酬率%（4欄）
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

                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
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

                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    // 報酬率顯示：零成本時顯示特殊提示
                    Text(
                        text = "報酬率",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    if (holding.isZeroCost) {
                        Text(
                            text = "🎉零成本",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            text = "${if (holding.profitLossPercentage >= 0) "+" else ""}${"%.2f".format(holding.profitLossPercentage)}%",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (holding.profitLoss >= 0)
                                MaterialTheme.colorScheme.error  // 賺錢：紅色
                            else
                                MaterialTheme.colorScheme.tertiary  // 虧錢：綠色
                        )
                    }
                }
            }

            // 第三行：今日漲跌幅、今日漲跌額、現價
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "今日漲跌幅",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "${if (holding.todayChangePercent >= 0) "+" else ""}${"%.2f".format(holding.todayChangePercent)}%",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = when {
                            holding.todayChangePercent > 0 -> MaterialTheme.colorScheme.error  // 上漲：紅色
                            holding.todayChangePercent < 0 -> MaterialTheme.colorScheme.tertiary  // 下跌：綠色
                            else -> MaterialTheme.colorScheme.onSurface  // 平盤：黑色
                        }
                    )
                }

                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "今日漲跌額",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    // 計算今日漲跌額 = 昨收價 × 今日漲跌幅%
                    val previousClose = holding.currentPrice / (1 + holding.todayChangePercent / 100)
                    val todayChange = previousClose * (holding.todayChangePercent / 100)
                    Text(
                        text = "${if (todayChange >= 0) "+" else ""}${"%.2f".format(todayChange)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = when {
                            todayChange > 0 -> MaterialTheme.colorScheme.error  // 上漲：紅色
                            todayChange < 0 -> MaterialTheme.colorScheme.tertiary  // 下跌：綠色
                            else -> MaterialTheme.colorScheme.onSurface  // 平盤：黑色
                        }
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

            // 第四行：預估市值、累積股利、未實現損益
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "預估市值",
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
                        text = "累積股利",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    // 根據查詢狀態顯示不同內容
                    when (holding.dividendQueryStatus) {
                        com.example.stonkseveryday.data.model.DividendQueryStatus.NOT_FOUND -> {
                            // FinMind 查不到該股票：顯示「查無資料」(灰色)
                            Text(
                                text = "查無資料",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        com.example.stonkseveryday.data.model.DividendQueryStatus.API_ERROR -> {
                            // API 錯誤：顯示「查詢錯誤」(紅色警告)
                            Text(
                                text = "查詢錯誤",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        else -> {
                            // 查詢成功：顯示金額（可能是 $0）
                            Text(
                                text = formatCurrency(holding.totalDividends),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = if (holding.totalDividends > 0)
                                    MaterialTheme.colorScheme.error  // 股利收入：紅色（台股習慣）
                                else
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(
                        text = "未實現損益",
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
            }
        }
    }
}
