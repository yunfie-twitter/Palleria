---
title: コントリビューション
description: PalleriaへIssueやPull Requestを送る前の確認事項。
---

# コントリビューション

不具合報告、機能提案、ドキュメント改善、Pull Requestを歓迎します。

## Issueを作成する前に

1. 既存のIssueとPull Requestを検索する
2. 最新版でも再現するか確認する
3. 再現手順を最小化する
4. アプリとAndroidのバージョンを記載する
5. 必要に応じて、機密情報を除いたスクリーンショットやログを添付する

::: danger 機密情報を含めない
パスワード、Cookie、リフレッシュトークン、署名鍵、個人情報はIssue、Pull Request、ログへ含めないでください。
:::

## Pull Request

- 変更目的を1つに絞る
- 既存のコードスタイルと設計パターンに合わせる
- UI変更ではMiuixコンポーネントとテーマを使用する
- 変更に応じたテストを追加・更新する
- UI変更には可能な範囲でスクリーンショットを添付する
- 動作確認した内容を説明へ記載する

## ローカル確認

Android側の変更:

```bash
./gradlew test
./gradlew :app:assembleDebug
```

ドキュメントの変更:

```bash
npm run docs:build
```

## ライセンス

コントリビューションはプロジェクトと同じGNU General Public License v3.0の下で提供されます。
