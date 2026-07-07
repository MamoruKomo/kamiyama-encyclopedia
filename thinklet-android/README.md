# 神山図鑑 THINKLET Android

THINKLET上で観察を撮影し、同期APIへ送る採集専用Androidアプリです。
THINKLET本体にはディスプレイがない前提なので、図鑑・地図・発見一覧の確認はスマホ側の `field-android` アプリで後から行います。

## できること

- CameraXでTHINKLETカメラを撮影用に起動
- サイドボタンで撮影
- scrcpy確認時のみ画面ボタンでテスト撮影/再送信
- 撮影写真をTHINKLET側ローカルへ保存
- 撮影時に新しい位置情報を取得し、取れない場合は最後に取得できた位置を使用
- 撮影時刻を取得
- ML Kit Image Labelingで簡易ラベルを推定
- 同期APIへ写真と観察メタデータを送信

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
3. THINKLETのサイドボタンを押す
4. 撮影、新しい位置情報取得、時刻取得、簡易ラベル推定、同期API送信が自動で走る
5. スマホ側の `field-android` を開き、「うけとる」画面から図鑑へ取り込む

画面UIはscrcpyでのデバッグ用です。実運用ではTHINKLET側の画面を見る必要はありません。

## 同期API設定

`../sync-worker/` をデプロイし、`local.properties` に以下を追加して再ビルドしてください。

```properties
kamiyamaSyncApiUrl=https://YOUR_WORKER_URL
kamiyamaSyncWriteToken=YOUR_SYNC_WRITE_TOKEN
```

昆虫・植物をAIでよそうするには、同期API側にOpenAI APIキーも設定してください。

```bash
cd ../sync-worker
npx wrangler secret put OPENAI_API_KEY
npx wrangler deploy
```

AIは写真から植物・昆虫をよそうし、候補に一致した場合は種名とレア度をスマホ側の図鑑へ同期します。結果は正解の断定ではなく「AIのよそう」として扱います。

## 注意

GitHub Pagesのみの構成では、ネイティブアプリからWeb IndexedDBへ写真本体を直接同期するのが難しいため、THINKLETは同期APIへ送信し、スマホ側アプリが同期APIから取り込む構成にしています。
