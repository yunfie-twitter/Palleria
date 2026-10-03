---
title: ビルドと公開パイプライン
---

# ビルドと公開パイプライン

このページは2026年10月3日に `app/build.gradle`、Gradle Wrapper、Rustクレート、GitHub Actionsを確認した内容です。設定を変更した場合はコード側の値を優先してください。

## 開発環境

| 項目 | リポジトリの設定 |
| --- | --- |
| Gradle | Wrapperで9.7.1を取得 |
| Gradle daemon JVM | JetBrains JDK 21（`gradle/gradle-daemon-jvm.properties`） |
| Javaソース・バイトコード | 17 |
| Android SDK | compileSdk 37、targetSdk 36、minSdk 24 |
| Android NDK | Android StudioのSDK Managerで導入。`cargo-ndk`から参照できること |
| Rust | edition 2024対応のstableツールチェーン。依存関係はCargo.lockに従う |
| ドキュメント | Node.jsとnpm。docsのCIはNode.js 22を使用 |

JDK 17というコンパイル設定と、Gradle daemonのJDK 21指定は別です。NDKの版は `app/build.gradle` で固定していません。WindowsではRustのホストビルドに必要なリンカーも用意してください。

## 準備とビルド

```sh
git clone https://github.com/yunfie-twitter/Palleria.git
cd Palleria
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
cargo install cargo-ndk --locked
./gradlew :app:assembleDebug
```

Windows PowerShellでは最後のコマンドを `.\gradlew.bat :app:assembleDebug` に置き換えます。Android SDKの場所はAndroid Studioまたは `local.properties` で設定します。

`preBuild`から次の処理が実行されます。

| タスク | 役割 |
| --- | --- |
| `buildUniFfiHost` | ホスト用の `palleria_pixiv_api` ライブラリをビルド |
| `generateUniFfiBindings` | `app/build/generated/uniffi/kotlin` にKotlinコードを生成 |
| `buildRustAndroid` | 3 ABIの `libpalleria_pixiv_api.so` を生成 |
| `buildPallaSyncCoreAndroid` | 3 ABIの `libpallasync_core.so` を生成 |

Cargoの出力先は共通の `rust/target`、Androidの共有ライブラリの配置先は `app/build/generated/uniffi/jniLibs` です。

## 署名付きリリース

`KEYSTORE_PATH`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD` を設定して `./gradlew :app:assembleRelease` を実行します。相対キーストアパスはappモジュール基準です。未設定時はコード上debug署名へフォールバックするので、配布用には必ずrelease署名を用意してください。

出力は `app/build/outputs/apk/release/` 以下です。

```text
Palleria-v<versionName>-release-universal.apk
Palleria-v<versionName>-release-arm64-v8a.apk
Palleria-v<versionName>-release-armeabi-v7a.apk
Palleria-v<versionName>-release-x86_64.apk
```

## GitHub ReleaseとF-Droid

- `release-apk.yml`: mainの `app/build.gradle` 更新、`v*` タグpush、手動実行でビルドします。成功時はDraft Releaseを作成し、ビルド失敗時もタグを保持します。
- `fdroid.yml`: Releaseの公開・削除イベントで起動します。公開時は添付APKを取得・検証し、削除時は対応するAPKを取り除いてインデックスを更新します。
- `docs.yml`: ドキュメントをビルドして同じ `gh-pages` へ公開します。F-Droid更新と共通のキューで直列化し、互いの成果物を保持します。

公開・復旧の詳しい手順は[RELEASING.md](https://github.com/yunfie-twitter/Palleria/blob/main/.github/RELEASING.md)を参照してください。

## ドキュメントの確認

```sh
npm ci
npm run docs:dev
npm run docs:build
npm run docs:preview
```

生成先は `docs/.vitepress/dist`、配信baseは `/Palleria/` です。ページ画像は `docs/assets` に置き、Markdownから相対パスで参照するとビルド時にURLが解決されます。

## 起動プロファイルと計測

`app/src/main/baselineProfiles/` には `baseline-prof.txt` と `startup-prof.txt` を配置しています。現在のファイルはクラス単位の暫定ルールです。`benchmark` モジュールの `BaselineProfileGenerator` は `includeInStartupProfile = true` を指定し、起動経路から両方のプロファイルを生成します。

AGP 9.4.1 では DEX レイアウト最適化が既定で有効です。release は `minifyEnabled = true` で R8 を使用します。`app/build.gradle` に `startupProfile` や `dexLayoutOptimization` の明示指定がないことだけでは、無効とは判断できません。[Android の公式説明](https://developer.android.com/topic/performance/startupprofiles/dex-layout-optimizations)も参照してください。

Android SDK・NDK と設定済みの管理対象デバイスを利用できる環境で `./gradlew :app:generateBaselineProfile` を実行し、生成したルールを確認・更新します。`StartupBenchmark` はプロファイル適用有無のコールド起動と、適用時のウォーム起動を各10回測定します。ただし同一APKでの ART コンパイル条件の比較だけでは、ビルド時の DEX 配置最適化の効果を単独では測定できません。DEX 最適化の適用は release ビルドの R8 出力でも確認し、短縮時間は実機の測定結果から判断してください。

ABI 別 APK と universal APK は配布形式の違いです。複数 ABI の同梱だけで、すべての共有ライブラリを起動時にロードするわけではありません。Pixiv の UniFFI クライアントは API 操作などで必要になった時に初期化します。設定を読み込んでクライアントのラッパーを生成するだけでは初期化しません。

Sentry は `io.sentry.auto-init = false` に加え、SDK が追加する `SentryInitProvider`・`SentryPerformanceProvider`・`SentryNdkPreloadProvider` を Manifest マージで除外しています。これにより、手動初期化前のライフサイクル登録や起動プロファイル設定ファイルの確認を避けます。代わりに Sentry による初期化前の自動起動計測は行いません。ユーザー操作・ネットワークイベントのブレッドクラムも無効です。テレメトリに同意している場合、起動後の処理で SDK を初期化します。トレース率は Manifest と `GlitchTipTelemetry` の両方で `1.0`（100%）に設定されています。これは同意済みのトレースを全件サンプリングする指定であり、起動前から自動計測する指定ではありません。
