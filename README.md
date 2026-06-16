# 神山生物図鑑 MVP

神山町を歩きながら植物と虫を撮影し、自分だけの観察ピンと図鑑を育てるスマホ向けWebアプリです。GitHub Pagesで配信する前提で、Expo / React Native Web寄りに実装しています。

## MVPでできること

- OpenStreetMap上に神山町周辺の自然レイヤーを表示
- GBIF由来の植物・虫の周辺記録を候補ピンとして表示
- カメラで写真撮影
- GPSと撮影時刻を観察記録に保存
- 写真・日時・場所・候補生物・レア度をIndexedDBにローカル保存
- 発見済みの観察を図鑑として一覧表示

## 方針

本格AI同定は次フェーズに回し、MVPでは「植物 / 虫」の大分類と位置・季節・周辺記録から候補を出します。APIキーをGitHub Pagesに置かないため、サーバー不要で安全に動かせる範囲を優先しています。

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

## データ

候補生物データはGBIF occurrence APIで神山町周辺の軽い範囲を確認し、MVP用に軽量化して `src/data/kamiyama.ts` に同梱しています。自然レイヤーは、観察体験を作るための神山町周辺の概略ポリゴンです。
