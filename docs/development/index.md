---
title: 開発ガイド
description: Palleriaの開発環境とプロジェクト構成の概要。
---

# 開発ガイド

PalleriaはKotlinとJetpack Composeを中心に構成されたAndroidアプリです。Pixiv API処理にはRustとUniFFIを利用し、PallaSyncのRust暗号コアはJNIで呼び出します。

## 技術スタック

| 領域 | 技術 |
| --- | --- |
| 言語 | Kotlin / Rust |
| UI | Jetpack Compose / Miuix KMP |
| 通信 | OkHttp / Rust HTTPクライアント |
| 画像 | Coil 3 |
| データベース | Room |
| 設定 | DataStore |
| ナビゲーション | Navigation 3 |
| 非同期処理 | Kotlin Coroutines |
| ライセンス | GPL-3.0-only |

## 読む順番

1. [ビルド](./build)で必要なツールを準備する
2. [アーキテクチャ](./architecture)で主要ディレクトリを把握する
3. [コントリビューション](./contributing)の方針を確認する

## リポジトリ

ソースコード、Issue、Pull Requestは[GitHub](https://github.com/yunfie-twitter/Palleria)で管理しています。
