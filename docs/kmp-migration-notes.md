注: このファイルは .commandcode/taste の管理対象外のメモ。必要なら taste ツールで登録する。

# Android→KMP 移行メモ (このリポジトリ固有)

## モジュール構成
- androidApp (com.android.application): Android 依存の実装すべて
- composeApp (androidLibrary, KMP): 共有 UI。commonMain のみ
- shared (androidLibrary, KMP): モデル・data・platform API・状態型

## 移せる条件
1. `android.*` / `androidx.activity` / `Context` / `Intent` / `Uri` / `Build` /
   `R.*` / `SharedPreferences` を使わない
2. 依存先の型がすべて shared / composeApp に存在する
3. `internal` 宣言を androidApp 側から参照していない
   - internal はモジュール境界を越えられない。public 化するか差し戻す
4. androidApp のユニットテストから参照される `internal` も同様 (SettingsProjections が該当)

## 分割パッケージについて
同一パッケージ名を androidApp と shared/composeApp に置くことは合法。
旧作業ログの「分割パッケージで移せない」は誤り (実測で models/settings は移せた)。

## ただし別モジュール間の拡張関数は androidApp のユニットテストから見えない
- `FeatureFlag.resolve(settings)` は shared にあるので composeApp から見える
- `AppSettings.isFeatureEnabled()` を shared に移すと androidApp のテストが解決できない
  (main からは見えるが test コンパイルだけ失敗する)
- 対処: androidApp に残し、composeApp 側は `FeatureFlag.resolve(settings)` を直接呼ぶ

## 状態型を別モジュールへ移すとスマートキャストが壊れる
`IllustiaUiState` を shared に移した結果、`state.foo != null` の後の
`state.foo.bar` が 9 箇所でコンパイルエラーになった。
(他モジュールの public プロパティは安定して読めないとみなされる)
対処: `val foo = state.foo` に一度取り出してから分岐する。

## commonMain で使えない Java API の置換
- java.util.UUID.randomUUID()  -> kotlin.uuid.Uuid.random()
- System.currentTimeMillis()   -> kotlin.time.Clock.System.now().toEpochMilliseconds()
(Kotlin 2.4.21 で両方 stable)

## 検証コマンド
  gradlew :androidApp:testDebugUnitTest ktlintCheck --console=plain -q
  gradlew :androidApp:compileDebugKotlin --console=plain -q
