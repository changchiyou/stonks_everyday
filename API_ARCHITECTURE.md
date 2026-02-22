# API 架構說明

## 雙重 API 策略流程圖

```
使用者查詢股價
    ↓
檢查使用者是否設定 FinMind Token
    ↓
有 Token？
├─ 是 → ① 嘗試 FinMind API (使用使用者的 Token)
│         ↓
│         成功？
│         ├─ 是 → 回傳股價資料 ✓
│         └─ 否 → ② 嘗試 TWSE 官方 API
│                  ↓
│                  成功？
│                  ├─ 是 → 回傳股價資料 ✓
│                  └─ 否 → 回傳 null (不顯示該股票損益)
│
└─ 否 → ② 直接使用 TWSE 官方 API
         ↓
         成功？
         ├─ 是 → 回傳股價資料 ✓
         └─ 否 → 回傳 null (不顯示該股票損益)
```

## API 比較表

| 特性 | FinMind API | TWSE 盤中即時 API |
|------|-------------|---------------|
| **免費使用** | ✓ (有限制) | ✓ (無限制) |
| **需要 Token** | 是（使用者設定） | ✗ |
| **請求限制** | 500次/天 (免費版) | 無限制 |
| **資料延遲** | ~15分鐘 | **盤中約 5 秒** |
| **查詢速度** | 快 | 中等 |
| **盤中即時** | ✓ | **✓** |
| **支援市場** | TSE, OTC | **TSE, OTC (自動判斷)** |
| **適用場景** | 有 Token 時優先 | 預設或備用 |

## API 使用策略

### 1. FinMind API (使用者設定 Token 時優先使用)
- **優點**：資料完整，查詢速度快
- **缺點**：需要使用者註冊並設定 Token
- **使用時機**：使用者有設定 Token 時優先使用

### 2. TWSE 盤中即時 API (預設或備用)
- **優點**：
  - 完全免費，無限制，官方資料，無需註冊
  - **盤中即時更新**（延遲僅約 5 秒）
  - 自動支援上市 (TSE) 和上櫃 (OTC) 股票
- **缺點**：回應格式需要解析
- **使用時機**：
  - 使用者未設定 Token 時作為預設
  - FinMind API 失敗時自動備用
- **資料來源**：https://mis.twse.com.tw/stock/api/getStockInfo.jsp

### 3. API 失敗處理
- **行為**：回傳 null，該股票不顯示未實現損益
- **原因**：避免顯示錯誤或過時的股價資料誤導使用者
- **使用者體驗**：明確告知無法取得資料，而非顯示錯誤資料

## 程式碼位置

### API 服務定義
- `StockApiService.kt` - FinMind API 介面
- `TwseApiService.kt` - TWSE 官方 API 介面

### API 實例
- `RetrofitInstance.kt` - FinMind Retrofit 實例
- `TwseRetrofitInstance` - TWSE Retrofit 實例

### Repository 邏輯
- `StockRepository.kt:getStockPrice()` - 主要查詢入口
- `StockRepository.kt:getStockPriceFromFinMind()` - FinMind 查詢
- `StockRepository.kt:getStockPriceFromTwse()` - TWSE 查詢

## 範例

### FinMind API 請求
```
GET https://api.finmindtrade.com/api/v4/data
  ?dataset=TaiwanStockPrice
  &data_id=2330
  &start_date=2024-01-01
```

### TWSE 盤中即時 API 請求
```
GET https://mis.twse.com.tw/stock/api/getStockInfo.jsp
  ?response=json
  &ex_ch=tse_2330.tw  (上市股票)
  &json=1
  &delay=0

或

GET https://mis.twse.com.tw/stock/api/getStockInfo.jsp
  ?response=json
  &ex_ch=otc_6488.tw  (上櫃股票)
  &json=1
  &delay=0
```

回應範例：
```json
{
  "rtcode": "0000",
  "msgArray": [{
    "c": "2330",
    "n": "台積電",
    "z": "595.00",  // 最新成交價
    "y": "590.00",  // 昨收價
    "o": "592.00",  // 開盤價
    "h": "597.00",  // 最高價
    "l": "591.00",  // 最低價
    "t": "13:30:00" // 成交時間
  }]
}
```

## 錯誤處理

所有 API 呼叫都包含完整的錯誤處理：

1. **網路錯誤** → 自動切換到備用 API
2. **API 限制** → 自動切換到備用 API
3. **資料解析錯誤** → 記錄 Log，使用降級方案
4. **股票代碼不存在** → 回傳 null

## 日誌記錄

所有 API 操作都會記錄到 Android Logcat：

- `Log.i` - 成功操作
- `Log.w` - API 切換警告
- `Log.e` - 錯誤訊息

查看日誌：
```bash
adb logcat | grep StockRepository
```

## 效能考量

### 快取策略 (未來可加入)
- 快取股價資料 15 分鐘
- 減少 API 呼叫次數
- 提升使用者體驗

### 批次查詢
- 支援一次查詢多支股票
- 減少網路往返次數
- 提高效率

## 安全性

- ✓ 使用 HTTPS 加密連線
- ✓ Token 存儲在 APP 內（非使用者端）
- ✓ 無敏感資料傳輸
- ✓ 符合台灣金融資料使用規範
