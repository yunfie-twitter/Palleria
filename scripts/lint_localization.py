#!/usr/bin/env python3
"""
Android Localization (strings.xml) Linter.

Validates that:
1. All strings.xml files are well-formed XML.
2. Format specifiers (%s, %d, %1$s, etc.) match exactly with the base strings.xml.
3. No duplicate keys exist in the same XML file.
4. Reports missing translation counts and translation coverage per locale.
"""

import os
import re
import sys
import glob
import xml.etree.ElementTree as ET

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8")

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES_DIR = os.path.join(REPO_ROOT, "app", "src", "main", "res")
BASE_STRINGS = os.path.join(RES_DIR, "values", "strings.xml")

# Regex to match Android string format specifiers: %s, %1$s, %d, %1$2.2f, etc.
FORMAT_RE = re.compile(r"%(?:\d+\$)?[-#+ 0,(<]*\d*(?:\.\d+)?[a-zA-Z%]")

def extract_format_tokens(text: str) -> list[str]:
    if not text:
        return []
    # Ignore escaped percent "%%"
    return sorted(t for t in FORMAT_RE.findall(text) if t != "%%")

def parse_strings_file(file_path: str):
    """Parses a strings.xml file and returns a dict: key -> (text, format_tokens)."""
    try:
        tree = ET.parse(file_path)
    except ET.ParseError as e:
        return None, [f"XML parse error: {e}"]

    root = tree.getroot()
    strings_map = {}
    duplicates = []
    seen = set()

    for item in root.findall("string"):
        name = item.get("name")
        if not name:
            continue
        if name in seen:
            duplicates.append(name)
        seen.add(name)
        text = "".join(item.itertext())
        tokens = extract_format_tokens(text)
        strings_map[name] = (text, tokens)

    errors = []
    if duplicates:
        errors.append(f"Duplicate string keys found: {', '.join(duplicates[:5])} (total {len(duplicates)})")

    return strings_map, errors

def main():
    if not os.path.exists(BASE_STRINGS):
        print(f"Error: Base strings file not found at {BASE_STRINGS}", file=sys.stderr)
        sys.exit(1)

    print(f"Loading base strings from: {os.path.relpath(BASE_STRINGS, REPO_ROOT)}")
    base_strings, base_errors = parse_strings_file(BASE_STRINGS)
    if base_strings is None or base_errors:
        print("Fatal error in base strings file:", file=sys.stderr)
        for err in base_errors:
            print(f"  - {err}", file=sys.stderr)
        sys.exit(1)

    base_count = len(base_strings)
    base_format_keys = {k for k, (_, tokens) in base_strings.items() if tokens}
    print(f"Base strings loaded: {base_count} keys ({len(base_format_keys)} format strings)\n")

    locale_files = sorted(glob.glob(os.path.join(RES_DIR, "values-*", "strings.xml")))
    has_fatal_errors = False
    stats = []

    for file_path in locale_files:
        rel_path = os.path.relpath(file_path, REPO_ROOT)
        locale_name = os.path.basename(os.path.dirname(file_path)).replace("values-", "")
        locale_strings, errors = parse_strings_file(file_path)

        if locale_strings is None:
            print(f"❌ {rel_path}:")
            for err in errors:
                print(f"   {err}")
            has_fatal_errors = True
            continue

        format_mismatches = []
        for name, (_, expected_tokens) in base_strings.items():
            if not expected_tokens:
                continue
            if name in locale_strings:
                _, actual_tokens = locale_strings[name]
                if expected_tokens != actual_tokens:
                    format_mismatches.append(
                        f"key '{name}': expected {expected_tokens}, got {actual_tokens}"
                    )

        if format_mismatches or errors:
            print(f"❌ {rel_path} ({locale_name}):")
            for err in errors:
                print(f"   Error: {err}")
            for mismatch in format_mismatches:
                print(f"   Format mismatch: {mismatch}")
            has_fatal_errors = True
        else:
            translated_count = len(locale_strings)
            missing_count = base_count - len(set(locale_strings.keys()) & set(base_strings.keys()))
            percentage = (translated_count / base_count) * 100 if base_count > 0 else 100.0
            stats.append((locale_name, translated_count, missing_count, percentage))
            print(f"✅ {rel_path}: Valid. {translated_count}/{base_count} translated ({percentage:.1f}%)")

    print("\n--- Localization Coverage Summary ---")
    for loc, trans, missing, pct in stats:
        print(f"  - {loc:10}: {trans:4}/{base_count:4} keys ({pct:5.1f}%) | Missing: {missing:3}")

    if has_fatal_errors:
        print("\n❌ Localization lint failed with format or XML errors.", file=sys.stderr)
        sys.exit(1)

    print("\n✅ All localization strings passed syntax and format validation.")

if __name__ == "__main__":
    main()
