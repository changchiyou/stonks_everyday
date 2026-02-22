package com.example.stonkseveryday.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.stonkseveryday.MainActivity
import com.example.stonkseveryday.data.database.StockDatabase
import com.example.stonkseveryday.data.model.StockSummary
import com.example.stonkseveryday.data.model.TransactionType
import com.example.stonkseveryday.data.repository.StockRepository
import kotlinx.coroutines.flow.first
import java.text.NumberFormat
import java.util.*

class StockWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val dao = StockDatabase.getDatabase(context).stockTransactionDao()
        val repository = StockRepository(dao, context)

        // Get today's transactions
        val todayTransactions = repository.todayTransactions.first()

        // Calculate daily profit/loss
        val dailyProfitLoss = todayTransactions.sumOf { tx ->
            when (tx.transactionType) {
                TransactionType.SELL -> tx.totalAmount
                TransactionType.BUY -> -tx.totalAmount
            }
        }

        // Get all transactions for total summary
        val allTransactions = repository.allTransactions.first()
        val summary = calculateQuickSummary(allTransactions)

        provideContent {
            WidgetContent(
                dailyProfitLoss = dailyProfitLoss,
                totalProfitLoss = summary.totalProfitLoss,
                totalIncome = summary.totalIncome
            )
        }
    }

    private fun calculateQuickSummary(transactions: List<com.example.stonkseveryday.data.model.StockTransaction>): StockSummary {
        val holdings = mutableMapOf<String, Pair<Int, Double>>()

        transactions.forEach { tx ->
            val (quantity, cost) = holdings.getOrDefault(tx.stockCode, Pair(0, 0.0))
            when (tx.transactionType) {
                TransactionType.BUY -> {
                    holdings[tx.stockCode] = Pair(
                        quantity + tx.quantity,
                        cost + tx.totalAmount
                    )
                }
                TransactionType.SELL -> {
                    holdings[tx.stockCode] = Pair(
                        quantity - tx.quantity,
                        cost - tx.totalAmount
                    )
                }
            }
        }

        val totalProfitLoss = holdings.values.sumOf { (quantity, cost) ->
            if (quantity > 0) {
                val averageCost = cost / quantity
                val currentPrice = averageCost * 1.05
                (currentPrice - averageCost) * quantity
            } else 0.0
        }

        val totalIncome = transactions
            .filter { it.transactionType == TransactionType.SELL }
            .sumOf { it.totalAmount }

        return StockSummary(
            totalIncome = totalIncome,
            dailyProfitLoss = 0.0,
            totalProfitLoss = totalProfitLoss,
            holdings = emptyList()
        )
    }

    @Composable
    fun WidgetContent(
        dailyProfitLoss: Double,
        totalProfitLoss: Double,
        totalIncome: Double
    ) {
        val currencyFormat = NumberFormat.getCurrencyInstance(Locale("zh", "TW"))

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(Color(0xFFF5F5F5)))
                .padding(16.dp)
                .clickable(actionStartActivity<MainActivity>()),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Title
                Text(
                    text = "股票交易",
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = ColorProvider(Color.Black)
                    )
                )

                Spacer(modifier = GlanceModifier.height(8.dp))

                // Daily Profit/Loss (主要顯示)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "當日損益",
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            color = ColorProvider(Color.Gray)
                        )
                    )

                    Spacer(modifier = GlanceModifier.height(4.dp))

                    Text(
                        text = currencyFormat.format(dailyProfitLoss),
                        style = TextStyle(
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (dailyProfitLoss >= 0) {
                                ColorProvider(Color(0xFF2E7D32))
                            } else {
                                ColorProvider(Color(0xFFC62828))
                            }
                        )
                    )
                }

                Spacer(modifier = GlanceModifier.height(12.dp))

                // Divider
                Box(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(ColorProvider(Color.LightGray)),
                    content = {}
                )

                Spacer(modifier = GlanceModifier.height(12.dp))

                // Additional Info
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = GlanceModifier.defaultWeight(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "總收入",
                            style = TextStyle(
                                fontSize = 10.sp,
                                color = ColorProvider(Color.Gray)
                            )
                        )
                        Spacer(modifier = GlanceModifier.height(2.dp))
                        Text(
                            text = currencyFormat.format(totalIncome),
                            style = TextStyle(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorProvider(Color.Black)
                            )
                        )
                    }

                    Column(
                        modifier = GlanceModifier.defaultWeight(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "總損益",
                            style = TextStyle(
                                fontSize = 10.sp,
                                color = ColorProvider(Color.Gray)
                            )
                        )
                        Spacer(modifier = GlanceModifier.height(2.dp))
                        Text(
                            text = currencyFormat.format(totalProfitLoss),
                            style = TextStyle(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (totalProfitLoss >= 0) {
                                    ColorProvider(Color(0xFF2E7D32))
                                } else {
                                    ColorProvider(Color(0xFFC62828))
                                }
                            )
                        )
                    }
                }
            }
        }
    }
}

class StockWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StockWidget()
}
