"""Draft builds and release-driven F-Droid updates (stdlib only)."""
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import time
from urllib.parse import quote

PUBLIC = Path("public")
APP_ID = "com.yunfie.illustia"
PRERELEASE = re.compile(r"-(alpha|beta|rc|pre|preview|dev|snapshot)([.-].*)?$", re.I)


def run(*args):
    return subprocess.check_output(args, text=True, encoding="utf-8").strip()


def api(endpoint, missing_ok=False):
    result = subprocess.run(["gh", "api", endpoint], capture_output=True, text=True, encoding="utf-8")
    if result.returncode:
        if missing_ok and "(HTTP 404)" in result.stderr:
            return None
        raise RuntimeError(f"GitHub API failed: {result.stderr}")
    return json.loads(result.stdout)


def endpoint(suffix):
    return f"repos/{os.environ['GITHUB_REPOSITORY']}/{suffix}"


def output(key, value):
    with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as stream:
        stream.write(f"{key}={value}\n")


def version(text):
    name = re.search(r'^\s*versionName\s*=?\s*[\"\x27]([\w.+-]+)[\"\x27]', text, re.M)
    code = re.search(r'^\s*versionCode\s*=?\s*(\d+)\s*$', text, re.M)
    if not name or not code:
        raise ValueError("Cannot read versionName/versionCode from app/build.gradle")
    return name[1], int(code[1])


def guard(tag):
    release = api(endpoint(f"releases/tags/{quote(tag, safe='')}"), missing_ok=True)
    if release and not release["draft"]:
        raise RuntimeError(f"Release {tag} is already published; refusing to overwrite it")


def prepare():
    name, _ = version(Path("app/build.gradle").read_text(encoding="utf-8"))
    tag = f"v{name}"
    ref = os.environ["GITHUB_REF"]
    if ref.startswith("refs/tags/") and ref != f"refs/tags/{tag}":
        raise ValueError("Tag and versionName do not match")
    guard(tag)
    existing = run("git", "ls-remote", "--tags", "origin", f"refs/tags/{tag}")
    if existing:
        run("git", "fetch", "origin", f"refs/tags/{tag}")
        if run("git", "rev-parse", "FETCH_HEAD^{commit}") != run("git", "rev-parse", "HEAD"):
            raise RuntimeError("Existing tag points to another commit. Retry the tag, or bump the version; the tag was preserved.")
    else:
        run("git", "tag", tag)
        run("git", "push", "origin", f"refs/tags/{tag}")
    output("tag", tag)
    output("prerelease", str(bool(PRERELEASE.search(name))).lower())


def apk_assets(release):
    assets = [a for a in release.get("assets", []) if a["name"].endswith(".apk")]
    for asset in assets:
        name = asset["name"]
        if Path(name).name != name or "/" in name or "\\" in name or name.startswith("."):
            raise ValueError(f"Unsafe APK asset name: {name!r}")
        if asset.get("state", "uploaded") != "uploaded":
            raise RuntimeError("Release asset upload is not complete")
    if len({a["name"] for a in assets}) != len(assets):
        raise ValueError("Duplicate APK asset names")
    return assets


def asset_snapshot(assets):
    return sorted((a["id"], a["name"], a["size"], a.get("updated_at"), a.get("digest")) for a in assets)


def download(release_id, destination):
    """Retry the entire snapshot; never fall back to an APK from another release."""
    for attempt in range(3):
        try:
            release = api(endpoint(f"releases/{release_id}"), missing_ok=True)
            if not release or release["draft"]:
                return None
            assets = apk_assets(release)
            if not assets:
                print("No APK found in release. Skipping F-Droid update.")
                return None
            with tempfile.TemporaryDirectory() as temporary:
                run("gh", "release", "download", release["tag_name"], "--repo", os.environ["GITHUB_REPOSITORY"],
                    "--pattern", "*.apk", "--dir", temporary)
                for asset in assets:
                    file = Path(temporary, asset["name"])
                    if not file.is_file() or file.stat().st_size != asset["size"]:
                        raise RuntimeError("Incomplete APK download")
                    digest = asset.get("digest")
                    if digest and digest.startswith("sha256:"):
                        with file.open("rb") as stream:
                            actual = hashlib.file_digest(stream, "sha256").hexdigest()
                        if actual != digest.removeprefix("sha256:"):
                            raise RuntimeError("APK checksum mismatch")
                after = api(endpoint(f"releases/{release_id}"), missing_ok=True)
                if not after or after["draft"]:
                    return None
                if asset_snapshot(assets) != asset_snapshot(apk_assets(after)) or release["tag_name"] != after["tag_name"]:
                    raise RuntimeError("Release changed during download")
                for asset in assets:
                    shutil.copy2(Path(temporary, asset["name"]), destination / asset["name"])
                return after
        except (RuntimeError, subprocess.CalledProcessError) as error:
            if attempt == 2:
                raise
            print(f"Download attempt {attempt + 1} failed: {error}; retrying")
            time.sleep(5 * (attempt + 1))
    raise AssertionError("unreachable")


def tag_metadata(tag):
    # Read data from the release commit without running its scripts or Gradle.
    run("git", "check-ref-format", f"refs/tags/{tag}")
    run("git", "fetch", "origin", f"refs/tags/{tag}")
    commit = run("git", "rev-parse", "FETCH_HEAD^{commit}")
    name, code = version(run("git", "show", f"{commit}:app/build.gradle"))
    if tag != f"v{name}":
        raise ValueError("Release tag does not match its build.gradle")
    paths = run("git", "ls-tree", "-r", "--name-only", commit, "--", "fdroid/metadata").splitlines()
    if not paths:
        raise ValueError(f"No F-Droid metadata in release tag {tag}")
    for path in paths:
        relative = Path(path).relative_to("fdroid/metadata")
        if ".." in relative.parts:
            raise ValueError("Unsafe metadata path")
        target = PUBLIC / "metadata" / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(subprocess.check_output(["git", "show", f"{commit}:{path}"]))
    return name, code


def sync():
    event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text(encoding="utf-8"))
    release = event["release"]
    repo = PUBLIC / "repo"
    repo.mkdir(parents=True, exist_ok=True)
    state_file = PUBLIC / "metadata" / "release-assets.json"
    state = json.loads(state_file.read_text()) if state_file.exists() else {}
    key = str(release["id"])
    if event["action"] == "published":
        with tempfile.TemporaryDirectory() as temporary:
            release = download(release["id"], Path(temporary))
            if release is None:
                output("changed", "false")
                return
            version_name, version_code = tag_metadata(release["tag_name"])
            assets = apk_assets(release)
            # A file cannot belong to two releases: deletion must be unambiguous.
            names = {a["name"] for a in assets}
            if any(names.intersection(item["files"]) for rid, item in state.items() if rid != key):
                raise ValueError("APK asset filename is shared by different releases")
            for name in state.get(key, {}).get("files", []):
                if name not in names:
                    (repo / name).unlink(missing_ok=True)
            for asset in assets:
                shutil.copy2(Path(temporary, asset["name"]), repo / asset["name"])
            state[key] = {"tag": release["tag_name"], "prerelease": release["prerelease"], "files": sorted(names),
                          "version_name": version_name, "version_code": version_code}
    elif event["action"] == "deleted":
        # Ignore stale deletion events for an ID that still exists (e.g. a rerun).
        if api(endpoint(f"releases/{release['id']}"), missing_ok=True):
            output("changed", "false")
            return
        tracked = state.pop(key, None)
        names = tracked["files"] if tracked else [a["name"] for a in apk_assets(release)]
        # Legacy repositories have no ledger. Event asset names provide migration.
        owned_elsewhere = {name for item in state.values() for name in item["files"]}
        removed = False
        for name in names:
            if name not in owned_elsewhere and (repo / name).is_file():
                (repo / name).unlink()
                removed = True
        if not tracked and not removed:
            output("changed", "false")
            return
    else:
        raise ValueError("Only published/deleted release events are supported")
    state_file.parent.mkdir(parents=True, exist_ok=True)
    state_file.write_text(json.dumps(state, indent=2) + "\n", encoding="utf-8")
    output("changed", "true")


def apk_version(aapt, apk):
    data = run(str(aapt), "dump", "badging", str(apk))
    match = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']*)'", data, re.M)
    if not match or match[1] != APP_ID:
        raise ValueError(f"Unexpected APK package: {apk}")
    return match[3], int(match[2])


def recommended(versions):
    stable = [(name, code) for name, code, prerelease in versions if not prerelease and not PRERELEASE.search(name)]
    return max(stable, key=lambda item: item[1], default=None)


def set_current(text, current):
    text = re.sub(r"^CurrentVersion(?:Code)?:.*\n?", "", text, flags=re.M).rstrip() + "\n"
    if current:
        name, code = current
        text += f"CurrentVersion: {json.dumps(name)}\nCurrentVersionCode: {code}\n"
    else:
        # F-Droid may otherwise infer a suggested version from prerelease APKs.
        text += "CurrentVersionCode: 0\n"
    return text


def index():
    aapts = sorted(Path(os.environ["ANDROID_HOME"]).glob("build-tools/*/aapt"))
    if not aapts:
        raise RuntimeError("Android build-tools aapt is required")
    state = json.loads((PUBLIC / "metadata" / "release-assets.json").read_text())
    prereleases = {name for item in state.values() if item["prerelease"] for name in item["files"]}
    expected = {name: (item["version_name"], item["version_code"])
                for item in state.values() for name in item["files"]}
    versions = []
    for apk in (PUBLIC / "repo").glob("*.apk"):
        actual = apk_version(aapts[-1], apk)
        if apk.name in expected and actual != expected[apk.name]:
            raise ValueError(f"APK version does not match release tag: {apk.name}")
        versions.append((*actual, apk.name in prereleases))
    metadata = PUBLIC / "metadata" / f"{APP_ID}.yml"
    metadata.write_text(set_current(metadata.read_text(encoding="utf-8"), recommended(versions)), encoding="utf-8")
    Path("release.keystore").write_bytes(base64.b64decode("".join(os.environ["KEYSTORE_BASE64"].split()), validate=True))
    Path("release.keystore").chmod(0o600)
    # JSON is valid YAML and safely quotes passwords containing punctuation.
    config = {
        "repo_url": f"https://yunfi.f5.si/{os.environ['GITHUB_REPOSITORY'].split('/')[-1]}/repo",
        "repo_name": "Palleria Repository",
        "repo_description": "Stable, beta and pre-release builds for Palleria",
        "archive_older": 0,
        "keystore": "../release.keystore",
        "keystorepass": os.environ["KEYSTORE_PASSWORD"],
        "keypass": os.environ["KEY_PASSWORD"],
        "repo_keyalias": os.environ["KEY_ALIAS"],
    }
    (PUBLIC / "config.yml").write_text(json.dumps(config), encoding="utf-8")
    (PUBLIC / "config.yml").chmod(0o600)


if __name__ == "__main__":
    commands = {"prepare": prepare, "guard": lambda: guard(os.environ["RELEASE_TAG"]), "sync": sync, "index": index}
    commands[sys.argv[1]]()
