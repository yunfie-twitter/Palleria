# Requirements Document

## Introduction

プライバシーモードは、Illustia（PixivクライアントAndroidアプリ）に対して、外部から閲覧された際にアプリの正体を隠すための機能である。
プライバシーモードが有効な場合、アプリの起動時には通常の電卓アプリとして表示される。特定の解除操作を入力した場合にのみ、Illustia本体の画面へ遷移する。
アプリ切り替え画面（Recents）にも Pixiv 関連のコンテンツを表示しない。

本機能は、Android (Kotlin + Jetpack Compose + Miuix) で実装する。既存の `AppSettings`、`SettingsStore`（EncryptedSharedPreferences・PBKDF2ハッシュ）、`MainActivity` の構造を活用する。

---

## Glossary

- **Privacy_Mode_Controller**: プライバシーモードのロック状態・解除状態を管理するコンポーネント
- **Calculator_Screen**: ダミーの電卓 UI を提供する画面コンポーネント
- **Unlock_Code**: 電卓画面から Illustia 本体へ遷移するための解除コード（PIN + `=`、または固定シーケンス）
- **Expression_Buffer**: 電卓画面で入力中の数式文字列を保持するバッファ
- **Privacy_Mode_Settings**: プライバシーモードに関する設定項目を保持するデータクラス
- **Settings_Store**: 既存の `SettingsStore`。EncryptedSharedPreferences による安全な保存を担う
- **Recents_Guard**: アプリ切り替え画面（Recents）で Pixiv コンテンツを隠すためのコンポーネント
- **Auto_Lock_Timer**: 無操作タイムアウトによる自動ロックを管理するタイマーコンポーネント
- **Dummy_App_Name**: プライバシーモード有効時にランチャーやタスク切り替えに表示するアプリ名
- **Calculator_History**: 電卓で計算した式と結果の一覧。解除コードは記録しない

---

## Requirements

### Requirement 1: プライバシーモードの有効化・無効化

**User Story:** アプリ管理者として、プライバシーモードの ON/OFF を切り替えたい。そうすることで、必要な時だけカモフラージュ機能を有効にできる。

#### Acceptance Criteria

1. THE `Privacy_Mode_Controller` SHALL プライバシーモードの有効・無効状態を `Privacy_Mode_Settings` として `Settings_Store` に永続化する
2. IF プライバシーモードが有効化される際に解除コードが設定されていない、THEN THE `Privacy_Mode_Controller` SHALL 初期解除コード `168=` を `Settings_Store` の暗号化領域に PBKDF2 ハッシュとして保存する
3. WHEN プライバシーモードが無効化される、THE `Privacy_Mode_Controller` SHALL アプリを Illustia 通常起動状態（電卓画面を表示せず、ロック状態フラグを `false` にした状態）に戻す
4. THE `Privacy_Mode_Settings` SHALL 以下の設定フィールドを含む：`privacyModeEnabled: Boolean`、`privacyModeAutoLockTiming: String`、`hideRecents: Boolean`、`hideNotifications: Boolean`、`dummyAppName: String`、`dummyIconVariant: String`
5. THE `Privacy_Mode_Controller` SHALL `privacyModeEnabled` の変更を 500ms 以内に `Settings_Store` へ書き込む

---

### Requirement 2: 起動時の電卓画面表示

**User Story:** 近くにいる人として、Illustia を起動したときに電卓画面しか見えないようにしたい。そうすることで、アプリの正体が分からない。

#### Acceptance Criteria

1. WHEN プライバシーモードが有効であり、かつアプリが起動する、THE `Calculator_Screen` SHALL アプリ起動後 300ms 以内に電卓画面を全画面で表示する
2. WHILE プライバシーモードがロック状態である、THE `Calculator_Screen` SHALL Illustia 固有の UI 要素（アイコン・タイトル・Pixivロゴ・ユーザーアイコン・作品画像など）を一切表示しない
3. THE `Calculator_Screen` SHALL Miuix デザインシステムのコンポーネントを使用した電卓 UI を表示する
4. THE `Calculator_Screen` SHALL 数字ボタン（0〜9）、四則演算ボタン（`+`、`-`、`×`、`÷`）、小数点ボタン（`.`）、クリアボタン（`C`）、1文字削除ボタン（`⌫`）、イコールボタン（`=`）を表示する
5. THE `Calculator_Screen` SHALL 直近最大 20 件の計算履歴を表示する `Calculator_History` 欄を持つ
6. WHEN ユーザーが画面右上隅（幅 72dp × 高さ 72dp の領域）を 2 秒以内に 5 回連続でタップする、THE `Calculator_Screen` SHALL 解除コード入力モードを起動する（パターンC）

---

### Requirement 3: 電卓としての基本動作

**User Story:** アプリを偶然確認した人として、電卓として自然に使用したい。そうすることで、プライバシーモードであることに気づかない。

#### Acceptance Criteria

1. WHEN ユーザーが数字・演算子・小数点を入力する、THE `Expression_Buffer` SHALL 入力文字を `Expression_Buffer` に追加する（上限 50 文字。上限に達した場合は追加しない）
2. WHEN ユーザーが `=` ボタンを押す、かつ `Expression_Buffer` の内容が解除コードと一致しない、THE `Calculator_Screen` SHALL 数式を評価して結果を表示する
3. WHEN ユーザーが `C` ボタンを押す、THE `Expression_Buffer` SHALL バッファをクリアして `0` を表示する
4. WHEN ユーザーが `⌫` ボタンを押す、THE `Expression_Buffer` SHALL バッファの末尾の1文字を削除する
5. IF 数式が不正である（ゼロ除算・構文エラーを含む）、THEN THE `Calculator_Screen` SHALL `エラー` と表示し、次の数字・演算子・`C` ボタン入力でクリアして新しい入力を受け付ける
6. WHEN ユーザーが `=` ボタンを押し計算結果が表示される、THE `Calculator_Screen` SHALL 入力式と結果を `Calculator_History` の先頭に追加する（解除操作として処理された入力は追加しない）
7. WHEN ユーザーが `Calculator_History` のヘッダー行を 500ms 以上長押しする、THE `Calculator_Screen` SHALL 解除コード入力ダイアログを表示する（パターンC）

---

### Requirement 4: 解除コードによる Illustia 本体への遷移（パターンB・C共通）

**User Story:** Illustia ユーザーとして、設定した解除コードを入力して Illustia 本体へアクセスしたい。そうすることで、一見して電卓に見えるアプリから Illustia を開ける。

#### Acceptance Criteria

1. WHEN ユーザーが `<解除コード>=` の形式で入力し `=` を押す（パターンB）、THE `Privacy_Mode_Controller` SHALL 入力の `=` を除いた部分を PBKDF2 ハッシュと照合する
2. WHEN ハッシュ照合が成功する、THE `Privacy_Mode_Controller` SHALL 電卓画面から Illustia 本体画面へ、300ms 以内にアニメーション付きで遷移する
3. WHEN ハッシュ照合が成功する、THE `Expression_Buffer` SHALL 解除コードを `Calculator_History` に記録しない
4. WHEN ハッシュ照合が成功する、THE `Calculator_Screen` SHALL 解除成功を示すメッセージ・ダイアログ・トーストを表示しない
5. WHEN ハッシュ照合が失敗する、THE `Calculator_Screen` SHALL 入力を通常の計算式として処理し、エラー表示や特別なフィードバックを出さない
6. WHEN パターンC のダイアログで正しい解除コードが入力される、THE `Privacy_Mode_Controller` SHALL ダイアログを閉じて Illustia 本体へ遷移する
7. WHEN パターンC のダイアログで誤った解除コードが入力されるか、ダイアログがキャンセルされる、THE `Calculator_Screen` SHALL ダイアログを閉じて電卓画面に戻り、フィードバックを表示しない
8. THE `Privacy_Mode_Controller` SHALL パターンB・パターンC の両方を常に有効とする
9. WHILE 遷移アニメーション中である、THE `Calculator_Screen` SHALL ユーザーの入力を受け付けない

---

### Requirement 5: 解除コードの管理

**User Story:** Illustia ユーザーとして、解除コードを自分で設定・変更したい。そうすることで、初期コード以外の任意のコードで Illustia を開けるようにできる。

#### Acceptance Criteria

1. THE `Settings_Store` SHALL 解除コードを平文で保存せず、PBKDF2WithHmacSHA256 アルゴリズムでハッシュ化して EncryptedSharedPreferences に保存する
2. IF プライバシーモードが有効化される際に解除コードが未設定である、THEN THE `Privacy_Mode_Controller` SHALL デフォルト解除コード `168=` のハッシュを保存する
3. THE `Privacy_Mode_Settings` SHALL 設定画面から解除コードを変更できる UI（現在のコード確認 + 新しいコード入力）を提供する
4. WHEN 解除コードの変更が要求される、THE `Settings_Store` SHALL 現在のコードのハッシュ照合に成功した場合のみ、新しい解除コードのハッシュを保存し古いハッシュを削除する
5. THE `Privacy_Mode_Controller` SHALL 解除コードの検証に PBKDF2 ソルト付きハッシュ比較を使用し、タイミング攻撃を防ぐために定数時間比較を行う
6. THE `Settings_Store` SHALL 解除コードとして 4〜20 文字の数字・記号（`+`、`-`、`×`、`÷`、`.`、`=` を含む）のみを受け付け、それ以外の入力は拒否する
7. WHEN 解除コード変更の確認時に現在のコードの照合が失敗する、THE `Privacy_Mode_Settings` SHALL エラーを表示し、コードを変更しない

---

### Requirement 6: 自動ロック

**User Story:** Illustia ユーザーとして、特定の条件で自動的に電卓画面に戻したい。そうすることで、閲覧中に席を外しても中身が漏洩しない。

#### Acceptance Criteria

1. WHEN アプリがバックグラウンドへ移行する（他アプリへの切り替え・ホームボタン押下を含む）、THE `Privacy_Mode_Controller` SHALL `privacyModeAutoLockTiming` の設定に従ってロック処理を実行する
2. WHEN 端末の画面がオフになる、THE `Auto_Lock_Timer` SHALL 即時ロックを実行する
3. WHEN 端末がロック状態になる、THE `Auto_Lock_Timer` SHALL 即時ロックを実行する
4. IF `privacyModeAutoLockTiming` が `immediate` である、THEN THE `Privacy_Mode_Controller` SHALL アプリがバックグラウンドへ移行した直後にロックする
5. IF `privacyModeAutoLockTiming` が `30s`、`1m`、`5m`、`10m` のいずれかである、THEN THE `Auto_Lock_Timer` SHALL バックグラウンド移行時点から指定時間が経過した後にロックする
6. IF `privacyModeAutoLockTiming` が `disabled` である、THEN THE `Auto_Lock_Timer` SHALL タイムアウトによるロックを行わない
7. THE `Privacy_Mode_Settings` SHALL `privacyModeAutoLockTiming` の初期値を `immediate`（アプリを閉じたら即時）とする
8. WHEN ロックが実行される、THE `Privacy_Mode_Controller` SHALL `Expression_Buffer` をクリアし、電卓画面を初期状態で表示する
9. WHEN アプリがフォアグラウンドに復帰する、かつ `privacyModeAutoLockTiming` が `disabled` でない、THE `Auto_Lock_Timer` SHALL 進行中のタイマーをキャンセルする

---

### Requirement 7: アプリ切り替え画面（Recents）での隠蔽

**User Story:** Illustia ユーザーとして、タスク切り替え画面に Pixiv コンテンツが表示されないようにしたい。そうすることで、端末を他人に見せる際も中身が見えない。

#### Acceptance Criteria

1. WHEN `hideRecents` が `true` であり、かつアプリがフォアグラウンドで Illustia 本体を表示している状態でユーザーが Recents 画面を開く、THE `Recents_Guard` SHALL Illustia 本体のコンテンツの代わりに電卓画面のスクリーンショットを Recents サムネイルとして表示する
2. WHEN `hideRecents` が `false` であり、かつアプリがロック状態でない、THE `Recents_Guard` SHALL Recents サムネイルの差し替えを行わない
3. WHEN Illustia 本体がロック状態（電卓画面表示中）である、THE `Recents_Guard` SHALL `hideRecents` の値によらず常に電卓画面のスクリーンショットを Recents サムネイルとして表示する
4. WHEN Illustia 本体がロック状態（電卓画面表示中）である、THE `Recents_Guard` SHALL 常に `FLAG_SECURE` を有効にする（`secureWindow` 設定に依存しない）

---

### Requirement 8: スクリーンショット制御

**User Story:** Illustia ユーザーとして、Illustia 本体の画面のスクリーンショットを禁止したい。そうすることで、コンテンツが画像として端末に残らない。

#### Acceptance Criteria

1. WHERE `secureWindow` が `true` に設定されている、THE `MainActivity` SHALL `WindowManager.LayoutParams.FLAG_SECURE` を適用してスクリーンショットを禁止する
2. WHILE アプリがロック状態（プライバシーモードで電卓画面を表示中）である、THE `MainActivity` SHALL `secureWindow` 設定の値によらず `FLAG_SECURE` を有効にする
3. IF `secureWindow` が `false` に変更され、かつアプリがロック状態でない、THEN THE `MainActivity` SHALL `FLAG_SECURE` を解除する

---

### Requirement 9: 通知内容の隠蔽

**User Story:** Illustia ユーザーとして、通知バナーに Pixiv 関連の内容が表示されないようにしたい。そうすることで、通知から使用状況が分からない。

#### Acceptance Criteria

1. WHERE `hideNotifications` が `true` に設定されている、THE `Privacy_Mode_Controller` SHALL アプリが発行する通知のタイトル・本文・サブテキストを汎用テキスト（例：「新しい通知があります」）に置き換える
2. WHERE `hideNotifications` が `true` に設定されている、THE `Privacy_Mode_Controller` SHALL 通知に画像・ラージアイコン・アートワーク・BigPicture スタイルのコンテンツを添付しない
3. WHERE `hideNotifications` が `true` に設定されている、THE `Privacy_Mode_Controller` SHALL 通知チャンネルの表示名を Pixiv 関連の識別子を含まない汎用名に置き換える
4. WHEN `hideNotifications` が `true` から `false` に変更される、THE `Privacy_Mode_Controller` SHALL 以降に発行する通知から元のコンテンツを使用する（既存の通知バナーの遡及変更は行わない）

---

### Requirement 10: ダミーアプリ名・アイコンの切り替え

**User Story:** Illustia ユーザーとして、ランチャーやタスク切り替え画面で表示されるアプリ名とアイコンを電卓風のものに変更したい。そうすることで、ホーム画面を他人に見られても Illustia だと分からない。

#### Acceptance Criteria

1. THE `Privacy_Mode_Settings` SHALL `dummyAppName` フィールドに 1〜30 文字のカスタムダミーアプリ名を保持し、空文字列は拒否する
2. WHERE `privacyModeEnabled` が `true` である、THE `Privacy_Mode_Controller` SHALL ランチャーに表示されるアイコンを `dummyIconVariant` で指定した `ic_launcher_dummy` 系リソース（`ic_launcher_dummy`、`ic_launcher_dummy_round`、`ic_launcher_dummy_foreground`、`ic_launcher_dummy_monochrome`）に切り替える
3. WHERE `privacyModeEnabled` が `true` である、THE `Privacy_Mode_Controller` SHALL ランチャーに表示されるアプリ名を `dummyAppName` の値に切り替える
4. THE `Privacy_Mode_Settings` SHALL `dummyIconVariant` フィールドに選択中のダミーアイコンバリアントを保持し、初期値は `ic_launcher_dummy` とする
5. WHEN `privacyModeEnabled` が `false` に変更される、THE `Privacy_Mode_Controller` SHALL アイコンとアプリ名を元の Illustia のものに戻す

---

### Requirement 11: プライバシーモード設定画面

**User Story:** Illustia ユーザーとして、プライバシーモードに関するすべての設定を一か所で管理したい。そうすることで、設定変更が簡単にできる。

#### Acceptance Criteria

1. THE `Privacy_Mode_Settings` SHALL 既存の設定画面（`SettingsScreen`）から遷移できる専用の設定画面を提供する
2. THE `Privacy_Mode_Settings` SHALL 以下の設定項目を含む UI を提供する：プライバシーモード ON/OFF、解除コード変更、自動ロック時間（即時 / 30秒 / 1分 / 5分 / 10分 / 無効）、起動時に必ず電卓を表示、バックグラウンド復帰時にロック、最近使ったアプリ画面を隠す、通知内容を隠す、ダミーアプリ名、ダミーアイコン切り替え
3. WHEN 電卓画面の `Calculator_History` ヘッダー行を 500ms 以上長押しする、THE `Privacy_Mode_Settings` SHALL 解除コード入力ダイアログを経由して設定画面へ到達できるようにする
4. THE `Privacy_Mode_Settings` SHALL 設定変更を 500ms 以内に `Settings_Store` へ保存する

---

### Requirement 12: セキュアなデータ保存（解除コード）

**User Story:** セキュリティ担当として、解除コードが安全に保存・検証されることを確認したい。そうすることで、データを抜き取られてもコードが漏洩しない。

#### Acceptance Criteria

1. THE `Settings_Store` SHALL 解除コードの検証ハッシュを `EncryptedSharedPreferences`（Android Keystore バックエンド）に保存する
2. THE `Settings_Store` SHALL 解除コードを PBKDF2WithHmacSHA256 アルゴリズム、32バイトのランダムソルト、100,000回以上のイテレーションでハッシュ化する
3. THE `Settings_Store` SHALL 定数時間比較を用いてハッシュを検証し、タイミング攻撃を防ぐ
4. THE `Settings_Store` SHALL 解除コードの平文を、検証処理のスコープ外で参照できない形で扱い、メモリに保持しない
5. FOR ALL 解除コード文字列 `c`、`Settings_Store.saveUnlockCodeHash(c)` の後に `Settings_Store.verifyUnlockCode(c)` を呼ぶと `true` を返す（ラウンドトリッププロパティ）
6. FOR ALL 解除コード文字列 `c1 ≠ c2`、`Settings_Store.saveUnlockCodeHash(c1)` の後に `Settings_Store.verifyUnlockCode(c2)` を呼ぶと `false` を返す（非衝突プロパティ）
