---
title: テレメトリ & クラッシュハンドラー仕様
description: GlitchTip の動的切り替えと CrashHandler 実装
---

# テレメトリ & クラッシュハンドラー仕様

---

## SDK 動的制御 (`IllustiaApplication`)

`AppSettings.sendTelemetry`（デフォルト `false`）の値に基づき、GlitchTip 互換の Sentry Android SDK を動的に開始・停止します。

SDK の ContentProvider による自動初期化は `AndroidManifest.xml` の `io.sentry.auto-init=false` で無効化されています。ユーザーが明示的にオプトインした場合に限り `GlitchTipTelemetry` が SDK を初期化し、オプトアウト時は直ちに停止します。

```kotlin
GlitchTipTelemetry.setEnabled(applicationContext, settings.sendTelemetry)
```

GlitchTip の DSN は `AndroidManifest.xml` に設定し、パフォーマンストレースはオプトイン時に 100%（`1.0`）サンプリングされます。GlitchTip が非対応の自動セッション追跡、既定 PII、Frame Metrics エンベロープは無効化されています。

---

## ローカルクラッシュハンドラー & 未捕獲例外送信 (`CrashHandler`)

`Thread.UncaughtExceptionHandler` を継承し、未捕獲例外発生時に以下の処理を行います：
1. スタックトレースを解析し、リソース未検出 (`Resources.NotFoundException`) やドキュメントアクセス権限エラーを検知して復旧ダイアログ/トーストを表示。
2. テレメトリ有効時、`GlitchTipTelemetry.recordException(ex, tag = "uncaught_crash")` によりクラッシュレポートを送信し、`GlitchTipTelemetry.flush()` でプロセス終了前に確実にフラッシュ。

---

## 非致命的例外（Non-fatal Exceptions）の集約

各種 ViewModel、フィード読み込み、検索、詳細表示、ブックマーク、ダウンロード、PallaSync 同期処理などで発生した予期せぬ例外を `GlitchTipTelemetry.recordException(...)` で収集します。
- `CancellationException`（コルーチンキャンセル）は自動的に除外されます。
- モジュールタグ（`module`）および追加コンテキスト情報（クエリ、URL、作品IDなど）を付与して記録されます。

---

## パフォーマンストレース

GlitchTip は Sentry Envelope の `transaction` アイテムタイプを受け入れるため、Sentry Android SDK のパフォーマンス計測機能をそのまま利用できます。

### サンプリングレート

`AndroidManifest.xml` の `io.sentry.traces.sample-rate = 1.0`（100%）。

### 計測トランザクション

| トランザクション名 | operation | 計測範囲 / 説明 |
|---|---|---|
| `app.startup` | `app.launch` | `IllustiaApplication.onCreate()` 内でテレメトリ有効化完了 〜 `startPostStartupWork()` 末尾 |
| `feed.home.load` / `feed.home.refresh` | `feed.home` | ホームフィードの取得・復元・キャッシュ |
| `feed.home.load_more` | `feed.home` | ホームフィードの追加ページ取得 |
| `feed.ranking.refresh` | `feed.ranking` | ランキング一覧の取得・更新 |
| `feed.ranking.load_more` | `feed.ranking` | ランキング一覧の追加ページ取得 |
| `feed.timeline.refresh` | `feed.timeline` | フォロー新着タイムラインの取得・更新 |
| `feed.timeline.load_more` | `feed.timeline` | フォロー新着タイムラインの追加ページ取得 |
| `feed.watchlist.refresh` | `feed.watchlist` | ウォッチリスト（タグ）の検索・取得 |
| `feed.watchlist.load_more` | `feed.watchlist` | ウォッチリスト（タグ）の追加ページ取得 |
| `feed.novels.refresh` | `feed.novels` | 小説フィードの取得・更新 |
| `search.query` | `search` | イラスト・マンガ・小説・ユーザーの複合検索 |
| `illust.detail.load` | `illust.detail` | 作品詳細、関連作品、コメント、投稿者情報の並行取得 |
| `user.profile.load` | `user.profile` | ユーザー詳細プロファイルおよび投稿作品の並行取得 |
| `bookmarks.refresh` / `bookmarks.load_more` | `bookmarks` | ブックマーク一覧の取得・追加読み込み |
| `download.artwork` | `download` | 通常イラスト・マンガ画像の保存 |
| `ugoira.convert.gif` | `ugoira.convert` | うごイラフレームの GIF エンコード処理 |
| `ugoira.convert.mp4` | `ugoira.convert` | うごイラフレームの MP4 エンコード処理 |
| `pallasync.sync` | `sync.operation` | PallaSync デバイス同期、アウトボックス送信、インボックス受信 |

`GlitchTipTelemetry.trace` / `GlitchTipTelemetry.traceAsync` はテレメトリが無効な場合に計測オーバーヘッドなしでブロックを直接実行するため、呼び出し側で同意状態を別途確認する必要はありません。

### GlitchTip 非対応のため無効化している機能

| オプション | 理由 |
|---|---|
| `isEnableFramesTracking = false` | GlitchTip が Frame Metrics エンベロープを処理しない |
| `isEnablePerformanceV2 = false` | Sentry 固有の拡張 API で GlitchTip 未対応 |
| `isEnableAutoSessionTracking = false` | セッション追跡は GlitchTip 非対応 |
