# 介面顏色客製化功能

## 功能概述

此功能允許使用者完全客製化應用程式的介面顏色，包含淺色和深色主題的所有顏色設定。

## 使用方式

1. 開啟應用程式，點擊設定按鈕
2. 在設定頁面中，找到「外觀設定」區塊
3. 點擊「自訂介面顏色」按鈕
4. 選擇要編輯的主題（淺色或深色）
5. 點擊任意顏色區塊進行修改
6. 在顏色選擇器中：
   - 手動輸入十六進位顏色代碼（支援 #AARRGGBB 或 #RRGGBB 格式）
   - 或從常用顏色快選面板選擇
7. 點擊「儲存設定」以套用變更
8. 如需恢復預設值，點擊「重置為預設」

## 可客製化的顏色項目

### 主要顏色
- **Primary（主要色）**: 應用程式的主色調
- **On Primary（主要色上的文字）**: 主要色上方的文字顏色
- **Primary Container（主要色容器）**: 主要色的容器背景
- **On Primary Container（容器上的文字）**: 主要色容器上的文字顏色

### 次要顏色
- **Secondary（次要色）**: 次要強調色
- **On Secondary（次要色上的文字）**: 次要色上的文字顏色
- **Secondary Container（次要色容器）**: 次要色的容器背景
- **On Secondary Container（容器上的文字）**: 次要色容器上的文字顏色

### 第三顏色
- **Tertiary（第三色）**: 第三強調色
- **On Tertiary（第三色上的文字）**: 第三色上的文字顏色
- **Tertiary Container（第三色容器）**: 第三色的容器背景
- **On Tertiary Container（容器上的文字）**: 第三色容器上的文字顏色

### 背景與表面
- **Background（背景）**: 應用程式主背景色
- **On Background（背景上的文字）**: 背景上的文字顏色
- **Surface（表面）**: 卡片和元件的表面顏色
- **On Surface（表面上的文字）**: 表面上的文字顏色

### 錯誤顏色
- **Error（錯誤色）**: 錯誤提示的顏色
- **On Error（錯誤色上的文字）**: 錯誤色上的文字顏色
- **Error Container（錯誤色容器）**: 錯誤訊息的容器背景
- **On Error Container（容器上的文字）**: 錯誤容器上的文字顏色

## 技術實作細節

### 資料儲存
- 使用 DataStore Preferences 儲存顏色設定
- 淺色和深色主題分別儲存
- 顏色值以 Long 格式儲存（AARRGGBB）

### 架構設計
1. **ColorCustomization**: 顏色設定的資料模型
2. **UserPreferences**: 負責讀取和儲存顏色設定
3. **Theme.kt**: 將顏色設定轉換為 Material3 ColorScheme
4. **ColorCustomizationScreen**: 顏色設定的 UI 介面
5. **MainActivity**: 整合顏色設定到應用程式主題

### 相關檔案
- `app/src/main/java/com/example/stonkseveryday/data/model/ColorCustomization.kt`
- `app/src/main/java/com/example/stonkseveryday/data/preferences/UserPreferences.kt`
- `app/src/main/java/com/example/stonkseveryday/ui/theme/Theme.kt`
- `app/src/main/java/com/example/stonkseveryday/ui/screens/ColorCustomizationScreen.kt`
- `app/src/main/java/com/example/stonkseveryday/ui/screens/SettingsScreen.kt`
- `app/src/main/java/com/example/stonkseveryday/MainActivity.kt`

## 備份與恢復

自訂的介面顏色設定會包含在**個人設定備份**中。應用程式提供兩種獨立的備份：

### 個人設定備份（包含顏色）⚙️
當你執行「設定」→「個人設定備份」→「備份設定」時，會保存：

- ✅ **淺色主題顏色設定**（20 個顏色項目）
- ✅ **深色主題顏色設定**（20 個顏色項目）
- ✅ 費率設定（手續費率、證交稅率）
- ✅ 股利計算偏好設定
- ❌ FinMind API Token（安全考量，不會備份）

備份檔案：`stonks_settings_yyyyMMdd_HHmmss.json`

### 交易資料備份 📊
交易資料備份（「備份交易」）**不包含**顏色設定，僅包含：
- 交易記錄
- 股利資料

### 恢復顏色設定
1. 設定 → 「個人設定備份」→「恢復設定」
2. 選擇之前備份的設定檔案
3. 顏色會立即套用（可能需要重新啟動 app）
4. **不會影響**交易記錄

詳細說明請參考 [BACKUP_RESTORE.md](BACKUP_RESTORE.md)

## 注意事項

1. 顏色變更會即時生效（儲存後重新渲染）
2. 顏色設定會持久化儲存，重啟應用程式後仍會保留
3. 建議選擇對比度足夠的顏色組合以確保可讀性
4. 可隨時重置為預設值
5. 淺色和深色主題的顏色設定是獨立的
6. 顏色設定包含在個人設定備份中，可獨立備份和恢復
7. API Token 不會被備份，確保安全性

## 未來改進方向

- 加入顏色預設主題（例如：夜間模式、高對比度等）
- 支援從圖片擷取顏色
- 加入顏色對比度檢查工具
- 支援匯出/匯入顏色設定
