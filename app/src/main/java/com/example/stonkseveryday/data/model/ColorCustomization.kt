package com.example.stonkseveryday.data.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * 顏色客製化設定
 */
data class ColorCustomization(
    // 主要顏色
    val primary: Long = 0xFF006C4C,
    val onPrimary: Long = 0xFFFFFFFF,
    val primaryContainer: Long = 0xFF89F8C7,
    val onPrimaryContainer: Long = 0xFF002114,

    // 次要顏色
    val secondary: Long = 0xFF4D6357,
    val onSecondary: Long = 0xFFFFFFFF,
    val secondaryContainer: Long = 0xFFCFE9D9,
    val onSecondaryContainer: Long = 0xFF092016,

    // 第三顏色
    val tertiary: Long = 0xFF3D6373,
    val onTertiary: Long = 0xFFFFFFFF,
    val tertiaryContainer: Long = 0xFFC1E8FB,
    val onTertiaryContainer: Long = 0xFF001F29,

    // 背景與表面
    val background: Long = 0xFFFBFDF9,
    val onBackground: Long = 0xFF191C1A,
    val surface: Long = 0xFFFBFDF9,
    val onSurface: Long = 0xFF191C1A,

    // 錯誤顏色
    val error: Long = 0xFFBA1A1A,
    val onError: Long = 0xFFFFFFFF,
    val errorContainer: Long = 0xFFFFDAD6,
    val onErrorContainer: Long = 0xFF410002
) {
    companion object {
        /**
         * 將十六進位字串轉換為 Long
         */
        fun parseColor(hexString: String): Long {
            return try {
                val cleanHex = hexString.removePrefix("#").removePrefix("0x")
                if (cleanHex.length == 6) {
                    // 如果只有 6 位，加上 FF 作為 alpha
                    ("FF" + cleanHex).toLong(16)
                } else if (cleanHex.length == 8) {
                    cleanHex.toLong(16)
                } else {
                    0xFFFFFFFF // 預設白色
                }
            } catch (e: Exception) {
                0xFFFFFFFF // 解析失敗時返回白色
            }
        }

        /**
         * 將 Long 轉換為十六進位字串（含 # 前綴）
         */
        fun colorToHex(color: Long): String {
            return "#${color.toString(16).padStart(8, '0').uppercase()}"
        }

        /**
         * 預設淺色主題顏色
         */
        fun defaultLight() = ColorCustomization()

        /**
         * 預設深色主題顏色
         */
        fun defaultDark() = ColorCustomization(
            primary = 0xFF6CDBAC,
            onPrimary = 0xFF003826,
            primaryContainer = 0xFF005138,
            onPrimaryContainer = 0xFF89F8C7,
            secondary = 0xFFB3CCBE,
            onSecondary = 0xFF1F352A,
            secondaryContainer = 0xFF354B40,
            onSecondaryContainer = 0xFFCFE9D9,
            tertiary = 0xFFA5CCDE,
            onTertiary = 0xFF073543,
            tertiaryContainer = 0xFF244C5B,
            onTertiaryContainer = 0xFFC1E8FB,
            background = 0xFF191C1A,
            onBackground = 0xFFE1E3DF,
            surface = 0xFF191C1A,
            onSurface = 0xFFE1E3DF,
            error = 0xFFFFB4AB,
            onError = 0xFF690005,
            errorContainer = 0xFF93000A,
            onErrorContainer = 0xFFFFDAD6
        )
    }

    /**
     * 轉換為 Compose Color
     */
    fun toColor(colorValue: Long): Color = Color(colorValue)
}
