import { defineConfig } from 'vitepress'

const repository = 'https://github.com/yunfie-twitter/Palleria'
const siteUrl = 'https://yunfi.f5.si/Palleria/'
const icon = `${siteUrl}repo/com.yunfie.illustia/en-US/icon.png`
const description = 'Android向けオープンソースPixivクライアント「Palleria」の公式ガイド。閲覧・同期・プライバシー機能を紹介。'

export default defineConfig({
  base: '/Palleria/',
  lang: 'ja-JP',
  title: 'Palleria',
  titleTemplate: ':title | Palleria',
  description,
  cleanUrls: true,
  lastUpdated: true,

  sitemap: {
    hostname: siteUrl
  },

  head: [
    ['link', { rel: 'icon', href: '/Palleria/logo.svg', type: 'image/svg+xml' }],
    ['meta', { name: 'keywords', content: 'Palleria, Pixiv, Android, Pixivクライアント, オープンソース, Jetpack Compose, Rust, うごイラ, マンガ, 小説, アプリロック, 電卓偽装, F-Droid' }],
    ['meta', { name: 'author', content: 'ゆんふぃ (yunfie)' }],
    ['meta', { name: 'robots', content: 'index, follow' }],
    ['meta', { name: 'theme-color', content: '#090b0f' }],

    ['meta', { property: 'og:type', content: 'website' }],
    ['meta', { property: 'og:site_name', content: 'Palleria 公式ドキュメント' }],
    ['meta', { property: 'og:title', content: 'Palleria - Android向けPixivクライアント' }],
    ['meta', { property: 'og:description', content: description }],
    ['meta', { property: 'og:image', content: icon }],
    ['meta', { property: 'og:url', content: siteUrl }],

    ['meta', { name: 'twitter:card', content: 'summary' }],
    ['meta', { name: 'twitter:title', content: 'Palleria - Android向けPixivクライアント' }],
    ['meta', { name: 'twitter:description', content: description }],
    ['meta', { name: 'twitter:image', content: icon }]
  ],

  themeConfig: {
    logo: {
      src: '/logo.svg',
      alt: 'Palleria公式ドキュメント'
    },
    siteTitle: 'Palleria Docs',

    outline: {
      level: [2, 3],
      label: '目次'
    },

    nav: [
      { text: 'ユーザーガイド', link: '/user/' },
      { text: '開発者ドキュメント', link: '/dev/' },
      { text: '開発者', link: '/developers' },
      { text: '寄付・支援', link: '/donate' },
      { text: 'GitHub', link: repository }
    ],

    sidebar: [
      {
        text: 'ユーザーガイド (使い方)',
        collapsed: false,
        items: [
          { text: 'Palleria とは', link: '/user/' },
          { text: 'アプリの入手とインストール', link: '/user/installation' },
          { text: 'ログイン・アカウント設定', link: '/user/authentication' },
          { text: '作品を見る (イラスト・マンガ・小説)', link: '/user/browse' },
          { text: '検索とお気に入りタグ', link: '/user/search' },
          { text: 'ブックマークとフォロー', link: '/user/bookmarks' },
          { text: '画像の保存と一括ダウンロード', link: '/user/downloads' },
          { text: '外観テーマと各種設定', link: '/user/personalization' },
          { text: 'アプリロックと電卓偽装', link: '/user/security' },
          { text: '端末間データ同期 (PallaSync)', link: '/user/sync' },
          { text: 'ライブ壁紙とウィジェット', link: '/user/wallpaper-widget' }
        ]
      },
      {
        text: '開発者ガイド (技術仕様)',
        collapsed: false,
        items: [
          { text: '開発者紹介', link: '/developers' },
          { text: 'アーキテクチャ概要 & レイヤー設計', link: '/dev/' },
          { text: 'アプリケーション仕様（実装基準）', link: '/dev/application-specification' },
          { text: 'ビルド & 環境構築パイプライン', link: '/dev/build' },
          { text: 'Rust Native コア (pixiv-api)', link: '/dev/rust-core' },
          { text: 'PallaSync 同期エンジン仕様', link: '/dev/pallasync-engine' },
          { text: 'セキュリティ & 電卓パーサー仕様', link: '/dev/security-engine' },
          { text: 'データベース & DataStore スキーマ', link: '/dev/database-schema' },
          { text: 'テレメトリ & クラッシュハンドラー仕様', link: '/dev/telemetry-spec' }
        ]
      },
      {
        text: 'ポリシー & 支援',
        collapsed: false,
        items: [
          { text: 'プライバシーポリシー', link: '/privacy-policy' },
          { text: '寄付・開発支援', link: '/donate' }
        ]
      }
    ],

    socialLinks: [{ icon: 'github', link: repository }],
    editLink: {
      pattern: `${repository}/edit/main/docs/:path`,
      text: 'GitHubでこのページを編集'
    },
    lastUpdated: {
      text: '最終更新'
    },
    docFooter: {
      prev: '前のページ',
      next: '次のページ'
    },
    search: {
      provider: 'local',
      options: {
        translations: {
          button: {
            buttonText: 'ドキュメントを検索',
            buttonAriaLabel: 'ドキュメントを検索'
          },
          modal: {
            noResultsText: '該当するページが見つかりませんでした',
            resetButtonTitle: '検索をリセット',
            footer: {
              selectText: '選択',
              navigateText: '移動',
              closeText: '閉じる'
            }
          }
        }
      }
    },
    darkModeSwitchLabel: 'テーマ',
    lightModeSwitchTitle: 'ライトテーマに切り替え',
    darkModeSwitchTitle: 'ダークテーマに切り替え',
    sidebarMenuLabel: 'メニュー',
    returnToTopLabel: 'ページ上部へ戻る',
    externalLinkIcon: true,
    footer: {
      message: 'PalleriaはPixiv Inc.とは関係のない非公式クライアントです。',
      copyright: 'Copyright © 2026 ゆんふぃ (yunfie) / GPL-3.0-only'
    }
  }
})
