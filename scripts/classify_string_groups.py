#!/usr/bin/env python3
"""Classify string groups by migration difficulty."""
import collections
import os
import re

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(REPO, "androidApp", "src", "main", "java")

values = os.path.join(REPO, "androidApp", "src", "main", "res", "values", "strings.xml")
with open(values, encoding="utf-8") as fh:
    keys = re.findall(r'<string name="([^"]+)"', fh.read())

groups = collections.Counter(k.split("_")[0] for k in keys)

usages = collections.defaultdict(lambda: {"plain": 0, "hard": 0})
hard_re = re.compile(r"(getString|pluralStringResource|String\.format)")

for root, _, files in os.walk(SRC):
    for name in files:
        if not name.endswith(".kt"):
            continue
        path = os.path.join(root, name)
        text = open(path, encoding="utf-8").read()
        for line in text.splitlines():
            for m in re.finditer(r"R\.string\.(\w+)", line):
                group = m.group(1).split("_")[0]
                if hard_re.search(line):
                    usages[group]["hard"] += 1
                elif "stringResource(R.string." in line and line.count(",") > 0:
                    usages[group]["hard"] += 1
                else:
                    usages[group]["plain"] += 1

print(f"{'group':<18}{'keys':>6}{'plain':>7}{'hard':>6}")
for g, count in groups.most_common(30):
    u = usages[g]
    print(f"{g:<18}{count:>6}{u['plain']:>7}{u['hard']:>6}")
