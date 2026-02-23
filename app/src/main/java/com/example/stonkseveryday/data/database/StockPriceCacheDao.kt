package com.example.stonkseveryday.data.database

import androidx.room.*
import com.example.stonkseveryday.data.model.StockPriceCache

@Dao
interface StockPriceCacheDao {
    @Query("SELECT * FROM stock_price_cache WHERE stockCode = :stockCode")
    suspend fun getPriceCache(stockCode: String): StockPriceCache?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(priceCache: StockPriceCache)

    @Query("DELETE FROM stock_price_cache WHERE stockCode = :stockCode")
    suspend fun delete(stockCode: String)

    @Query("DELETE FROM stock_price_cache")
    suspend fun deleteAll()
}
