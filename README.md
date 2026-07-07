# 神山生物図鑑 MVP

神山町を歩きながら植物と虫を撮影し、自分だけの観察ピンと図鑑を育てるスマホ向けWebアプリです。GitHub Pagesで配信する前提で、Expo / React Native Web寄りに実装しています。

## MVPでできること

- OpenStreetMap上に神山町周辺の自然レイヤーを表示
- GBIF由来の植物・虫の周辺記録を候補ピンとして表示
- カメラで写真撮影
- GPSと撮影時刻を観察記録に保存
- 写真・日時・場所・候補生物・レア度をIndexedDBにローカル保存
- 発見済みの観察を図鑑として一覧表示
- THINKLET Androidアプリから写真つき観察を取り込み
- 同期APIにOpenAIキーを設定した場合、写真から植物/虫をAI判定

## 方針

通常のWeb/Androidアプリ単体では「植物 / 虫」の大分類と位置・季節・周辺記録から候補を出します。AI同定が必要な場合は `sync-worker/` にOpenAI APIキーを置き、GitHub Pagesや端末アプリにはAPIキーを置かない構成にしています。

## ミニマムシステム構成

今回のAndroid Studio版MVPは、まず次の3つだけを確実に動かす構成に戻しています。

1. `記録`: カメラ起動、撮影前の現在地取得、写真URI・座標・精度・撮影時刻の保存
2. `地図`: OpenStreetMap上に現在地と保存済み観察ピンを表示
3. `図鑑`: SQLiteに保存した観察の写真、名前、日時、場所、レア度を一覧表示

端末内の保存先は `field-android` のSQLiteです。サーバーなしでもスマホ単体で記録できます。THINKLETからのAI判定済み観察は任意の追加経路として `sync-worker` 経由で取り込みます。

## ファイル構成

```text
src/                         GitHub Pages向けのExpo / React Native Webアプリ
src/components/              Web版の地図・撮影・図鑑パネル
src/data/kamiyama.ts         Web版の候補生物・自然レイヤーデータ
src/lib/                     Web版の位置計算、IndexedDB保存、同期取り込み
field-android/               Android Studioで開くスマホ向けネイティブAndroidアプリ
field-android/app/src/main/java/com/mamorukomo/kamiyama/field/data/
                             Android版の候補データ、位置計算、SQLite保存
field-android/app/src/main/java/com/mamorukomo/kamiyama/field/ui/
                             Android版の共通UI、ヘッダー、フォーマット
field-android/app/src/main/java/com/mamorukomo/kamiyama/field/ui/screens/
                             Android版の地図・撮影・図鑑画面
thinklet-android/            THINKLET向けの撮影・同期用Androidアプリ
sync-worker/                 Webを起動しない同期用Cloudflare Worker
scripts/                     GitHub Pagesビルド後の補正スクリプト
```

`node_modules/`、`dist/`、`.expo/`、各Androidプロジェクトの `.gradle/`、`.kotlin/`、`build/` は再生成できるignored生成物です。容量が厳しいときは、これらを削除候補として扱えます。

## 開発

```bash
npm install
npm run dev
```

## ビルド

```bash
npm run build
```

`dist/` がGitHub Pages用の静的出力です。

## THINKLETアプリ

THINKLET側のネイティブAndroidアプリは `thinklet-android/` にあります。

```bash
cd thinklet-android
./gradlew assembleDebug
./gradlew installDebug
```

THINKLETアプリでは、カメラ撮影、GPS取得、撮影時刻取得、ML Kit Image Labelingによる簡易ラベル推定を行います。同期API URLを設定している場合は、「撮影→AI送信」ボタンまたはサイドボタンで、写真を圧縮してWorkerへPOSTします。

同期API URLが未設定の場合は、従来どおり「Webで確認」でGitHub Pagesを開き、観察メタデータだけを取り込めます。

### Webを起動しない同期

`sync-worker/` にCloudflare Worker + KVの同期APIを追加しています。これをデプロイすると、THINKLETはWeb画面を開かずに写真つき観察をPOSTできます。Workerに `OPENAI_API_KEY` を設定している場合は、写真をAI判定して、判定名・学名候補・信頼度・根拠を観察に付与します。Web図鑑は起動時に、Android Studio版アプリは図鑑画面の「AI同期を取り込む」から未取り込み観察を取得します。

Worker側:

```bash
cd sync-worker
npm install
npx wrangler kv namespace create OBSERVATIONS
# 表示されたidを sync-worker/wrangler.jsonc の kv_namespaces[0].id へ設定
npx wrangler secret put SYNC_WRITE_TOKEN
npx wrangler secret put OPENAI_API_KEY
# 必要ならモデル変更
# npx wrangler secret put OPENAI_MODEL
npm run deploy
```

THINKLET側:

```properties
# thinklet-android/local.properties
kamiyamaSyncApiUrl=https://YOUR_WORKER_URL
kamiyamaSyncWriteToken=YOUR_SYNC_WRITE_TOKEN
```

```bash
cd thinklet-android
./gradlew installDebug
```

Android Studio版アプリ側:

```properties
# field-android/local.properties
kamiyamaSyncApiUrl=https://YOUR_WORKER_URL
```

```bash
cd field-android
./gradlew installDebug
```

Web側は一度だけ以下のURLで開くと同期API URLを端末に保存します。

```text
https://mamorukomo.github.io/kamiyama-encyclopedia/?syncEndpoint=https://YOUR_WORKER_URL
```

## Android Studio版アプリ

スマホ本体で動かすネイティブAndroidアプリは `field-android/` にあります。Android Studioでは、リポジトリ直下ではなく `field-android/` フォルダを開いてください。

実装内容:

- Material 3 + Jetpack Composeの統一UI
- OpenStreetMap表示
- 神山町周辺の自然レイヤーと候補生物スポット表示
- カメラアプリで写真撮影
- GPS、撮影時刻、写真URIを観察記録に保存
- SQLiteローカル保存
- 発見済み図鑑一覧、レア度、場所、日時表示
- 同期APIからAI判定済みTHINKLET観察をSQLiteへ取り込み

ビルド:

```bash
cd field-android
./gradlew assembleDebug
```

端末へインストール:

```bash
cd field-android
./gradlew installDebug
```

このAndroid Studio版は、ローカル撮影では植物/虫の分類と、場所・季節・周辺記録から候補を出します。THINKLETから送られた写真は同期API側でAI判定され、図鑑画面の「AI同期を取り込む」でSQLiteに保存できます。

## データ

候補生物データはGBIF occurrence APIで神山町周辺の軽い範囲を確認し、MVP用に軽量化して `src/data/kamiyama.ts` に同梱しています。自然レイヤーは、観察体験を作るための神山町周辺の概略ポリゴンです。
