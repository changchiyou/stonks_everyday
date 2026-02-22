package com.example.stonkseveryday

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.stonkseveryday.data.model.StockSummary
import com.example.stonkseveryday.data.preferences.UserPreferences
import com.example.stonkseveryday.ui.screens.AddTransactionScreen
import com.example.stonkseveryday.ui.screens.MainScreen
import com.example.stonkseveryday.ui.screens.SettingsScreen
import com.example.stonkseveryday.ui.theme.StonksEverydayTheme
import com.example.stonkseveryday.ui.viewmodel.StockViewModel
import com.example.stonkseveryday.widget.WidgetUpdateHelper
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Schedule widget updates
        WidgetUpdateHelper.scheduleWidgetUpdates(this)

        setContent {
            StonksEverydayTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    StockTradingApp()
                }
            }
        }
    }
}

@Composable
fun StockTradingApp() {
    val context = LocalContext.current
    val viewModel: StockViewModel = viewModel()
    val userPreferences = remember { UserPreferences(context) }
    val scope = rememberCoroutineScope()

    val transactions by viewModel.allTransactions.collectAsState(initial = emptyList())
    val summary by viewModel.stockSummary.collectAsState(initial = StockSummary(0.0, 0.0, 0.0, emptyList()))
    val currentToken by userPreferences.finmindToken.collectAsState(initial = "")

    var showAddTransaction by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    when {
        showAddTransaction -> {
            AddTransactionScreen(
                onNavigateBack = { showAddTransaction = false },
                onSaveTransaction = { transaction ->
                    viewModel.insertTransaction(transaction)
                }
            )
        }
        showSettings -> {
            SettingsScreen(
                currentToken = currentToken,
                onNavigateBack = { showSettings = false },
                onSaveToken = { token ->
                    scope.launch {
                        userPreferences.saveFinmindToken(token)
                    }
                }
            )
        }
        else -> {
            MainScreen(
                transactions = transactions,
                summary = summary,
                onAddTransaction = { showAddTransaction = true },
                onTransactionClick = { /* 可以在這裡加入編輯功能 */ },
                onOpenSettings = { showSettings = true }
            )
        }
    }
}
