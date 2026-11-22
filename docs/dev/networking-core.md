---
title: Kotlin 通信コア (Pixiv API) 仕様
description: OkHttp と kotlinx.serialization による Pixiv API クライアント、署名、サイズ上限制限、うごイラ展開
---

# Kotlin 通信コア (Pixiv API) 仕様

`app/src/main/java/com/yunfie/illustia/data` 以下の `Kotlin*` / `Pixiv*` クラスが、Pixiv API のネットワーク通信・リクエスト署名・レスポンス JSON のパースを担います。以前は UniFFI 経由の Rust 実装でしたが、Kotlin に一本化しました。

---

## バッファ上限仕様表

うごイラ ZIP の安全読み込み上限です。Rust 版と同じ値を保っています。

| 対象データ | 上限サイズ | 超過時の動作 |
| :--- | :--- | :--- |
| **うごイラ ZIP アーカイブ** | **512 MiB** | ダウンロード中に `PixivApiException` |
| **うごイラ 単一フレーム** | **32 MiB** | 展開時に `PixivApiException` |
| **うごイラ 合計サイズ** | **512 MiB** | 展開時に `PixivApiException` |
| **うごイラ エントリ数** | **10,000** | 展開時に `PixivApiException` |

---

## モジュール構造 (`data/`)

- `KotlinPixivHttpClient.kt`: OkHttp ベースのクライアント。OAuth ログイン、Web ログイン URL、うごイラ準備、`PixivCall`
- `PixivCallDecoders.kt` / `PixivCallDecoders2.kt`: エンドポイント別デコード（extension 関数）
- `PixivApiDtos.kt`: kotlinx.serialization のワイヤ DTO と OAuth / 小説 JSON パーサ
- `PixivApiDecoder.kt`: 作品・ユーザー・アカウント系の DTO → モデル変換
- `PixivIllustMapper.kt`: IllustDto の URL フォールバックを含むマッピング
- `PixivActivityDecoder.kt`: コメント・通知・スタンプ・トレンド・スポットライト
- `PixivNovelDecoder.kt`: 小説・マンガ・シリーズ
- `PixivRequestSigner.kt`: 署名ヘッダ生成
- `UgoiraCache.kt` / `UgoiraValidation.kt` / `UgoiraFiles.kt`: うごイラ ZIP のダウンロード・検証・展開

---

## リクエスト署名

公式アプリを模したヘッダ（`app-os` / `app-version` / `Accept-Language`）を付与します。`x-client-hash` は「現在時刻 + 共有シークレット」の MD5 で、これが無いと API 側に弾かれます。認証付きリクエストには `Accept: application/json` と `Referer` も付与します。

うごイラ展開では、パストラバーサル（`..`・絶対パス）・シンボリックリンク・重複エントリ・必須フレーム欠落を拒否します。
