#!/bin/bash

# 檢查特定交易的股利明細
echo "清除 logcat..."
adb logcat -c

echo "開始監控 HoldingDetail Log..."
echo "請在 app 中進入該筆交易的持股明細頁面"
echo "=========================================="

# 監控 HoldingDetail 的詳細 Log
adb logcat -v time | grep -E "HoldingDetail|StockRepository"
