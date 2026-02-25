package com.example.stonkseveryday.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.stonkseveryday.data.model.ColorCustomization

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorCustomizationScreen(
    lightColors: ColorCustomization,
    darkColors: ColorCustomization,
    onNavigateBack: () -> Unit,
    onSaveLightColors: (ColorCustomization) -> Unit,
    onSaveDarkColors: (ColorCustomization) -> Unit,
    onResetLightColors: () -> Unit,
    onResetDarkColors: () -> Unit
) {
    var selectedTheme by remember { mutableStateOf(0) } // 0: 淺色, 1: 深色
    var currentLightColors by remember { mutableStateOf(lightColors) }
    var currentDarkColors by remember { mutableStateOf(darkColors) }
    var showColorPicker by remember { mutableStateOf(false) }
    var selectedColorField by remember { mutableStateOf("") }
    var showSaveSuccess by remember { mutableStateOf(false) }

    // 更新顏色時的回調
    LaunchedEffect(lightColors, darkColors) {
        currentLightColors = lightColors
        currentDarkColors = darkColors
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("介面顏色設定") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 主題選擇
            TabRow(selectedTabIndex = selectedTheme) {
                Tab(
                    selected = selectedTheme == 0,
                    onClick = { selectedTheme = 0 },
                    text = { Text("淺色主題") }
                )
                Tab(
                    selected = selectedTheme == 1,
                    onClick = { selectedTheme = 1 },
                    text = { Text("深色主題") }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            val colors = if (selectedTheme == 0) currentLightColors else currentDarkColors

            // 說明文字
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Text(
                    text = "點擊顏色區塊以修改顏色，修改後請記得儲存設定。",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            // 主要顏色
            ColorSection(
                title = "主要顏色",
                colors = listOf(
                    "主要色 (Primary)" to colors.primary,
                    "主要色上的文字 (On Primary)" to colors.onPrimary,
                    "主要色容器 (Primary Container)" to colors.primaryContainer,
                    "容器上的文字 (On Primary Container)" to colors.onPrimaryContainer
                ),
                onColorClick = { field, _ ->
                    selectedColorField = field
                    showColorPicker = true
                }
            )

            // 次要顏色
            ColorSection(
                title = "次要顏色",
                colors = listOf(
                    "次要色 (Secondary)" to colors.secondary,
                    "次要色上的文字 (On Secondary)" to colors.onSecondary,
                    "次要色容器 (Secondary Container)" to colors.secondaryContainer,
                    "容器上的文字 (On Secondary Container)" to colors.onSecondaryContainer
                ),
                onColorClick = { field, _ ->
                    selectedColorField = field
                    showColorPicker = true
                }
            )

            // 第三顏色
            ColorSection(
                title = "第三顏色",
                colors = listOf(
                    "第三色 (Tertiary)" to colors.tertiary,
                    "第三色上的文字 (On Tertiary)" to colors.onTertiary,
                    "第三色容器 (Tertiary Container)" to colors.tertiaryContainer,
                    "容器上的文字 (On Tertiary Container)" to colors.onTertiaryContainer
                ),
                onColorClick = { field, _ ->
                    selectedColorField = field
                    showColorPicker = true
                }
            )

            // 背景與表面
            ColorSection(
                title = "背景與表面",
                colors = listOf(
                    "背景 (Background)" to colors.background,
                    "背景上的文字 (On Background)" to colors.onBackground,
                    "表面 (Surface)" to colors.surface,
                    "表面上的文字 (On Surface)" to colors.onSurface
                ),
                onColorClick = { field, _ ->
                    selectedColorField = field
                    showColorPicker = true
                }
            )

            // 錯誤顏色
            ColorSection(
                title = "錯誤顏色",
                colors = listOf(
                    "錯誤色 (Error)" to colors.error,
                    "錯誤色上的文字 (On Error)" to colors.onError,
                    "錯誤色容器 (Error Container)" to colors.errorContainer,
                    "容器上的文字 (On Error Container)" to colors.onErrorContainer
                ),
                onColorClick = { field, _ ->
                    selectedColorField = field
                    showColorPicker = true
                }
            )

            if (showSaveSuccess) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Text(
                        text = "顏色設定已儲存",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }

            // 操作按鈕
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        if (selectedTheme == 0) {
                            onSaveLightColors(currentLightColors)
                        } else {
                            onSaveDarkColors(currentDarkColors)
                        }
                        showSaveSuccess = true
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("儲存設定")
                }

                OutlinedButton(
                    onClick = {
                        if (selectedTheme == 0) {
                            onResetLightColors()
                            currentLightColors = ColorCustomization.defaultLight()
                        } else {
                            onResetDarkColors()
                            currentDarkColors = ColorCustomization.defaultDark()
                        }
                        showSaveSuccess = true
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("重置為預設")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // 顏色選擇器對話框
    if (showColorPicker) {
        ColorPickerDialog(
            initialColor = when (selectedColorField) {
                "主要色 (Primary)" -> if (selectedTheme == 0) currentLightColors.primary else currentDarkColors.primary
                "主要色上的文字 (On Primary)" -> if (selectedTheme == 0) currentLightColors.onPrimary else currentDarkColors.onPrimary
                "主要色容器 (Primary Container)" -> if (selectedTheme == 0) currentLightColors.primaryContainer else currentDarkColors.primaryContainer
                "容器上的文字 (On Primary Container)" -> if (selectedTheme == 0) currentLightColors.onPrimaryContainer else currentDarkColors.onPrimaryContainer
                "次要色 (Secondary)" -> if (selectedTheme == 0) currentLightColors.secondary else currentDarkColors.secondary
                "次要色上的文字 (On Secondary)" -> if (selectedTheme == 0) currentLightColors.onSecondary else currentDarkColors.onSecondary
                "次要色容器 (Secondary Container)" -> if (selectedTheme == 0) currentLightColors.secondaryContainer else currentDarkColors.secondaryContainer
                "容器上的文字 (On Secondary Container)" -> if (selectedTheme == 0) currentLightColors.onSecondaryContainer else currentDarkColors.onSecondaryContainer
                "第三色 (Tertiary)" -> if (selectedTheme == 0) currentLightColors.tertiary else currentDarkColors.tertiary
                "第三色上的文字 (On Tertiary)" -> if (selectedTheme == 0) currentLightColors.onTertiary else currentDarkColors.onTertiary
                "第三色容器 (Tertiary Container)" -> if (selectedTheme == 0) currentLightColors.tertiaryContainer else currentDarkColors.tertiaryContainer
                "容器上的文字 (On Tertiary Container)" -> if (selectedTheme == 0) currentLightColors.onTertiaryContainer else currentDarkColors.onTertiaryContainer
                "背景 (Background)" -> if (selectedTheme == 0) currentLightColors.background else currentDarkColors.background
                "背景上的文字 (On Background)" -> if (selectedTheme == 0) currentLightColors.onBackground else currentDarkColors.onBackground
                "表面 (Surface)" -> if (selectedTheme == 0) currentLightColors.surface else currentDarkColors.surface
                "表面上的文字 (On Surface)" -> if (selectedTheme == 0) currentLightColors.onSurface else currentDarkColors.onSurface
                "錯誤色 (Error)" -> if (selectedTheme == 0) currentLightColors.error else currentDarkColors.error
                "錯誤色上的文字 (On Error)" -> if (selectedTheme == 0) currentLightColors.onError else currentDarkColors.onError
                "錯誤色容器 (Error Container)" -> if (selectedTheme == 0) currentLightColors.errorContainer else currentDarkColors.errorContainer
                "容器上的文字 (On Error Container)" -> if (selectedTheme == 0) currentLightColors.onErrorContainer else currentDarkColors.onErrorContainer
                else -> 0xFFFFFFFF
            },
            onColorSelected = { newColor ->
                if (selectedTheme == 0) {
                    currentLightColors = updateColorField(currentLightColors, selectedColorField, newColor)
                } else {
                    currentDarkColors = updateColorField(currentDarkColors, selectedColorField, newColor)
                }
                showColorPicker = false
            },
            onDismiss = { showColorPicker = false }
        )
    }

    LaunchedEffect(showSaveSuccess) {
        if (showSaveSuccess) {
            kotlinx.coroutines.delay(2000)
            showSaveSuccess = false
        }
    }
}

@Composable
private fun ColorSection(
    title: String,
    colors: List<Pair<String, Long>>,
    onColorClick: (String, Long) -> Unit
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            colors.forEach { (name, colorValue) ->
                ColorItem(
                    name = name,
                    color = colorValue,
                    onClick = { onColorClick(name, colorValue) }
                )
            }
        }
    }
}

@Composable
private fun ColorItem(
    name: String,
    color: Long,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClick = onClick,
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            )
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = ColorCustomization.colorToHex(color),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(color), RoundedCornerShape(8.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            )
        }
    }
}

@Composable
private fun ColorPickerDialog(
    initialColor: Long,
    onColorSelected: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var hexInput by remember { mutableStateOf(ColorCustomization.colorToHex(initialColor)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("選擇顏色") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("請輸入十六進位顏色代碼（例如：#FF6200EE 或 #6200EE）")

                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { hexInput = it },
                    label = { Text("顏色代碼") },
                    placeholder = { Text("#AARRGGBB 或 #RRGGBB") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // 預覽顏色
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("預覽：")
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .background(
                                Color(ColorCustomization.parseColor(hexInput)),
                                RoundedCornerShape(8.dp)
                            )
                            .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
                    )
                }

                // 常用顏色快選
                Text("常用顏色：", style = MaterialTheme.typography.labelMedium)
                CommonColorPicker { selectedColor ->
                    hexInput = ColorCustomization.colorToHex(selectedColor)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val newColor = ColorCustomization.parseColor(hexInput)
                    onColorSelected(newColor)
                }
            ) {
                Text("確定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun CommonColorPicker(onColorSelected: (Long) -> Unit) {
    val commonColors = listOf(
        0xFFFF0000, // 紅
        0xFFFF5722, // 深橙
        0xFFFF9800, // 橙
        0xFFFFC107, // 琥珀
        0xFFFFEB3B, // 黃
        0xFFCDDC39, // 萊姆
        0xFF8BC34A, // 淺綠
        0xFF4CAF50, // 綠
        0xFF009688, // 青
        0xFF00BCD4, // 青色
        0xFF03A9F4, // 淺藍
        0xFF2196F3, // 藍
        0xFF3F51B5, // 靛藍
        0xFF673AB7, // 深紫
        0xFF9C27B0, // 紫
        0xFFE91E63, // 粉紅
        0xFF000000, // 黑
        0xFF757575, // 灰
        0xFFFFFFFF  // 白
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        commonColors.chunked(6).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(color), RoundedCornerShape(8.dp))
                            .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
                            .clickable(
                                onClick = { onColorSelected(color) },
                                indication = null,
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                            )
                    )
                }
            }
        }
    }
}

private fun updateColorField(colors: ColorCustomization, field: String, newColor: Long): ColorCustomization {
    return when (field) {
        "主要色 (Primary)" -> colors.copy(primary = newColor)
        "主要色上的文字 (On Primary)" -> colors.copy(onPrimary = newColor)
        "主要色容器 (Primary Container)" -> colors.copy(primaryContainer = newColor)
        "容器上的文字 (On Primary Container)" -> colors.copy(onPrimaryContainer = newColor)
        "次要色 (Secondary)" -> colors.copy(secondary = newColor)
        "次要色上的文字 (On Secondary)" -> colors.copy(onSecondary = newColor)
        "次要色容器 (Secondary Container)" -> colors.copy(secondaryContainer = newColor)
        "容器上的文字 (On Secondary Container)" -> colors.copy(onSecondaryContainer = newColor)
        "第三色 (Tertiary)" -> colors.copy(tertiary = newColor)
        "第三色上的文字 (On Tertiary)" -> colors.copy(onTertiary = newColor)
        "第三色容器 (Tertiary Container)" -> colors.copy(tertiaryContainer = newColor)
        "容器上的文字 (On Tertiary Container)" -> colors.copy(onTertiaryContainer = newColor)
        "背景 (Background)" -> colors.copy(background = newColor)
        "背景上的文字 (On Background)" -> colors.copy(onBackground = newColor)
        "表面 (Surface)" -> colors.copy(surface = newColor)
        "表面上的文字 (On Surface)" -> colors.copy(onSurface = newColor)
        "錯誤色 (Error)" -> colors.copy(error = newColor)
        "錯誤色上的文字 (On Error)" -> colors.copy(onError = newColor)
        "錯誤色容器 (Error Container)" -> colors.copy(errorContainer = newColor)
        "容器上的文字 (On Error Container)" -> colors.copy(onErrorContainer = newColor)
        else -> colors
    }
}
