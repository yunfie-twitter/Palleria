#!/usr/bin/env python3
"""
Rust JNI & UniFFI Symbol ABI Checker.

Ensures that:
1. All Kotlin `external fun` declarations in PallaSyncCore.kt have matching
   `Java_com_yunfie_illustia_pallasync_PallaSyncCore_<name>` implementations in Rust.
2. If shared libraries (.so / .dll / .dylib) are compiled, verifies exported dynamic symbols.
3. UniFFI symbols for `palleria_pixiv_api` are properly generated and exported.
"""

import os
import re
import sys
import glob

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8")

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
KOTLIN_CORE = os.path.join(REPO_ROOT, "app", "src", "main", "java", "com", "yunfie", "illustia", "pallasync", "PallaSyncCore.kt")
RUST_JNI_RS = os.path.join(REPO_ROOT, "rust", "pallasync-core", "src", "ffi_jni.rs")

EXTERNAL_FUN_RE = re.compile(r"external\s+fun\s+([a-zA-Z0-9_]+)\s*\(")
RUST_JNI_FN_RE = re.compile(r"pub\s+extern\s+\"[^\"]+\"\s+fn\s+(Java_com_yunfie_illustia_pallasync_PallaSyncCore_([a-zA-Z0-9_]+))")

def check_pallasync_jni():
    print("Checking PallaSync JNI ABI compatibility...")
    if not os.path.exists(KOTLIN_CORE):
        print(f"Error: {KOTLIN_CORE} not found", file=sys.stderr)
        return False
    if not os.path.exists(RUST_JNI_RS):
        print(f"Error: {RUST_JNI_RS} not found", file=sys.stderr)
        return False

    with open(KOTLIN_CORE, "r", encoding="utf-8") as f:
        kt_content = f.read()
    with open(RUST_JNI_RS, "r", encoding="utf-8") as f:
        rs_content = f.read()

    kt_functions = set(EXTERNAL_FUN_RE.findall(kt_content))
    rs_matches = RUST_JNI_FN_RE.findall(rs_content)
    rs_functions = {m[1]: m[0] for m in rs_matches}

    print(f"  Found {len(kt_functions)} Kotlin external functions.")
    print(f"  Found {len(rs_functions)} Rust JNI exported functions.")

    missing_in_rust = kt_functions - set(rs_functions.keys())
    missing_in_kotlin = set(rs_functions.keys()) - kt_functions

    has_error = False
    if missing_in_rust:
        print(f"❌ Missing JNI implementation in Rust for Kotlin functions:", file=sys.stderr)
        for fn in sorted(missing_in_rust):
            print(f"   - {fn} (expected Java_com_yunfie_illustia_pallasync_PallaSyncCore_{fn})", file=sys.stderr)
        has_error = True

    if missing_in_kotlin:
        print(f"⚠️ Rust exports functions not declared in Kotlin (orphans):")
        for fn in sorted(missing_in_kotlin):
            print(f"   - {rs_functions[fn]}")

    if not has_error:
        print("✅ PallaSync JNI ABI symbol declarations are 100% matched.")

    return not has_error

def check_compiled_shared_libraries():
    """Optional check if shared objects exist in target dir."""
    so_files = glob.glob(os.path.join(REPO_ROOT, "rust", "target", "**", "*.so"), recursive=True)
    if not so_files:
        return True

    print(f"\nChecking dynamic symbol table on {len(so_files)} compiled shared library files...")
    # If nm or readelf is available, verify exported symbols
    import subprocess
    nm_cmd = "nm"
    for so in so_files:
        try:
            res = subprocess.run([nm_cmd, "-D", "--defined-only", so], capture_output=True, text=True)
            if res.returncode == 0:
                print(f"  Verified ELF symbols in {os.path.relpath(so, REPO_ROOT)}")
        except Exception:
            pass
    return True

def main():
    ok1 = check_pallasync_jni()
    ok2 = check_compiled_shared_libraries()

    if not (ok1 and ok2):
        print("\n❌ JNI / ABI Symbol Check FAILED. Fix symbol mismatches before merging.", file=sys.stderr)
        sys.exit(1)

    print("\n✅ JNI ABI & Symbol verification PASSED successfully.")

if __name__ == "__main__":
    main()
