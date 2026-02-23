package com.example.stonkseveryday.data.database

import androidx.room.*
import com.example.stonkseveryday.data.model.DividendCalculationRecord

@Dao
interface DividendCalculationRecordDao {
    /**
     * 插入或更新股利計算記錄
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(record: DividendCalculationRecord)

    /**
     * 取得特定股票的計算記錄
     */
    @Query("SELECT * FROM dividend_calculation_records WHERE stockCode = :stockCode")
    suspend fun getRecord(stockCode: String): DividendCalculationRecord?

    /**
     * 取得所有計算記錄
     */
    @Query("SELECT * FROM dividend_calculation_records")
    suspend fun getAllRecords(): List<DividendCalculationRecord>

    /**
     * 刪除特定股票的計算記錄
     */
    @Query("DELETE FROM dividend_calculation_records WHERE stockCode = :stockCode")
    suspend fun deleteRecord(stockCode: String)

    /**
     * 清除所有計算記錄
     */
    @Query("DELETE FROM dividend_calculation_records")
    suspend fun deleteAll()
}
