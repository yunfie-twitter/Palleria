---
title: PallaSync 同期エンジン仕様
---

# PallaSync 同期エンジン仕様

## 対象データとプロトコル

`PallaSyncSettingsEvents.kt` が生成する対象は、お気に入りタグ、検索履歴、タグ・ユーザー・作品ミュート、既読フィード作品、閲覧履歴です。テーマや認証トークン、保存画像を含むAppSettings全体を複製する実装ではありません。

`PallaSyncWireModels.kt` の現行プロトコルは **2.1**、旧版 **2.0** も受け付けます。受信ページサイズは200件です。イベントにはスキーマ、エンティティID、`upsert` / `delete` 操作を持たせます。

## 責務

| 実装 | 役割 |
| --- | --- |
| `PalleriaSyncManager` / `PalleriaSyncCoordinator` | チェーン作成・参加、復旧、同期要求、送受信の実行 |
| `PallaSyncSettingsEvents` / `PallaSyncEventWriter` | ローカル差分からイベントを生成し送信キューへ保存 |
| `PallaSyncEventApplier` | 受信イベントを設定・コレクションへ適用 |
| `PallaSyncConflictResolver` | バージョンベクトルと決定的な順序比較 |
| `PallaSyncKeystore` | チェーンの鍵素材をEncryptedSharedPreferencesへ保存 |
| `rust/pallasync-core` | レコードの暗号化・復号・署名検証など。AndroidからJNIで利用 |

## 暗号化の区別

通信レコードの2.1形式は **XChaCha20-Poly1305** を使い、24バイトのnonceを扱います。復号側は旧2.0のChaCha20-Poly1305形式も処理します。署名にはEd25519を使用します。

Android側の鍵保存に使うAES256-GCM / AES256-SIV（EncryptedSharedPreferences）と、通信本文の暗号方式は別です。同期サーバーへ送る本文は暗号化しますが、チェーンIDや配送・順序制御の情報をすべて隠す設計ではありません。

## 競合と耐久性

`compareVectors` は端末ごとのカウンターで因果関係を比較します。同時更新の比較は **`(lamport, deviceId, deviceSeq)`** の順序で行います。端末の壁時計の時刻だけで勝敗を決める方式ではありません。

Roomにはoutbox、チェーン状態、端末、inboxを保存します。relay cursorとLamport clockを保持し、受信データをinboxへ耐久保存してから適用します。復号・検証・スキーマ処理に失敗した受信項目は隔離します。初回pull完了前の変更は `pending_initial_merge` として待機します。

実装: [同期パッケージ](https://github.com/yunfie-twitter/Palleria/tree/main/app/src/main/java/com/yunfie/illustia/pallasync)、[Rust暗号処理](https://github.com/yunfie-twitter/Palleria/blob/main/rust/pallasync-core/src/crypto.rs)。
