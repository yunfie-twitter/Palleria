---
layout: home
title: Palleria - Android向けPixivクライアント
titleTemplate: false
description: Android向けオープンソースPixivクライアント「Palleria」の公式ガイド。閲覧・同期・プライバシー機能を紹介。

hero:
  name: "Palleria"
  text: "Pixiv client for Android"
  tagline: "Jetpack Compose と Rust ネイティブエンジンで構築された Android 向け Pixiv クライアント"
  image:
    src: /logo.svg
    alt: Palleria Androidアプリのロゴ
  actions:
    - theme: brand
      text: インストール方法を見る
      link: /user/installation
    - theme: alt
      text: ユーザーガイド
      link: /user/

features:
  - title: 快適な作品鑑賞
    details: イラスト、マンガ、小説の縦書き表示、うごイラの再生に対応。
    link: /user/browse
    linkText: 閲覧機能を見る
  - title: 検索とコレクション
    details: タグ検索、ウォッチリスト、ブックマークで好きな作品を整理。
    link: /user/search
    linkText: 検索機能を見る
  - title: プライバシー保護
    details: PINロック、生体認証、本物として使える電卓偽装モードを搭載。
    link: /user/security
    linkText: セキュリティ機能を見る
---

## Palleriaを使い始める

まずは[対応環境とインストール手順](/user/installation)を確認し、アプリを入手してください。初回起動後は[Pixivアカウントでのログイン方法](/user/authentication)を案内しています。

## 機能から探す

- [イラスト・マンガ・小説・うごイラの閲覧](/user/browse)
- [タグ検索とお気に入りタグ](/user/search)
- [ブックマークとフォロー](/user/bookmarks)
- [画像の保存と一括ダウンロード](/user/downloads)
- [外観テーマと各種設定](/user/personalization)
- [アプリロックと電卓偽装](/user/security)
- [ライブ壁紙とウィジェット](/user/wallpaper-widget)

アプリの仕組みや開発への参加方法は、[開発者ドキュメント](/dev/)と[ビルド手順](/dev/build)を参照してください。

## 対応環境

- Android 7.0（API 24）以降
- ABI: `arm64-v8a` / `armeabi-v7a` / `x86_64`（互換性向けの universal APK も配布）
- Pixiv アカウント（オンライン機能を利用する場合）
- キーボード・マウス、ChromeOS でも操作できます

## 主な機能

| 分類 | 内容 |
| --- | --- |
| 閲覧 | イラスト、マンガ、小説（縦書き・読み上げ）、うごイラの再生 |
| 検索 | 作品・小説・ユーザー検索、タグ検索、お気に入りタグ |
| コレクション | ブックマーク、公開範囲、フォロー、Watchlist、閲覧履歴 |
| 保存 | 画像の保存、一括ダウンロード、ダウンロードキュー、オフラインライブラリ |
| カスタマイズ | ダーク/AMOLEDテーマ、Dynamic Color、グリッド列数、カード表示 |
| プライバシー | PIN・生体認証ロック、電卓偽装モード、スクリーンショット保護 |
| 連携 | ライブ壁紙、ウィジェット、Discord Rich Presence、更新通知 |

## よくある質問

### Palleriaは無料ですか？

はい。PalleriaはGPL-3.0-onlyで公開されている無料のオープンソースアプリです。

### Pixiv公式アプリとの違いは？

PalleriaはPixiv Inc.とは関係のない非公式クライアントです。閲覧体験のカスタマイズ、保存管理、プライバシー保護に重点を置いています。利用時はPixivの利用規約と法令を守ってください。

### ログインできないときは？

[ログインとアカウント設定](/user/authentication)を確認してください。認証コード方式とリフレッシュトークン方式の両方に対応しています。

### 保存したデータはどこにありますか？

端末内のアプリ領域と、選択した保存先フォルダに保存されます。詳細は[プライバシーポリシー](/privacy-policy)を参照してください。

<style>
:root {
  --vp-home-hero-name-color: transparent;
  --vp-home-hero-name-background: -webkit-linear-gradient(120deg, #3ab8f4 30%, #1976d2);
}

.VPHero .image-src {
  max-width: 280px !important;
  max-height: 280px !important;
  width: 100% !important;
  height: auto !important;
  filter: drop-shadow(0 8px 24px rgba(58, 184, 244, 0.25));
}
</style>
