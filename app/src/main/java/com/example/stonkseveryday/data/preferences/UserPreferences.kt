package com.example.stonkseveryday.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.stonkseveryday.data.model.ColorCustomization
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class UserPreferences(private val context: Context) {

    companion object {
        private val FINMIND_API_TOKEN = stringPreferencesKey("finmind_api_token")
        private val DEFAULT_FEE_RATE = doublePreferencesKey("default_fee_rate")
        private val DEFAULT_STOCK_TAX_RATE = doublePreferencesKey("default_stock_tax_rate")
        private val DEFAULT_ETF_TAX_RATE = doublePreferencesKey("default_etf_tax_rate")
        private val INCLUDE_DIVIDENDS = booleanPreferencesKey("include_dividends")

        // 淺色主題顏色設定
        private val LIGHT_PRIMARY = longPreferencesKey("light_primary")
        private val LIGHT_ON_PRIMARY = longPreferencesKey("light_on_primary")
        private val LIGHT_PRIMARY_CONTAINER = longPreferencesKey("light_primary_container")
        private val LIGHT_ON_PRIMARY_CONTAINER = longPreferencesKey("light_on_primary_container")
        private val LIGHT_SECONDARY = longPreferencesKey("light_secondary")
        private val LIGHT_ON_SECONDARY = longPreferencesKey("light_on_secondary")
        private val LIGHT_SECONDARY_CONTAINER = longPreferencesKey("light_secondary_container")
        private val LIGHT_ON_SECONDARY_CONTAINER = longPreferencesKey("light_on_secondary_container")
        private val LIGHT_TERTIARY = longPreferencesKey("light_tertiary")
        private val LIGHT_ON_TERTIARY = longPreferencesKey("light_on_tertiary")
        private val LIGHT_TERTIARY_CONTAINER = longPreferencesKey("light_tertiary_container")
        private val LIGHT_ON_TERTIARY_CONTAINER = longPreferencesKey("light_on_tertiary_container")
        private val LIGHT_BACKGROUND = longPreferencesKey("light_background")
        private val LIGHT_ON_BACKGROUND = longPreferencesKey("light_on_background")
        private val LIGHT_SURFACE = longPreferencesKey("light_surface")
        private val LIGHT_ON_SURFACE = longPreferencesKey("light_on_surface")
        private val LIGHT_ERROR = longPreferencesKey("light_error")
        private val LIGHT_ON_ERROR = longPreferencesKey("light_on_error")
        private val LIGHT_ERROR_CONTAINER = longPreferencesKey("light_error_container")
        private val LIGHT_ON_ERROR_CONTAINER = longPreferencesKey("light_on_error_container")

        // 深色主題顏色設定
        private val DARK_PRIMARY = longPreferencesKey("dark_primary")
        private val DARK_ON_PRIMARY = longPreferencesKey("dark_on_primary")
        private val DARK_PRIMARY_CONTAINER = longPreferencesKey("dark_primary_container")
        private val DARK_ON_PRIMARY_CONTAINER = longPreferencesKey("dark_on_primary_container")
        private val DARK_SECONDARY = longPreferencesKey("dark_secondary")
        private val DARK_ON_SECONDARY = longPreferencesKey("dark_on_secondary")
        private val DARK_SECONDARY_CONTAINER = longPreferencesKey("dark_secondary_container")
        private val DARK_ON_SECONDARY_CONTAINER = longPreferencesKey("dark_on_secondary_container")
        private val DARK_TERTIARY = longPreferencesKey("dark_tertiary")
        private val DARK_ON_TERTIARY = longPreferencesKey("dark_on_tertiary")
        private val DARK_TERTIARY_CONTAINER = longPreferencesKey("dark_tertiary_container")
        private val DARK_ON_TERTIARY_CONTAINER = longPreferencesKey("dark_on_tertiary_container")
        private val DARK_BACKGROUND = longPreferencesKey("dark_background")
        private val DARK_ON_BACKGROUND = longPreferencesKey("dark_on_background")
        private val DARK_SURFACE = longPreferencesKey("dark_surface")
        private val DARK_ON_SURFACE = longPreferencesKey("dark_on_surface")
        private val DARK_ERROR = longPreferencesKey("dark_error")
        private val DARK_ON_ERROR = longPreferencesKey("dark_on_error")
        private val DARK_ERROR_CONTAINER = longPreferencesKey("dark_error_container")
        private val DARK_ON_ERROR_CONTAINER = longPreferencesKey("dark_on_error_container")
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

    /**
     * 取得是否將股利納入損益計算
     */
    val includeDividends: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[INCLUDE_DIVIDENDS] ?: true  // 預設為 true（納入計算）
    }

    /**
     * 儲存是否將股利納入損益計算
     */
    suspend fun saveIncludeDividends(include: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[INCLUDE_DIVIDENDS] = include
        }
    }

    /**
     * 取得淺色主題顏色設定
     */
    val lightColorCustomization: Flow<ColorCustomization> = context.dataStore.data.map { preferences ->
        val default = ColorCustomization.defaultLight()
        ColorCustomization(
            primary = preferences[LIGHT_PRIMARY] ?: default.primary,
            onPrimary = preferences[LIGHT_ON_PRIMARY] ?: default.onPrimary,
            primaryContainer = preferences[LIGHT_PRIMARY_CONTAINER] ?: default.primaryContainer,
            onPrimaryContainer = preferences[LIGHT_ON_PRIMARY_CONTAINER] ?: default.onPrimaryContainer,
            secondary = preferences[LIGHT_SECONDARY] ?: default.secondary,
            onSecondary = preferences[LIGHT_ON_SECONDARY] ?: default.onSecondary,
            secondaryContainer = preferences[LIGHT_SECONDARY_CONTAINER] ?: default.secondaryContainer,
            onSecondaryContainer = preferences[LIGHT_ON_SECONDARY_CONTAINER] ?: default.onSecondaryContainer,
            tertiary = preferences[LIGHT_TERTIARY] ?: default.tertiary,
            onTertiary = preferences[LIGHT_ON_TERTIARY] ?: default.onTertiary,
            tertiaryContainer = preferences[LIGHT_TERTIARY_CONTAINER] ?: default.tertiaryContainer,
            onTertiaryContainer = preferences[LIGHT_ON_TERTIARY_CONTAINER] ?: default.onTertiaryContainer,
            background = preferences[LIGHT_BACKGROUND] ?: default.background,
            onBackground = preferences[LIGHT_ON_BACKGROUND] ?: default.onBackground,
            surface = preferences[LIGHT_SURFACE] ?: default.surface,
            onSurface = preferences[LIGHT_ON_SURFACE] ?: default.onSurface,
            error = preferences[LIGHT_ERROR] ?: default.error,
            onError = preferences[LIGHT_ON_ERROR] ?: default.onError,
            errorContainer = preferences[LIGHT_ERROR_CONTAINER] ?: default.errorContainer,
            onErrorContainer = preferences[LIGHT_ON_ERROR_CONTAINER] ?: default.onErrorContainer
        )
    }

    /**
     * 儲存淺色主題顏色設定
     */
    suspend fun saveLightColorCustomization(colors: ColorCustomization) {
        context.dataStore.edit { preferences ->
            preferences[LIGHT_PRIMARY] = colors.primary
            preferences[LIGHT_ON_PRIMARY] = colors.onPrimary
            preferences[LIGHT_PRIMARY_CONTAINER] = colors.primaryContainer
            preferences[LIGHT_ON_PRIMARY_CONTAINER] = colors.onPrimaryContainer
            preferences[LIGHT_SECONDARY] = colors.secondary
            preferences[LIGHT_ON_SECONDARY] = colors.onSecondary
            preferences[LIGHT_SECONDARY_CONTAINER] = colors.secondaryContainer
            preferences[LIGHT_ON_SECONDARY_CONTAINER] = colors.onSecondaryContainer
            preferences[LIGHT_TERTIARY] = colors.tertiary
            preferences[LIGHT_ON_TERTIARY] = colors.onTertiary
            preferences[LIGHT_TERTIARY_CONTAINER] = colors.tertiaryContainer
            preferences[LIGHT_ON_TERTIARY_CONTAINER] = colors.onTertiaryContainer
            preferences[LIGHT_BACKGROUND] = colors.background
            preferences[LIGHT_ON_BACKGROUND] = colors.onBackground
            preferences[LIGHT_SURFACE] = colors.surface
            preferences[LIGHT_ON_SURFACE] = colors.onSurface
            preferences[LIGHT_ERROR] = colors.error
            preferences[LIGHT_ON_ERROR] = colors.onError
            preferences[LIGHT_ERROR_CONTAINER] = colors.errorContainer
            preferences[LIGHT_ON_ERROR_CONTAINER] = colors.onErrorContainer
        }
    }

    /**
     * 取得深色主題顏色設定
     */
    val darkColorCustomization: Flow<ColorCustomization> = context.dataStore.data.map { preferences ->
        val default = ColorCustomization.defaultDark()
        ColorCustomization(
            primary = preferences[DARK_PRIMARY] ?: default.primary,
            onPrimary = preferences[DARK_ON_PRIMARY] ?: default.onPrimary,
            primaryContainer = preferences[DARK_PRIMARY_CONTAINER] ?: default.primaryContainer,
            onPrimaryContainer = preferences[DARK_ON_PRIMARY_CONTAINER] ?: default.onPrimaryContainer,
            secondary = preferences[DARK_SECONDARY] ?: default.secondary,
            onSecondary = preferences[DARK_ON_SECONDARY] ?: default.onSecondary,
            secondaryContainer = preferences[DARK_SECONDARY_CONTAINER] ?: default.secondaryContainer,
            onSecondaryContainer = preferences[DARK_ON_SECONDARY_CONTAINER] ?: default.onSecondaryContainer,
            tertiary = preferences[DARK_TERTIARY] ?: default.tertiary,
            onTertiary = preferences[DARK_ON_TERTIARY] ?: default.onTertiary,
            tertiaryContainer = preferences[DARK_TERTIARY_CONTAINER] ?: default.tertiaryContainer,
            onTertiaryContainer = preferences[DARK_ON_TERTIARY_CONTAINER] ?: default.onTertiaryContainer,
            background = preferences[DARK_BACKGROUND] ?: default.background,
            onBackground = preferences[DARK_ON_BACKGROUND] ?: default.onBackground,
            surface = preferences[DARK_SURFACE] ?: default.surface,
            onSurface = preferences[DARK_ON_SURFACE] ?: default.onSurface,
            error = preferences[DARK_ERROR] ?: default.error,
            onError = preferences[DARK_ON_ERROR] ?: default.onError,
            errorContainer = preferences[DARK_ERROR_CONTAINER] ?: default.errorContainer,
            onErrorContainer = preferences[DARK_ON_ERROR_CONTAINER] ?: default.onErrorContainer
        )
    }

    /**
     * 儲存深色主題顏色設定
     */
    suspend fun saveDarkColorCustomization(colors: ColorCustomization) {
        context.dataStore.edit { preferences ->
            preferences[DARK_PRIMARY] = colors.primary
            preferences[DARK_ON_PRIMARY] = colors.onPrimary
            preferences[DARK_PRIMARY_CONTAINER] = colors.primaryContainer
            preferences[DARK_ON_PRIMARY_CONTAINER] = colors.onPrimaryContainer
            preferences[DARK_SECONDARY] = colors.secondary
            preferences[DARK_ON_SECONDARY] = colors.onSecondary
            preferences[DARK_SECONDARY_CONTAINER] = colors.secondaryContainer
            preferences[DARK_ON_SECONDARY_CONTAINER] = colors.onSecondaryContainer
            preferences[DARK_TERTIARY] = colors.tertiary
            preferences[DARK_ON_TERTIARY] = colors.onTertiary
            preferences[DARK_TERTIARY_CONTAINER] = colors.tertiaryContainer
            preferences[DARK_ON_TERTIARY_CONTAINER] = colors.onTertiaryContainer
            preferences[DARK_BACKGROUND] = colors.background
            preferences[DARK_ON_BACKGROUND] = colors.onBackground
            preferences[DARK_SURFACE] = colors.surface
            preferences[DARK_ON_SURFACE] = colors.onSurface
            preferences[DARK_ERROR] = colors.error
            preferences[DARK_ON_ERROR] = colors.onError
            preferences[DARK_ERROR_CONTAINER] = colors.errorContainer
            preferences[DARK_ON_ERROR_CONTAINER] = colors.onErrorContainer
        }
    }

    /**
     * 重置淺色主題為預設值
     */
    suspend fun resetLightColors() {
        saveLightColorCustomization(ColorCustomization.defaultLight())
    }

    /**
     * 重置深色主題為預設值
     */
    suspend fun resetDarkColors() {
        saveDarkColorCustomization(ColorCustomization.defaultDark())
    }
}
