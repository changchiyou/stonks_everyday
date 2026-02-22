package com.example.stonkseveryday.data.backup

import android.content.Context
import android.net.Uri
import com.example.stonkseveryday.data.database.StockDatabase
import com.example.stonkseveryday.data.model.Dividend
import com.example.stonkseveryday.data.model.StockTransaction
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

    data class BackupData(
        @SerializedName("version")
        val version: Int = 1,
        @SerializedName("backup_date")
        val backupDate: String,
        @SerializedName("transactions")
        val transactions: List<StockTransaction>,
        @SerializedName("dividends")
        val dividends: List<Dividend>
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

            val backupData = BackupData(
                backupDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
                transactions = txList,
                dividends = divList
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
     * @return 是否成功
     */
    suspend fun restoreFromUri(uri: Uri, clearExisting: Boolean = true): Boolean = withContext(Dispatchers.IO) {
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

            backupData.transactions.forEach { transaction ->
                database.stockTransactionDao().insertTransaction(transaction)
            }

            backupData.dividends.forEach { dividend ->
                database.dividendDao().insertDividend(dividend)
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
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
