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

### Step 1 — Extract `str()` into an injected `StringProvider` (69 sites)

```kotlin
// shared/commonMain
interface StringProvider {
    fun get(key: StringResKey, vararg args: Any): String
}
```

`StringResKey` is an enum-or-inline-class over the resource name, generated from the
same `strings.xml` that is currently being migrated to Compose Resources. This makes
the string migration and the ViewModel move the *same* change: keys already in
`composeResources` get a `StringResKey` entry for free, and the Android
`actual` resolves them via `getString` until each call site moves to composition scope.

Zero behaviour change. Fully mechanical. Do this first because it touches every file
and shrinks the rest of the diff.

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

### Step 3 — Replace `Application` with `ViewModelDeps`

All seven constructors take exactly `app: Application`. Replace with one data class:

```kotlin
// shared/commonMain
class ViewModelDeps(
    val strings: StringProvider,
    val logger: Logger,
    val clock: MonotonicClock,
    val mainThread: MainThreadChecker,
    val connectivity: Connectivity,
    val appInfo: AppInfo,
    val paths: AppPaths,
    val backgroundWork: BackgroundWork,
    val images: ImagePrefetcher,
    val device: DeviceProfile,
    val settings: SettingsStoreFacade,
    val repositories: FeatureRepositories,
    val scope: CoroutineScope,
)
```

The Android side constructs it once in the Application/DI graph. Because all seven
ctrs are uniform and positional, this is one mechanical edit per file.

### Step 4 — The small facades (class 4)

Each follows the existing `DynamicHzController` shape (interface + Default impl +
injected), with the interface in `commonMain` and the Android impl in `androidMain` —
mirroring the `PixivUrlPolicy.kt` split that already works.

| Interface | Replaces |
|---|---|
| `Logger` | `android.util.Log` |
| `MonotonicClock` | `SystemClock.elapsedRealtime()` |
| `MainThreadChecker` | `Looper.myLooper() == Looper.getMainLooper()` |
| `Connectivity` | `ConnectivityManager` / `NetworkCapabilities` / `isActiveNetworkMetered` |
| `AppInfo` | `PackageManager.getPackageInfo` + `PackageInfoCompat` |
| `AppPaths` | `cacheDir`, temp-file creation |
| `ImagePrefetcher` | Coil `SingletonImageLoader` + cache clearing |
| `DeviceProfile` | `PlatformCapabilities` low-RAM / debounce |
| `IncomingLink` (data class) | `Intent` in `handleIncomingIntent` |
| `String` URI | `android.net.Uri` in export/import |

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

Step 2 has landed. Step 1 (`StringProvider` for the 69 ViewModel-layer `str()` sites) is
the next unblocked slice.
