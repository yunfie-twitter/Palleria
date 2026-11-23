#!/usr/bin/env python3
"""Generate FeatureFlagLabels.kt from the enum definition in FeatureFlag.kt."""
import os
import re

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(REPO, "androidApp", "src", "main", "java", "com", "yunfie", "illustia", "settings", "FeatureFlag.kt")
DST = os.path.join(os.path.dirname(SRC), "FeatureFlagLabels.kt")

text = open(SRC, encoding="utf-8").read()
entries = re.findall(
    r"(\w+)\(\s*\n\s*key\s*=\s*\"([^\"]+)\",\s*\n\s*titleRes\s*=\s*R\.string\.(\w+),\s*\n\s*descRes\s*=\s*R\.string\.(\w+)",
    text,
)
if not entries:
    raise SystemExit("no enum entries parsed")

title_lines, desc_lines = [], []
for name, key, title, desc in entries:
    title_lines.append(f"            FeatureFlag.{name} -> Res.string.{title}")
    desc_lines.append(f"            FeatureFlag.{name} -> Res.string.{desc}")

res_names = []
for _, _, title, desc in entries:
    res_names.extend([title, desc])
import_block = "\n".join(
    f"import com.yunfie.illustia.resources.{n}" for n in sorted(set(res_names))
)

body = f'''package com.yunfie.illustia.settings

import com.yunfie.illustia.resources.Res
import org.jetbrains.compose.resources.StringResource
{import_block}

/** 機能フラグの表示ラベル。キーは shared 側の enum が保持する。 */
val FeatureFlag.titleRes: StringResource
    get() =
        when (this) {{
{chr(10).join(title_lines)}
        }}

val FeatureFlag.descRes: StringResource
    get() =
        when (this) {{
{chr(10).join(desc_lines)}
        }}
'''

with open(DST, "w", encoding="utf-8", newline="\n") as fh:
    fh.write(body)
print(f"wrote {len(entries)} entries to {DST}")
