package com.example.stonkseveryday.data.backup

import android.content.Context
import android.net.Uri
import com.example.stonkseveryday.data.api.RetrofitInstance
import com.example.stonkseveryday.data.database.StockDatabase
import com.example.stonkseveryday.data.model.ColorCustomization
import com.example.stonkseveryday.data.model.Dividend
import com.example.stonkseveryday.data.model.StockTransaction
import com.example.stonkseveryday.data.model.TransactionType
import com.example.stonkseveryday.data.preferences.UserPreferences
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.*

class BackupManager(private val context: Context) {
    private val database = StockDatabase.getDatabase(context)
    private val userPreferences = UserPreferences(context)
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

    /**
     * 備份用的使用者設定（不包含敏感資訊如 API Token）
     */
    data class BackupSettings(
        @SerializedName("default_fee_rate")
        val defaultFeeRate: Double? = null,
        @SerializedName("default_stock_tax_rate")
        val defaultStockTaxRate: Double? = null,
        @SerializedName("default_etf_tax_rate")
        val defaultEtfTaxRate: Double? = null,
        @SerializedName("include_dividends")
        val includeDividends: Boolean? = null,
        @SerializedName("light_colors")
        val lightColors: ColorCustomization? = null,
        @SerializedName("dark_colors")
        val darkColors: ColorCustomization? = null
    )

    /**
     * 交易資料備份（包含交易記錄和股利）
     */
    data class BackupData(
        @SerializedName("version")
        val version: Int = 3,
        @SerializedName("backup_date")
        val backupDate: String,
        @SerializedName("transactions")
        val transactions: List<BackupTransaction>,
        @SerializedName("dividends")
        val dividends: List<BackupDividend>
    )

    /**
     * 個人設定備份（費率、顏色等，不含敏感資訊）
     */
    data class SettingsBackupData(
        @SerializedName("version")
        val version: Int = 1,
        @SerializedName("backup_date")
        val backupDate: String,
        @SerializedName("settings")
        val settings: BackupSettings
    )

    /**
     * 備份交易資料到 URI（包含交易記錄和股利）
     * @param uri 備份檔案的 URI（由 SAF 提供）
     * @return 是否成功
     */
    suspend fun backupTransactionsToUri(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            // 使用 first() 取得資料，而不是 collect
            val txList = database.stockTransactionDao().getAllTransactions().first()
            val divList = database.dividendDao().getAllDividends().first()

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
     * 備份個人設定到 URI（費率、顏色等，不含 API Token）
     * @param uri 備份檔案的 URI（由 SAF 提供）
     * @return 是否成功
     */
    suspend fun backupSettingsToUri(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val backupSettings = BackupSettings(
                defaultFeeRate = userPreferences.defaultFeeRate.first(),
                defaultStockTaxRate = userPreferences.defaultStockTaxRate.first(),
                defaultEtfTaxRate = userPreferences.defaultEtfTaxRate.first(),
                includeDividends = userPreferences.includeDividends.first(),
                lightColors = userPreferences.lightColorCustomization.first(),
                darkColors = userPreferences.darkColorCustomization.first()
            )

            val settingsBackup = SettingsBackupData(
                backupDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
                settings = backupSettings
            )

            val json = gson.toJson(settingsBackup)

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
     * 從 URI 恢復交易資料（交易記錄和股利）
     * @param uri 備份檔案的 URI（由 SAF 提供）
     * @param clearExisting 是否先清除現有資料（預設為 true）
     * @param token FinMind API Token（用於查詢股票資訊，可選）
     * @return 是否成功
     */
    suspend fun restoreTransactionsFromUri(uri: Uri, clearExisting: Boolean = true, token: String = ""): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    reader.readText()
                }
            } ?: return@withContext false

            val backupData = gson.fromJson(json, BackupData::class.java)

            // 檢查版本相容性（支援版本 2 和 3）
            if (backupData.version !in 2..3) {
                android.util.Log.e("BackupManager", "不支援的備份版本: ${backupData.version}，目前僅支援版本 2-3")
                return@withContext false
            }

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
            val restoredTransactions = database.stockTransactionDao().getAllTransactions().first()
            restoredTransactions.forEach { tx ->
                transactionMap.getOrPut(tx.stockCode) { mutableListOf() }.add(tx.id)
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
     * 從 URI 恢復個人設定（費率、顏色等）
     * @param uri 備份檔案的 URI（由 SAF 提供）
     * @return 是否成功
     */
    suspend fun restoreSettingsFromUri(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    reader.readText()
                }
            } ?: return@withContext false

            val settingsBackup = gson.fromJson(json, SettingsBackupData::class.java)

            // 檢查版本相容性
            if (settingsBackup.version != 1) {
                android.util.Log.e("BackupManager", "不支援的設定備份版本: ${settingsBackup.version}")
                return@withContext false
            }

            // 恢復設定
            settingsBackup.settings.let { settings ->
                settings.defaultFeeRate?.let { userPreferences.saveDefaultFeeRate(it) }
                settings.defaultStockTaxRate?.let { userPreferences.saveDefaultStockTaxRate(it) }
                settings.defaultEtfTaxRate?.let { userPreferences.saveDefaultEtfTaxRate(it) }
                settings.includeDividends?.let { userPreferences.saveIncludeDividends(it) }
                settings.lightColors?.let { userPreferences.saveLightColorCustomization(it) }
                settings.darkColors?.let { userPreferences.saveDarkColorCustomization(it) }
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
     * 生成交易資料備份檔名
     */
    fun generateTransactionsBackupFileName(): String {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        return "stonks_transactions_${dateFormat.format(Date())}.json"
    }

    /**
     * 生成個人設定備份檔名
     */
    fun generateSettingsBackupFileName(): String {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        return "stonks_settings_${dateFormat.format(Date())}.json"
    }

    /**
     * 生成建議的備份檔名（舊版相容，建議使用新的分類方法）
     */
    @Deprecated("請使用 generateTransactionsBackupFileName() 或 generateSettingsBackupFileName()")
    fun generateBackupFileName(): String {
        return generateTransactionsBackupFileName()
    }
}
