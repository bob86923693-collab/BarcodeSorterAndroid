# 條碼分類 Android App（MVP）

這是依照需求製作的 Android 第一版：

- App 內直接開啟相機掃描一維條碼
- 預設區域：BB01、BB02、BR16
- 可新增其他區域
- 選好區域後可連續掃描
- 保留條碼前導 0，例如 `01147`
- 同一條碼若已在其他區域，會跳出移動 / 保留原區域提示
- 可查詢條碼目前位置
- 可查看各區域條碼與數量
- 可刪除條碼
- 可匯出 CSV
- 使用本機 Room 資料庫，不需要網路

## APK 自動建置

GitHub Actions 會在 `main` 分支有新提交時自動建置 Debug APK。
完成後到 GitHub repository 的 **Actions** 頁面，打開最新一次 `Build Android APK`，在 **Artifacts** 下載 `BarcodeSorter-debug-apk`。

下載 ZIP 解壓後，裡面的 `app-debug.apk` 就能安裝到 Android 手機測試。

## 現場操作

1. 開啟 App。
2. 上方選擇目前區域，例如 `BB01`。
3. 將條碼放在掃描框內。
4. 成功後會嗶聲 + 震動並儲存。
5. 換區時切換到 `BB02`、`BR16` 等，再繼續掃。
6. 若條碼已存在其他區域，App 會詢問是否移動。
7. 「查詢」頁可輸入條碼找位置。
8. 「區域」頁可新增區域、查看內容與匯出 CSV。
