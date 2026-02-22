package com.example.stonkseveryday.data.database

import androidx.room.*
import com.example.stonkseveryday.data.model.StockTransaction
import kotlinx.coroutines.flow.Flow

@Dao
interface StockTransactionDao {
    @Query("SELECT * FROM stock_transactions ORDER BY transactionDate DESC")
    fun getAllTransactions(): Flow<List<StockTransaction>>

    @Query("SELECT * FROM stock_transactions WHERE id = :id")
    suspend fun getTransactionById(id: Long): StockTransaction?

    @Query("SELECT * FROM stock_transactions WHERE DATE(transactionDate / 1000, 'unixepoch') = DATE('now')")
    fun getTodayTransactions(): Flow<List<StockTransaction>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: StockTransaction): Long

    @Update
    suspend fun updateTransaction(transaction: StockTransaction)

    @Delete
    suspend fun deleteTransaction(transaction: StockTransaction)

    @Query("DELETE FROM stock_transactions")
    suspend fun deleteAllTransactions()
}
