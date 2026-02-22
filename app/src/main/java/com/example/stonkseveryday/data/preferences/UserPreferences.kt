package com.example.stonkseveryday.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class UserPreferences(private val context: Context) {

    companion object {
        private val FINMIND_API_TOKEN = stringPreferencesKey("finmind_api_token")
        private val DEFAULT_FEE_RATE = doublePreferencesKey("default_fee_rate")
        private val DEFAULT_STOCK_TAX_RATE = doublePreferencesKey("default_stock_tax_rate")
        private val DEFAULT_ETF_TAX_RATE = doublePreferencesKey("default_etf_tax_rate")
    }

    /**
     * 取得使用者設定的 FinMind API Token
     */
    val finmindToken: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[FINMIND_API_TOKEN] ?: ""
    }

    /**
     * 儲存使用者的 FinMind API Token
     */
    suspend fun saveFinmindToken(token: String) {
        context.dataStore.edit { preferences ->
            preferences[FINMIND_API_TOKEN] = token
        }
    }

    /**
     * 清除 FinMind API Token
     */
    suspend fun clearFinmindToken() {
        context.dataStore.edit { preferences ->
            preferences.remove(FINMIND_API_TOKEN)
        }
    }

    /**
     * 取得預設手續費率（%）
     */
    val defaultFeeRate: Flow<Double> = context.dataStore.data.map { preferences ->
        preferences[DEFAULT_FEE_RATE] ?: 0.1425
    }

    /**
     * 儲存預設手續費率（%）
     */
    suspend fun saveDefaultFeeRate(rate: Double) {
        context.dataStore.edit { preferences ->
            preferences[DEFAULT_FEE_RATE] = rate
        }
    }

    /**
     * 取得預設一般股票證交稅率（%）
     */
    val defaultStockTaxRate: Flow<Double> = context.dataStore.data.map { preferences ->
        preferences[DEFAULT_STOCK_TAX_RATE] ?: 0.3
    }

    /**
     * 儲存預設一般股票證交稅率（%）
     */
    suspend fun saveDefaultStockTaxRate(rate: Double) {
        context.dataStore.edit { preferences ->
            preferences[DEFAULT_STOCK_TAX_RATE] = rate
        }
    }

    /**
     * 取得預設 ETF 證交稅率（%）
     */
    val defaultEtfTaxRate: Flow<Double> = context.dataStore.data.map { preferences ->
        preferences[DEFAULT_ETF_TAX_RATE] ?: 0.1
    }

    /**
     * 儲存預設 ETF 證交稅率（%）
     */
    suspend fun saveDefaultEtfTaxRate(rate: Double) {
        context.dataStore.edit { preferences ->
            preferences[DEFAULT_ETF_TAX_RATE] = rate
        }
    }
}
