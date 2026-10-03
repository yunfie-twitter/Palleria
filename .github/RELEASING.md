# APK Release と F-Droid 更新

## ビルドと手動公開

1. `main` の `app/build.gradle` 更新、または `v*` タグのpushで
   `release-apk.yml` が署名付きAPKをビルドします。`workflow_dispatch` でも再実行できます。
2. `versionName` と一致するタグを作成・検証します。失敗してもタグは削除しません。
   既存タグが別コミットを指している場合は停止します。依存サービス等の一時的な失敗は
   元のrunまたはタグを選んだ手動実行で再試行してください。ソースを修正する場合は
   バージョンを上げるか、管理者がタグの修正を明示的に行ってください。
3. universal / arm64-v8a / armeabi-v7a / x86_64 のAPKが揃った場合のみ、
   **`draft: true`** でReleaseを作成します。既存Draftへの再アップロードは可能ですが、
   公開済みReleaseは変更しません。ビルド実行中にはDraftを公開しないでください。
4. ビルド完了後にGitHubのUIで添付APKを確認し、Draftを手動で公開します。
   DraftのままではF-Droidには追加されません。

## F-Droidへの反映

`fdroid.yml` は `release.published` と `release.deleted` でのみ起動します。
ビルド・タグ作成は行いません。安定版・プレリリースとも公開時に処理します。

- `gh release download --pattern "*.apk"` で全APKを一時ディレクトリに取得します。
  サイズ、提供されるSHA-256、取得前後のasset ID/更新情報を検証し、競合・取得失敗は
  最大3回再試行します。失敗したAPKや別バージョンのAPKへのフォールバックは行いません。
- APKが存在しない公開Release、実行待ち中に削除されたReleaseは正常終了し、デプロイしません。
- メタデータと期待バージョンは対象のタグから取得します。タグのプログラムは実行せず、
  実際のAPKのパッケージID・バージョンとの一致も検証します。
- `CurrentVersion` は残っているAPKのうち最大の `versionCode` を持つ安定版にします。
  公開順によるダウングレードやプレリリースへの意図しない切り替えを防ぎます。
  安定版が残っていなければ `CurrentVersion` を削除し `CurrentVersionCode: 0` にします。
- 公開Releaseを削除すると、`metadata/release-assets.json` の対応表からAPKを削除し、
  推奨バージョンと署名済みインデックスを再生成します。移行前のReleaseは削除イベントの
  asset名を使います。削除イベントにasset情報も対応表もない古いReleaseは手動修復が必要です。
- Draftの削除はGitHub Actionsの `deleted` イベントを発火しませんが、DraftのAPKは
  F-Droidに未登録なので削除処理は不要です。
- デプロイ失敗時は対象イベントのrunを再実行してください。Releaseの添付ファイル編集
  (`edited`) は自動反映対象ではありません。公開APKを差し替える場合は新バージョンを推奨します。

## Pagesと権限

ドキュメントとF-Droidは同じ `gh-pages` ブランチを更新するため、両ジョブで
`gh-pages-publish` と `queue: max` を共有します。タグ別のグループでは共有ブランチへの
同時書き込みを防げません。キューは最大100件で、上限超過のrunは再実行が必要です。
既存サイトを取得できなければ公開を中止し、F-Droid側の削除はPagesにも反映します。

必要なトークン権限は `contents: write` です。Release assetの取得自体は `contents: read`
で可能で、`packages: read` は不要です。Organizationのポリシーでも書き込みが許可されて
いる必要があります。既存の `RELEASE_KEYSTORE_*` / `RELEASE_KEY_*` secretsを引き続き使います。

参考: [Release assetsの権限](https://docs.github.com/en/rest/releases/assets#get-a-release-asset)、
[キューと並列制御](https://docs.github.com/en/actions/how-tos/write-workflows/choose-when-workflows-run/control-workflow-concurrency)、
[Releaseイベント](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#release)。

## ローカル検証

```sh
python -m unittest discover -s .github/scripts -p 'test_*.py' -v
```

actionlint 1.7.12 はGitHubの `concurrency.queue` に未対応です。
公式構文を確認したうえで、このプロパティに対する警告だけを除外して検証します。

```sh
actionlint -ignore 'unexpected key "queue" for "concurrency" section' .github/workflows/{fdroid,release-apk,docs,release-pipeline-tests}.yml
```
