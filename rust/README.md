# Native Rust Architecture in Palleria

Palleriaでは、CPU/メモリ負荷の高いメディア処理とネットワークレスポンスのデコードのコアエンジンとしてネイティブRustを採用しています。KotlinとRustの境界は、型安全な**UniFFI**を介して結合されています。

---

## 1. クレート構成

`rust/` ディレクトリは以下のクレートで構成されています。

```
rust/
├── pixiv-api/                # Pixiv API クライアント & メディア処理 (UniFFI)
│   ├── benches/              # Criterion ベンチマーク (illust_decode, image_analysis)
│   ├── src/
│   │   ├── client/           # HTTPトランスポート (reqwest + rustls), OAuth, エンドポイント
│   │   ├── models/           # Wire DTOs & UniFFI 公開アプリモデル
│   │   ├── ugoira.rs         # うごイラ ZIP 高速展開 & メタデータ処理
│   │   ├── image_analysis.rs # Rayon 並列画像解析 (ドミナントカラー、輝度)
│   │   ├── headers.rs        # モバイルエミュレーション & クエリヘッダー
│   │   ├── error.rs          # 厳密なエラー型 & 境界ガード
│   │   └── lib.rs            # UniFFI バインディング定義
│   ├── Cargo.toml
│   └── uniffi.toml
│
└── README.md                 # 本ドキュメント
```

---

## 2. 各クレートの役割と特徴

### `pixiv-api` (`palleria_pixiv_api`)
- **HTTPトランスポート & ストリーミングデコード**:
  - `reqwest` (rustls-tls, gzip, brotli, deflate) + `tokio` 非同期ランタイム。
  - レスポンスJSONをKotlinのヒープへ巨大な文字列として渡さず、Rust側で`serde` DTOへ直接ストリーミングデコード。
  - 展開上限ガード: 成功JSONは最大16 MiB、小説HTMLは最大8 MiB、HTTPエラー本文は最大64 KiBに制限し、OOMやDoS攻撃を防止。
- **うごイラ (Ugoira) のネイティブ高速処理**:
  - ZIPアーカイブのストリーミング展開、フレーム順序・遅延時間 (`delay_msec`) の厳密なパース。
  - `BufReader`/`BufWriter` によるバッファリングと `HashSet<PathBuf>` による親ディレクトリの作成重複システムコール削減。
- **Rayon 並列画像解析**:
  - `image_analysis.rs` により、マルチスレッド (`rayon`) で画像バッファからドミナントカラーや輝度を高速抽出。
- **UniFFI による自動バインディング**:
  - `uniffi` (v0.29) により、安全なKotlinコード (`com.yunfie.illustia.rust.palleria_pixiv_api`) を自動生成。

---

## 3. 開発環境のセットアップ

### 前提要件
- Rust 1.80+ (Stable, 2024 edition)
- Android NDK (Android SDK Manager からインストール済みであること)
- `cargo-ndk`

### ツールチェーンのインストール

```powershell
# Android 向けクロスコンパイルターゲットを追加
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android

# cargo-ndk をインストール
cargo install cargo-ndk
```

---

## 4. Gradle ビルドとの連携

通常の Android アプリビルド (`./gradlew assembleDebug`, `./gradlew testDebugUnitTest` 等) を実行すると、Gradle タスクが自動的に以下のフローで Rust コードをビルドします：

1. **`buildUniFfiHost`**:
   ホスト環境向け（Windows では `.dll`, Linux では `.so`, macOS では `.dylib`）に `pixiv-api` をビルド。
2. **`generateUniFfiBindings`**:
   ホスト用ライブラリから `uniffi-bindgen` を実行し、`app/build/generated/uniffi/kotlin/` 配下に Kotlin バインディングを自動生成。
3. **`buildRustAndroid`**:
   `cargo-ndk` を使用して `arm64-v8a`, `armeabi-v7a`, `x86_64` 向けの `libpalleria_pixiv_api.so` をクロスコンパイルし、`app/build/generated/uniffi/jniLibs/` に配置。

ホスト用ライブラリと `uniffi-bindgen` はビルド時間を優先して dev プロファイルで実行します。Android の release ライブラリは従来どおり `--release`（LTO / `codegen-units = 1`）です。ローカルで ABI を絞る場合は次のプロパティを使えます（release CI は既定の全 ABI のままです）。

```powershell
./gradlew :app:assembleDebug -Ppalleria.abis=arm64-v8a -Ppalleria.rustProfile=debug
```

`.cargo/config.toml` の `build.target-dir` と Gradle の `CARGO_TARGET_DIR` はどちらも `rust/target` を指すため、直接の `cargo` 実行と Gradle ビルドで同じ依存キャッシュを共有します。

---

## 5. テスト・静的解析・ベンチマーク

各クレートの品質確認は以下のコマンドで個別に実行できます。

### `pixiv-api` の検証

```powershell
cd rust/pixiv-api

# フォーマット確認
cargo fmt --check

# Clippy による静的解析
cargo clippy --all-targets --all-features -- -D warnings

# 単体テスト (40+ テスト)
cargo test --all-features

# ベンチマーク実行
cargo bench --features bench --bench illust_decode
cargo bench --features bench --bench image_analysis
```

---

## 6. リリース最適化設定

APKサイズと実行時パフォーマンスを最大化するため、各 `Cargo.toml` の `[profile.release]` に最適化オプションを設定しています。

- **`lto = "thin"` / `"fat"`**: リンク時最適化によりインライン化を最大化
- **`codegen-units = 1`**: コンパイラ全体最適化
- **`opt-level = 3`**: 最大実行速度最適化
- **`panic = "abort"`**: アンワインドテーブルを削除しバイナリサイズ削減
- **`strip = "symbols"`**: デバッグシンボルを削除
