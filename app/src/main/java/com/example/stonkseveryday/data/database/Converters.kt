package com.example.stonkseveryday.data.database

import androidx.room.TypeConverter
import com.example.stonkseveryday.data.model.DividendQueryStatus
import com.example.stonkseveryday.data.model.DividendType
import com.example.stonkseveryday.data.model.TransactionType

class Converters {
    @TypeConverter
    fun fromTransactionType(type: TransactionType): String {
        return type.name
    }

    @TypeConverter
    fun toTransactionType(value: String): TransactionType {
        return TransactionType.valueOf(value)
    }

    @TypeConverter
    fun fromDividendType(type: DividendType): String {
        return type.name
    }

    @TypeConverter
    fun toDividendType(value: String): DividendType {
        return DividendType.valueOf(value)
    }

    @TypeConverter
    fun fromDividendQueryStatus(status: DividendQueryStatus): String {
        return status.name
    }

    @TypeConverter
    fun toDividendQueryStatus(value: String): DividendQueryStatus {
        return try {
            DividendQueryStatus.valueOf(value)
        } catch (e: IllegalArgumentException) {
            // 如果遇到未知的值，預設為 SUCCESS
            DividendQueryStatus.SUCCESS
        }
    }
}
