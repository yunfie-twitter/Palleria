---
title: アプリの入手とインストール
description: F-Droid や GitHub からのわかりやすいインストール手順
---

# アプリの入手とインストール

Palleria は Google Play ストア以外から入手・インストールできます。

---

## 対応環境

Android 7.0（API 24）以降に対応します。x86_64版はIntel / AMD系の対応端末向けです。

## インストール方法

おすすめのインストール方法は **F-Droid リポジトリ** です。更新の検出・通知・インストールの方法はF-Droidクライアントの設定に従います。

### 方法 1: F-Droid からインストール (推奨)

1. スマホに F-Droid アプリ（または Droid-ify 等）をインストールします。
2. F-Droid の「設定」>「リポジトリ」を開き、以下の URL を追加します：
   ```text
   https://yunfi.f5.si/Palleria/repo/
   ```
3. リポジトリを更新後、検索画面で `Palleria` と入力して「インストール」をタップします。

---

### 方法 2: 直接ダウンロードしてインストール

1. [GitHub Releases ページ](https://github.com/yunfie-twitter/Palleria/releases/latest) を開きます。
2. 最新のファイル一覧（Assets）から `Palleria-v<バージョン>-release-universal.apk`（端末のABIが分かる場合は `arm64-v8a` / `armeabi-v7a` / `x86_64` 版）をタップしてダウンロードします。
3. ダウンロードしたファイルを開き、「不明なアプリのインストールを許可」画面が出たら「許可」をオンにしてインストールを完了させます。

---

## アプリの更新方法

- **F-Droid から入れた場合**: F-Droid アプリ内で自動または手動で更新できます。
- **直接ダウンロードした場合**: アプリ内の「設定」の更新画面から新しいバージョンが配信されているか確認できます。
