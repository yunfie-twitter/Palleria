---
title: セキュリティ & 電卓パーサー仕様
description: CalculatorEngine の構文解析パーサー、パターンC解錠、FLAG_SECURE
---

# セキュリティ & 電卓パーサー仕様

---

## 演算評価エンジン (`CalculatorEngine`)

- **字句解析 (`tokenize`)**: 入力文字列から数値および演算子 (`+`, `-`, `*`, `/`) を抽出。
- **再帰降下パーサー (Recursive Descent Parser)**:
  - `parseAddSub`: 加算・減算の評価
  - `parseMulDiv`: 乗算・除算の評価（内部ではゼロ除算を `NaN` とし、公開関数 `evaluate` は `null` に変換）
  - `parseUnary`: 単項符号の評価
  - `parsePrimary`: 数値リテラルの評価

---

## ステルス解錠メカニズム

- **通常解錠**: 設定 PIN コードを入力し、`=` ボタンタップで解除。
- **パターンC (右上隅タップ解錠)**: 右上領域の連続タップで緊急解錠ダイアログ (`OverlayDialog`) 起動。
- **動的コンポーネント切り替え**: `PackageManager.setComponentEnabledSetting` により、アプリ表示名 (`dummyAppName`) およびアイコン (`dummyIconVariant`) を動的変更。

## PINと評価失敗

PIN設定画面は6桁入力を扱います。`CalculatorEngine.evaluate` は空入力、未知の文字、構文エラー、無限大などでも `null` を返します。電卓画面はアプリロックへの入口を変えるもので、DBや画像ファイル全体を暗号化する機能ではありません。
