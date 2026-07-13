# Palleria — Pixivイラスト専用ビューアー

[![License: GPL-3.0](https://img.shields.io/badge/License-GPL%20v3-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Platform-Android%2013%2B-green.svg)](https://developer.android.com/)

> シンプルで使いやすい、Pixivの非公式クライアントアプリです。

**Palleria**（パレリア）は、Pixivのイラスト・漫画・作品を快適に閲覧するためのオープンソースAndroidアプリです。おすすめやランキング、新着フィードの閲覧から、ブックマーク管理・画像保存・高度な検索まで、Pixivを楽しむために必要な機能をまとめています。

本アプリはPixiv公式とは無関係の非公式クライアントです。

---

## ✨ 主な機能

### 閲覧
- **おすすめ / ランキング / 新着フィード** の切り替え表示
- **フォロー中ユーザーのタイムライン** を確認
- イラスト・漫画の詳細表示と全画面ビューアー
- 関連イラストの表示

### 検索・発見
- タグ検索、完全一致検索、タイトル・キャプション検索
- 並び順（新着順 / 古い順 / 人気順）、期間指定、ブックマーク数フィルタ
- ユーザー検索
- **お気に入りタグ（Watchlist）** で気になるタグを登録

### ブックマーク・フォロー
- 作品のブックマーク追加 / 解除（公開・非公開選択可）
- ユーザーのフォロー / フォロー解除
- ブックマーク時の自動ダウンロード、保存時の自動ブックマーク

### 保存・管理
- 画像の単体保存、全ページ保存
- 同時保存数の設定
- 閲覧履歴、検索履歴の保存
- **ミュート機能**（作品・ユーザー・タグ）
- データのエクスポート / インポート（JSON形式）

### カスタマイズ・セキュリティ
- 多言語対応（日本語 / 英語 / システム設定に追随）
- ダークテーマ / AMOLEDモード
- 画像画質の詳細設定（一覧・詳細・全画面ごと）
- Pixiv画像プロキシの設定
- **アプリロック**（PINコード / 生体認証）
- **セキュアウィンドウ**（スクリーンショット・画面録画の制限）

---

## 📥 インストール方法

### F-Droid リポジトリからインストール

1. 以下のページを開きます。  
   👉 [https://yunfi.f5.si/Palleria/repo/](https://yunfi.f5.si/Palleria/repo/)

2. ページ内の手順に従い、F-Droidクライアントで「Palleria」を検索・インストールしてください。

### 動作環境
- Android 13 以降（API 33+）
- Pixivアカウントが必要です（初回起動時にログイン）

---

## 🔑 ログイン方法

初回起動時、もしくは設定から以下のいずれかの方法でPixivにログインできます。

1. **Webログイン（推奨）**  
   Pixivの公式ログインページをアプリ内ブラウザで開き、安全に認証します。

2. **Refresh Tokenログイン**  
   取得済みのRefresh Tokenを直接入力してログインします。

---

## 🛠️ 技術情報

開発者向けの参考情報です。

| 項目 | 詳細 |
|------|------|
| プラットフォーム | Android |
| 言語 | Kotlin |
| UI | Jetpack Compose |
| デザインライブラリ | [Miuix KMP](https://github.com/yukonga/Miuix) |
| 画像読み込み | Coil 3 |
| 通信 | OkHttp |
| ローカルDB | Room |
| 設定保存 | DataStore |
| ライセンス | GPL-3.0-only |

### ビルド方法

```bash
./gradlew :app:assembleRelease
```

署名には `KEYSTORE_PATH` などの環境変数を設定するか、`debug.keystore` を配置してください。

---

## 🤝 貢献・フィードバック

バグ報告や機能提案は、GitHubのIssueトラッカーからお願いします。

- GitHubリポジトリ: [https://github.com/yunfie-twitter/Palleria](https://github.com/yunfie-twitter/Palleria)
- 問題報告: [https://github.com/yunfie-twitter/Palleria/issues](https://github.com/yunfie-twitter/Palleria/issues)

コードのプルリクエストも歓迎します。

---

## ⚠️ 免責事項

本アプリは **Pixivの非公式クライアント** です。Pixiv公式とは無関係であり、本アプリの利用により生じたいかなる損害についても開発者は責任を負いません。Pixivの利用規約を遵守した上でご利用ください。

---

## 📄 ライセンス

本プロジェクトは [GNU General Public License v3.0](LICENSE) の下で公開されています。

---

© 2024-2025 ゆんふぃ / Illustia Project
