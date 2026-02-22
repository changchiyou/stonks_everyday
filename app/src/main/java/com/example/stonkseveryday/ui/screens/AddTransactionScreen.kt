package com.example.stonkseveryday.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import com.example.stonkseveryday.data.model.StockTransaction
import com.example.stonkseveryday.data.model.TransactionType
import kotlinx.coroutines.launch
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    onNavigateBack: () -> Unit,
    onSaveTransaction: (StockTransaction) -> Unit,
    onFetchStockInfo: suspend (String) -> com.example.stonkseveryday.data.api.StockInfoResponse? = { null },
    defaultFeeRate: Double = 0.1425,
    defaultStockTaxRate: Double = 0.3,
    defaultEtfTaxRate: Double = 0.1
) {
    var stockCode by remember { mutableStateOf("") }
    var stockName by remember { mutableStateOf("") }
    var transactionType by remember { mutableStateOf(TransactionType.BUY) }
    var quantity by remember { mutableStateOf("") }
    var pricePerShare by remember { mutableStateOf("") }
    var fee by remember { mutableStateOf("") }
    var tax by remember { mutableStateOf("") }
    var isEtf by remember { mutableStateOf(false) }

    // 客製化費率
    var customFeeRate by remember { mutableStateOf("") }
    var customTaxRate by remember { mutableStateOf("") }
    var useCustomRates by remember { mutableStateOf(false) }

    // 交易日期時間
    var selectedDateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }

    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var isFetchingStockName by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val dateFormat = remember { java.text.SimpleDateFormat("yyyy/MM/dd", Locale.TAIWAN) }

    // 自動計算手續費和稅
    fun calculateFeeAndTax() {
        val amount = (quantity.toDoubleOrNull() ?: 0.0) * (pricePerShare.toDoubleOrNull() ?: 0.0)

        // 計算手續費
        val feeRate = if (useCustomRates && customFeeRate.isNotEmpty()) {
            customFeeRate.toDoubleOrNull() ?: defaultFeeRate
        } else {
            defaultFeeRate
        }
        fee = String.format("%.0f", amount * feeRate / 100)

        // 計算證交稅（只有賣出才有）
        if (transactionType == TransactionType.SELL) {
            val taxRate = if (useCustomRates && customTaxRate.isNotEmpty()) {
                customTaxRate.toDoubleOrNull() ?: (if (isEtf) defaultEtfTaxRate else defaultStockTaxRate)
            } else {
                if (isEtf) defaultEtfTaxRate else defaultStockTaxRate
            }
            tax = String.format("%.0f", amount * taxRate / 100)
        } else {
            tax = "0"
        }
    }

    // 當數量或價格改變時自動計算
    LaunchedEffect(quantity, pricePerShare, transactionType, isEtf, useCustomRates, customFeeRate, customTaxRate) {
        if (quantity.isNotEmpty() && pricePerShare.isNotEmpty()) {
            calculateFeeAndTax()
        }
    }

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

            // ETF 選項
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isEtf)
                        MaterialTheme.colorScheme.tertiaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isEtf,
                        onCheckedChange = { isEtf = it }
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "此為 ETF",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isEtf) "證交稅 0.1%（已自動偵測）" else "證交稅 0.3%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Stock Code
            OutlinedTextField(
                value = stockCode,
                onValueChange = { newCode ->
                    val upperCode = newCode.uppercase()
                    stockCode = upperCode

                    // 當輸入完整股票代碼時自動查詢股票名稱和是否為 ETF
                    if (upperCode.length == 4 && upperCode.all { it.isDigit() }) {
                        scope.launch {
                            isFetchingStockName = true
                            val stockInfo = onFetchStockInfo(upperCode)
                            if (stockInfo != null) {
                                stockName = stockInfo.stockName
                                isEtf = stockInfo.isEtf
                                showError = false
                            } else {
                                stockName = ""
                                isEtf = false
                                showError = true
                                errorMessage = "查無此股票代號，請確認後重新輸入"
                            }
                            isFetchingStockName = false
                        }
                    }
                },
                label = { Text("股票代碼") },
                placeholder = { Text("例如: 2330") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    if (isFetchingStockName) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            )

            // Stock Name (auto-filled)
            OutlinedTextField(
                value = stockName,
                onValueChange = { stockName = it },
                label = { Text("股票名稱") },
                placeholder = { Text("輸入股票代碼後自動帶入") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = stockName.isNotEmpty() || !isFetchingStockName,
                supportingText = {
                    if (stockName.isEmpty() && stockCode.isNotEmpty() && !isFetchingStockName) {
                        Text("等待輸入股票代碼...")
                    }
                }
            )

            // Transaction Date
            OutlinedTextField(
                value = dateFormat.format(Date(selectedDateMillis)),
                onValueChange = {},
                label = { Text("交易日期") },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "選擇日期"
                        )
                    }
                }
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
                singleLine = true,
                supportingText = {
                    Text("已自動計算（賣出才有）")
                }
            )

            // 客製化費率選項
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = useCustomRates,
                    onCheckedChange = { useCustomRates = it }
                )
                Text(
                    text = "客製化費率",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (useCustomRates) {
                OutlinedTextField(
                    value = customFeeRate,
                    onValueChange = { customFeeRate = it },
                    label = { Text("自訂手續費率（%）") },
                    placeholder = { Text(defaultFeeRate.toString()) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    supportingText = {
                        Text("留空使用預設值 $defaultFeeRate%")
                    }
                )

                if (transactionType == TransactionType.SELL) {
                    OutlinedTextField(
                        value = customTaxRate,
                        onValueChange = { customTaxRate = it },
                        label = { Text("自訂證交稅率（%）") },
                        placeholder = { Text((if (isEtf) defaultEtfTaxRate else defaultStockTaxRate).toString()) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        supportingText = {
                            Text("留空使用預設值 ${if (isEtf) defaultEtfTaxRate else defaultStockTaxRate}%")
                        }
                    )
                }
            }

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
                                transactionDate = selectedDateMillis,
                                isEtf = isEtf
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

    // Date Picker Dialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDateMillis
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            selectedDateMillis = millis
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("確定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("取消")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
