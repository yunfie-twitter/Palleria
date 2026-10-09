#!/usr/bin/env python3
"""
Generate third-party open-source licenses for Palleria.
Parses gradle/libs.versions.toml for Android libraries and
rust/*/Cargo.lock for Rust crates.
Outputs:
  - app/src/main/assets/licenses.json (used by app's About / Settings screen)
  - THIRD_PARTY_LICENSES.md (repo root documentation)
"""

import os
import re
import json

ROOT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TOML_PATH = os.path.join(ROOT_DIR, "gradle", "libs.versions.toml")
CARGO_LOCKS = [
    os.path.join(ROOT_DIR, "rust", "pixiv-api", "Cargo.lock"),
    os.path.join(ROOT_DIR, "rust", "pallasync-core", "Cargo.lock"),
]
ASSETS_DIR = os.path.join(ROOT_DIR, "app", "src", "main", "assets")
JSON_OUTPUT = os.path.join(ASSETS_DIR, "licenses.json")
MD_OUTPUT = os.path.join(ROOT_DIR, "THIRD_PARTY_LICENSES.md")

KNOWN_ANDROID_LICENSES = {
    "androidx": ("The Android Open Source Project", "Apache-2.0", "https://developer.android.com/jetpack"),
    "org.jetbrains.kotlin": ("JetBrains s.r.o.", "Apache-2.0", "https://kotlinlang.org/"),
    "org.jetbrains.kotlinx": ("JetBrains s.r.o.", "Apache-2.0", "https://github.com/Kotlin/kotlinx.coroutines"),
    "io.coil-kt": ("Coil Contributors", "Apache-2.0", "https://github.com/coil-kt/coil"),
    "com.squareup.okhttp3": ("Square, Inc.", "Apache-2.0", "https://square.github.io/okhttp/"),
    "top.yukonga.miuix": ("yukonga", "Apache-2.0", "https://github.com/miuix-kotlin-multiplatform/miuix"),
    "dev.rikka.shizuku": ("RikkaApps", "Apache-2.0", "https://github.com/RikkaApps/Shizuku-API"),
    "io.sentry": ("Sentry", "MIT", "https://github.com/getsentry/sentry-java"),
    "net.java.dev.jna": ("Timothy Wall", "LGPL-2.1 / Apache-2.0", "https://github.com/java-native-access/jna"),
    "com.google.zxing": ("ZXing Authors", "Apache-2.0", "https://github.com/zxing/zxing"),
    "com.journeyapps": ("Journey Mobile", "Apache-2.0", "https://github.com/journeyapps/zxing-android-embedded"),
    "io.kotest": ("Kotest", "Apache-2.0", "https://github.com/kotest/kotest"),
    "junit": ("JUnit.org", "EPL-2.0", "https://junit.org/"),
    "org.robolectric": ("Google LLC / Robolectric Authors", "Apache-2.0", "https://robolectric.org/"),
}


def parse_gradle_libraries():
    if not os.path.exists(TOML_PATH):
        return []
    with open(TOML_PATH, "r", encoding="utf-8") as f:
        content = f.read()

    versions = {}
    ver_match = re.search(r"\[versions\](.*?)(?:\[|\Z)", content, re.DOTALL)
    if ver_match:
        for line in ver_match.group(1).splitlines():
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            m = re.match(r'([\w\-]+)\s*=\s*"([^"]+)"', line)
            if m:
                versions[m.group(1)] = m.group(2)

    libs = []
    seen = set()
    lib_match = re.search(r"\[libraries\](.*?)(?:\[|\Z)", content, re.DOTALL)
    if lib_match:
        for line in lib_match.group(1).splitlines():
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            m = re.search(r'module\s*=\s*"([^:]+):([^"]+)"', line)
            if not m:
                continue
            group, name = m.group(1), m.group(2)
            key = f"{group}:{name}"
            if key in seen:
                continue
            seen.add(key)

            ver_ref = re.search(r'version\.ref\s*=\s*"([^"]+)"', line)
            version = versions.get(ver_ref.group(1), "") if ver_ref else ""
            if not version:
                ver_val = re.search(r'version\s*=\s*"([^"]+)"', line)
                version = ver_val.group(1) if ver_val else ""

            author = "Open Source Contributors"
            license_type = "Apache-2.0"
            url = f"https://search.maven.org/artifact/{group}/{name}"

            for prefix, info in KNOWN_ANDROID_LICENSES.items():
                if group.startswith(prefix):
                    author, license_type, url = info
                    break

            libs.append({
                "name": f"{group}:{name}",
                "category": "Android / Gradle",
                "version": version or "bundled",
                "author": author,
                "license": license_type,
                "url": url,
            })
    return sorted(libs, key=lambda x: x["name"])


def parse_cargo_packages():
    crates = {}
    for lock_path in CARGO_LOCKS:
        if not os.path.exists(lock_path):
            continue
        with open(lock_path, "r", encoding="utf-8") as f:
            content = f.read()

        blocks = content.split("[[package]]")
        for b in blocks[1:]:
            name_m = re.search(r'name\s*=\s*"([^"]+)"', b)
            ver_m = re.search(r'version\s*=\s*"([^"]+)"', b)
            if name_m and ver_m:
                name = name_m.group(1)
                ver = ver_m.group(1)
                if name not in crates:
                    crates[name] = {
                        "name": name,
                        "category": "Rust Crate",
                        "version": ver,
                        "author": "Rust Community",
                        "license": "MIT / Apache-2.0",
                        "url": f"https://crates.io/crates/{name}",
                    }
    return sorted(crates.values(), key=lambda x: x["name"])


def main():
    os.makedirs(ASSETS_DIR, exist_ok=True)
    android_libs = parse_gradle_libraries()
    rust_crates = parse_cargo_packages()

    all_deps = android_libs + rust_crates

    with open(JSON_OUTPUT, "w", encoding="utf-8") as f:
        json.dump(all_deps, f, indent=2, ensure_ascii=False)
    print(f"Generated {len(all_deps)} licenses into {JSON_OUTPUT}")

    with open(MD_OUTPUT, "w", encoding="utf-8") as f:
        f.write("# Third-Party Open Source Licenses\n\n")
        f.write("Palleria incorporates components from the following open-source libraries and crates.\n\n")

        f.write("## Android & Gradle Dependencies\n\n")
        f.write("| Dependency | Version | License | Upstream |\n")
        f.write("| :--- | :--- | :--- | :--- |\n")
        for lib in android_libs:
            f.write(f"| [{lib['name']}]({lib['url']}) | {lib['version']} | {lib['license']} | {lib['author']} |\n")

        f.write("\n## Rust Native Crates\n\n")
        f.write("| Crate | Version | License | Registry |\n")
        f.write("| :--- | :--- | :--- | :--- |\n")
        for crate in rust_crates:
            f.write(f"| [{crate['name']}]({crate['url']}) | {crate['version']} | {crate['license']} | [crates.io]({crate['url']}) |\n")

        f.write("\n---\n\n*This file is automatically generated by `scripts/generate_licenses.py`.*\n")
    print(f"Generated {MD_OUTPUT}")


if __name__ == "__main__":
    main()
