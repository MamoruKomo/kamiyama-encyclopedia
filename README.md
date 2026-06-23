# 神山生物図鑑 MVP

神山町を歩きながら植物と虫を撮影し、自分だけの観察ピンと図鑑を育てるスマホ向けWebアプリです。GitHub Pagesで配信する前提で、Expo / React Native Web寄りに実装しています。

## MVPでできること

- OpenStreetMap上に神山町周辺の自然レイヤーを表示
- GBIF由来の植物・虫の周辺記録を候補ピンとして表示
- カメラで写真撮影
- GPSと撮影時刻を観察記録に保存
- 写真・日時・場所・候補生物・レア度をIndexedDBにローカル保存
- 発見済みの観察を図鑑として一覧表示
- THINKLET Androidアプリから観察メタデータを取り込み

## 方針

本格AI同定は次フェーズに回し、MVPでは「植物 / 虫」の大分類と位置・季節・周辺記録から候補を出します。APIキーをGitHub Pagesに置かないため、サーバー不要で安全に動かせる範囲を優先しています。

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

`node_modules/`、`dist/`、`.expo/`、各Androidプロジェクトの `.gradle/` と `build/` は再生成できるignored生成物です。容量が厳しいときは、これらを削除候補として扱えます。

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

THINKLETアプリでは、カメラ撮影、GPS取得、撮影時刻取得、ML Kit Image Labelingによる簡易ラベル推定を行います。「Webへ送る」を押すと、GitHub PagesのWeb図鑑を開き、`thinkletObservation` パラメータ経由で観察メタデータをIndexedDBへ取り込みます。

現時点ではサーバーなし構成のため、撮影した写真本体はTHINKLET側ローカルに保存し、Web側にはプレースホルダー画像と写真URIメモを保存します。写真本体の同期は、次フェーズでPWA Share Targetまたは小さなAPIを追加する想定です。

### Webを起動しない同期

`sync-worker/` にCloudflare Worker + KVの同期APIを追加しています。これをデプロイすると、THINKLETはWeb画面を開かずに観察メタデータをPOSTできます。Web図鑑は起動時に同期APIから未取り込み観察を取得します。

Worker側:

```bash
cd sync-worker
npm install
npx wrangler kv namespace create OBSERVATIONS
# 表示されたidを sync-worker/wrangler.jsonc の kv_namespaces[0].id へ設定
npx wrangler secret put SYNC_WRITE_TOKEN
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

このAndroid Studio版は、Web版と同じMVP方針で本格AI同定は次フェーズです。現段階では、植物/虫の分類と、場所・季節・周辺記録から候補を出します。

## データ

候補生物データはGBIF occurrence APIで神山町周辺の軽い範囲を確認し、MVP用に軽量化して `src/data/kamiyama.ts` に同梱しています。自然レイヤーは、観察体験を作るための神山町周辺の概略ポリゴンです。
