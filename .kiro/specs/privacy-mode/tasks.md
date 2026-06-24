# Implementation Plan: Privacy Mode

## Overview

既存の `AppSettings`・`SettingsStore`・`IllustiaViewModel`・`MainActivity` を最大限活用し、
以下の順序でプライバシーモードを実装する。

1. データ層（AppSettings 拡張・SettingsStore 拡張・DataStore キー追加）
2. ViewModel 層（IllustiaUiState 拡張・IllustiaViewModel 拡張）
3. CalculatorEngine（純粋関数・PBT 検証）
4. CalculatorScreen Composable（Miuix デザイン、パターン B/C）
5. PrivacyModeSettingsScreen Composable
6. MainActivity 拡張（FLAG_SECURE・自動ロック・Recents 対応）
7. PrivacyNotificationInterceptor
8. DummyAppIconSwitcher + AndroidManifest activity-alias
9. ナビゲーション統合（IllustiaApp.kt・AppRoute 追加）
10. テスト（CalculatorEngine のユニットテスト + PBT）

---

## Tasks

- [x] 1. データ層の拡張
  - [x] 1.1 `AppSettings` に Privacy Mode 関連フィールドを追加する
    - `privacyModeEnabled: Boolean = false`、`privacyModeAutoLockTiming: String = "immediate"`、`hideRecents: Boolean = true`、`hideNotifications: Boolean = false`、`dummyAppName: String = "電卓"`、`dummyIconVariant: String = "ic_launcher_dummy"` を既存 `data class AppSettings` に追加する
    - _Requirements: 1.4_

  - [x] 1.2 `SettingsStore` に DataStore キーと Privacy Mode 設定の読み書きを追加する
    - `PRIVACY_MODE_ENABLED`、`PRIVACY_MODE_AUTO_LOCK_TIMING`、`HIDE_RECENTS`、`HIDE_NOTIFICATIONS`、`DUMMY_APP_NAME`、`DUMMY_ICON_VARIANT` の DataStore PreferencesKey を定義する
    - 既存の `read()` / `write()` で上記フィールドを扱うよう拡張する
    - _Requirements: 1.1, 1.5_

  - [ ]* 1.3 設定ラウンドトリップのプロパティテストを書く（Property 12）
    - **Property 12: 設定ラウンドトリップ（プライバシーモード設定）**
    - **Validates: Requirements 1.1**

  - [x] 1.4 `SettingsStore` に解除コード管理メソッドを追加する
    - `saveUnlockCodeHash(code: String)`、`verifyUnlockCode(code: String): Boolean`、`hasUnlockCodeSet(): Boolean`、`clearUnlockCodeHash()`、`isValidUnlockCode(code: String): Boolean` を実装する
    - PBKDF2WithHmacSHA256 + 32byte ランダムソルト + 100,000 イテレーション、EncryptedSharedPreferences（`KEY_UNLOCK_CODE_HASH`、`KEY_UNLOCK_CODE_SALT`）を使用する
    - `verifyUnlockCode()` は `MessageDigest.isEqual` による定数時間比較を行う
    - _Requirements: 5.1, 5.5, 12.1, 12.2, 12.3, 12.4_

  - [x] 1.5 解除コードのラウンドトリッププロパティテストを書く（Property 1）
    - **Property 1: 解除コードのラウンドトリップ保存**
    - **Validates: Requirements 12.5**

  - [x] 1.6 解除コードの非衝突性プロパティテストを書く（Property 2）
    - **Property 2: 解除コードの非衝突性**
    - **Validates: Requirements 12.6**

  - [x] 1.7 解除コードの長さバリデーションプロパティテストを書く（Property 6）
    - **Property 6: 解除コードの長さバリデーション**
    - **Validates: Requirements 5.6**

- [x] 2. チェックポイント — データ層の確認
  - 全テストが通ることを確認し、疑問点があればユーザーに確認する。

- [x] 3. ViewModel 層の拡張
  - [x] 3.1 `CalculatorHistoryEntry` データクラスを作成する
    - `data class CalculatorHistoryEntry(val expression: String, val result: String)` を定義する（`IllustiaUiState.kt` または専用ファイル）
    - _Requirements: 3.6_

  - [x] 3.2 `IllustiaUiState` に Privacy Mode 関連フィールドを追加する
    - `privacyLocked: Boolean = false`、`calculatorBuffer: String = ""`、`calculatorHistory: List<CalculatorHistoryEntry> = emptyList()`、`isTransitioningToIllustia: Boolean = false` を追加する
    - _Requirements: 2.1, 3.1, 4.2_

  - [x] 3.3 `IllustiaViewModel` に Privacy Mode 制御メソッドを実装する
    - `enablePrivacyMode()`、`disablePrivacyMode()`、`verifyAndUnlockPrivacy(code: String): Boolean`、`confirmPrivacyUnlock()`、`lockPrivacyMode()` を実装する
    - `enablePrivacyMode()` は `hasUnlockCodeSet()` が `false` の場合 `saveUnlockCodeHash("168")` を呼ぶ
    - `disablePrivacyMode()` は `privacyLocked = false` にし電卓画面を非表示にする
    - 遷移アニメーション開始: `isTransitioningToIllustia = true`、300ms 後に `privacyLocked = false`
    - _Requirements: 1.2, 1.3, 4.2, 4.4_

  - [x] 3.4 `IllustiaViewModel` に電卓バッファ操作メソッドを実装する
    - `appendToCalculatorBuffer(char: Char)`（上限 50 文字）、`clearCalculatorBuffer()`、`deleteLastCalculatorBuffer()`、`evaluateCalculatorExpression()` を実装する
    - `evaluateCalculatorExpression()` は解除コード照合 → 成功なら遷移・履歴非記録、失敗なら計算実行・履歴追加（上限 20 件）
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.6, 4.1, 4.3_

  - [ ]* 3.5 バッファ上限不変条件プロパティテストを書く（Property 3）
    - **Property 3: Expression_Buffer の上限不変条件**
    - **Validates: Requirements 3.1**

  - [ ]* 3.6 バッファ削除操作の長さ不変条件プロパティテストを書く（Property 4）
    - **Property 4: バッファ削除操作の長さ不変条件**
    - **Validates: Requirements 3.4**

  - [ ]* 3.7 クリア操作の完全性プロパティテストを書く（Property 5）
    - **Property 5: クリア操作の完全性**
    - **Validates: Requirements 3.3**

  - [ ]* 3.8 解除コード照合成功時の履歴非記録プロパティテストを書く（Property 8）
    - **Property 8: 解除コード照合成功時の履歴非記録**
    - **Validates: Requirements 4.3**

  - [x] 3.9 `IllustiaViewModel` に AutoLock タイマーを実装する
    - `startAutoLockTimer()`、`cancelAutoLockTimer()` を実装する
    - `privacyModeAutoLockTiming` の値に応じて `scheduleAutoLock(delayMs)` を呼ぶ（`immediate` は遅延 0ms）
    - `disabled` の場合はタイマーを開始しない
    - _Requirements: 6.1, 6.4, 6.5, 6.6, 6.8, 6.9_

  - [x] 3.10 `IllustiaViewModel` に設定値更新メソッドと解除コード変更メソッドを実装する
    - `updatePrivacyModeAutoLockTiming()`、`updateHideRecents()`、`updateHideNotifications()`、`updateDummyAppName()`、`updateDummyIconVariant()`、`changeUnlockCode(currentCode, newCode): Boolean`、`verifyCurrentUnlockCode(code: String): Boolean` を実装する
    - `changeUnlockCode()` は現コードのハッシュ照合成功時のみ新ハッシュを保存し旧ハッシュを削除する
    - _Requirements: 1.5, 5.3, 5.4, 5.7, 11.4_

  - [ ]* 3.11 コード変更の前提条件プロパティテストを書く（Property 7）
    - **Property 7: コード変更の前提条件**
    - **Validates: Requirements 5.4**

- [x] 4. チェックポイント — ViewModel 層の確認
  - 全テストが通ることを確認し、疑問点があればユーザーに確認する。

- [x] 5. CalculatorEngine の実装
  - [x] 5.1 `CalculatorEngine` オブジェクトを作成する
    - `evaluate(expression: String): Double?` を実装する（四則演算・小数点・ゼロ除算・構文エラーを処理し、エラー時は `null` を返す。例外を UI 層に伝播させない）
    - `formatResult(value: Double): String` を実装する（整数結果は小数点以下を省略する等の表示整形）
    - _Requirements: 3.2, 3.5_

  - [ ] 5.2 有効式の評価が Double を返すプロパティテストを書く（Property 10）
    - **Property 10: 有効式の評価は Double を返す**
    - **Validates: Requirements 3.2**

  - [ ]* 5.3 無効式の評価が null を返すプロパティテストを書く（Property 9）
    - **Property 9: 無効式の評価はエラー状態を返す**
    - **Validates: Requirements 3.5**

  - [ ]* 5.4 `CalculatorEngine.evaluate()` のユニットテストを書く
    - `1+1=2`、`10/2=5`、`0.5*4=2`、`0/0=null`、構文エラー=null 等の具体例テスト
    - _Requirements: 3.2, 3.5_

- [x] 6. チェックポイント — CalculatorEngine の確認
  - 全テストが通ることを確認し、疑問点があればユーザーに確認する。

- [ ] 7. CalculatorScreen Composable の実装
  - [x] 7.1 `CalculatorScreen` Composable を作成する（ボタンレイアウト）
    - Miuix デザインシステムのコンポーネントを使用して全画面電卓 UI を実装する
    - 数字ボタン（0〜9）、四則演算ボタン（`+`、`-`、`×`、`÷`）、`.`、`C`、`⌫`、`=` を配置する
    - Illustia 固有の UI 要素（アイコン・タイトル・Pixivロゴ等）を含まない
    - _Requirements: 2.2, 2.3, 2.4_

  - [x] 7.2 `CalculatorScreen` にバッファ表示・ボタン操作を `IllustiaViewModel` に接続する
    - ボタン押下時に `appendToCalculatorBuffer()`、`clearCalculatorBuffer()`、`deleteLastCalculatorBuffer()`、`evaluateCalculatorExpression()` を呼ぶ
    - バッファ内容をディスプレイ領域に表示する
    - `isTransitioningToIllustia = true` の間はユーザー入力を無効にする
    - _Requirements: 2.1, 3.1, 3.2, 3.3, 3.4, 4.9_

  - [x] 7.3 `CalculatorScreen` に `Calculator_History` 欄を実装する
    - 最大 20 件の計算履歴を表示する
    - ヘッダー行への 500ms 以上の長押しで解除コード入力ダイアログ（パターンC）を表示する
    - _Requirements: 2.5, 3.6, 3.7_

  - [x] 7.4 `CalculatorScreen` にパターンC（右上隅タップ + ダイアログ）を実装する
    - 画面右上隅（72dp × 72dp）を 2 秒以内に 5 回連続タップで解除コード入力モード起動
    - ダイアログに解除コードを入力し、照合成功で遷移・失敗でダイアログを閉じ電卓へ戻る（フィードバックなし）
    - _Requirements: 2.6, 4.6, 4.7, 4.8_

  - [x] 7.5 `CalculatorScreen` に Illustia 本体への遷移アニメーションを実装する
    - `isTransitioningToIllustia` が `true` になったとき 300ms 以内にアニメーション付き遷移を開始し、完了後 `confirmPrivacyUnlock()` を呼ぶ
    - _Requirements: 4.2, 4.9_

- [ ] 8. PrivacyModeSettingsScreen Composable の実装
  - [ ] 8.1 `PrivacyModeSettingsScreen` Composable を作成する
    - Miuix デザインシステムで、プライバシーモード ON/OFF、解除コード変更（現コード確認 + 新コード入力）、自動ロック時間選択（即時/30秒/1分/5分/10分/無効）、Recents 隠蔽、通知隠蔽、ダミーアプリ名、ダミーアイコン切り替え の項目を実装する
    - `onBack` コールバックで前画面へ戻る
    - _Requirements: 11.1, 11.2_

  - [x] 8.2 `PrivacyModeSettingsScreen` の各設定操作を `IllustiaViewModel` に接続する
    - ON/OFF 切り替え → `enablePrivacyMode()` / `disablePrivacyMode()`
    - 解除コード変更 → `changeUnlockCode(currentCode, newCode)`（失敗時はエラー表示）
    - その他の設定値 → 対応する `update*` メソッドを呼ぶ
    - 各設定変更が 500ms 以内に `Settings_Store` へ書き込まれることを確認する
    - _Requirements: 5.3, 5.4, 5.7, 11.4_

- [ ] 9. MainActivity 拡張
  - [ ] 9.1 `MainActivity` に `FLAG_SECURE` 制御を追加する
    - `uiState.privacyLocked == true` または `appSettings.secureWindow == true` の場合に `WindowManager.LayoutParams.FLAG_SECURE` を適用する
    - `secureWindow == false` かつロック状態でない場合は `FLAG_SECURE` を解除する
    - _Requirements: 7.4, 8.1, 8.2, 8.3_

  - [ ] 9.2 `MainActivity` に `Lifecycle.Event` を使った自動ロック連携を実装する
    - `onStop` で `startAutoLockTimer()` を呼ぶ
    - `onStart` で `cancelAutoLockTimer()` を呼ぶ
    - `ACTION_SCREEN_OFF` / `ACTION_USER_PRESENT` の `BroadcastReceiver` を登録し、画面 OFF・端末ロック時に `lockPrivacyMode()` を呼ぶ
    - _Requirements: 6.1, 6.2, 6.3, 6.9_

  - [ ] 9.3 `MainActivity` に Recents サムネイル差し替えを実装する
    - `hideRecents == true` かつロック解除中の場合、`onWindowFocusChanged` または `onTrimMemory` を利用して電卓スクリーンショットを Recents サムネイルとして表示する（`setTaskDescription(ActivityManager.TaskDescription)` を使用）
    - ロック状態では `hideRecents` によらず常に電卓サムネイルを使用する
    - _Requirements: 7.1, 7.2, 7.3_

- [ ] 10. チェックポイント — MainActivity 統合の確認
  - 全テストが通ることを確認し、疑問点があればユーザーに確認する。

- [ ] 11. PrivacyNotificationInterceptor の実装
  - [ ] 11.1 `PrivacyNotificationInterceptor` オブジェクトを作成する
    - `intercept(builder: NotificationCompat.Builder, hideNotifications: Boolean): NotificationCompat.Builder` を実装する
    - `hideNotifications == true` の場合、タイトル・本文・サブテキストを汎用テキストに置き換え、大アイコン・BigPicture コンテンツを除去する
    - 例外が発生した場合は元の `builder` をそのまま返す（フェイルオープン）
    - _Requirements: 9.1, 9.2, 9.3_

  - [ ] 11.2 既存の通知発行箇所に `PrivacyNotificationInterceptor.intercept()` を差し込む
    - `hideNotifications` 設定値を `IllustiaViewModel` / `SettingsStore` から取得して渡す
    - `hideNotifications` が `false` に変更されると以降の通知は元のコンテンツを使用することを確認する
    - _Requirements: 9.4_

  - [ ]* 11.3 通知隠蔽の普遍性プロパティテストを書く（Property 11）
    - **Property 11: 通知隠蔽の普遍性**
    - **Validates: Requirements 9.1, 9.2**

- [ ] 12. DummyAppIconSwitcher + AndroidManifest 拡張
  - [ ] 12.1 `AndroidManifest.xml` に `<activity-alias>` を追加する
    - `android:name="com.yunfie.illustia.MainActivityDummy"` のエイリアスを追加し、ダミーアイコン・ダミーラベルリソースを指定する
    - 通常起動時はエイリアスを無効（`android:enabled="false"`）にする
    - _Requirements: 10.2, 10.3_

  - [ ] 12.2 `DummyAppIconSwitcher` オブジェクトを作成する
    - `ALIAS_REAL`、`ALIAS_DUMMY` 定数を定義する
    - `apply(context: Context, privacyModeEnabled: Boolean)` を実装する（`PackageManager.setComponentEnabledSetting()` を使用し、エラーはログ記録のみ）
    - メインスレッド外（`viewModelScope.launch(Dispatchers.IO)`）で呼ぶ
    - _Requirements: 10.2, 10.3, 10.5_

  - [ ] 12.3 `IllustiaViewModel.applyDummyAppIcon()` から `DummyAppIconSwitcher.apply()` を呼ぶよう実装する
    - `enablePrivacyMode()` / `disablePrivacyMode()` 内で `applyDummyAppIcon()` を呼ぶ
    - `dummyAppName` / `dummyIconVariant` 更新時も適宜 `applyDummyAppIcon()` を呼ぶ
    - _Requirements: 10.2, 10.3, 10.5_

- [ ] 13. ナビゲーション統合
  - [ ] 13.1 `AppRoute` に `PrivacyModeSettings` ルートを追加する
    - 既存の `AppRoute` sealed class / object に `PrivacyModeSettings` を追加する
    - _Requirements: 11.1_

  - [ ] 13.2 `IllustiaApp.kt` のナビゲーショングラフに `PrivacyModeSettings` 画面を追加する
    - `SettingsScreen` から `PrivacyModeSettings` への遷移を追加する
    - `CalculatorScreen` を `privacyLocked == true` のとき全画面オーバーレイとして表示するよう `IllustiaApp.kt` に統合する（300ms 以内に表示）
    - _Requirements: 2.1, 11.1, 11.3_

- [ ] 14. 最終チェックポイント — 全機能の確認
  - 全テストが通ることを確認し、疑問点があればユーザーに確認する。

---

## Notes

- `*` が付いたサブタスクはオプションであり、MVP では省略可能
- 各タスクは前のタスクの成果物を前提として積み上げる
- PBT には [Kotest Property Testing](https://kotest.io/docs/proptest/property-based-testing-container.html) を使用し、各テストは最低 100 回のイテレーションを実行する
- `CalculatorEngine` は純粋関数として実装し、ViewModel の状態変化から独立してテストできるようにする
- ActivityAlias 切り替えは非同期で反映されるため、即時の状態変化を保証しない（エラーはログのみ）
- PBKDF2 ハッシュのエラーは `verifyUnlockCode()` が `false` を返すフェイルセーフ動作とする

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2"] },
    { "id": 1, "tasks": ["1.3", "1.4", "3.1"] },
    { "id": 2, "tasks": ["1.5", "1.6", "1.7", "3.2"] },
    { "id": 3, "tasks": ["3.3", "3.4", "5.1"] },
    { "id": 4, "tasks": ["3.5", "3.6", "3.7", "3.8", "3.9", "3.10", "5.2", "5.3", "5.4"] },
    { "id": 5, "tasks": ["3.11", "7.1", "11.1"] },
    { "id": 6, "tasks": ["7.2", "7.3", "8.1", "11.2", "12.1", "12.2"] },
    { "id": 7, "tasks": ["7.4", "7.5", "8.2", "9.1", "9.2", "9.3", "11.3", "12.3"] },
    { "id": 8, "tasks": ["13.1"] },
    { "id": 9, "tasks": ["13.2"] }
  ]
}
```
