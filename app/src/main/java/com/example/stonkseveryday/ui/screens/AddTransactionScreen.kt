package com.example.stonkseveryday.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.stonkseveryday.data.model.StockTransaction
import com.example.stonkseveryday.data.model.TransactionType
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    onNavigateBack: () -> Unit,
    onSaveTransaction: (StockTransaction) -> Unit
) {
    var stockCode by remember { mutableStateOf("") }
    var stockName by remember { mutableStateOf("") }
    var transactionType by remember { mutableStateOf(TransactionType.BUY) }
    var quantity by remember { mutableStateOf("") }
    var pricePerShare by remember { mutableStateOf("") }
    var fee by remember { mutableStateOf("0") }
    var tax by remember { mutableStateOf("0") }

    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("新增交易") },
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
            // Transaction Type Selector
            Text(
                text = "交易類型",
                style = MaterialTheme.typography.titleMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = transactionType == TransactionType.BUY,
                    onClick = { transactionType = TransactionType.BUY },
                    label = { Text("買入") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = transactionType == TransactionType.SELL,
                    onClick = { transactionType = TransactionType.SELL },
                    label = { Text("賣出") },
                    modifier = Modifier.weight(1f)
                )
            }

            // Stock Code
            OutlinedTextField(
                value = stockCode,
                onValueChange = { stockCode = it.uppercase() },
                label = { Text("股票代碼") },
                placeholder = { Text("例如: 2330") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Stock Name
            OutlinedTextField(
                value = stockName,
                onValueChange = { stockName = it },
                label = { Text("股票名稱") },
                placeholder = { Text("例如: 台積電") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Quantity
            OutlinedTextField(
                value = quantity,
                onValueChange = { quantity = it.filter { char -> char.isDigit() } },
                label = { Text("股數") },
                placeholder = { Text("1000") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )

            // Price Per Share
            OutlinedTextField(
                value = pricePerShare,
                onValueChange = { pricePerShare = it },
                label = { Text("每股價格") },
                placeholder = { Text("100.5") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            )

            // Fee
            OutlinedTextField(
                value = fee,
                onValueChange = { fee = it },
                label = { Text("手續費") },
                placeholder = { Text("0") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            )

            // Tax
            OutlinedTextField(
                value = tax,
                onValueChange = { tax = it },
                label = { Text("證交稅") },
                placeholder = { Text("0") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            )

            if (showError) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Save Button
            Button(
                onClick = {
                    when {
                        stockCode.isBlank() -> {
                            showError = true
                            errorMessage = "請輸入股票代碼"
                        }
                        stockName.isBlank() -> {
                            showError = true
                            errorMessage = "請輸入股票名稱"
                        }
                        quantity.isBlank() || quantity.toIntOrNull() == null -> {
                            showError = true
                            errorMessage = "請輸入有效的股數"
                        }
                        pricePerShare.isBlank() || pricePerShare.toDoubleOrNull() == null -> {
                            showError = true
                            errorMessage = "請輸入有效的股價"
                        }
                        else -> {
                            val transaction = StockTransaction(
                                stockCode = stockCode,
                                stockName = stockName,
                                transactionType = transactionType,
                                quantity = quantity.toInt(),
                                pricePerShare = pricePerShare.toDouble(),
                                fee = fee.toDoubleOrNull() ?: 0.0,
                                tax = tax.toDoubleOrNull() ?: 0.0,
                                transactionDate = Date().time
                            )
                            onSaveTransaction(transaction)
                            onNavigateBack()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("儲存")
            }
        }
    }
}
