# Rust API client

Pixiv APIのHTTPトランスポートは`pixiv-api`クレートで実装し、UniFFI生成のKotlinバインディングを介して利用します。

初回のみAndroid向けRustツールを準備してください。

```powershell
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
cargo install cargo-ndk
```

通常のGradleビルドでは、`preBuild`がKotlinバインディング生成と3 ABIの`.so`生成を自動実行します。NDKはAndroid SDKのインストール済み最新版を`cargo-ndk`が検出します。
