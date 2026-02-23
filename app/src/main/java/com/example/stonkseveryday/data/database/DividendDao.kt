package com.example.stonkseveryday.data.database

import androidx.room.*
import com.example.stonkseveryday.data.model.Dividend
import kotlinx.coroutines.flow.Flow

@Dao
interface DividendDao {
    @Query("SELECT * FROM dividends ORDER BY exDividendDate DESC")
    fun getAllDividends(): Flow<List<Dividend>>

    @Query("SELECT * FROM dividends WHERE transactionId = :transactionId ORDER BY exDividendDate DESC")
    fun getDividendsByTransactionId(transactionId: Long): Flow<List<Dividend>>

    @Query("SELECT * FROM dividends WHERE stockCode = :stockCode ORDER BY exDividendDate DESC")
    fun getDividendsByStockCode(stockCode: String): Flow<List<Dividend>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDividend(dividend: Dividend): Long

    @Update
    suspend fun updateDividend(dividend: Dividend)

    @Delete
    suspend fun deleteDividend(dividend: Dividend)

    @Query("DELETE FROM dividends")
    suspend fun deleteAllDividends()

    @Query("SELECT SUM(dividendAmount) FROM dividends WHERE transactionId = :transactionId")
    suspend fun getTotalDividendsByTransaction(transactionId: Long): Double?

    @Query("SELECT SUM(dividendAmount) FROM dividends WHERE stockCode = :stockCode")
    suspend fun getTotalDividendsByStockCode(stockCode: String): Double?
}
