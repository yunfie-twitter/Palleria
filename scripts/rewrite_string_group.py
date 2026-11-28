#!/usr/bin/env python3
"""Rewrite Compose-context R.string.<group>_* usages to Compose Resources.

Handles plain calls and positional-argument calls; refuses anything it cannot
rewrite confidently (getString, plurals, non-Compose contexts) and reports them.
"""
import argparse
import os
import re

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(REPO, "androidApp", "src", "main", "java")

PLAIN_RE = re.compile(r"stringResource\(R\.string\.(\w+)\)")
ARGS_RE = re.compile(r"stringResource\(R\.string\.(\w+),\s*([^()]*(?:\([^()]*\)[^()]*)*)\)")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("group")
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    prefix = args.group + "_"
    changed_files = []
    skipped = []

    for root, _, files in os.walk(SRC):
        for name in files:
            if not name.endswith(".kt"):
                continue
            path = os.path.join(root, name)
            text = open(path, encoding="utf-8").read()
            if f"R.string.{prefix}" not in text:
                continue

            original = text
            keys = set()

            def plain(m):
                key = m.group(1)
                if not key.startswith(prefix):
                    return m.group(0)
                keys.add(key)
                return f"resString(Res.string.{key})"

            def with_args(m):
                key = m.group(1)
                if not key.startswith(prefix):
                    return m.group(0)
                keys.add(key)
                return f"resString(Res.string.{key}, {m.group(2)})"

            text = ARGS_RE.sub(with_args, text)
            text = PLAIN_RE.sub(plain, text)

            if text == original:
                continue

            # 残った参照（変数へ代入するだけ等）を報告
            leftover = re.findall(rf"R\.string\.{prefix}\w+", text)
            if leftover:
                skipped.append((os.path.relpath(path, REPO), sorted(set(leftover))))

            if "import com.yunfie.illustia.resources.Res" not in text:
                lines = text.split("\n")
                idx = max(i for i, l in enumerate(lines) if l.startswith("import "))
                lines.insert(idx + 1, "import com.yunfie.illustia.resources.Res")
                text = "\n".join(lines)
            if "as resString" not in text:
                lines = text.split("\n")
                idx = max(i for i, l in enumerate(lines) if l.startswith("import "))
                lines.insert(idx + 1, "import org.jetbrains.compose.resources.stringResource as resString")
                text = "\n".join(lines)

            for key in sorted(keys):
                if f"import com.yunfie.illustia.resources.{key}" not in text:
                    lines = text.split("\n")
                    idx = max(i for i, l in enumerate(lines) if l.startswith("import com.yunfie.illustia.resources."))
                    lines.insert(idx + 1, f"import com.yunfie.illustia.resources.{key}")
                    text = "\n".join(lines)

            if not args.dry_run:
                open(path, "w", encoding="utf-8", newline="\n").write(text)
            changed_files.append((os.path.relpath(path, REPO), sorted(keys)))

    for path, keys in changed_files:
        print(f"rewrote {path}: {len(keys)} keys")
    if skipped:
        print("\nNOT REWRITTEN (needs manual handling):")
        for path, keys in skipped:
            print(f"  {path}: {', '.join(keys)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
