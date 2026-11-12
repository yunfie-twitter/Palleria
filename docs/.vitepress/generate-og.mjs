// Generates one Open Graph image per documentation page at build time.
// Output: docs/public/og/<slug>.png, referenced from transformHead in config.mts.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import satori from 'satori'
import { html } from 'satori-html'
import { Resvg } from '@resvg/resvg-js'

const here = path.dirname(fileURLToPath(import.meta.url))
const docsDir = path.resolve(here, '..')
const rootDir = path.resolve(docsDir, '..')
const outDir = path.join(docsDir, 'public', 'og')

const SKIP_DIRS = new Set(['.vitepress', 'public', 'assets', 'node_modules'])
const EXCLUDE = new Set([
  'dev/audit-2026-09-26.md',
  'search-input-validation.md',
  'webp-proxy-validation.md',
  'pixiv-report-implementation.md',
])
const FALLBACK_DESCRIPTION =
  'Android向けオープンソースPixivクライアント「Palleria」の公式ドキュメント。'

// Mi Sans (the app font) is missing a few CJK punctuation glyphs, and a raw '&'
// would be re-parsed as an entity by satori-html. Normalise them before rendering.
const encode = (s) =>
  s
    .replace(/・/g, '、')
    .replace(/[〜～]/g, '~')
    .replace(/…/g, '...')
    .replace(/[—–]/g, '-')
    .replace(/&/g, '＆')
    .replace(/</g, '＜')
    .replace(/>/g, '＞')

function slug(relativePath) {
  return (
    relativePath
      .replace(/\\/g, '/')
      .replace(/\.md$/, '')
      .replace(/(^|\/)index$/, '')
      .replace(/\//g, '-') || 'index'
  )
}

function walk(dir, base = dir) {
  const files = []
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (entry.isDirectory()) {
      if (SKIP_DIRS.has(entry.name)) continue
      files.push(...walk(path.join(dir, entry.name), base))
    } else if (entry.name.endsWith('.md')) {
      files.push(path.relative(base, path.join(dir, entry.name)).replace(/\\/g, '/'))
    }
  }
  return files
}

function frontmatter(text) {
  if (!text.startsWith('---')) return {}
  const end = text.indexOf('\n---', 3)
  if (end < 0) return {}
  const data = {}
  for (const raw of text.slice(3, end).split(/\r?\n/)) {
    const line = raw.trim()
    const match = line.match(/^(\w+):\s*(.*)$/)
    if (match) data[match[1]] = match[2].replace(/^['"]|['"]$/g, '').trim()
  }
  return data
}

function firstHeading(text) {
  const match = text.match(/^#\s+(.+)$/m)
  return match ? match[1].trim() : ''
}

function card(title, description, background) {
  const titleSize = title.length <= 18 ? 64 : title.length <= 30 ? 52 : 44
  const desc = description.length > 110 ? `${description.slice(0, 108)}…` : description
  return html`<div style="display:flex;width:1200px;height:630px;background:#090b0f;font-family:MiSans;">
    <div style="display:flex;flex-direction:column;justify-content:space-between;width:760px;height:630px;padding:72px;background:linear-gradient(135deg,#0a1424 0%,#102340 55%,#0a1220 100%);">
      <div style="display:flex;align-items:center;">
        <div style="display:flex;width:52px;height:52px;border-radius:14px;background:#3ab8f4;align-items:center;justify-content:center;margin-right:18px;font-size:30px;font-weight:700;color:#04121f;">P</div>
        <div style="display:flex;font-size:30px;font-weight:700;color:#dce9f8;">Palleria Docs</div>
      </div>
      <div style="display:flex;flex-direction:column;">
        <div style="display:flex;font-size:${titleSize}px;font-weight:700;color:#ffffff;line-height:1.18;">${encode(title)}</div>
        <div style="display:flex;margin-top:22px;font-size:25px;font-weight:400;color:#9fb6d1;line-height:1.45;">${encode(desc)}</div>
      </div>
      <div style="display:flex;justify-content:space-between;align-items:center;font-size:21px;color:#6d88a6;">
        <div style="display:flex;">オープンソース Pixiv クライアント</div>
        <div style="display:flex;">palleria.yunfie.date</div>
      </div>
    </div>
    <img src="data:image/png;base64,${background}" style="width:440px;height:630px;object-fit:cover;" />
  </div>`
}

const background = fs.readFileSync(path.join(docsDir, 'public', 'ogp.png')).toString('base64')
const fonts = [
  { name: 'MiSans', data: fs.readFileSync(path.join(rootDir, 'app/src/main/res/font/mi_sans_regular.ttf')), weight: 400, style: 'normal' },
  { name: 'MiSans', data: fs.readFileSync(path.join(rootDir, 'app/src/main/res/font/mi_sans_bold.ttf')), weight: 700, style: 'normal' },
]

fs.mkdirSync(outDir, { recursive: true })
let count = 0
for (const relativePath of walk(docsDir)) {
  if (EXCLUDE.has(relativePath)) continue
  const text = fs.readFileSync(path.join(docsDir, relativePath), 'utf8')
  const meta = frontmatter(text)
  const title = meta.title || firstHeading(text) || 'Palleria'
  const description = meta.description || FALLBACK_DESCRIPTION
  const markup = card(title, description, background)
  const svg = await satori(markup, { width: 1200, height: 630, fonts })
  const png = new Resvg(svg, { fitTo: { mode: 'width', value: 1200 } }).render().asPng()
  fs.writeFileSync(path.join(outDir, `${slug(relativePath)}.png`), png)
  count += 1
}
console.log(`[og] generated ${count} image(s) in docs/public/og`)
