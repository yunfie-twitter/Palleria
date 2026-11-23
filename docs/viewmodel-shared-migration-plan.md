# Plan: ViewModel → `shared` via a platform-facade layer

## Context

`IllustiaViewModel` and its six subclasses form a 7-level inheritance chain, all
constructed from `Application`, totalling ~6,400 lines:

```
IllustiaViewModel              (15 L, passthrough)
 └ IllustiaViewModelCore       (15 L, passthrough)
    └ IllustiaLibraryNavigationModule   (990 L)
       └ IllustiaBookmarkModule         (752 L)
          └ IllustiaDetailProfileModule (847 L)
             └ IllustiaAuthFeedModule   (1435 L)
                └ IllustiaSettingsSecurityModule (1111 L)
                   └ IllustiaViewModelFoundation (1198 L, extends AndroidViewModel)
```

Moving this into `shared` is blocked by six distinct dependency classes. Each needs a
different treatment; they are not interchangeable.

### What `shared` has today

`shared` uses the `androidLibrary` KMP plugin (Android target only) and depends on:

- `compose.runtime` (NOT `compose.foundation`)
- kotlinx-coroutines, serialization, datetime
- ktor client core / negotiation / json

It has **no** `androidx.lifecycle`, **no** WorkManager, **no** Room, **no** Coil,
**no** `compose.foundation`.

An expect/actual mechanism exists but is only used for four top-level functions
(`PixivUrlPolicy.kt`, `Md5Hex.android.kt`). There is no class-level platform facade yet.

---

## The six dependency classes

| # | Dependency | Sites | Treatment |
|---|---|---|---|
| 1 | `str(R.string.x)` | 69 | Inject a `StringProvider` |
| 2 | `LazyGridState` / `LazyListState` | 29 (23 in Foundation, 6 in SettingsSecurity) | **Lift out of the ViewModel** into UI |
| 3 | `Application` ctor param | 7 | Replace with `ViewModelDeps` |
| 4 | `ConnectivityManager`, `Looper`, `SystemClock`, `Log`, `PackageInfo`, `cacheDir`, `Intent`, `Uri` | ~15 | Small interfaces, `expect`/`actual` impls |
| 5 | `WorkManager` | 3 collaborators | `BackgroundWork` interface |
| 6 | Room (`SavedIllustEntity`, `SettingsStore`), Coil, `AndroidViewModel`/`viewModelScope` | pervasive | Interface seams + injected `CoroutineScope` |

Classes 1–4 are mechanical. Classes 5–6 are the real work, and class 2 is a design
correction that should happen regardless of KMP.

---

## Ordered steps

### Step 1 — Extract `str()` into an injected `StringProvider` (69 sites) — ✅ done

```kotlin
// androidApp/src/main/java/com/yunfie/illustia/platform
interface StringProvider {
    fun get(resId: Int): String
    fun get(resId: Int, vararg args: Any): String
}
class AndroidStringProvider(private val context: Context) : StringProvider
```

`IllustiaViewModelFoundation` now takes `strings: StringProvider` as a constructor
parameter (defaulting to `AndroidStringProvider(app)`), and its two `str()` overloads
delegate to it. A `shared` implementation can be injected instead.

Verified: `getString(` no longer appears anywhere in `IllustiaViewModel*.kt` or
`Illustia*Module.kt`. All 54 `str()` sites and the 5 direct
`getApplication<Application>().getString(...)` calls in
`IllustiaLibraryNavigationModule` now route through the provider.

The 52 `R.string` keys those ViewModel sites reference were also migrated into
`composeApp/src/commonMain/composeResources/values*/strings.xml` (all 14 locales),
so the string data — not just the call seam — is now available outside `androidApp`.

Remaining `getApplication()` uses in the ViewModel tree are for `cacheDir`,
`contentResolver`, `applicationContext` and `isNetworkMetered` — those belong to
dependency classes 4 and 6, not Step 1.

Zero behaviour change. Fully mechanical.

### Step 2 — Lift the Compose scroll states out of the ViewModel — ✅ done

The ViewModel used to own `LazyGridState` for nine grids plus five LRU caches of
scroll states (23 references in `IllustiaViewModelFoundation`, 6 in
`IllustiaSettingsSecurityModule`). That was UI state in the wrong layer, and it was
the only reason the ViewModel tree needed `compose.foundation`.

Landed in `refactor(scroll)`:

- `ui/scroll/ScrollStateRegistry` — UI-owned LRU cache of `LazyGridState` /
  `LazyListState`, keyed by `ScrollStateKeys` (fixed tab keys + `userProfile(id)`,
  `illustDetail(id)`, `ranking(mode)`).
- `ui/scroll/ScrollPositionProbe` — the minimal read-only view the ViewModel needs
  (`isScrolledAwayFromStart()`), installed by `InstallHomeScrollProbe` via
  `DisposableEffect`.
- The home-feed deferral decision now reads `homeScrollProbe` instead of
  `homeFeedGridState.firstVisibleItemIndex` / `…ScrollOffset` / `isScrollInProgress`.
- The keyed accessors (`userProfileGridState`, `illustDetailListState`,
  `rankingGridState`, …) were removed from the ViewModel; call sites query the
  registry directly.
- Verified: no `LazyGridState`, `LazyListState` or `compose.foundation` reference
  remains in `IllustiaViewModel*.kt` / `Illustia*Module.kt`.

The ViewModel keeps the primitives it genuinely needs (e.g. `illustDetailPage(id): Int`).

### Step 3 — Replace `Application` with `ViewModelDeps` — ✅ done (first slice)

Measured before the change: the `app: Application` parameter was pure passthrough down
all seven constructors. Nothing below `IllustiaViewModelFoundation` read it; every real
use was `getApplication()` inherited from `AndroidViewModel`. So the parameter was
carrying exactly four things:

1. `AndroidViewModel(app)` superclass argument
2. `ManagedDataRepository(app.contentResolver)`
3. `AndroidStringProvider(app)`
4. `app as? IllustiaApplication`

Landed in `refactor(deps)`:

```kotlin
// androidApp/src/main/java/com/yunfie/illustia/platform
class ViewModelDeps(val application: Application) {
    val illustiaApplication: IllustiaApplication? get() = application as? IllustiaApplication
    val contentResolver get() = application.contentResolver
}
```

`IllustiaViewModel` keeps the `Application` constructor (required by
`AndroidViewModelFactory`, which `MainActivity` uses) and converts immediately:
`IllustiaViewModelCore(ViewModelDeps(app))`. All six remaining classes take
`deps: ViewModelDeps` and forward it.

Verified: `app: Application` now appears in exactly one place in the whole tree —
`IllustiaViewModel`'s constructor.

**Not yet done:** the full dependency set in the original sketch (`Logger`, `Clock`,
`Connectivity`, `AppPaths`, `BackgroundWork`, …). Those are dependency classes 4–6 and
need the facades from Step 4; `ViewModelDeps` currently carries only what the chain
actually consumed. That is the honest boundary — adding unused fields now would be
speculative.

### Step 4 — The small facades (class 4) — ✅ partially done

Landed so far (`refactor(platform)`): `AppLogger`, `MonotonicClock`, `MainThreadChecker`,
each with an interface + Android impl, carried on `ViewModelDeps` and exposed as
`logger` / `clock` / `mainThread` on the foundation.

That removed the last direct uses of `android.util.Log` (1), `android.os.SystemClock`
(2) and `android.os.Looper` (1) from the ViewModel tree — all four are now 0.

### Step 5 — AndroidViewModel → ViewModel — ✅ done

`IllustiaViewModelFoundation` no longer extends `AndroidViewModel`. It extends plain
`androidx.lifecycle.ViewModel`, and both the `Application` and a lifetime-bound
`CoroutineScope` arrive through `ViewModelDeps`.

| Check | Before | After |
|---|---|---|
| `) : AndroidViewModel(` | 1 | **0** |
| `import androidx.lifecycle.AndroidViewModel` | 1 | **0** |
| `import androidx.lifecycle.viewModelScope` | 6 | **0** |
| `) : ViewModel()` | 0 | **1** |

How the old semantics are preserved:
- `viewModelScope` is a property on the foundation pointing at `deps.scope`, so the
  70 existing `viewModelScope.launch { }` call sites are untouched.
- `IllustiaViewModel.Factory` builds a `CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)`
  and cancels it in `onCleared()` — the same lifetime contract as `viewModelScope`.
- `getApplication<T>()` became a local helper returning `deps.application`, so the 26
  existing call sites keep compiling.

**Still outstanding, measured from the tree (not estimated):**

| Dependency | Remaining sites | Note |
|---|---|---|
| `getApplication<…>()` / `contentResolver` | 8 | all in `IllustiaViewModelFoundation` — collaborator construction |
| `viewModelScope.launch` sites | 70 | **seam done** — now `deps.scope` |
| `Intent` | 0 | `AppServices` facade |
| `Uri` | 0 | `DocumentIo` facade |
| `Coil` (`SingletonImageLoader`) | 0 | `ImageCache` facade |
| `Room` (`SavedIllustEntity`) | 0 | `SavedIllust` model + `SettingsStore` mapping |
| `WorkManager` / `WorkInfo` | 0 | `BackgroundWork` returns domain `AppUpdateEvent` |
| `ConnectivityManager` / `NetworkCapabilities` | 0 | |
| `PackageInfoCompat` | 0 | |

The inbound `Intent` edge is gone: `handleIncomingIntent` now takes the Android-free
`IncomingLink`, and `Intent.toIncomingLink()` lives outside the ViewModel tree.

`viewModelScope` is no longer a blocker — the seam is in place and the type-level
`AndroidViewModel` edge is gone (Step 5).

### Step 4 detail — the small facades (class 4)

Each follows the existing `DynamicHzController` shape (interface + Default impl +
injected), with the interface in `commonMain` and the Android impl in `androidMain` —
mirroring the `PixivUrlPolicy.kt` split that already works.

| Interface | Replaces | Status |
|---|---|---|
| `AppLogger` | `android.util.Log` | ✅ done |
| `MonotonicClock` | `SystemClock.elapsedRealtime()` | ✅ done |
| `MainThreadChecker` | `Looper.myLooper() == Looper.getMainLooper()` | ✅ done |
| `Connectivity` | `ConnectivityManager` / `NetworkCapabilities` / `isNetworkMetered` | ✅ done |
| `AppInfo` | `PackageManager.getPackageInfo` + `PackageInfoCompat` | ✅ done |
| `AppPaths` | `cacheDir` | ✅ done |
| `BackgroundWork` | `WorkManager` (app-update job) | ✅ done |
| `IncomingLink` (data class) | `Intent` in `handleIncomingIntent` | ✅ done |
| `ImageCache` | Coil `SingletonImageLoader` + cache clearing | ✅ done |
| `DocumentIo` | `Uri` in export/import managed data | ✅ done |
| `DeviceProfile` | `PlatformCapabilities` low-RAM / debounce | ✅ done |
| `SavedIllust` (model) | Room `SavedIllustEntity` in UI state | ✅ done |
| `AppServices` | wallpaper scheduler / widget refresh / download service acquire | ✅ done |
| `AppServices` (拡張) | live-wallpaper broadcast / account reconcile / bookmark enqueue / follow-delta snapshot | ✅ done |
| `String` URI (save image) | `android.net.Uri` in `saveImageToDocument` | ✅ done |

### Step 6 — The actual move into `shared`

`shared` is **already** a Kotlin Multiplatform module (`kotlin.multiplatform` +
`android.kmp.library`, `commonMain` / `androidMain`, compiles via
`:shared:compileAndroidMain` and `:shared:compileKotlinMetadata`).

Platform-neutral facades already living in `shared/src/commonMain/.../platform/`:

- `AppLogger` (+ `NoopAppLogger`)
- `MonotonicClock`, `MainThreadChecker`
- `Connectivity`
- `AppInfo`
- `ImageCache`
- `DeviceProfile`

Still in `androidApp` (interfaces are neutral but not yet moved, or need a
platform type swapped first):

- `AppPaths` — returns `java.io.File`; needs okio `Path` (okio not yet declared)
- `DocumentIo` — takes `java.io.OutputStream`
- `BackgroundWork` — references `AppReleaseInfo` (updater package)
- `AppServices` — references `SavedIllust`-era models + `IllustiaRepository`
- `StringProvider` — `Int` resource ids are Android-shaped

ViewModel tree's remaining non-`shared` imports (**25** distinct, measured
per-symbol — a coarse per-package check undercounts badly here, because a shared
package does *not* make every symbol in it shared):

| Import | Blocking on |
|---|---|
| `data.ManagedDataRepository` | **除去済み** — ViewModel から未使用だったコンストラクタ配線を削除 |
| `data.IllustiaRepository`, `data.FeatureRepositories` | network + settings |
| `data.AnimatedGifEncoder`, `data.UgoiraMp4Encoder`, `data.ResumableDownloader` | `Bitmap`/`MediaCodec`/`File` |
| `data.isPixivRateLimited`, `pixivLoginCodeOrNull`, `proxyPixivImageUrl` | **すでに `shared` にあり解決済み**（スクリプトが未解決と誤判定していただけ） |
| `platform.ViewModelDeps`, `AppPaths`, `AppServices`, `DocumentIo`, `StringProvider`, `AndroidStringProvider` | 境界型 / `File` / `Int` リソース |
| `settings.SettingsStore` | DataStore + Room |
| `settings.isFeatureEnabled`, `isDynamicColorAvailable` | トップレベル `fun` |
| `settings.AppHapticMode` | `R` リソース |
| `settings.store.STARTUP_LOGGED_IN_TOKEN` | トップレベル `const val` |
| `nativebridge.NativeImageStore` | `ContentResolver` / MediaStore |
| `ui.app.AppRoute`, `ui.app.AppTab` | `NavKey` / `ImageVector` / `R` |
| `updater.AppUpdaterRepository` | `PackageManager` / Shizuku |
| `DummyAppIconSwitcher` | `PackageManager` |

#### Known trap: split packages hide top-level declarations

`shared` と `androidApp` が**同じパッケージ**を宣言すると、そのパッケージの
トップレベル宣言は他方のモジュールから解決できない:

- クラス・enum・`sealed interface` は **移動できる**（`UpdateModels`,
  `BackgroundWork`, `AppSettings`, `FeatureFlag`, `SavedIllust`,
  `DetailEntrySnapshot`, `ScrollPositionProbe` で実証、テスト緑）
- トップレベル `fun` も **移動できる**（`proxyPixivImageUrl`,
  `isPixivRateLimited`, `pixivLoginCodeOrNull` はすでに `shared` にあり、
  `androidApp` の unit test から普通に解決される）
- トップレベル `const val` は**分割パッケージでは解決されない**
  （`STARTUP_LOGGED_IN_TOKEN` を `shared` に写して `androidApp` から削除したら
  `UNRESOLVED_IMPORT`）

`isFeatureEnabled` が以前テストから見えなかったのは、`FeatureFlagKt` と
`SettingsStore` などが**同一ファイル/同一パッケージで併存**していたため。
enum のメソッド化（`FeatureFlag.resolve`）は今も妥当な整理だが、
「トップレベル関数は移せない」という一般則ではなかった。

原則: **パッケージを丸ごと移す**。ただしクラス/関数単位の移動は実際に機能する。

Already moved to `shared` this session:

- facades: `AppLogger`, `MonotonicClock`/`MainThreadChecker`, `Connectivity`,
  `AppInfo`, `ImageCache`, `DeviceProfile`, `Telemetry`（`AndroidTelemetry` が
  `GlitchTipTelemetry` へ委譲）
- data: `AppSettings`, `FeatureFlag` enum (with `resolve()` as an enum method),
  `UpdateModels` (`UpdateInstallMethod`/`AppReleaseInfo`/`UpdateCheckState`),
  `BackgroundWork` + `AppUpdateEvent`
- routing: `NativeIntentEvent`, `NativeIntentRouter` (pure), `IncomingLink`,
  plus a `commonMain` percent-decoder
- pure logic: `CalculatorEngine`
- snapshots: `DetailEntrySnapshot`, `SearchEntrySnapshot`
- scroll: `ScrollPositionProbe` interface（拡張関数と `@Composable` は `androidApp`）
- models: `SavedIllust`, `SavedIllustPage`

`ScrollStateRegistry` は ViewModel から**切り離した**（`ScrollStateRegistry.shared`
を UI が直接参照）。`compose.foundation` は `shared` に入れない。

#### `AppPaths` stays in `androidApp` (deliberate)

`clearAppCache()` needs `cacheDir.listFiles()` and the ugoira path feeds
`File.createTempFile`. Returning a `String` would push `File` construction into
the ViewModel — worse than the status quo. Keep the `File`-returning facade.

### Step 5 — The hard seams (classes 5–6)

**`BackgroundWork`** — no KMP WorkManager exists. Interface only:

```kotlin
interface BackgroundWork {
    fun progressFlow(uniqueName: String): Flow<WorkProgress>
    fun cancel(uniqueName: String)
    fun enqueue(request: WorkRequest)
}
```

Affects `AppUpdateDownloadWorker`, `AppUpdateScheduler`, `BookmarkToggleScheduler`.

**Room** — `SavedIllustEntity` and `SettingsStore` are Room/DataStore-backed. Room has
KMP support but is not wired into `shared/build.gradle`. Two options:

- *(preferred)* Introduce plain domain models in `commonMain` and map to/from the Room
  entities in `androidApp`. The ViewModel then depends only on the domain models.
- *(faster, weaker)* Add the Room KMP artifact to `shared`. Couples `shared` to Room
  persistence and makes non-Android targets harder later.

**`AndroidViewModel` / `viewModelScope`** — Inject a `CoroutineScope` and drop
`AndroidViewModel` for a plain class with a `close()` contract (replacing the
`onCleared()` override). Either add the lifecycle KMP ViewModel artifact or hand-roll a
minimal `ScopedViewModel` base in `commonMain`. ~90 `viewModelScope.launch` sites route
through the injected scope.

### Step 6 — Move files bottom-up

Only after 1–5 compile cleanly does the move itself happen, in dependency order:

```
Foundation → SettingsSecurity → AuthFeed → DetailProfile → Bookmark
  → LibraryNavigation → Core → IllustiaViewModel
```

Each step must build green on its own; do not batch them.

---

## Risks and constraints

- **`shared` is Android-only.** `java.*` imports in `commonMain` compile today but would
  break the moment another target is added. Do not add non-Android targets during this
  work.
- **The 7-level inheritance chain** means a change to the Foundation constructor
  signature ripples through all six subclasses. Steps 3 and 5 are single-shot edits per
  file, not incremental.
- **`IllustiaApplication` service-locator downcast** (`getApplication() as IllustiaApplication`)
  must be replaced by constructor injection before Step 6, or the move fails to compile.
- **Branch churn has bitten twice.** PR #659 and #661 were both merged with work still
  sitting on the branch. Any long-running branch for this refactor should be rebased onto
  `main` frequently, and each step landed as its own PR.
- **Steps 1 and 2 are independently valuable** and can ship as their own PRs even if the
  full move stalls.

---

## Recommended first slice

Step 2 (lifting scroll state) and Step 1 (`StringProvider`) are both low-risk and
independently shippable. Step 2 is the better first *code* change because it removes a
genuine layering defect and cuts the ViewModel's hardest dependency; Step 1 is the
better first *structural* change because it overlaps with the in-flight string migration.

Suggested order: **Step 2 → Step 1 → Step 3**, each as its own PR.

Steps 1 and 2 have landed. Step 3 (`ViewModelDeps` in place of `Application`) is the
next unblocked slice.

### 進捗メモ（最新）

- `ViewModelDeps` に `settings` / `repository` / `sharedHttpClient` を追加し、
  `getApplication<Application>() as IllustiaApplication` の downcast を
  `ViewModelDeps` 内の 1 箇所へ集約。ViewModel 側の downcast は 0 件。
- 未使用だった配線を削除: `ManagedDataRepository`（継承チェーン全体）、
  `featureRepositories`。
- `ScrollStateRegistry` を ViewModel から切り離し、`ScrollStateRegistry.shared`
  を UI が参照する形に変更（`compose.foundation` を `shared` に入れないため）。
- `AppHapticMode` を `shared` へ（トップレベル関数 `effectiveAppHapticMode` ごと移動し
  unit test 緑 → 分割パッケージでもトップレベル関数は移設可能と確定）。
- `StringKey`（52 キー）を導入し、`StringProvider` を `StringKey` ベースに変更。
  ViewModel の `R.string.*` 76 箇所を `StringKey.*` へ置換、`R` への参照は 0 件。
- `AppServices` インターフェースを `shared` へ。
- 残ブロッカーは **17**（per-symbol 計測）。最大の壁は `str(R.string.*)` 82 箇所だったが、
  `StringKey` enum + `StringProvider` の `StringKey` 化で**解消済み**。
