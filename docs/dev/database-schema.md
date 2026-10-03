---
title: データベースと設定の保存
---

# データベースと設定の保存

## メインのRoom DB

`settings/db/IllustiaDatabase.java` は `illustia.db` を使用し、スキーマ版は **7** です。

| エンティティ | 主な内容 |
| --- | --- |
| `AccountEntity` | userId、name、account、profileImageUrl、position。refreshTokenやisPremium列はない |
| `SearchHistoryEntity` | queryと表示順position |
| `FavoriteTagEntity` | tagと表示順position |
| `ViewHistoryEntity` | 作品ID、タイトル、作者名、画像URL、ページ数、position、ブックマーク・年齢制限・タグ・AI区分など |
| `SavedIllustEntity` / `SavedIllustPageEntity` | 保存作品の情報と各ページのファイル情報 |

`SavedIllustWithPages` は作品とページの関連を読み出すモデルです。これらのDB全体をSQLCipherなどで暗号化する指定はありません。

## PallaSyncのRoom DB

`pallasync/data/PallaSyncDatabase.kt` は `pallasync_database` を使用し、スキーマ版は **6** です。outbox、チェーン状態、参加端末、受信inboxを別々のエンティティで保持します。DB更新時は各Databaseクラスのmigrationとfallback設定を確認してください。

## DataStoreと秘密値

`AppSettings` はアプリの設定モデルです。`SettingsStore` がPreferences DataStore、Room、EncryptedSharedPreferencesの値を集約します。すべてのプロパティがそのままDataStoreに保存されるわけではありません。

一般設定にはテーマ、画質、ダウンロード、ミュート、ロック、PallaSync、テレメトリ等があります。認証トークンなどの秘密値は通常EncryptedSharedPreferencesで保護します。移行・暗号化ストアの初期化失敗時には、既存値を保持して再試行する処理があります。

フィールド追加時は `AppSettings` だけでなく、`settings/store/` の読み書き・移行、Roomへの投影、必要に応じて同期イベントの対象も確認してください。

実装: [設定のDB](https://github.com/yunfie-twitter/Palleria/tree/main/app/src/main/java/com/yunfie/illustia/settings/db)、[保存処理](https://github.com/yunfie-twitter/Palleria/tree/main/app/src/main/java/com/yunfie/illustia/settings/store)。
