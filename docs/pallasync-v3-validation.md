# PALLASYNC-3.0-R1 実装と検証の記録

規範は依頼者が指定した `PallaSync-Protocol/docs/v3.0.md` だけです。
本記録・コード・試験・設定は仕様を変更しません。全機能の適合完了はまだ宣言しません。

§7.10/§10.15/§17.7/§17.11に従い、root seedを名前付きRust session内に保持したままCAS-bound AdminOperationを送信します。202/410単独では消去せず、同じsessionでGET `/deletion`を取得し、Rustが固定relay pin・既存authority・ローカル要求署名・CAS head・ledger hash・DeleteReceipt署名・transparency entryを照合します。証明をRoomへ保存してreadbackを確認した後、当該chainのRoom状態・outbox・同期鍵とpinを消去し、署名済み証拠は保持します。証明が取得/検証できない場合は鍵と状態を保持し`deletion_unverified`として返します。

検証: Rust `cargo fmt --check`、`cargo test --locked`、`cargo clippy --locked --all-targets -- -D warnings`成功。Android `compileDebugKotlin`、`compileDebugUnitTestKotlin`、`ktlintCheck`、`detekt`、全`testDebugUnitTest`成功。削除時のRoom試験では署名済み証拠だけを残し、要求証拠と当該chainの行を消す境界を追加確認しました。

実サーバーを使ったGET `/deletion` end-to-end試験、commit境界の障害注入、独立したオフライン媒体への証拠export確認はまだ実施していません。従って全適合済みとは扱いません。

2026-10-13、追加実装の前に依頼どおり両リポジトリの作業をcommitしました。
Palleriaは`470b5131`、PallaSyncは`6c9f51f`です。これらは途中の実装を保存するcommitであり、
全適合・最終APK・本番更新・新規PRの完了を表しません。

§7.11／§18.6–§18.8のJNIには配布、proposalのadmin署名、保護package作成、readback ack、
自端末共有・live収集、threshold試験、active化、指定操作用rootの型付き所有権を接続しています。
複数の署名済みstatus分岐を保持する修正後、Kotlin全464試験（PallaSync対象127試験、失敗/error/skip 0）、detekt/ktlintが成功しました。実Androidの検証範囲と版ごとの結果は後段に記録します。Rust core全61試験とclippyも、直前のnative実装時点で成功しています。
holder保存枠は通常DBから独立し、全chainで単一の保護entryをCAS更新します。host試験では、
占有拒否、再開、古い状態による削除拒否、署名済みstatus forkの保存とunsigned変更の拒否を確認しました。
RSWKはOS認証を必要とするKeystore cipherで包み、解放には新しい認証と明示同意を要求します。
OS認証、保護保存/readback、保護prefs再読込後の単一枠、自端末の一時収集、認証取消による鍵利用拒否は、
隔離Androidの2試験で27.424秒で成功しました。初回はOSのPIN padをテストが検出できず失敗し、
観測したSystemUIのCompose resourceへ合わせて再試験しました。human UIと全lifecycleは未完了です。別install/cloneを
独立した物理holderと扱いません。Androidの1つのapp保存域だけで他appの保存域まで列挙・認証できるという主張はしません。

live session鍵、dealer/collectorの一時共有、RMSとholder RSWKには専用pageを使い、終了時に消去します。
SAS拒否/不一致は再確認で復活できません。署名済みの先行packet欠落はC_MISSING_DEPENDENCYとして
復号・処理を進めず、元packetのretryで再開します。Java/Rust双方の対象試験で重複処理がないことを確認しました。
通常lookupで期限切れsessionを除去した場合もholderのRSWKを解放するよう修正しています。
実時間5分の全expiryケース・process kill・全ITの代替にはしません。独立PythonのC.8/Argon2/PSR3/fragment、公開artifact schema/status ancestryとlive SAS/HPKE/gap/retryを含む22試験も成功しました。
§3.6の署名済みlocal descriptor/transferのextを検証し、署名・SAS・AADへ含めます。§7.11.1/§18.6–§18.8の退役/漏洩status生成はroot操作のscope、既知の最新headとのCAS、厳密な遷移を検査し、replacementにはそのセッションで配布/readback/threshold試験後にactive化したsetだけを許可します。PSR3/断片JNIは完全packageを既知admin/statusで検証し、破損・非正規Base64・上限超過・矛盾する断片を拒否します。単一slotの交換/消去後も公開manifest/status/hash/漏洩・forkフラグを保護して保持します。
最新の三者再実行ではADB readが20秒でtimeoutし、Android試験も失敗しました（target/android-recovery-bridge-normal-peer-interop.log）。read-only pollingを元の240秒期限内で再試行するよう修正しました。その後の再実行結果は以下に記録しています。§18.7のset単位の進行中collector/sessionと復旧済み未消費rootの取消、§7.11.4のdevice package/hosted provider出力拒否を追加しました。3 ABI/universal/test APKを更新し、OS認証・保護保存/readback・取消の実Android2試験は27.332秒で成功しました。保存成功後の同一encrypted ack再送では再保存/追加認証が発生しないことも確認しました。当該時点のAPKで三者再実行は成功しました（Android側203.131秒、target/android-recovery-session-peer-interop.log）。Android approver/独立Python subject/隔離relayのSAS・承認・元receipt・Pのdelivery/ackと、3端末の完全epoch bundle/HPKE/commitment/H/T、通常同期/outbox再照合を確認しました。human UI、Android subjectの全保存/再起動境界や全ITの完了とは扱いません。monotonic expiryを境界へ移動したlazy cleanupの対象Rust試験は成功しましたが、実時間5分の試験とは扱いません。
§7.4/§7.11の送信前・復号後metadata検証、encoded PSR3 packageの通常保存前拒否、§3/§5.5の不正UTF-16 root入力拒否を追加しました。§11.7/§17.6では保護済みauthorityのoriginを送信サービスへ固定し、設定URLの変更を移行として扱いません。更新APKの実Android画面6試験は15.898秒で成功し、通常三者試験のAndroid部分は168.703秒で成功しました。独立Pythonの後段確認も通り、8 artifact種・root入力・encoded packageの計10ケースがjournal/outbox/authorを進めないことと、異なるoriginへのchain要求が送信前に拒否されることを確認しました。

§10.7/§18.1のpeer処理には、offer/request wrapper検証、保存されたofferの消費、署名済み承認本文の送信前journal、原receiptの保持、結果不明時の同一本文retryを追加しています。独立Pythonと隔離リレーのpeer承認試験は成功しました。Android hostの実JNIでも、独立Pythonと実リレー由来の公開genesis/原receiptを使い、二者のSAS・subject自己署名・HPKE・型付き6鍵バッファと消去を確認しました。実Android approver→独立Python subject→隔離relayの三者試験も成功しました（Android部分221.313秒）。保護journal開き直し、独立SAS比較、subject自己署名、HPKE/commitment、原receipt、Pのアクセス拒否・own-envelope取得・ackを検証し、その後は3端末の完全epoch bundleとH/Tを独立検証しました。ファイル交換と確認は試験harnessの明示入力であり、peer UI、Android subjectの保存/ack、全管理者variant、全ITの完了とは扱いません。
`pallasync-v3-acceptance.json`にはAppendix Aの全項目、IT-01〜IT-50、C.1〜C.11、Dの全例を収録します。
三者で未実行の項目はpendingのままです。単体試験やテストfixtureの成功で置き換えません。

| 実装 | 根拠 | 実行した検証 | 残る検証・接続 |
|---|---|---|---|
| RustのJSON/JCS、閉じたsigned wire、必須null、署名・DH・plaintext schema | §3–§8、§14 | strict_wire、protocol_fixture、disclosure | 全response parserと全管理操作への接続、全vectors |
| 操作に束縛されたrootハンドル、背景移行・取消・単調時計による無効化、作成・参加の型付き鍵結果 | §5.4–§5.5、§7.11.3 | secret_lifetime、recovery、recovery_flow、InitialRecordJniTest | 全管理UIの取消・failure境界、既存鍵ストアのString adapter廃止 |
| 固定GF profile、RMS・共有・RSWK・offline包み・live SASのRust処理 | §5.6、§7.11、§11.4、§18.6–§18.9 | recovery_flow、C.8、独立galois | 保護された物理holder store・認証・UI・全lifecycle |
| Room 1〜6の保持移行、version 7論理テーブル、完全Kとauthor前方hash、受信pageのatomic commit | §11.1–§11.2、§12.1、§15 | MigrationTest、ProtocolStoreTest（page commitへの障害注入を含む） | 全authority/epoch更新・全アプリ投影・全画面のcold membership |
| 正確な分数順序キー、200行を既定とするrevision付きkeyset API | §15.5、§16.3 | FractionalOrderTest、ProtocolStoreTest | ページUI、実サイズAndroidストレス、全projection/floor規則 |
| source・relay鍵・policy同意を別操作にし、token生成・HTTP前に現行pairを検証 | §7.13、§11.6–§11.7、§17.12 | disclosure、health/JNI試験 | 全画面・中断・同意変更・withdrawal競合のAndroid試験 |
| 新規chainのH0〜H5と全5receiptを検証し保存、originに束縛された正確な再送journal | §7.12.1、§10.2、§11.1、§17.3 | Python→隔離HTTPSリレー→Palleria core、AuthorityJniTest | 全作成commit境界、registry/epoch全履歴の通常同期 |
| outboxの証拠保存と削除を同一Room transactionにし、送信object/hashへ束縛 | §11.1、§17.3、§17.9 | C.9 Rust/Python、AttestationJniTest | commit障害、全enrollment/rotation/snapshot receipt、継続probe |
| T0からの最大1000件pageの検証、gap・署名・incarnation変更の拒否 | §7.12.2、§17.9 | attestations、独立HTTPS runner | Androidの継続worker・checkpoint export/import・UI進捗 |
| 未検証410の全既存分岐で鍵・outboxを保持 | §17.7、§17.11 | コード経路確認、HTTP classification | 削除認可・台帳・witness・export後の完全な消去フロー |
| Keystore wrapperの既存alias保持、利用可能時のStrongBox要求、明示的なbackup/D2D除外 | §5.4、§7.11.3 | Kotlin compile・manifest resource build | Android instrumentation、実hardware、復旧storeとlocal auth |
| 操作単位のdevice JNI、nonceとpadding済みframeの署名前journal、outbox/author/仮反映のatomic commit、未受理のrelay位置はnull | §5.4、§11.1、§12.1、§14.3 | device_session、InitialRecordJniTest、ProtocolStoreTest | 実Androidのjournal障害・容量試験、旧鍵での未解決送信 |
| 署名済みauthority、固定された端末ID・公開鍵、署名済みT先頭観測の保存、OS保護anchorとの照合 | §7.4、§11.5、§17.3、§17.9–§17.10、§18.4 | authority、client_response、AuthorityJniTest | 通常同期のepoch履歴と認可、失効前の独立証拠 |
| ログ先頭と署名済みT先頭の同一view確認、同時進行時の再取得、200件のT page、既定15分＋0〜20%jitter probe | §10.9、§11.5、§17.9 | client_response、AuthorityJniTest、独立HTTPS runner | probeの全worker/UI障害試験、post-pruning bootstrap・repair |
| 全7kindのsnapshot stateを順序・重複検査し、part境界を跨いでliteral arrayのHMACを検証 | §7.8、§16.6、Appendix C.10 | snapshot、Python test_snapshot | 暗号化partの生成・download、意味上の全状態照合、staging install・pruning |
| 現在epochの署名済み全履歴・receipt・T suffixを参加前に検証し、実HPKE復号・readback後にack | §10.4、§10.8、§11.6、§17.9、§18.4 | bootstrap、epoch_session、AuthorityJniTest、InitialRecordJniTest、独立enroll runner | 実Androidの全参加境界、全eligible歴史envelopeの配布、snapshotからの参加 |
| 初期作成中のchainを分離し、構築journal/tail/initial進捗を1件ずつ保存してから切替 | §11.1、§12.1 | LocalAuthorTest（frame・署名・Room・tailの4境界、既存chain保持） | OS実ストレージでのfailure/容量試験、初期mergeの全競合 |
| epoch secretのmutable JNI、commitmentの定数時間照合、tagの再照合、保護済み歴史鍵の選択 | §5.3–§5.4、§11.2、§12.1、§14 | epoch_session、InitialRecordJniTest | 通常同期の全鍵更新・historical retention・失効前の独立証拠 |
| 表示キャッシュからの消失をdeleteにしない明示的な履歴削除・clear、seen/muteのcold索引照会、optional viewed body、有限projectionの可変floor | §8.2–§8.4、§15.6、§16.1–§16.5 | SettingsEventsTest、ViewedProjectionTest、ProtocolStoreTest、RetentionProjectionTest | 全履歴画面、実サイズ計測、write-stopの未送信intent・UI |
| 完全LogEntryと元receiptを独立テーブルへ保持し、既知の全H cutを再検証してからplaintext prefixと進捗をatomic適用、初期項目はremote clock観測後に作成。通常workerも全H/Tを検証してからprotected authorityを更新 | §11.1–§11.3、§12.1、§17.9 | bootstrap（保持H link・連続seqの拒否）、Kotlin compile | 故障境界、全管理control検証・snapshot-covered prefix |
| 管理操作の正確なpayload/CAS/操作IDと完全な失効epochを検証。relayの並替えで元payloadを復元できない場合は、実payloadの証拠を待ち認可を進めない | §7.10、§10.4–§10.10、§13.3–§13.4 | authority、epoch_interop、EpochHistoryJniTest、実Android EpochBoundaryTest | 全管理UI・媒体でのpayload証拠取得、re-seed/prune/delete |
| 500・通信結果不明の書込みは完全H/T再照合後に再送。full jitter・minimum wait・次回時刻・時計補正を保護して保存し、403/409/507の同一要求は前提変更まで保留 | §6.3、§17.8、§19 | RetryGateTest、HTTP classification、client_response、device_session、bootstrap | 匿名取得の失敗境界・全lifecycle/capacity UI・実三者IT-20 |
| 表示cacheにviewed bodyの既知fieldを保持し、再読込・再アクセスでplaceholderをwire metadataへ変換しない | §8.4.2、§16.5 | ViewedProjectionTest（cache保存/再読込、欠落fieldを含む再送） | 全画面でのunknown表示・履歴paging |
| root操作内でランダムな次epoch・完全member bundleを生成し、復旧envelopeを開く。通常/予定/失効の提案・dispatch・鍵readback・ackを独立したjournal境界で扱い、結果不明では提案を保持、検証済みCAS勝者で未公開secretを破棄 | §5.4–§5.5、§10.8–§10.10、§13.1–§13.7 | rotation、epoch_interop、EpochProposalJniTest、EpochJournalTest（再起動、取消拒否、commit失敗時のmutable入力消去） | 実Androidの更新・全journal障害、UI、全端末失効とfresh subject、旧outbox rebase、retention snapshot |

2026-10-13の以前の実装時点では、Kotlin全427試験（PallaSync対象90試験を含む）は失敗・error・skipなしで、detektとktlintCheckも成功しました。Shamirの公開set作成・型付きlive session・SAS・確認拒否/不正thresholdでの消去に加え、3つのoffline packageの配布・credential readback・2-of-3 threshold試験・active化・指定操作用rootの復旧を実JNIで確認しました。RSWK/credentialのJVM可変入力は成功・不正package時とも消去し、rootや共有をJSONで返しません。これはOSでのphysical holder保存/認証やhuman UI試験の代替ではありません。これらのJNI追加とlive session鍵の専用page化は、先に検証したAPKへまだ含めていません。
Rust core全56試験とfmt/clippyは、管理payload・CAS・歴史epochの受理位置・保持outboxの自署名・完全LogEntry/receiptのH検証、更新proposal・予定due・HPKE/復旧readback、通常データの復旧artifact混入拒否、peer QR/SAS/subject自己署名とapproval本文・HTTP wrapperのfactory境界を含め成功しました。
独立Python8試験と隔離HTTPS runner（enroll・pending制限・HPKE・ackを含む）はそれ以前の実装時点で成功しました。
Androidの2境界試験と実coordinator/Python/隔離リレーの1ケースも、それ以前のAPKで成功しました（後者61.575秒）。この1ケースでIT-01〜IT-50を成功扱いしません。
完全prefix適用と初期merge順の変更を含む3 ABI/universalのdebug APKとAndroidTest APKを生成し、実Androidの対象1ケースが74.832秒で成功しました。通常workerの全H prefix取得・元receipt/T検証・保護されたauthority更新も接続し、その最新APKの同ケースは90.728秒で成功、global D/V/Mの一致を確認しました。Python側は初期項目のLamportがremoteの1,000,000を超えること、署名・AEAD・元receiptを独立に確認しました。これも全ITの完了を意味しません。
管理認可と保護されたretry状態を追加したAPKの同ケースも91.332秒で成功しました。独立Python/隔離リレーでは、通常epoch更新・全memberのHPKE readback・競合CAS・正確な再送・再起動・誤admin targetの拒否・atomic失効と全最終H/T/receiptを確認しました。公開fixtureだけをRust/JNIに渡して独立に再検証し、実Androidでは3つの歴史epochの復旧commitmentと各epoch暗号文を確認しました。authority公開のSTAGED/ROOM_COMMITTED/PIN_COMMITTED障害を含むAndroidの4境界試験は0.723秒で成功しました。これらは全rotation UI・全ITの完了ではありません。
通常epoch更新をcoordinatorへ接続した後の実Android/Python/隔離HTTPSリレー試験は138.084秒で成功しました。Androidが新しいepochを生成・送信し、完全H/Tを検証、自端末HPKEとOS保存readback後にackし、epoch 1のレコードを送信しました。Pythonは全member bundle、HPKEと復旧envelope双方の復号、commitment、元receiptと全H/T、更新後暗号文を独立に検証しました。この試験で発見した「後から承認された端末を既存epochのmember配列に要求する」誤りは§10.4/§13.2に従って修正し、承認位置とepoch受理位置で認可を判定しています。全端末失効・fresh subject、既存outboxのrebase、全故障境界・UI・全ITはまだ未完了です。
§11.1の旧epoch outbox再作成を接続した実Android/Python/隔離リレー試験は141.308秒で成功しました。完全な保護済みauthority/H/TとD/V/M cutを確認し、未受理の2件を新しいID・nonce・seq・Lamportで再作成し、元のSignedRecordと未受理author証拠を保持しました。原receiptで受理済みと判明した旧1件は再署名せずoutboxから照合除去しました。Pythonは旧IDの未受理、編集内容、前方hashを独立に検証しました。mappingと新SignedRecord/outbox/tailの同一Room commitは4つのhost障害境界で確認しています。Androidの再作成障害境界は別に実行し、まだ全ITの成功扱いにはしません。
再作成のSOURCE_STAGED、DESTINATION_COMMITTED、SOURCE_RETIRED直後の中断は、nativeセッション終了とOS journal開き直し後に実Androidで再開し、141.778／142.925／142.344秒で各1試験が成功しました。各ケースでもPythonが新旧ID、署名・AEAD、1件だけの再作成、前方hashと全H/Tを独立に検証しました。これらは当該3境界の検証であり、実プロセス強制終了・全epoch障害・全ITの完了を意味しません。
§5.5/§13.6の入力・予定更新表示を接続しています。鍵更新/鍵取得/他端末の失効は操作単位のroot入力とBIP39 passphraseを受け取り、終了・取消でnative rootを閉じます。取消tokenはその操作だけに束縛され、古い画面の取消が新しいrootを閉じないことと、構築前取消による消去を実JNIの2試験で確認しました。予定時期は検証済みcommitted_at_msと署名済みintervalからrotation_dueを記録し、rootセッションや無人更新を開始しません。UIの実画面・全取消境界のAndroid検証は未完了です。
root入力/予定状態の接続を含む直近APKのAndroid/Python/隔離リレー試験は142.324秒で成功しました。通常epoch更新、2件の旧epoch再作成、受理済み旧objectの原receipt照合、全H/Tを再確認しています。Compose実画面の取消・FLAG_SECURE・lifecycle試験の代替にはしません。
§11.1/§16.4の未署名編集journalを追加しています。停止/無効中は編集意図を保存し、再開後は原意図から署名済みoutboxを構築します。mappingはSignedRecord/outbox/author tailと同一transactionで、200件の巡回scanにより保留中の作成が後続delete/clearを塞ぎません。generationが変わった原意図は自動で移しません。同期スイッチの無効化は一時停止とし、既存chainの明示再開で新genesis/新端末IDを作成しません。署名前に明示的write-stopを確認し、超過/不正/oversizeの意図は保持します。未送信コレクションはローカル表示を保持し、受信の検証済みregisterはRoomへ保存します。4つのauthor中断境界、201件の旧generation保留と後続処理、1.1MiBのoversize intent保持をactual JNI/Roomで確認しました。実Android/Python/隔離リレーの対象試験は166.059秒で成功し、無効中の編集保存・表示保持・native終了/OS journal開き直し・同じ端末IDでの明示再開・1回だけの受理を確認しました。実プロセスkill、MAX_*の全容量境界・UI解決フロー・全ITは別途未完了です。
Appendix A全28項目には仕様条項、現存する実装/対象試験、未完了範囲を`pallasync-v3-acceptance.json`へ対応付けています。参照pathの存在を確認し、同じauthority hashでの再生成は既記録の結果を保持します。全50 IT、C.1–C.11、D.1–D.8の完全受入結果はpendingのままであり、対象試験の成功を完全受入へ昇格しません。
§5.5の生成単語画面にも任意passphraseと操作単位のnative取消を接続しました。単語/passphraseはrememberだけで保持し、送信・取消・背景移行・10分idleで表示を消去します。秘密Dialog自身と親windowをFLAG_SECUREで保護し、DialogのdecorViewをOS autofill対象から除外します。Compose ownerへ設定すると初期化時に上書きされることを実Android試験で検出し、ancestorへの設定へ修正しました。wrapped Context、両windowの保護、正確なUnicode/空白入力、busy時の非表示、再表示時の空欄、ON_STOP、元FLAGの保持、lease重複、生成passphraseの消去を6 Android UI試験（13.927秒）で確認しました。全10分の実経過・全管理操作のHTTP/commit取消・プロセスkillの試験は未完了です。
§5.5/§7.11の8つのreserved recovery typeとmnemonic/passphrase入力objectは、入れ子でも通常wire/InnerRecord/snapshotと未署名編集journalへ入れません。JNIの検査はRustに集約し、mutable入力と検査したSecretJsonを消去します。actual JNI/Roomでは、不正な後続項目があれば先行項目やcursorも同一transactionで保存されないことを確認しました。通常文字列PSR3:debugは拒否しません。serverは公開JSON構造だけを検査し、暗号文を復号・解釈せず9不正recordを保存しません。これは生の復旧秘密を任意の別表現にした場合まで識別できるという主張ではありません。復旧API/store/transportの分離接続は引き続き必要です。
全管理・復旧・snapshot・移行機能、全Android障害シナリオ、実サイズストレス、全三者IT試験、
最終APK、本番更新と新規PRは未完了です。今回のdebug APKも全機能完成前の検証用であり、最終納品物にはしません。

RootKeys、dealer RMS、holder RSWK、device操作鍵には専用anonymous pageを使用し、消去してから解放します。
mlock/VirtualLockとdump抑止はOS制限を受けるbest effortです。KDF/stack、復旧入力・一時共有、
表示用文字列、既存鍵ストアのmanaged Stringコピーまで一律に保護・消去できるとは主張しません。root操作で保持する正規化入力は専用pageへ移し、復旧set作成/交換以外の操作では導出後に保持しません。不要なencoded chain saltのheapコピーも消去します。

独立PythonのHTTPS runnerは使い捨てのDB・TLS endpoint・公開テスト鍵・署名済みpolicyを作成し、
新規chain、暗号化レコード、receiptの同一retry、H/T全prefixのpaging、再起動後のnonce拒否を確認します。
同じ公開成果物をPalleria coreが独立に署名検証します。公開fixtureには私有鍵を含めません。これだけで全Android機能の適合とはしません。

§7.7の依頼者確認済み解釈を継続し、Dがrecovery envelopeを露出する/log・/receiptsは403です。
代替のredacted wire/APIは追加しません。Cloudflareは変更しません。
公開側のTLS 1.1受理は§17.1の残事項であり、公開環境全体の完全適合は宣言しません。
クライアントのtransportはTLS 1.2以上です。

実Android試験で発見したhealthのメディアタイプ不一致を§0/B-01に従って修正しました。
privacy取得に先立つhealthはv3メディアタイプ、privacy.jsonだけは静的公開文書のapplication/jsonです。
Android試験の自己署名CA・loopback originは明示的な使い捨てtransportに閉じ、本番とCloudflareは変更していません。

§7.11.3/§18.6のOS認証3試験は33.637秒で成功しました（app/build/pallasync-recovery-auth-events-android.log）。同じPIN padがreadbackの2回目の認証で連続表示されるため、消失を待つ旧テストは1ケース失敗しました。OSの成功callbackの観測だけで同期を修正し、認証済みCryptoObjectによる実doFinalを引き続き要求しています。productionのProcessLifecycle監視を有効にしたまま、認証中のroot操作が維持されることも確認しました。この試験は全OS/バックグラウンド遷移の代替ではありません。

§18.7–§18.9の公開セット一覧、紛失/漏洩の明示記録、H=tの次の紛失警告、H<tの利用不能表示、未完了/署名fork表示を設定画面へ接続しています。Hはこの端末の保存/readback・紛失証拠に基づき、他holderの最新可用性を保証しません。既存の署名済みpackageから公開状態を取り込んでも、credential/readback未確認の共有を利用可能として数えません。§8.6の厳密な許可metadataだけを同じchainへ監査journal保存し、監査失敗を安全操作の前提にしません。ローカルpackage消去/交換とOS鍵消去の間の中断は、秘密を含まないalias削除記録から再開します。この追加部分を含むhost全441試験（PallaSync対象104、failure/error/skip 0）、detekt/ktlint、3 ABI/universal/test APK生成は成功しました（app/build/pallasync-recovery-management-checks.log）。実AndroidはOS認証3試験とH=1/H=t/H>t・明示ボタン/処理中禁止の表示2試験、計5試験が38.318秒で成功しました（app/build/pallasync-recovery-management-android.log）。表示試験には公開の模擬状態を使い、署名・物理独立性の検証と混同しません。APKのSHA-256はapp/build/pallasync-recovery-management-apk-metadata.jsonへ保存し、final_delivery=falseの検証用artifactとしています。セット作成・転送・署名済み退役伝播・復旧操作選択の全product UIと全受入条件は未完了です。


復旧状態画面を含むAPKのAndroid/Python/隔離リレー対象試験は、Android側222.013秒と独立Pythonの全後段確認で成功しました（PallaSync/target/android-recovery-management-peer-interop.log）。3端末のepoch bundle、HPKE/復旧envelope/commitment、元receipt/H/T、peer承認、旧epoch outboxの再作成を確認しています。全ITや復旧媒体の実三者ceremonyの完了には置き換えません。
§8.6の個別紛失監査では対象のholder_kind=device/offlineだけを含め、共有IDは含めません。この修正はInventoryの実JNI/host対象2試験とdetekt/ktlintで成功しました（app/build/pallasync-recovery-audit-kind-checks.log）。その後追加した退役署名・署名済みpublic status-arrayのローカル媒体読込/保存・既知fork保持・holderへの状態反映と再起動時の反映再開は、検証中です。公開statusは原署名対象だけを使い、新しいRecoveryTransfer.message_kindや暗号方式を追加しません。署名ステップの完了は、全媒体への伝播・消去完了や残された旧コピーの失効を意味しません。

公開status追加部分はhost全444試験と静的検査で成功しました（app/build/pallasync-recovery-public-status-checks.log）。その後の状態表示順序修正を含むAPKでAndroid対象6試験が44.575秒で成功しました（app/build/pallasync-recovery-public-status-native-android.log）。新しい1試験は実JNIの退役署名、試験専用の内部ファイルreadback、保護prefsの再読込を確認します。実SAF picker・物理媒体・全holder伝播・process killの検証ではありません。最新5 APKのhashはapp/build/pallasync-recovery-public-status-native-apk-metadata.jsonに記録し、final_delivery=falseです。全受入条件は引き続き未完了です。

§5.6/§7.11.5/§18.6/§18.9の明示的なt/n設定、物理holder IDの選択・重複拒否、オフライン媒体なしのリスク確認を作成画面へ追加しました。ローカル入出力ファイルは秘密セッション開始前に選択します。dealerの公開descriptor QR、両端末SAS確認、暗号化した元transferのファイル交換、全n読戻し、別reader/holderからのlive閾値試験、有効化を接続しました。未確認SASの試験期待値をC_RECOVERY_AUTH_REQUIREDへ修正し、全449 host試験・detekt/ktlint・3 ABI/universal/test APK生成が成功しました（app/build/pallasync-recovery-creation-verified-checks.log）。同じAPKでAndroid対象8試験が40.057秒で成功しました（app/build/pallasync-recovery-creation-android.log）。新規2試験はAndroid JNIのdealer stage、保護された公開prefs、成功後の秘密owner終了と取消を検証し、reader peer/媒体readbackはRAM内の模擬です。実SAF picker・物理媒体・端末間の完全なhuman ceremony・全ITの成功を意味しません。holder/reader受取・復旧・交換・新root移行の全画面と、全受入条件は未完了です。有効化成功時はroot/dealer/collector/pairを直ちに閉じ、元の署名済み公開状態だけを残します。中断時はprepared/public evidenceを保持し、秘密状態からの再開を作りません。

§7.11.1/§18.9のholder保存forkを既知の公開inventoryにも反映する修正は、Inventory/PublicStatus/DealerWorkflow/Configurationの対象11試験とdetekt/ktlintで成功しました（app/build/pallasync-recovery-inventory-fork-checks.log）。§7.11.3–§7.11.5/§18.6の受取サービスを追加し、pinned chain/admin・元holder binding・prepared状態・両端末SASを確認してから受理します。端末保管はOS認証付きの保存/readback、offlineは別credentialで媒体readbackを検証してから元のACKを返します。このサービス追加時点の全453 host試験（PallaSync対象116、failure/error/skip 0）と静的検査は成功しました（app/build/pallasync-recovery-recipient-checks.log）。その後追加した原packet再送cacheと実Androidの端末受取試験は検証中です。受取サービスのproduct UIは未接続であり、全機能完了は宣言しません。

受取サービスの再送cache追加後は関連14 host試験（failure/error/skip 0）・detekt/ktlint・3 ABI/universal/test APK生成が成功しました（app/build/pallasync-recovery-recipient-verified-checks.log）。最新APKでAndroid対象9試験が53.495秒で成功しました（app/build/pallasync-recovery-recipient-android.log）。追加の端末受取試験では新しいRecipientを経由し、実Keystoreの2回のOS認証、保護保存/readback、再読込、秘密pair終了後の元ACK再利用を確認しました。模擬peer/媒体を使うdealer試験と、実OS認証を使うholder試験の範囲を分けて記録します。最新5 APKのhashはapp/build/pallasync-recovery-recipient-apk-metadata.jsonへ保存し、final_delivery=falseです。productの受取/reader/復旧/交換/新root移行画面、全A/C/D/IT、stress/障害試験、本番更新、PR作成は未完了です。

§7.11.3–§7.11.5/§18.6/§18.9の受取画面を設定から接続しました。ローカルの原manifest/status/descriptorと入出力ファイルを秘密セッション開始前に選び、固定chain/admin/generation/holder/deviceを検証します。端末側は既存共有の明示置換、保護クラス表示、両端SAS、OS保存と新規認証readbackを要求します。offline側は別credentialで保護したPSR3媒体を実ファイルから再読取りした後だけACKを作ります。IO再送は原ciphertextを維持し、ACK完成時に秘密pairを終了します。background/cancel/期限で秘密を閉じ、package/保管済み共有は保持します。対象host試験、静的検査、APKとcredential実Android表示試験を検証中です。SAF/物理媒体・複製holder・全復旧操作・全三者ITの成功はまだ記録していません。

受取追加部分はhost対象9件（configuration 3、dealer 2、recipient 4、failure/error/skip 0）、detekt/ktlint、3 ABI/universal/test APK生成で成功しました（app/build/pallasync-recovery-receive-verified-checks.log）。実Android対象11件が58.329秒で成功しました（app/build/pallasync-recovery-receive-android.log）。追加2件は実Compose/Window/Lifecycle上でcredentialの正確なmutable引渡し、送信後の空欄、背景移動時のdismissと再表示時の空欄を確認しています。SAF pickerと実媒体を使う完全ceremony試験とは別です。5 APKのhashはapp/build/pallasync-recovery-receive-apk-metadata.jsonに記録し、final_delivery=falseです。最新全host試験453件の記録は以前のrecipient段階のsourceに対するものであり、今回の最新sourceの対象9件と区別します。

§7.11.4–§7.11.5/§18.7/§18.9の保管端末・別readerからの共有解放画面を接続しました。選択したcollector descriptorと原packageを固定chain/admin/generation/既知statusに照合し、preparedはcreate/rotateのthreshold試験だけに限定します。SASとrecipient/purposeを表示し、明示的な解放同意と毎回のOS認証または別credentialを要求します。readerが他共有も持つリスクを事前表示し、解除入力とRSWKを消去します。元collect_share ciphertextだけをIO再送でき、保管済みpackageは保持します。host全試験・静的検査・APK生成を実行中です。復旧collectorの全操作選択、実SAF媒体、OS解放ownerの途中取消、独立Pythonとの復旧三者ceremonyと全ITはまだ完了扱いにしません。

共有解放追加部分は全host456件（PallaSync対象119件、failure/error/skip 0）、detekt/ktlint、3 ABI/universal/test APK生成が成功しました（app/build/pallasync-recovery-release-checks.log）。実Android対象11件が60.391秒で成功しました（app/build/pallasync-recovery-release-android.log）。dealerの閾値試験2件は新しいreader ownerを使い、native解除入力消去、1共有の解放、原ciphertext再送、collector distinct countを実Android JNIで確認しています。package媒体はRAM模擬であり、実SAF・独立Python・物理媒体の完全試験ではありません。5 APKのhashはapp/build/pallasync-recovery-release-apk-metadata.jsonへ保存し、final_delivery=falseです。

§18.6–§18.8の置換ギャップ表示では、保護slotに残る原public evidenceと以前のreadback record_hashを照合してから、既に除去したローカル旧共有をHから外します。原manifest/status/hashは保持し、再観測・再起動で利用可能へ戻しません。§5.6/§7.11/§18.8に従い、現chainのgeneration更新だけで元の有効setを再生成・失効させません。解放pairは選択した元manifestのgenerationへnative署名/HPKE/SASで固定し、後のroot管理操作は現行authorityと別に照合する必要があります。この修正の対象試験を実行中です。

置換後H表示とgeneration維持修正は対象host13件（Inventory 4、Recipient/Reader 6、PublicStatus 3、failure/error/skip 0）、detekt/ktlint、3 ABI/universal/test APK生成で成功しました（app/build/pallasync-recovery-replacement-checks.log）。実Android対象11件は61.467秒で成功しました（app/build/pallasync-recovery-replacement-android.log）。Readerは元generationのsetでthreshold転送が成立することも確認します。全456件の記録はこの修正直前のsourceに対するもので、最新の対象13件とは分けています。最新5 APKのhashはapp/build/pallasync-recovery-replacement-apk-metadata.jsonへ記録し、final_delivery=falseです。

§5.5/§11.4/§18.7のcollector画面から、選択したsetの無効化または手動admin epoch更新へ型付きnative rootを接続しました。共有ごとのSAS・認証、distinct count、exact packet hashによる再計数防止、明示的な閾値確認を経て、rootを指定操作だけで消費します。取消・背景移行ではcollectorと未消費completionを閉じ、消費後はRootCancellationをDB/ネットワークの最初のsuspend前に接続します。原package・公開set証拠は消去しません。手動更新は現authority/chain/admin、同意、CAS/H/T/original receiptの既存検証を使用します。最初のhost collector対象試験は16件中1件がRobolectric/parent JNI間のRootSession classloader不一致で失敗しました。RootSession/Operation/Identity/serializerを共通loaderへ修正し、全host試験と新しい実Android native collector試験を検証中です。実SAF・全管理操作・復旧経由epochの三者HTTP・プロセスkill・全50 ITは未完了です。

collectorのclassloader修正後はhost全460件（PallaSync対象123件、failure/error/skip 0）、detekt/ktlint、3 ABI/universal/test APK生成が成功しました（app/build/pallasync-recovery-collector-verified-checks.log）。実Android12件は61.454秒で成功しました（app/build/pallasync-recovery-collector-android.log）。新しい1件は実JNI/protected public storeでactive setの2共有を収集し、typed rootを無効化操作へ渡してretired statusへ署名し、終了後のroot利用を拒否します。これもRAM媒体と模擬独立endpointであり、SAF・実物理媒体・全三者ITの代替ではありません。最新5 APKのhashはapp/build/pallasync-recovery-collector-apk-metadata.jsonへ記録し、final_delivery=falseです。

§7.11.5のUI受取/collector cacheではnativeのstrict parserを先に通し、原wire bytesのSHA-256、transfer_id、方向別session/sender/message_seqを、署名と処理に成功した後だけ保持します。原packetの再送は元の公開/ciphertext応答を返し、古いpacketへ現時点のcountを返しません。同じID/seqの変更bytesは、秘密pairを終了した後やstage遷移後にもC_RECOVERY_SHARE_INVALIDで拒否します。1024 packetのローカルRAM上限ではC_LOCAL_LIMITで止め、媒体を削除しません。送受信は直列化し、処理中のUI二重実行を防ぎます。この追加部分を対象試験で検証中です。

exact retry/ID/sequence補強はhost対象18件（Collector 4、Inventory 4、Recipient/Reader 7、PublicStatus 3、failure/error/skip 0）、detekt/ktlint、3 ABI/universal/test APK生成で成功しました（app/build/pallasync-recovery-replay-checks.log）。実Android対象12件は60.613秒で成功しました（app/build/pallasync-recovery-replay-android.log）。全460件の記録は再送補強直前のsource、最新の対象18件は補強後のsourceです。最新5 APKのhashはapp/build/pallasync-recovery-replay-apk-metadata.jsonへ記録し、final_delivery=falseです。全適合試験・最終APK・本番更新・新規PRは引き続き未完了です。

§7.11.1／§19.4に従い、同じ既知statusから分岐した複数の署名済みstatus chainを、端末内の保護ストアで追記・重複排除して保持するよう変更しました。元のaccepted chainと最初の矛盾証拠を上書きしません。holder消去後の公開証拠、再インポート、inventoryからholderへの伝播も同じ証拠集合を保持します。wire objectへのフィールド追加はありません。新しいhost試験は、複数の有効なadmin署名、exact再送、再open、消去／再import、不正statusによる既存証拠変更の拒否を対象にしています。全体テストの実行結果は確定後に追記します。

複数分岐の保存修正後、Kotlin全464件（PallaSync対象127件、failure/error/skip 0）、detekt／ktlintが成功しました（app/build/pallasync-recovery-fork-checks.log、8分38秒）。追加のAndroid試験は別に実行し、実際の結果を区別して記録します。これは全IT・本番確認の完了を意味しません。

実Androidの対象2件は2.434秒で成功しました（app/build/pallasync-recovery-fork-android.log）。追加1件では実JNIで同じprepared statusから2つの異なるcompromised successorに署名し、元の受理済みretired chainを変更せず、双方の矛盾証拠をEncryptedSharedPreferencesへ保持します。再openと重複再送、不正statusの拒否も検証しました。これはOSプロセスkillや全復旧三者ITの試験ではありません。detekt／ktlint、3 ABI／universal／test APK生成も成功しました（app/build/pallasync-recovery-fork-android-build.log、54秒）。最新5 APKのhashはapp/build/pallasync-recovery-fork-apk-metadata.jsonへ記録し、final_delivery=falseです。前段のAndroid12件は以前のsourceに対する検証として区別します。

§8.6／§11.4／§18.6–§18.7の復旧監査を、作成・holder readback・閾値テスト・collectorの開始／選択操作の成功／失敗／取消・紛失／漏洩へ接続しました。保護prefsの待機キューに保存するのは許可された監査メタデータだけです。未登録／別chainのwriterへは渡さず、Roomへの引渡しはevent UUIDと原serialization hashの同一transaction markerで重複防止します。Occurrence時刻をOperation.created_at_msへ保持します。監査の失敗はroot消去／安全上の操作の前提条件にしません。cleanupの一部が失敗しても、後続の所有resourceはすべてcloseします。

Nativeのaudit schemaではwhole-set／ceremonyのholder_kind=nullを§8.6どおり検査します。Rust全62件、fmt／clippy、関連Kotlin20件（failure/error/skip 0）、detekt／ktlintが成功しました。ログはrust/pallasync-core/target/recovery-audit-tests-fixed.logとapp/build/pallasync-recovery-audit-checks-fixed.log（5分30秒）です。初回のRust検証はcheckoutによる署名済みprivacy.mdのCRLF変換で失敗しました。元のgit blobが署名済みmarkdown_sha256と一致することを確かめてbytesを復元し、.gitattributesで変換を防止しました。初回のKotlin検査はMagicNumberで失敗し、定数へ修正しています。検証を緩めたり、失敗を成功と扱ったりしていません。

実Androidの新しい1件は1.595秒で成功しました（app/build/pallasync-recovery-audit-android.log）。実JNI／EncryptedSharedPreferences／Roomで、引渡しcommit直後の中断とstore再openを経て、同じUUID・原body・発生時刻を一度だけ署名し、author sequence／outboxが1件であることを確認しています。Roomはin-memoryであり、実プロセスkill・ディスク障害・三者HTTPの試験ではありません。3 ABI／universal／test APK生成と静的検査も成功しました（app/build/pallasync-recovery-audit-android-build.log、3分）。最新APKは最終配布版ではありません。

独立Pythonには同じ§8.6の許可listを別実装で追加しました。Rust／JNIを使用せず、秘密field・holder ID・誤count・whole-setのholder_kind・不正時刻を署名／author sequence更新前に拒否し、元UUID／時刻のEd25519／AEAD roundtripを確認します。参照unit suite全25件が1.003秒で成功しました（PallaSync/target/recovery-audit-reference-tests.log）。これは全50 ITや完全適合の証明ではありません。

追加実装直前には最新main（27b90453）のGradle 9.8.1を取り込み、Kotlin全464件／静的検査／APK生成が13分12秒で成功しました（app/build/qualification-kotlin-build.log）。現行の関連20件とはsourceとscopeを区別します。全適合、未完了の管理・移行・snapshot機能、本番更新と新規PRは引き続き未完了です。

§18.7のrecovery set置換を追加しました。新setの全readback・閾値試験・有効化が先行し、その後にだけ旧setを新set ID付きretiredへ署名します。途中終了後は、保護領域へshare/RMS/rootを保存せず、activeな新setと同chain/adminを確認し、fresh rootを再入力して旧statusの署名を完了する画面を追加しました。old setのfork・compromise・別置換途中とCAS不一致は拒否します。置換workflow host試験を含め、469件（PallaSync対象132件、failure/error/skip 0）、detekt／ktlint、debug appとandroidTest APK生成が成功しました（app/build/pallasync-recovery-resume-final.log）。最終APKで専用AVD instrumentation 25件も成功しました（app/build/pallasync-recovery-resume-final-instrumentation.log）。この再開処理は公開statusのみを確定し、全offline holderへ新statusが配布済みであることは保証しません。

§10.15／§17.7／§17.11の端末削除は、DELETE後に認証付きGET `/pallasync/v3/chains/{chain_id}/deletion` から取得した署名証明をnative verifierで検証し、端末内の公開証拠領域へ保存してから進みます。検証済み証明の保存直後に同期を停止し、未完了の証明がある間はchain再開を拒否します。ユーザーが保存先を選び、書出し後に同じbytesを読み戻して一致を確認し、独立したオフライン媒体への保存を明示確認した場合だけchain-local key/stateを消去します。picker取消、保存/readback失敗、画面の確認取消では端末のkey/stateを消去せず、設定画面から再試行できます。Kotlin compile、detekt、ktlintと対象Room host testは通過しました。実リレーに対する削除E2E、SAF pickerの実端末操作、物理的に独立した媒体の確認は未実施です。

§10.7／§18.1の承認端末側入口として、端末一覧から既存のpeer-offer serviceを呼び出し、リレーへ登録した期限付きofferのQRを表示するUIを接続しました。Kotlin compile、detekt、ktlintと`PallaSyncPeerJournalTest`が通過しました。これはQR作成側の入口までです。新端末でのQR scan・source/identity表示・request作成、2端末のSAS確認、signed certificate/envelopeのローカルfile transfer、鍵readback後のack UIは未接続であり、peerの全承認フロー適合を主張しません。

置換host試験は「新status active・replacement pending」の永続commit直後にroot ownerをcloseし、その後fresh native root sessionでstore CASを再開する境界も検証します（3つのrecovery dealer workflow testが成功。詳細log: app/build/pallasync-recovery-replacement-resume-host.log）。これはOSプロセスkillや物理ディスク障害注入ではありません。

Draft PR CIでJNI declaration checkが失敗したため確認しました。Rust JNI exportは`ffi_jni.rs`以外の`src/**/*.rs`にも分割されていますが、check scriptが単一ファイルしか走査していませんでした。scannerを全Rust sourceへ拡張し、Kotlin external 103件とRust export 103件、12個のビルド済みshared librariesのsymbol inspectionがローカルで成功しました。

Secret Scanの`generic-api-key`検出は固定公開テストベクターのfieldだけでした。値は表示せず、15個の正確なJSON/markdown fixture pathに限る`.gitleaks.toml` allowlistを加え、他のpathとruleは標準scanに残します。TOML parseとdiff checkは成功し、CI scan結果を次commitで確認します。

CI ASanがexpiry中にholderのlocked RSWK stateが残るraceを検出しました。並列unit testの`local_transfer::end_all`がsessionを削除する時も、対応するholder handleを収集してRSWK holder stateを破棄するよう修正しました。Rust lib 6件、package全体tests、clippyが成功し、CI ASanとMiriも修正commit上で成功しました。

同じCIでRustSec監査がquinn-proto 0.11.16の5 advisoryを報告しました。pixiv API crateのlockfileを修正済み0.11.18へ更新し、package test 40件が通過しました。RustSec advisoryのpatched versionが0.11.17/0.11.18であることは[0.11.18対象のRustSec notice](https://rustsec.org/advisories/RUSTSEC-2026-0338.html) と [0.11.17対象のRustSec notice](https://rustsec.org/advisories/RUSTSEC-2026-0342.html) に対応します。dependency auditの再確認はpush後に行います。

同じsource/APKの独立Python+Android interopは190.27秒で成功しました（PallaSync/target/qualification-android-resume-interop.log）。実Android端末承認・JNI/Room・HTTP、独立PythonによるSAS/署名/HPKE検証、epoch bundle/recovery envelope/receipt、epoch再base、initial sync/outbox、application payload boundary、origin bindingを確認しました。隔離relayを使ったこれらの対象試験は全50 ITではありません。

全適合は未達です。IT-01〜IT-50、Appendix A/C/D全体、三者独立復旧・完全な移行／snapshot・大規模履歴・物理holderの独立保管・実ディスク障害試験、本番更新は未完了です。v2.1 ChainParameters.signatureの検証方式も§4.1に定義がないため追加していません。`docs/pallasync-v3-acceptance.json`の`conformance_claim`はfalseのまま維持します。

§7.12.3/§7.12.5/§17.11のoptional public deletion evidenceとして、immutable DeleteReceiptと独立したDeletionEvidenceBundleを生成・native検証し、仕様例の`{delete_receipt,evidence_bundle}` archive wrapperを保護領域に保持して別ファイルへ書き出す経路を追加しました。receipt全体のhash、relay署名、authorized parameters/epoch、witnessのreceipt binding、承認epoch membership・端末登録/失効境界・certified signing key・署名contextをRustで検証します。現行UIのbundleはwitness 0件です。端末間witness署名ceremony、bundle import、128件境界、completion revisionとIT-44の実行、実SAF端末試験は未実施です。Rust host `cargo check`とlib 6件は成功しました。Androidの変更後compile/lintは実行中のGradle結果を確認してから記録します。
