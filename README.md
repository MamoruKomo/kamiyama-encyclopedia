# 神山生物図鑑 MVP

神山町を歩きながらTHINKLETで植物と虫を撮影し、自分だけの観察ピンと図鑑を育てるスマホ向けアプリです。スマホ側は、THINKLETから届いた発見を受け取り、地図と図鑑で確認する役割です。

## MVPでできること

- OpenStreetMap上に神山町周辺の自然レイヤーを表示
- GBIF由来の植物・虫の周辺記録を候補ピンとして表示
- THINKLETで写真撮影
- THINKLET側で撮影時のGPS/ネットワーク位置と撮影時刻を観察記録に保存
- 写真・日時・場所・候補生物・レア度をIndexedDBにローカル保存
- 発見済みの観察を図鑑として一覧表示
- THINKLET Androidアプリから写真つき観察を取り込み
- 同期APIにOpenAIキーを設定した場合、写真から植物/虫を「AIのよそう」として分析

## 方針

AI同定が必要な場合は `sync-worker/` にOpenAI APIキーを置き、GitHub Pagesや端末アプリにはAPIキーを置かない構成にしています。小学生向けの体験として、AI結果は正解の断定ではなく「AIのよそう」として表示します。

## ミニマムシステム構成

Android Studio版MVPは、THINKLET撮影を前提に次の3つだけを確実に動かす構成にしています。

1. `うけとる`: THINKLETから届いた写真つき観察を取り込む
2. `マップ`: OpenStreetMap上に現在地と保存済み観察ピンを表示
3. `ずかん`: SQLiteに保存した観察の写真、AIのよそう、日時、場所、レア度を一覧表示

スマホ側の保存先は `field-android` のSQLiteです。撮影はTHINKLET、写真分析は `sync-worker`、確認はスマホアプリという最小構成です。

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

`sync-worker/` にCloudflare Worker + KVの同期APIを追加しています。これをデプロイすると、THINKLETはWeb画面を開かずに写真つき観察をPOSTできます。無料運用ではTHINKLET側のML KitラベルとWorkerの候補補正だけで動かします。Workerに `AI_MODE=openai` と `OPENAI_API_KEY` を設定した場合だけ、写真をOpenAIで分析して、AIのよそう、学名候補、信頼度、根拠、レア度を観察に付与します。Android Studio版アプリは「うけとる」画面から未取り込み観察を取得します。

OpenAI判定を有効化した場合は、候補表だけでなくGBIFの公開APIから候補生物の参考写真URLを取得し、観察写真と一緒にOpenAIへ渡します。参考写真はアプリや観察データとして保存せず、WorkerのKVに短期間キャッシュして使います。ネット写真はライセンスが混ざるため、MVPでは「判定補助」に限定し、図鑑内の写真は子どもがTHINKLETで撮ったものを主役にします。

Worker側:

```bash
cd sync-worker
npm install
npx wrangler kv namespace create OBSERVATIONS
# 表示されたidを sync-worker/wrangler.jsonc の kv_namespaces[0].id へ設定
npx wrangler secret put SYNC_WRITE_TOKEN
# 無料運用ではOPENAI_API_KEYは不要です
# 本格AIを使う場合だけ設定します
# npx wrangler secret put AI_MODE # openai
# npx wrangler secret put OPENAI_API_KEY
# 必要ならモデル変更。未設定時は sync-worker 側のデフォルトモデルを使います
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

- 小学生向けにしたMaterial 3 + Jetpack ComposeのシンプルUI
- OpenStreetMap表示
- THINKLETから届いた写真つき観察の取り込み
- GPS、撮影時刻、写真、AIのよそうを観察記録に保存
- SQLiteローカル保存
- 発見済み図鑑一覧、レア度、場所、日時表示
- 同期APIからAI分析済みTHINKLET観察をSQLiteへ取り込み

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

このAndroid Studio版はスマホで撮影しません。THINKLETから送られた写真を同期API側でAI分析し、アプリの「うけとる」画面からSQLiteに保存します。

## データ

候補生物データはGBIF occurrence APIで神山町周辺の軽い範囲を確認し、MVP用に軽量化して `src/data/kamiyama.ts` と Android 側の `KamiyamaData.kt` に同梱しています。地図では、根拠の弱い植生ポリゴンは表示せず、GBIF記録に紐づく候補点と、THINKLETから届いた実際の発見ピンだけを表示します。植生図そのものを表示する場合は、環境省などの公開GISデータを別フェーズで取り込みます。
