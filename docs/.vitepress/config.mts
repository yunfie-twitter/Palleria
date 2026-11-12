import { defineConfig } from 'vitepress'

const repository = 'https://github.com/yunfie-twitter/Palleria'
const siteUrl = 'https://yunfi.f5.si/Palleria/'
// One generated image per page lives in docs/public/og/<slug>.png.
const ogBase = `${siteUrl}og/`
const defaultOgImage = `${ogBase}index.png`
const icon = `${siteUrl}logo.png`
const description =
  'Android向けオープンソースPixivクライアント「Palleria」の公式ドキュメント。イラスト・マンガ・小説・うごイラの閲覧、検索、ブックマーク、画像保存、プライバシー保護機能を解説します。'
const keywords =
  'Palleria, Pixiv, Android, Pixivクライアント, オープンソース, Jetpack Compose, Rust, うごイラ, マンガ, 小説, アプリロック, 電卓偽装, F-Droid, Kotlin'

const structuredData = {
  '@context': 'https://schema.org',
  '@type': 'SoftwareApplication',
  name: 'Palleria',
  alternateName: 'Palleria - Android向けPixivクライアント',
  applicationCategory: 'MultimediaApplication',
  applicationSubCategory: 'Pixiv client',
  operatingSystem: 'Android 7.0 (API 24) 以降',
  url: siteUrl,
  image: defaultOgImage,
  screenshot: defaultOgImage,
  description,
  inLanguage: 'ja',
  softwareVersion: '7.1.0',
  license: 'https://www.gnu.org/licenses/gpl-3.0.html',
  isAccessibleForFree: true,
  offers: { '@type': 'Offer', price: '0', priceCurrency: 'JPY' },
  downloadUrl: 'https://github.com/yunfie-twitter/Palleria/releases',
  codeRepository: repository,
  sameAs: [repository],
  author: { '@type': 'Person', name: 'ゆんふぃ (yunfie)', url: repository },
}

const websiteData = {
  '@context': 'https://schema.org',
  '@type': 'WebSite',
  name: 'Palleria 公式ドキュメント',
  alternateName: 'Palleria Docs',
  url: siteUrl,
  inLanguage: 'ja-JP',
  description,
}

function pageUrl(relativePath: string): string {
  const clean = relativePath.replace(/(^|\/)index\.md$/, '$1').replace(/\.md$/, '')
  return siteUrl + clean
}

// Must match docs/.vitepress/generate-og.mjs.
function ogSlug(relativePath: string): string {
  return (
    relativePath
      .replace(/\\/g, '/')
      .replace(/\.md$/, '')
      .replace(/(^|\/)index$/, '')
      .replace(/\//g, '-') || 'index'
  )
}

export default defineConfig({
  base: '/Palleria/',
  lang: 'ja-JP',
  title: 'Palleria',
  titleTemplate: ':title | Palleria',
  description,
  cleanUrls: true,
  lastUpdated: true,

  // Internal validation/audit notes are kept in the repository but are not part of
  // the public site, the sitemap, or search results.
  srcExclude: [
    'dev/audit-2026-09-26.md',
    'search-input-validation.md',
    'webp-proxy-validation.md',
    'pixiv-report-implementation.md',
  ],

  markdown: {
    image: { lazyLoading: true },
  },

  sitemap: {
    hostname: siteUrl,
  },

  transformHead({ pageData }) {
    const url = pageUrl(pageData.relativePath)
    const isHome = pageData.relativePath === 'index.md'
    const title = isHome
      ? 'Palleria - Android向けPixivクライアント'
      : `${pageData.title ?? 'Palleria'} | Palleria`
    const desc = pageData.description || description
    const image = `${ogBase}${ogSlug(pageData.relativePath)}.png`
    return [
      ['link', { rel: 'canonical', href: url }],
      ['meta', { property: 'og:url', content: url }],
      ['meta', { property: 'og:title', content: title }],
      ['meta', { property: 'og:description', content: desc }],
      ['meta', { property: 'og:image', content: image }],
      ['meta', { property: 'og:image:secure_url', content: image }],
      ['meta', { property: 'og:image:type', content: 'image/png' }],
      ['meta', { property: 'og:image:width', content: '1200' }],
      ['meta', { property: 'og:image:height', content: '630' }],
      ['meta', { property: 'og:image:alt', content: title }],
      ['meta', { name: 'twitter:title', content: title }],
      ['meta', { name: 'twitter:description', content: desc }],
      ['meta', { name: 'twitter:image', content: image }],
      ['meta', { name: 'twitter:image:alt', content: title }],
    ]
  },

  head: [
    ['link', { rel: 'icon', href: '/Palleria/logo.svg', type: 'image/svg+xml' }],
    ['link', { rel: 'apple-touch-icon', href: '/Palleria/logo.png' }],
    ['link', { rel: 'preload', href: '/Palleria/logo.svg', as: 'image', type: 'image/svg+xml' }],
    ['meta', { name: 'keywords', content: keywords }],
    ['meta', { name: 'author', content: 'ゆんふぃ (yunfie)' }],
    ['meta', { name: 'robots', content: 'index, follow, max-image-preview:large, max-snippet:-1, max-video-preview:-1' }],
    ['meta', { name: 'theme-color', content: '#090b0f' }],
    ['meta', { name: 'application-name', content: 'Palleria' }],
    ['meta', { name: 'format-detection', content: 'telephone=no' }],

    ['meta', { property: 'og:type', content: 'website' }],
    ['meta', { property: 'og:site_name', content: 'Palleria 公式ドキュメント' }],
    ['meta', { property: 'og:locale', content: 'ja_JP' }],

    ['meta', { name: 'twitter:card', content: 'summary_large_image' }],

    ['script', { type: 'application/ld+json' }, JSON.stringify(structuredData)],
    ['script', { type: 'application/ld+json' }, JSON.stringify(websiteData)],
  ],

  themeConfig: {
    logo: {
      src: '/logo.svg',
      alt: 'Palleria 公式ドキュメント',
    },
    siteTitle: 'Palleria Docs',

    outline: {
      level: [2, 3],
      label: '目次',
    },

    nav: [
      { text: 'ユーザーガイド', link: '/user/' },
      { text: '開発者ドキュメント', link: '/dev/' },
      { text: '開発者', link: '/developers' },
      { text: '支援・感謝', link: '/donate' },
      { text: 'GitHub', link: repository },
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
          { text: '検索とタグ', link: '/user/search' },
          { text: 'ブックマークとフォロー', link: '/user/bookmarks' },
          { text: '画像の保存と一括ダウンロード', link: '/user/downloads' },
          { text: '外観テーマと各種設定', link: '/user/personalization' },
          { text: 'アプリロックと電卓偽装', link: '/user/security' },
          { text: 'ライブ壁紙とウィジェット', link: '/user/wallpaper-widget' },
        ],
      },
      {
        text: '開発者ガイド (実装仕様)',
        collapsed: false,
        items: [
          { text: '開発者向け', link: '/developers' },
          { text: 'アーキテクチャ概観 & レイヤー設計', link: '/dev/' },
          { text: 'アプリケーション仕様（実装ベース）', link: '/dev/application-specification' },
          { text: 'ビルド & 環境構築パイプライン', link: '/dev/build' },
          { text: 'ChromeOS・キーボード操作', link: '/dev/chromeos' },
          { text: 'Rust Native コア (pixiv-api)', link: '/dev/rust-core' },
          { text: 'セキュリティ & 電卓パーサー仕様', link: '/dev/security-engine' },
          { text: 'データベース & DataStore スキーマ', link: '/dev/database-schema' },
          { text: 'テレメトリ & クラッシュハンドラー仕様', link: '/dev/telemetry-spec' },
        ],
      },
      {
        text: 'ポリシー & 支援',
        collapsed: false,
        items: [
          { text: 'プライバシーポリシー', link: '/privacy-policy' },
          { text: '支援・開発感謝', link: '/donate' },
        ],
      },
    ],

    socialLinks: [{ icon: 'github', link: repository }],
    editLink: {
      pattern: `${repository}/edit/main/docs/:path`,
      text: 'GitHubでこのページを編集',
    },
    lastUpdated: {
      text: '最終更新',
    },
    docFooter: {
      prev: '前のページ',
      next: '次のページ',
    },
    search: {
      provider: 'local',
      options: {
        translations: {
          button: {
            buttonText: 'ドキュメントを検索',
            buttonAriaLabel: 'ドキュメントを検索',
          },
          modal: {
            noResultsText: '該当するページが見つかりませんでした',
            resetButtonTitle: '検索をリセット',
            footer: {
              selectText: '選択',
              navigateText: '移動',
              closeText: '閉じる',
            },
          },
        },
      },
    },
    darkModeSwitchLabel: 'テーマ',
    lightModeSwitchTitle: 'ライトテーマに切り替え',
    darkModeSwitchTitle: 'ダークテーマに切り替え',
    sidebarMenuLabel: 'メニュー',
    returnToTopLabel: 'ページ上部へ戻る',
    externalLinkIcon: true,
    footer: {
      message: 'PalleriaはPixiv Inc.とは関係のない非公式クライアントです。',
      copyright: 'Copyright © 2026 ゆんふぃ (yunfie) / GPL-3.0-only',
    },
  },
})
