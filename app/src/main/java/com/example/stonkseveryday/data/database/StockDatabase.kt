package com.example.stonkseveryday.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.stonkseveryday.data.model.StockTransaction

@Database(
    entities = [StockTransaction::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class StockDatabase : RoomDatabase() {
    abstract fun stockTransactionDao(): StockTransactionDao

    companion object {
        @Volatile
        private var INSTANCE: StockDatabase? = null

        fun getDatabase(context: Context): StockDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    StockDatabase::class.java,
                    "stock_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
