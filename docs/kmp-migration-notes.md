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

## ハプティクスの抽象化（プラットフォーム注入）
Vibrator API は Android 専用のため、共有 UI から直接呼べない。
composeApp の `HapticContract.kt` に次の 3 つを置き、androidApp 側で実装を提供する。

  enum class AppHapticEffect
  val LocalAppHapticEffect = compositionLocalOf<(AppHapticEffect) -> Unit>
  val LocalAppHapticMode / ProvideAppHaptics(...)

`miuixClickable` と `pinchToChangeColumns` は LocalAppHapticEffect 経由で発火する。
androidApp の `Haptics.kt` は Vibrator 実装本体のみを持つ。

## 現在の到達点
androidApp (main) のファイル数と、composeApp へ移したもの:

| 移動済み | 移動先 |
|---|---|
| models/{CommonLabels,SearchLabels,SearchAgeRestrictionLabels} | composeApp |
| settings/{AppFont,AppThemeMode,FeatureFlagLabels,PixivNetworkMode} | composeApp |
| ui/components/{NavigationChrome,MiuixGlass,CommonCore,HeaderComponents,PermissionRationaleDialog,FeedbackComponents,SettingsComponents,HapticContract} | composeApp |
| ui/screens/{CalculatorScreen,CalculatorScreenParts,DetailSectionSettingsScreen,DownloadQueueScreen,SavedIllustViewerScreen,AccountSettingsScreen,NavigationSettingsScreen,NetworkSettingsScreen,FeatureFlagsScreen} | composeApp |
| IllustiaUiState.kt, ChromeStates.kt | shared |

画面数の推移: androidApp 250 / composeApp(commonMain) 41 / shared 85

Android 側に残したファイル:
- ui/components/AndroidPlatformCore.kt
  （Context.isActiveNetworkMetered / PredictiveBackGestureHandler / AvatarImage）
- ui/components/CenteredErrorOverlay.kt（DialogWindowProvider 依存）
- ui/components/Haptics.kt（Vibrator 実装）

## 残っている大きな作業

**ViewModel interface 抽出は稼働中**。画面ごとに「必要な操作だけの
インターフェース」を composeApp に定義し、Foundation と各 Module に
override を付け、画面の引数型を差し替える。この手順で移したもの:

  CalculatorActions  -> CalculatorScreen, CalculatorScreenParts
  SettingsActions    -> DetailSectionSettingsScreen, NavigationSettingsScreen,
                        FeatureFlagsScreen (+ NetworkActions 経由)
  LibraryActions     -> DownloadQueueScreen, SavedIllustViewerScreen
                        (+ NetworkActions 経由)
  AccountActions     -> AccountSettingsScreen

**新しい画面を移す手順**

1. androidApp 側で `viewModel.<member>` / `viewModel::<member>` を全部洗い出す
   （grep は `viewModel\.[A-Za-z_]+` と `viewModel::` の両方を見ること。
   片方だけだと漏れる）
2. 必要な操作を composeApp の既存 interface に足す（無ければ新設）。
   引数型が shared にあるものだけインターフェースに出せる。
   androidApp 側の型（FeatureFlag 拡張など）は出せないので、
   呼び出し側を `flag.resolve(settings)` のように書き換える。
3. Module 側の `fun` に `override` を付ける（シグネチャは変えない）。
4. Foundation のスーパータイプ一覧にインターフェースを追加。
5. 画面の引数型を差し替え → コンパイル → 移動 → テスト。

**共通の落とし穴（移すと壊れるもの）**

- `SettingsActions` や `FeatureFlag.titleRes` は基本そのまま移せるが、
  `isFeatureEnabled` 拡張は androidApp の単体テストが import しているため
  androidApp に残す（app は shared/composeApp を implementation 依存しており、
  テストからは見えない）
- `PredictiveBackGestureHandler` を使っている画面は
  `BackNavigationHandler`（共有側）に置換する。予測型戻るの可否は
  androidApp の BackHandlerRegistrar 実装が判断する
- `LocalView` / `DialogWindowProvider` / `Vibrator` / `Coil Bitmap` を
  使う部品は androidApp に残す（CenteredErrorOverlay, PixivImage, Haptics 等）
