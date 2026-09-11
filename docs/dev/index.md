---
title: アーキテクチャ概要 & レイヤー設計
description: Palleria の開発者向け技術ドキュメント、モジュール構造、データフロー
---

# アーキテクチャ概要 & レイヤー設計

本ページは Palleria のソースコード構造と責務分割の概要です。利用者に見える振る舞い、状態、外部連携の正確な仕様は[アプリケーション仕様](/dev/application-specification)を基準にしてください。

---

## 全体レイヤー構成図

```
 ┌────────────────────────────────────────────────────────┐
 │                   UI Layer (Kotlin)                    │
 │  - Jetpack Compose + Miuix KMP Components              │
 │  - Navigation / ViewModels (StateFlow / Coroutines)    │
 └───────────────────────────┬────────────────────────────┘
                             │
 ┌───────────────────────────▼────────────────────────────┐
 │                Data & Local Storage Layer              │
 │  - Room DB (Account, View/Search History, SavedIllust) │
 │  - DataStore (AppSettings / SettingsStore)             │
 │  - PallaSync Engine (暗号化バックアップ & 同期)        │
 └───────────────────────────┬────────────────────────────┘
                             │ (JNI via UniFFI)
 ┌───────────────────────────▼────────────────────────────┐
 │                 Rust Core (pixiv-api)                  │
 │  - Transport Bridge (OkHttp / Rust Client)             │
 │  - Serde JSON Decoder (バッファサイズ上限制御)         │
 │  - DTO & Domain Model Mapping                          │
 └────────────────────────────────────────────────────────┘
```

---

## 主要パッケージ構成 (`com.yunfie.illustia`)

- `account`: ログイン処理、OAuth2 トークン取得・管理
- `data`: リポジトリ層 (`IllustiaRepository`)、API クライアント、画像キャッシュ・URL 検証
- `models`: UI およびデータレイヤーで共有するデータモデル群 (`Illust`, `UserProfile`, `SearchTarget` 等)
- `nativebridge`: UniFFI により生成された Rust との JNI バインディング層
- `pallasync`: 設定およびステートの暗号化同期・復元エンジンと同期用 Room DB
- `settings`: `AppSettings` 定義と `SettingsStore` による設定の永続化
- `ui`: Compose 画面 (`ui/screens/`) および共通 UI コンポーネント (`ui/components/`)
- `updater`: 更新情報の取得と APK ダウンロード
- `wallpaper`: ライブ壁紙サービス (`PalleriaLiveWallpaperService`)
- `widget`: アプリウィジェットプロバイダ (`IllustWidgetProvider`, `RankingWidgetProvider`)
