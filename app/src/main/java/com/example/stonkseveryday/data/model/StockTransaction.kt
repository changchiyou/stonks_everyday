package com.example.stonkseveryday.data.model

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize
import java.util.Date

@Parcelize
@Entity(tableName = "stock_transactions")
data class StockTransaction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val stockCode: String,
    val stockName: String,
    val transactionType: TransactionType,
    val quantity: Int,
    val pricePerShare: Double,
    val transactionDate: Long = Date().time,
    val fee: Double = 0.0,
    val tax: Double = 0.0
) : Parcelable {
    val totalAmount: Double
        get() = quantity * pricePerShare + fee + tax
}

enum class TransactionType {
    BUY,
    SELL
}
