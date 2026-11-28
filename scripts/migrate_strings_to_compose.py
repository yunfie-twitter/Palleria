#!/usr/bin/env python3
"""Migrate a group of Android string resources into Compose Resources.

Moves every key matching the given prefixes from androidApp/src/main/res/values*/strings.xml
into composeApp/src/commonMain/composeResources/<locale>/strings.xml and prunes the originals.
"""
import argparse
import os
import re
import sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ANDROID_RES = os.path.join(REPO, "androidApp", "src", "main", "res")
COMPOSE_RES = os.path.join(REPO, "composeApp", "src", "commonMain", "composeResources")

STRING_RE = re.compile(r'^\s*<string name="([^"]+)"([^>]*)>(.*)</string>\s*$')
NAME_RE = re.compile(r'^\s*<string name="([^"]+)"')


def locale_dirs():
    out = ["values"]
    for name in sorted(os.listdir(ANDROID_RES)):
        if name.startswith("values-") and "night" not in name and "v31" not in name:
            if os.path.isdir(os.path.join(ANDROID_RES, name)):
                out.append(name)
    return out


def read_strings(path):
    entries = {}
    if not os.path.exists(path):
        return entries
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            m = STRING_RE.match(line.rstrip("\n"))
            if m:
                entries[m.group(1)] = (m.group(2).strip(), m.group(3))
    return entries


def write_strings(path, entries):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    lines = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>"]
    for key in sorted(entries):
        attrs, value = entries[key]
        attr_text = f" {attrs}" if attrs else ""
        lines.append(f'    <string name="{key}"{attr_text}>{value}</string>')
    lines.append("</resources>")
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write("\n".join(lines) + "\n")


def prune(path, keys):
    with open(path, encoding="utf-8") as fh:
        lines = fh.readlines()
    kept, removed = [], 0
    for line in lines:
        m = NAME_RE.match(line)
        if m and m.group(1) in keys:
            removed += 1
            continue
        kept.append(line)
    if removed:
        with open(path, "w", encoding="utf-8", newline="\n") as fh:
            fh.writelines(kept)
    return removed


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("prefixes", nargs="+")
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    base = read_strings(os.path.join(ANDROID_RES, "values", "strings.xml"))
    keys = {
        k
        for k in base
        if any(k == p or k.startswith(p + "_") for p in args.prefixes)
    }
    if not keys:
        print("no keys matched", file=sys.stderr)
        return 1

    print(f"target keys: {len(keys)}")
    for loc in locale_dirs():
        src_path = os.path.join(ANDROID_RES, loc, "strings.xml")
        if not os.path.exists(src_path):
            continue
        moved = {k: v for k, v in read_strings(src_path).items() if k in keys}
        if not moved:
            continue
        dst_path = os.path.join(COMPOSE_RES, loc, "strings.xml")
        merged = read_strings(dst_path)
        merged.update(moved)
        if not args.dry_run:
            write_strings(dst_path, merged)
        print(f"{loc}: +{len(moved)} (total {len(merged)})")

    total_removed = 0
    for loc in locale_dirs():
        src_path = os.path.join(ANDROID_RES, loc, "strings.xml")
        if not os.path.exists(src_path):
            continue
        if args.dry_run:
            total_removed += sum(
                1 for k in read_strings(src_path) if k in keys
            )
        else:
            total_removed += prune(src_path, keys)
    print(f"pruned from androidApp: {total_removed}")
    for k in sorted(keys):
        print(f"KEY {k}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
