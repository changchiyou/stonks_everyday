package com.example.stonkseveryday.data.database

import androidx.room.TypeConverter
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
}
