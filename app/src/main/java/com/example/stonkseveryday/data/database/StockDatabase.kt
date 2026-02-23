package com.example.stonkseveryday.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.stonkseveryday.data.model.Dividend
import com.example.stonkseveryday.data.model.DividendCalculationRecord
import com.example.stonkseveryday.data.model.StockTransaction

@Database(
    entities = [
        StockTransaction::class,
        Dividend::class,
        DividendCalculationRecord::class,
        com.example.stonkseveryday.data.model.StockPriceCache::class
    ],
    version = 7,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class StockDatabase : RoomDatabase() {
    abstract fun stockTransactionDao(): StockTransactionDao
    abstract fun dividendDao(): DividendDao
    abstract fun dividendCalculationRecordDao(): DividendCalculationRecordDao
    abstract fun stockPriceCacheDao(): StockPriceCacheDao

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

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 新增股利計算記錄表
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS dividend_calculation_records (
                        stockCode TEXT PRIMARY KEY NOT NULL,
                        lastCalculationTime INTEGER NOT NULL,
                        recordCount INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 修復重複的股利記錄問題
                // 1. 建立臨時表，只保留每個 (transactionId, exDividendDate, dividendType) 組合的第一筆記錄
                database.execSQL(
                    """
                    CREATE TABLE dividends_temp (
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

                // 2. 複製不重複的資料到臨時表（使用 MIN(id) 保留最早的記錄）
                database.execSQL(
                    """
                    INSERT INTO dividends_temp (id, transactionId, stockCode, dividendType, exDividendDate, dividendPerShare, quantity, dividendAmount, note)
                    SELECT id, transactionId, stockCode, dividendType, exDividendDate, dividendPerShare, quantity, dividendAmount, note
                    FROM dividends
                    WHERE id IN (
                        SELECT MIN(id)
                        FROM dividends
                        GROUP BY transactionId, exDividendDate, dividendType
                    )
                    """.trimIndent()
                )

                // 3. 刪除舊表
                database.execSQL("DROP TABLE dividends")

                // 4. 重新命名臨時表
                database.execSQL("ALTER TABLE dividends_temp RENAME TO dividends")

                // 5. 建立索引（包含唯一性約束）
                database.execSQL("CREATE INDEX IF NOT EXISTS index_dividends_transactionId ON dividends(transactionId)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_dividends_unique ON dividends(transactionId, exDividendDate, dividendType)")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 新增股價快取表
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS stock_price_cache (
                        stockCode TEXT PRIMARY KEY NOT NULL,
                        currentPrice REAL NOT NULL,
                        previousClose REAL NOT NULL,
                        change REAL NOT NULL,
                        changePercent REAL NOT NULL,
                        lastUpdateTime INTEGER NOT NULL,
                        isStale INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
            }
        }

        fun getDatabase(context: Context): StockDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    StockDatabase::class.java,
                    "stock_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
