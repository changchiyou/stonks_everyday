package com.example.stonkseveryday.data.backup

import android.content.Context
import android.net.Uri
import com.example.stonkseveryday.data.api.RetrofitInstance
import com.example.stonkseveryday.data.database.StockDatabase
import com.example.stonkseveryday.data.model.Dividend
import com.example.stonkseveryday.data.model.StockTransaction
import com.example.stonkseveryday.data.model.TransactionType
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.*

class BackupManager(private val context: Context) {
    private val database = StockDatabase.getDatabase(context)
    private val gson = Gson()

    /**
     * 備份用的交易記錄（僅包含必要欄位）
     */
    data class BackupTransaction(
        @SerializedName("stock_code")
        val stockCode: String,
        @SerializedName("transaction_type")
        val transactionType: String,  // "BUY" or "SELL"
        @SerializedName("quantity")
        val quantity: Int,
        @SerializedName("price_per_share")
        val pricePerShare: Double,
        @SerializedName("fee")
        val fee: Double,
        @SerializedName("tax")
        val tax: Double,
        @SerializedName("transaction_date")
        val transactionDate: Long
    )

    /**
     * 備份用的股利記錄
     */
    data class BackupDividend(
        @SerializedName("stock_code")
        val stockCode: String,
        @SerializedName("dividend_type")
        val dividendType: String,  // "CASH" or "STOCK"
        @SerializedName("ex_dividend_date")
        val exDividendDate: Long,
        @SerializedName("dividend_per_share")
        val dividendPerShare: Double,
        @SerializedName("quantity")
        val quantity: Int,
        @SerializedName("dividend_amount")
        val dividendAmount: Double,
        @SerializedName("note")
        val note: String = ""
    )

    data class BackupData(
        @SerializedName("version")
        val version: Int = 2,  // 升級到版本 2
        @SerializedName("backup_date")
        val backupDate: String,
        @SerializedName("transactions")
        val transactions: List<BackupTransaction>,
        @SerializedName("dividends")
        val dividends: List<BackupDividend>
    )

    /**
     * 備份所有資料到 URI
     * @param uri 備份檔案的 URI（由 SAF 提供）
     * @return 是否成功
     */
    suspend fun backupToUri(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val transactions = database.stockTransactionDao().getAllTransactions()
            val dividends = database.dividendDao().getAllDividends()

            // 需要先轉為 List 才能序列化
            val txList = mutableListOf<StockTransaction>()
            val divList = mutableListOf<Dividend>()

            transactions.collect { txList.addAll(it) }
            dividends.collect { divList.addAll(it) }

            // 將完整交易記錄轉換為備份格式（移除 stockName 和 isEtf）
            val backupTransactions = txList.map { tx ->
                BackupTransaction(
                    stockCode = tx.stockCode,
                    transactionType = tx.transactionType.name,
                    quantity = tx.quantity,
                    pricePerShare = tx.pricePerShare,
                    fee = tx.fee,
                    tax = tx.tax,
                    transactionDate = tx.transactionDate
                )
            }

            // 將股利記錄轉換為備份格式
            val backupDividends = divList.map { div ->
                BackupDividend(
                    stockCode = div.stockCode,
                    dividendType = div.dividendType.name,
                    exDividendDate = div.exDividendDate,
                    dividendPerShare = div.dividendPerShare,
                    quantity = div.quantity,
                    dividendAmount = div.dividendAmount,
                    note = div.note
                )
            }

            val backupData = BackupData(
                backupDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
                transactions = backupTransactions,
                dividends = backupDividends
            )

            val json = gson.toJson(backupData)

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream).use { writer ->
                    writer.write(json)
                }
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 從 URI 恢復資料
     * @param uri 備份檔案的 URI（由 SAF 提供）
     * @param clearExisting 是否先清除現有資料（預設為 true）
     * @param token FinMind API Token（用於查詢股票資訊，可選）
     * @return 是否成功
     */
    suspend fun restoreFromUri(uri: Uri, clearExisting: Boolean = true, token: String = ""): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    reader.readText()
                }
            } ?: return@withContext false

            val backupData = gson.fromJson(json, BackupData::class.java)

            if (clearExisting) {
                database.stockTransactionDao().deleteAllTransactions()
                database.dividendDao().deleteAllDividends()
            }

            // 建立股票代碼到股票資訊的快取，避免重複查詢
            val stockInfoCache = mutableMapOf<String, Pair<String, Boolean>>()

            // 還原交易記錄，從 API 取得 stockName 和 isEtf
            backupData.transactions.forEach { backupTx ->
                // 取得或查詢股票資訊
                val (stockName, isEtf) = stockInfoCache.getOrPut(backupTx.stockCode) {
                    fetchStockInfo(backupTx.stockCode, token)
                }

                val transaction = StockTransaction(
                    id = 0,  // 自動生成新 ID
                    stockCode = backupTx.stockCode,
                    stockName = stockName,
                    transactionType = TransactionType.valueOf(backupTx.transactionType),
                    quantity = backupTx.quantity,
                    pricePerShare = backupTx.pricePerShare,
                    fee = backupTx.fee,
                    tax = backupTx.tax,
                    transactionDate = backupTx.transactionDate,
                    isEtf = isEtf
                )
                database.stockTransactionDao().insertTransaction(transaction)
            }

            // 還原股利記錄
            // 需要根據 stockCode 找到對應的新 transactionId
            val transactionMap = mutableMapOf<String, MutableList<Long>>()
            database.stockTransactionDao().getAllTransactions().collect { txList ->
                txList.forEach { tx ->
                    transactionMap.getOrPut(tx.stockCode) { mutableListOf() }.add(tx.id)
                }
            }

            backupData.dividends.forEach { backupDiv ->
                // 找到該股票代碼的第一筆交易作為關聯
                val transactionId = transactionMap[backupDiv.stockCode]?.firstOrNull() ?: 0L

                val dividend = Dividend(
                    id = 0,  // 自動生成新 ID
                    transactionId = transactionId,
                    stockCode = backupDiv.stockCode,
                    dividendType = com.example.stonkseveryday.data.model.DividendType.valueOf(backupDiv.dividendType),
                    exDividendDate = backupDiv.exDividendDate,
                    dividendPerShare = backupDiv.dividendPerShare,
                    quantity = backupDiv.quantity,
                    dividendAmount = backupDiv.dividendAmount,
                    note = backupDiv.note
                )
                database.dividendDao().insertDividend(dividend)
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 從 API 取得股票資訊
     * @return Pair(股票名稱, 是否為ETF)
     */
    private suspend fun fetchStockInfo(stockCode: String, token: String): Pair<String, Boolean> {
        return try {
            val response = RetrofitInstance.stockApiService.getTaiwanStockInfo(
                stockCode = stockCode,
                token = token
            )

            if (response.data.isNotEmpty()) {
                val stockData = response.data[0]
                val stockName = stockData.stockName ?: stockCode
                val isEtf = stockData.type?.contains("ETF", ignoreCase = true) == true ||
                           isEtfByCode(stockCode)
                Pair(stockName, isEtf)
            } else {
                // API 查不到，使用股票代碼作為名稱，並根據代碼判斷是否為 ETF
                Pair(stockCode, isEtfByCode(stockCode))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // 發生錯誤時，使用股票代碼作為名稱，並根據代碼判斷是否為 ETF
            Pair(stockCode, isEtfByCode(stockCode))
        }
    }

    /**
     * 根據股票代碼判斷是否為 ETF
     */
    private fun isEtfByCode(stockCode: String): Boolean {
        return stockCode.startsWith("00") ||
               (stockCode.startsWith("5") && stockCode.length == 4)
    }

    /**
     * 清除所有資料
     * @return 是否成功
     */
    suspend fun clearAllData(): Boolean = withContext(Dispatchers.IO) {
        try {
            database.stockTransactionDao().deleteAllTransactions()
            database.dividendDao().deleteAllDividends()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 生成建議的備份檔名
     */
    fun generateBackupFileName(): String {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        return "stonks_backup_${dateFormat.format(Date())}.json"
    }
}
