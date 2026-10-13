# Relay privacy policy

Revision: 1  
Disclosure ID: fbbc7392-f700-420c-b8a3-3f935c754bdb  
Published / effective (UTC): 2026-10-11T15:04:53.217+00:00  
Published / effective (Unix milliseconds): 1791731093217  
Relay key ID: kaIMLJ9IFYPfw8DsH-79tONuUTPOs4ruaZoK1Wtnlzc

## 運用者と適用範囲

運用者：ゆんふぃ

問い合わせ・障害・削除依頼：https://api.yunfi.f5.si/privacy.md

連絡先メール: yunfie168@proton.me（問い合わせ・障害・削除依頼。利用者アカウント不要）

適用するHTTPS origin：https://api.yunfi.f5.si

連絡先は利用者アカウントなしで利用できます。本ポリシーは、このoriginの `/privacy.md` と署名付き `/privacy.json` に公開します。公開文書の取得にも接続メタデータが発生します。両文書は同じ現行版であり、JSONの `markdown_sha256` がMarkdown本文の厳密なbytesを保護します。

## 保存と容量

このリレーはPallaSync 3.0のblind relayです。署名、公開構造、権限を検証し、暗号文と公開証拠を保存します。レコード、デバイスメタデータ、epoch secretを復号しません。mnemonic、passphrase、復旧共有、RMS、RSWK、復旧パッケージを収集・保存・解釈しません。

durability tierは `single-durable` です。単一の永続SQLiteストアでWAL、synchronous=FULL、secure_delete=ONを使用します。レプリカとバックアップは作成しません。ホスト・永続ボリュームの喪失では受理済みデータを復元できない場合があります。バックアップ間隔、保存期限、復元試験、バックアップ鍵破棄は対象外です。外部の自動スナップショットやバックアップも無効です。

チェーンごとのquota：max_bytes = null（設定quotaなし）

割当と計上規則：All chains have no configured quota. Count stored signed objects, IDs/hashes, log wrappers, receipts, transparency/head evidence, outcomes, cursors and offers; exclude SQLite/WAL physical overhead.

計上する保存bytesは、署名付きオブジェクト、ID/hash、LogEntry、UploadReceipt、透明性entry/head、操作結果、cursor、enrollment offer/requestの合計です。SQLiteページ、WALの物理的重複、index等のオーバーヘッドは計上しません。quotaを設定しないことは、無限容量、無料、永久保存、可用性保証を意味しません。

上限超過では `507 QUOTA_EXCEEDED` として書込みを拒否し、受理済みデータを黙って削除しません。利用者は認証されたAPIで実quotaと利用量を確認し、連絡先へ相談するか、検証済み完全snapshotによる安全なcompaction、または利用者が選択した別リレーへのexport・移行を行えます。休眠・利用停止だけで受理済み履歴を自動期限削除しません。サービス終了は可能な限り30日前に告知し、公表した書込み停止後も終了まで読取り・export・認証済み削除を提供します。移行先候補への同意や権限移転は自動で行いません。

## リクエスト制限

| 対象 | 1分あたり | burst |
|---|---:|---:|
| 認可済みsigner | 120 | 30 |
| record upload device | 30 | 10 |
| enrollment source IP | 6 | 3 |
| enrollment offer | 3 | 1 |
| 公開文書のsource IP | 240 | 60 |

最大requestは8,388,608 bytes、1 uploadは64 records、pageは1,000 entries、deviceは128です。チェーンごとの同時pull/exportは2です。超過は `429` と `Retry-After` を返します。IPのscopeはリレーへの直近接続元です。reverse proxyの背後では同じproxyを経由する利用者がIP枠を共有します。転送されたIPヘッダーは信頼せず、個別利用者のIPで制限しているとは扱いません。

サーバーコンテナのメモリ上限は512 MiBで、コンテナのswapを無効にします。資源枯渇・停止・再起動では、保護鍵の再解除まで利用できない場合があります。

## コピー、保存場所、アクセス権

保存地域・ホスティング委託先・役割：日本。運用者ゆんふぃが管理するUbuntu Linuxサーバーの永続Docker volume

アクセス可能な主体と役割：運用者ゆんふぃ、許可されたOS管理者・Docker管理者

保存用暗号化・アクセス制御の実際の方法：Ubuntu内のext4/LVM保存ディスクの暗号化は設定していません。OS/Docker管理者によるアクセス制御とUID 10001でのサービス実行を使用します。保存する利用者データはクライアント側でE2EE暗号化されています。公開署名・台帳は平文です。

SQLite内部の公開署名・識別子・台帳はE2EEで暗号化されません。保存用のホスト保護はクライアントのE2EEとは別で、利用者のepoch鍵を運用者が持つことを意味しません。

| コピー分類 | 有無・目的 | 保持終了条件・削除後の扱い |
|---|---|---|
| active_store | 有。暗号文と署名付き状態 | チェーン削除まで。削除受理から60000 ms以内に稼働コピーを消去 |
| replica | 作成しない | 対象外 |
| WAL | 有。永続トランザクション | 通常checkpointを行い、残存分もチェーン削除受理から60000 ms以内に処理 |
| snapshot_store | 有。暗号化snapshot | 認証済みcompaction・保持規則またはチェーン削除。削除期限60000 ms |
| backup | 作成しない | 対象外 |
| staging | 有。import・snapshot作成途中の暗号文 | 公開・プロトコルのcleanupまたはチェーン削除。削除期限60000 ms |
| temporary_export | 永続コピーは作成しない | 認証されたexportを応答で送信。処理中のRAMは処理終了時に解放 |
| support | 作成しない | 対象外 |
| cache | 有。IPハッシュ・更新時刻を含むrate bucket | 無期限。削除後も運用メタデータを保持 |
| operational_log | 有。Cloudflareの通信メタデータ。有限の最大期限を約束せずindefinite・retain_metadataで開示 | リレー自身のDockerログ保存を無効化。既存proxy側の扱いは次項で開示 |
| attestations | 有。署名付きreceipt、公開hash、透明性entry/head | 無期限。削除後も公開証拠を保持し、暗号文を含むLogEntryは消去 |
| deletion_ledger | 有。削除の公開認可と復活防止 | 無期限。削除対象chain/deletion ID、公開鍵・認可証明、hash、時刻、DeleteReceipt等の最小証拠を保持 |

表の「作成しない」は本運用者の永続コピーについての宣言です。受信・検証・export中の暗号文は一時的にRAMに存在します。保持期間と削除期限は各コピーの作成時点・削除受理時点に基づき、稼働コピーの削除時点を起算して延期しません。

## 削除保証と限界

assuranceは `backup-expiry`、稼働コピーの最長処理期限は削除受理から60000 msです。バックアップとレプリカが存在しないため、その削除期限は対象外です。運用者の保存鍵破棄によるcrypto-shredは提供しません。

削除認可が成立すると台帳で再登録・復活・書込みを防ぎ、稼働暗号文を処理します。`requested_at_ms` は削除受理時刻、`completed_at_ms` は稼働処理完了後の時刻、`delete_after_ms` は最終処理期限です。認可済み利用者は規定のdeletion APIからledgerと署名付きDeleteReceiptを取得できます。SQLite消去とWAL checkpointに失敗した場合は完了を宣言しません。

公表期限を守るための運用・障害対応は運用者の責務です。署名は運用者の声明を証明し、物理消去や全コピーの不存在を証明しません。利用者、失効端末、攻撃者、運用者の管理外のコピーを消去できません。削除後も残る最小の認可・復活防止証拠から、暗号文やデバイスの復号済みメタデータを復元する機能はありません。

## 観測・保持するメタデータ

リレーは公開chain/device ID、公開鍵、epoch、世代、署名、暗号文の長さ、sequence、requestの時刻・順序等を観測できます。E2EEはこれらを隠しません。レート制限用の直近接続元IPをハッシュ化した値と最終更新時刻をSQLiteに無期限で保持します。ハッシュ化は匿名性を保証しません。前述のアクセス主体がレート制限運用のために扱い、未記載の第三者へ提供しません。

このPallaSyncプロセスは、生のIP、User-Agent、接続時刻、path、status、byte countをアクセスログとして永続保存しません。公開文書取得も同じ制限対象です。署名付きreceiptや透明性証拠等に含まれるプロトコル上の時刻・公開識別子は前述の保存・証拠として保持します。root materialやトークンを診断ログへ出力しません。deviceによる削除では、仕様で要求される公開削除認可の証拠だけをdeletion ledgerに保持します。

既存CloudflareをHTTPS終端・通信転送に利用します。CloudflareはIP、User-Agent、接続/request情報、status、時刻、byte count等を処理し、セキュリティ・可用性・トラフィック分析のNetwork Dataを保持します。Cloudflare側メタデータには有限の最大保持期限を約束せず、この開示ではindefiniteとして扱います。処理地域は日本に限定されず、Cloudflareとその委託先がアクセスします。リレーのno-store応答を使用し、リレーDBの複製・backupとして使用しません。Cloudflareの設定は変更しません。提供者の取扱い: https://www.cloudflare.com/privacypolicy/ ; cache規則: https://developers.cloudflare.com/cache/concepts/default-cache-behavior/

## リレー鍵とreplay対策

relay Ed25519 seedは独立したCSPRNGで32 bytes生成し、ageのpassphrase暗号化で保護します。パスフレーズは起動時に対話入力し、平文鍵はDocker tmpfsと稼働プロセスのメモリ・環境だけに渡します。Docker設定、イメージ、平文.envに保存しません。鍵を扱う起動処理・Docker daemonとリレーのcgroupでswapを無効化し、core dumpを無効化します。ホスト管理者・Docker管理者・侵害された稼働環境はメモリ内の鍵にアクセスし得ます。

自動relay key rotationは行いません。手動交換は事前告知し、新しい透明性IDと明示的な信頼再確立・再同意を要します。旧公開検証鍵と署名証拠を保持します。ポリシーへの同意だけで新しい鍵を信頼しません。

replay protectionは永続SQLite nonce cacheによる `durable` 方式です。再起動時には同じ永続ストアを再利用します。古いDBを上書きしてnonceや削除台帳を巻き戻す自動復元は行いません。コンテナ再作成・再起動時には保護鍵を再度解除するまで同期サービスを開始しません。

## 変更告知と利用者の同意

変更告知：https://github.com/yunfie-twitter/PallaSync/pulls?q=is%3Apr

過去版の公開・確認場所：https://github.com/yunfie-twitter/PallaSync/pulls?q=is%3Apr

変更前の最低告知期間：0 ms

これは初版のため過去版はありません。以後の変更予告と公開文書はPallaSyncリポジトリの公開PRに掲載し、そのcommitに含まれるprivacy.md/privacy.jsonで各版を確認できます。閉じたPRも確認対象です。

運用者は文書を変更する場合に版識別子を更新し、実値と本文を再署名し、告知と過去版を前述のURLで公開します。利用者は各origin/key/両文書hash/revisionに紐づく明示的な同意を必要とします。変更・取得不能・署名や本文不整合時にはオンライン操作を停止し、ローカルデータ・鍵・outboxを保持して再確認します。拒否・撤回時にオンライン操作は停止します。未検証の410や運用者の告知だけでローカルデータ・鍵を自動消去しません。

ポリシー同意はepoch保持、復旧共有の作成・配布、初回復元の信頼源選択の同意を代替しません。受理済み履歴を勝手に期限削除したり、復旧秘密をリレーへ保存したりする許可にもなりません。
