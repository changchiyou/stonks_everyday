package com.example.stonkseveryday.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.stonkseveryday.data.model.Dividend
import com.example.stonkseveryday.data.model.StockTransaction

@Database(
    entities = [StockTransaction::class, Dividend::class],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class StockDatabase : RoomDatabase() {
    abstract fun stockTransactionDao(): StockTransactionDao
    abstract fun dividendDao(): DividendDao

    companion object {
        @Volatile
        private var INSTANCE: StockDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS dividends (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        transactionId INTEGER NOT NULL,
                        stockCode TEXT NOT NULL,
                        dividendDate INTEGER NOT NULL,
                        dividendAmount REAL NOT NULL,
                        dividendType TEXT NOT NULL,
                        notes TEXT NOT NULL,
                        FOREIGN KEY(transactionId) REFERENCES stock_transactions(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 新增 isEtf 欄位，預設為 false
                database.execSQL("ALTER TABLE stock_transactions ADD COLUMN isEtf INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 重建 dividends 表以更新欄位結構
                database.execSQL("DROP TABLE IF EXISTS dividends")
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS dividends (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        transactionId INTEGER NOT NULL,
                        stockCode TEXT NOT NULL,
                        dividendType TEXT NOT NULL,
                        exDividendDate INTEGER NOT NULL,
                        dividendPerShare REAL NOT NULL,
                        quantity INTEGER NOT NULL,
                        dividendAmount REAL NOT NULL,
                        note TEXT NOT NULL,
                        FOREIGN KEY(transactionId) REFERENCES stock_transactions(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                // 建立索引以提升外鍵查詢效能
                database.execSQL("CREATE INDEX IF NOT EXISTS index_dividends_transactionId ON dividends(transactionId)")
            }
        }

        fun getDatabase(context: Context): StockDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    StockDatabase::class.java,
                    "stock_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
