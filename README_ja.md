# Palleria

[English](README.md) · [日本語](README_ja.md) · [한국어](README_ko.md)

<p align="center">
  <img
    src="https://yunfi.f5.si/Palleria/repo/com.yunfie.illustia/en-US/icon.png"
    alt="Palleria アイコン"
    width="160"
  />
</p>

<h3 align="center">
  Android 向けの、高速でオープンソースな Pixiv クライアント
</h3>

<p align="center">
  Jetpack Compose、Miuix UI、Rust ネイティブコアによる滑らかな操作で、イラスト・マンガ・小説を楽しめます。
</p>

<p align="center">

[![License](https://img.shields.io/badge/License-GPL--3.0--only-blue.svg?style=flat-square)](LICENSE)
[![Android](https://img.shields.io/badge/Android-7.0%2B%20(API%2024%2B)-3DDC84.svg?style=flat-square&logo=android&logoColor=white)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.x-7F52FF.svg?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Rust](https://img.shields.io/badge/Rust-Native%20Core-DEA584.svg?style=flat-square&logo=rust&logoColor=white)](https://www.rust-lang.org/)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-UI-4285F4.svg?style=flat-square&logo=jetpackcompose&logoColor=white)](https://developer.android.com/compose)
[![Miuix KMP](https://img.shields.io/badge/Design-Miuix%20KMP-FF6900.svg?style=flat-square)](https://github.com/miuix-kotlin-multiplatform/miuix)
[![F-Droid](https://img.shields.io/badge/F--Droid-Repository-1976D2.svg?style=flat-square&logo=fdroid&logoColor=white)](https://yunfi.f5.si/Palleria/repo/)
[![GitHub Release](https://img.shields.io/github/v/release/yunfie-twitter/Palleria?style=flat-square&logo=github&label=Release)](https://github.com/yunfie-twitter/Palleria/releases)
[![GitHub Downloads](https://img.shields.io/github/downloads/yunfie-twitter/Palleria/total?style=flat-square&logo=github&label=Downloads)](https://github.com/yunfie-twitter/Palleria/releases)
[![GitHub Stars](https://img.shields.io/github/stars/yunfie-twitter/Palleria?style=flat-square&logo=github)](https://github.com/yunfie-twitter/Palleria/stargazers)
[![GitHub Issues](https://img.shields.io/github/issues/yunfie-twitter/Palleria?style=flat-square&logo=github)](https://github.com/yunfie-twitter/Palleria/issues)
[![Architecture diagram](https://gitdiagram.com/diagram-badge.svg)](https://gitdiagram.com/diagram/yunfie-twitter/palleria?utm_source=readme&utm_medium=badge)
</p>

---

## Palleria について

**Palleria** は、モバイルで快適に Pixiv を閲覧するための、無料・プライバシー重視のオープンソース Android アプリです。

Jetpack Compose と Miuix のデザイン、UniFFI 経由の Rust ネイティブバックエンドを採用し、高速な動作、低い消費電力、豊かな操作感、エンドツーエンド暗号化同期を実現します。

> [!IMPORTANT]
> Palleria は非公式 Pixiv クライアントです。Pixiv Inc. との提携・承認・関係はありません。

## 主な特徴

- **高性能な Rust コア**: UniFFI によるネイティブエンジンで、うごイラのデコード、APNG/WebP/MP4 変換、データ処理を高速化。
- **Miuix と Material You の UI**: 滑らかなアニメーション、AMOLED・作品連動テーマ、カスタマイズ可能な複数列グリッド。
- **先回りする操作最適化**: 起動中の DNS/TLS 接続準備、差分・ETag 同期、スクロール停止位置の予測プリフェッチ、中断ダウンロードの再開、マンガの方向に応じた先読み。
- **PallaSync**: ブックマーク、閲覧履歴、設定、ミュートを Android 端末やブラウザー拡張とエンドツーエンド暗号化で同期。
- **読書機能**: マンガビューア、小説リーダー、書体設定、読書位置の保存、バックグラウンド TTS。
- **ダウンロードと更新**: 並列ダウンロード、EXIF のプライバシー除去、ファイル名テンプレート、Shizuku 対応の APK 更新。
- **プライバシーとセキュリティ**: PIN・生体認証ロック、履歴画面のサムネイルぼかし、追跡 URL の除去。
- **3言語対応**: 日本語、英語、韓国語。

## スクリーンショット

<table align="center">
  <tr>
    <td align="center"><img src="https://yunfi.f5.si/Palleria/repo/com.yunfie.illustia/en-US/phoneScreenshots/1.png" alt="ホーム画面" width="160" /><br /><strong>ホーム・フィード</strong></td>
    <td align="center"><img src="https://yunfi.f5.si/Palleria/repo/com.yunfie.illustia/en-US/phoneScreenshots/2.png" alt="検索画面" width="160" /><br /><strong>検索・発見</strong></td>
    <td align="center"><img src="https://yunfi.f5.si/Palleria/repo/com.yunfie.illustia/en-US/phoneScreenshots/3.png" alt="ランキング画面" width="160" /><br /><strong>ランキング</strong></td>
    <td align="center"><img src="https://yunfi.f5.si/Palleria/repo/com.yunfie.illustia/en-US/phoneScreenshots/5.png" alt="ユーザープロフィール画面" width="160" /><br /><strong>作家プロフィール</strong></td>
  </tr>
</table>

## 機能

### イラスト・メディア閲覧
- おすすめフィード、日・週・月間ランキング、フォロー中の作家の新着作品。
- 全画面を縦にスワイプするショートフィード。
- うごイラの省電力デコードと WebP/APNG/GIF/MP4 への書き出し。
- 横読み・縦読みマンガ、ページ向きの自動判定、音量キーでのページ送り。
- 小説シリーズの閲覧、書体設定、進捗保存、スリープタイマー付きバックグラウンド読み上げ。
- 最大6倍のピンチズーム、スワイプで閉じる操作、クイックプレビュー。

### 検索・発見
- タグ、完全一致タグ、タイトル、キャプション、作家名で検索。
- 作品種別、年齢制限、ブックマーク数、日付で絞り込み。
- タグや作家を登録してすばやくアクセスできるウォッチリスト。

### ダウンロード・メディア管理
- 作品の個別ページまたは複数ページ一括保存、同時実行数の設定。
- HTTP 206 `Range` リクエストによる中断ダウンロードの再開。
- SAF の任意フォルダーまたは標準の Pictures へ保存。
- 保存時の EXIF 個人情報除去、`{id}`・`{title}`・`{artist}`・`{page}` によるファイル名設定。

### PallaSync
- サードパーティーの追跡を伴わないエンドツーエンド暗号化同期。
- ブックマーク、閲覧・検索履歴、設定、ミュートを Android とブラウザー拡張間で同期。
- QR コードまたは安全なペアリングフレーズで接続。

### UI とカスタマイズ
- システム・ライト・ダーク・AMOLED 黒・作品アクセント連動テーマ。
- スマートフォン、折りたたみ端末、タブレット向けの1〜4列グリッド。
- フィードや検索結果でタグ・ユーザー・作品 ID をミュート。
- コメントの Pixiv スタンプ表示・送信。
- 設定とローカルデータベースの JSON バックアップ・復元。

## インストール

### F-Droid リポジトリ（推奨）

公式 Palleria リポジトリを F-Droid または互換クライアント（Droid-ify、Neo Store など）に追加してください。

```text
https://yunfi.f5.si/Palleria/repo/
```

[![F-Droid に追加](https://img.shields.io/badge/F--Droid-リポジトリを追加-1976D2.svg?style=for-the-badge&logo=fdroid&logoColor=white)](https://yunfi.f5.si/Palleria/repo?fingerprint=28A7F64F373AC1AD5FBB4822870E2E07B2B204C7EC71E58CE40F9D54EF2727D9)

### GitHub Releases

[最新の APK をダウンロード](https://github.com/yunfie-twitter/Palleria/releases/latest)できます。

### 動作要件

- **OS**: Android 7.0（API 24）以降。Target SDK は Android 16（API 36）。
- **アカウント**: Pixiv アカウント（Web ログインまたは Refresh Token）。
- **ネットワーク**: インターネット接続（独自画像プロキシ・DoH 対応）。

## 技術スタック

| 分野 | 技術 |
| :--- | :--- |
| 言語 | Kotlin 2.x、Rust 1.80+ |
| UI | Jetpack Compose、Compose Foundation、Material 3 |
| デザイン | Miuix KMP（Xiaomi HyperOS / MIUI のデザイン指針） |
| ネイティブエンジン | UniFFI 経由の Rust (`pixiv-api`)、Tokio |
| 通信 | OkHttp 4 / 5、DoH、接続プールの事前準備 |
| 画像 | Coil 3、Hardware Bitmap、Display P3、`inBitmap` 再利用 |
| 保存 | Room、Jetpack DataStore Preferences、AtomicFile |
| バックグラウンド | WorkManager（ETag 差分同期・更新） |
| OS連携 | Shizuku、Android SAF、Glance AppWidget |

## ソースからビルド

### 必要なもの

1. Android Studio Ladybug / Meerkat 以降
2. JDK 17 または 21
3. Android SDK Manager から NDK 26 以降
4. Stable Rust と `cargo-ndk`

```bash
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
cargo install cargo-ndk
```

### クローンとビルド

```bash
git clone https://github.com/yunfie-twitter/Palleria.git
cd Palleria
```

```bash
# Debug APK（Linux / macOS）
./gradlew :app:assembleDebug

# Release APK（Linux / macOS）
./gradlew :app:assembleRelease
```

Windows PowerShell では `./gradlew` の代わりに `.\gradlew.bat` を使用してください。生成物は `app/build/outputs/apk/debug/` と `app/build/outputs/apk/release/` に出力されます。

### Release 署名

Release ビルドの署名には次の環境変数を設定します。

```powershell
$env:KEYSTORE_PATH = "C:\path\to\keystore.jks"
$env:KEYSTORE_PASSWORD = "your-keystore-password"
$env:KEY_ALIAS = "your-key-alias"
$env:KEY_PASSWORD = "your-key-password"

.\gradlew.bat :app:assembleRelease
```

## コミュニティ・統計

<!-- METRICS-STATS:START -->
<p align="center">
  <img src="https://github-readme-stats.vercel.app/api/pin/?username=yunfie-twitter&repo=Palleria&theme=transparent" alt="Palleria リポジトリ統計" />
</p>
<p align="center">
  <a href="https://github.com/yunfie-twitter/Palleria/stargazers"><img src="https://img.shields.io/badge/Stars-10-blue.svg?style=for-the-badge&logo=github" alt="Stars: 10" /></a>
  <a href="https://github.com/yunfie-twitter/Palleria/releases"><img src="https://img.shields.io/badge/Total%20Downloads-1000-2ea44f.svg?style=for-the-badge&logo=github" alt="Total Downloads: 1000" /></a>
  <a href="https://github.com/yunfie-twitter/Palleria/network/members"><img src="https://img.shields.io/badge/Forks-4-orange.svg?style=for-the-badge&logo=github" alt="Forks: 4" /></a>
</p>
<!-- METRICS-STATS:END -->

## コントリビューター

<p align="center"><a href="https://github.com/yunfie-twitter/Palleria/graphs/contributors"><img src="https://contrib.rocks/image?repo=yunfie-twitter/Palleria" alt="コントリビューター" /></a></p>

## コントリビュート

コード、バグ報告、機能提案、翻訳の改善を歓迎します。

1. リポジトリを Fork し、作業ブランチを作成します（`git checkout -b feat/my-feature`）。
2. コミット前にスタイルチェックとユニットテストを実行します。
   ```bash
   ./gradlew ktlintCheck detekt testDebugUnitTest
   ```
3. 変更をコミットして `main` 向けの Pull Request を作成してください。

[Issue を報告](https://github.com/yunfie-twitter/Palleria/issues) · [Pull Request](https://github.com/yunfie-twitter/Palleria/pulls)

## 免責事項

Palleria は Pixiv Inc. と関係のない非公式オープンソースアプリです。Pixiv の商標・ロゴ・作品素材は各権利者に帰属します。本アプリを利用する際は Pixiv の利用規約を遵守してください。

## ライセンス

Palleria は [GNU General Public License v3.0（GPL-3.0-only）](LICENSE) のもとで公開されています。

<p align="center">ゆんふぃ が心を込めて開発</p>
