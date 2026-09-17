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

GlitchTip の DSN は `AndroidManifest.xml` に設定し、パフォーマンストレースは 1% をサンプリングします。GlitchTip が非対応の自動セッション追跡、既定 PII、ネットワークイベントおよびユーザー操作の breadcrumb は無効です。

---

## ローカルクラッシュハンドラー (`CrashHandler`)

`Thread.UncaughtExceptionHandler` を継承し、未捕獲例外発生時にスタックトレースを解析。リソース未検出 (`Resources.NotFoundException`) やドキュメントアクセス権限エラーを検知してユーザーに復旧ダイアログをトースト表示。

---

## パフォーマンストレース

GlitchTip は Sentry Envelope の `transaction` アイテムタイプを受け入れるため、Sentry Android SDK のパフォーマンス計測機能をそのまま利用できます。

### サンプリングレート

`AndroidManifest.xml` の `io.sentry.traces.sample-rate = 0.01`（1%）。

### 計測トランザクション

| トランザクション名 | operation | 計測範囲 |
|---|---|---|
| `app.startup` | `app.launch` | `IllustiaApplication.onCreate()` 内でテレメトリ有効化完了 〜 `startPostStartupWork()` 末尾 |

`GlitchTipTelemetry.startTransaction(name, operation)` はテレメトリが無効な場合に `null` を返すため、呼び出し側で同意状態を別途確認する必要はありません。

### GlitchTip 非対応のため無効化している機能

| オプション | 理由 |
|---|---|
| `isEnableFramesTracking = false` | GlitchTip が Frame Metrics エンベロープを処理しない |
| `isEnablePerformanceV2 = false` | Sentry 固有の拡張 API で GlitchTip 未対応 |
| `isEnableAutoSessionTracking = false` | 既存。セッション追跡は GlitchTip 非対応 |

