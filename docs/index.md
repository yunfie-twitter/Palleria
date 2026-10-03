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
  - title: 暗号化された端末間同期
    details: PallaSyncでお気に入りタグ・履歴・ミュートをペアリングした端末間で同期。
    link: /user/sync
    linkText: 同期機能を見る
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
- [PallaSyncによる端末間同期](/user/sync)
- [ライブ壁紙とウィジェット](/user/wallpaper-widget)

アプリの仕組みや開発への参加方法は、[開発者ドキュメント](/dev/)と[ビルド手順](/dev/build)を参照してください。

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
