---
title: PallaSync v3 接続不具合の修正
---

# PallaSync v3 接続不具合の修正

規範は PALLASYNC-3.0-R1 の `v3.0.md` です。この記録は仕様を追加・変更しません。

2026-10-12 JST、v7.0.0-beta.1 のリリースコードで、`/pallasync/v3/chains` に送る Content-Type と Accept が `application/vnd.palleria.sync.v2+json` のまま残っていました。公開リレー `https://api.yunfi.f5.si` でも同形式の匿名・空の登録要求は `415 UNSUPPORTED_MEDIA_TYPE` となりました。リレーは仕様どおり拒否しており、v2の互換APIを追加して解決しません。

| 修正 | 仕様の根拠 |
| --- | --- |
| Content-Type / Accept を既存のv3定数へ統一 | §0、§3.1、§10 |
| healthの必須項目、実際のprotocol、origin、公開鍵のfingerprint、上限を検査。存在しないstatus/protocol_versionをデフォルトで補わない | §10.1、§17.8、§17.9 |
| 設定URLをHTTPS originに制限し、API pathの二重連結を防止 | §6.1、§10、§17.1 |
| 新規SyncRecord IDをUUIDv4に変更 | §3.4、§7.1 |
| デバイス一覧のitems/device_recordラッパーを解析。statusを署名済みobjectと分離し、cursorを含む実際のqueryへtokenを結び付け、全pageの取得後に一覧を更新 | §6.2、§10.5、§10.16 |
| レコード数とUTF-8 bodyのbytesをhealth上限内へ分割 | §10.6、§17.8 |
| HTTP 200のaccepted/rejectedを検査。全IDの結果と元objectが一致するreceiptを確認し、acceptedだけをoutboxから削除。拒否・不明・不正な応答は未受理データを保持 | §10.6、§11.1 |
| 公開pushRecords入口も規定のrecordsラッパーとdevice tokenを送信 | §6、§10.6 |
| 未有効・有効化失敗中も同期ログを表示 | §11.1、§19.3 |

Androidの同期テストに、実際の公開health、v3 registry、UUIDv4、部分拒否・欠落ID・改変receipt、下限化したbatchサイズの回帰ケースを追加しました。PalleriaのRust coreで生成したgenesisとrecordをリレー実装に直接渡し、201登録、200一覧・pull・受理を確認しました。リレー側には公開KDF-01 mnemonicに基づくテスト専用fixtureと回帰テストを追加しています。実ユーザーの鍵やchainは使用していません。

SSHで実機Dockerを確認したところ、`pallasync-pallasync-1` は3090でhealthy、restart countは0でした。origin側とCloudflare経由のhealthはpallasync/3を返し、公開privacy文書は保存した署名検証済みrevisionと一致しています。リレーの認可・wire検査・Cloudflare設定は変更していません。

この修正は接続・登録直後の応答処理に関するものです。クライアント全体の完全準拠を宣言するものではありません。既存コードには、§7.13/§11.7の署名済みprivacy検証と明示同意、§11.6の復旧source選択、§17.9の継続的なrelay-key pin、§17.7の削除権限証拠の検証など、別途実装が必要な部分が残っています。healthのfingerprint一致だけをidentity trustやprivacy同意の代わりに扱いません。

修正前のインストール済みv7.0.0-beta.1にこの変更は反映されません。更新ビルドが必要です。このPCではNDK未導入のためAPK生成は未完了で、接続されたAndroid端末もなかったため実端末の有効化操作は検証していません。
