package com.example.stonkseveryday.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class UserPreferences(private val context: Context) {

    companion object {
        private val FINMIND_API_TOKEN = stringPreferencesKey("finmind_api_token")
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
}
