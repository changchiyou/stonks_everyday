package com.example.stonkseveryday.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.stonkseveryday.data.model.StockSummary
import com.example.stonkseveryday.data.model.StockTransaction
import com.example.stonkseveryday.data.model.TransactionType
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    transactions: List<StockTransaction>,
    summary: StockSummary,
    onAddTransaction: () -> Unit,
    onTransactionClick: (StockTransaction) -> Unit,
    onOpenSettings: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("股票交易記錄") },
                actions = {
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

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@Composable
fun SummaryCard(summary: StockSummary) {
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale("zh", "TW"))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "總覽",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "總收入",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = currencyFormat.format(summary.totalIncome),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "當日損益",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = currencyFormat.format(summary.dailyProfitLoss),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (summary.dailyProfitLoss >= 0)
                            MaterialTheme.colorScheme.tertiary
                        else
                            MaterialTheme.colorScheme.error
                    )
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "總損益",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = currencyFormat.format(summary.totalProfitLoss),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (summary.totalProfitLoss >= 0)
                        MaterialTheme.colorScheme.tertiary
                    else
                        MaterialTheme.colorScheme.error
                )
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
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale("zh", "TW"))
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
                    text = "${transaction.quantity} 股 @ ${currencyFormat.format(transaction.pricePerShare)}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Surface(
                    color = if (transaction.transactionType == TransactionType.BUY)
                        MaterialTheme.colorScheme.errorContainer
                    else
                        MaterialTheme.colorScheme.tertiaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = if (transaction.transactionType == TransactionType.BUY) "買入" else "賣出",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (transaction.transactionType == TransactionType.BUY)
                            MaterialTheme.colorScheme.onErrorContainer
                        else
                            MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = currencyFormat.format(transaction.totalAmount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (transaction.transactionType == TransactionType.BUY)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}
