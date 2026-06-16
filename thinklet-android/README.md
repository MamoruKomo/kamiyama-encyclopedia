# 神山図鑑 THINKLET Android

THINKLET上で観察を撮影し、神山生物図鑑Webアプリへメタデータを送るネイティブAndroidアプリです。

## できること

- CameraXでTHINKLETカメラをプレビュー
- サイドボタンまたは画面ボタンで撮影
- 撮影写真をTHINKLET側ローカルへ保存
- 位置情報と撮影時刻を取得
- ML Kit Image Labelingで簡易ラベルを推定
- Web図鑑へ `thinkletObservation` パラメータで連携
- 同期API設定時はWebを起動せず観察メタデータを送信

## ビルド

```bash
./gradlew assembleDebug
```

## インストール

```bash
./gradlew installDebug
```

## 操作

1. アプリを起動
2. カメラと位置情報を許可
3. THINKLETのサイドボタン、または画面の「撮影」を押す
4. 「同期送信」を押す
5. Web図鑑をあとで開くと自動取り込みされる

同期APIをまだ設定していない場合は「Webで確認」で従来のURL連携を使えます。

## 同期API設定

`../sync-worker/` をデプロイし、`local.properties` に以下を追加して再ビルドしてください。

```properties
kamiyamaSyncApiUrl=https://YOUR_WORKER_URL
kamiyamaSyncWriteToken=YOUR_SYNC_WRITE_TOKEN
```

## 注意

GitHub Pagesのみの構成では、ネイティブアプリからWeb IndexedDBへ写真本体を直接同期するのが難しいため、MVPでは写真URIと観察メタデータのみを連携します。写真本体はTHINKLET側に保存されます。
