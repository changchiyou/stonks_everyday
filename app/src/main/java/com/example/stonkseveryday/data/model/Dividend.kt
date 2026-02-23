package com.example.stonkseveryday.data.model

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize
import java.util.Date

@Parcelize
@Entity(
    tableName = "dividends",
    foreignKeys = [
        ForeignKey(
            entity = StockTransaction::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["transactionId"]),
        Index(value = ["transactionId", "exDividendDate", "dividendType"], unique = true)
    ]
)
data class Dividend(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val transactionId: Long,
    val stockCode: String,
    val dividendType: DividendType,
    val exDividendDate: Long = Date().time,  // 除息日
    val dividendPerShare: Double,             // 每股股利
    val quantity: Int,                        // 持有股數
    val dividendAmount: Double,               // 股利總額
    val note: String = ""                     // 備註
) : Parcelable

enum class DividendType {
    CASH,    // 現金股利
    STOCK    // 股票股利
}
