"""Generates an ISO-week build version such as 26w46a (stdlib only).

The two-digit year, ISO week number and a revision letter form the version:

    2026, week 46, first build of the week  -> 26w46a
    second build of the same week           -> 26w46b
    ...
    twenty-seventh build                    -> 26w46a (wraps; avoided in practice)

The letter distinguishes rebuilds that share the same ISO week. It is chosen
from the number of nightly runs already recorded for that week, so a rerun
never publishes a version that is already taken.
"""
import datetime
import os
import re
import subprocess
import sys
from pathlib import Path

# A maximum of 26 builds per week maps onto the letters a..z.
MAX_REVISION = 26

# versionCode space reserved per release so nightly offsets never bleed into the
# next release. The offset covers the two-digit ISO year, all 53 weeks and the 26
# revisions, peaking at 137825, so 200000 leaves headroom while 194 * 200000 still
# fits comfortably inside a 32-bit int.
NIGHTLY_CODE_RANGE = 200000


def iso_week_version(now=None):
    """Returns (year, week) for the given moment, defaulting to now (UTC)."""
    moment = now or datetime.datetime.now(datetime.timezone.utc)
    year, week, _ = moment.isocalendar()
    return year % 100, week


def revision_letter(index):
    """Maps a zero-based build index onto a..z, wrapping after 26 builds."""
    return chr(ord("a") + (index % MAX_REVISION))


def format_version(year2, week, index):
    return f"{year2:02d}w{week:02d}{revision_letter(index)}"


def release_version_code(gradle_text):
    """Reads the release versionCode from app/build.gradle.

    The literal fallback in the env override is the release value, so nightly builds
    can anchor to whatever release is currently prepared without hardcoding it here.
    """
    text = re.sub(r"\(?\s*System\.getenv\([^)]*\)\s*\?:\s*", "", gradle_text)
    text = re.sub(r"\)\.toInteger\(\)", "", text)
    match = re.search(r'^\s*versionCode\s*=?\s*"?(\d+)"?', text, re.M)
    if not match:
        raise ValueError("Cannot read versionCode from app/build.gradle")
    return int(match.group(1))


def version_code(base_code, year2, week, index):
    """Builds a versionCode that installs over the current release.

    Android compares versionCode, not versionName. Nightly builds must exceed the
    release they sit on top of, yet stay below the next release so users can move
    back to a normal build. The offset is bounded by NIGHTLY_CODE_RANGE, which is
    small enough that a release bump always clears every nightly of the previous one.
    """
    offset = (((year2 % 100) * 53) + week) * MAX_REVISION + index
    if offset >= NIGHTLY_CODE_RANGE:
        raise ValueError(f"Nightly offset {offset} exceeds the reserved range {NIGHTLY_CODE_RANGE}")
    return (base_code * NIGHTLY_CODE_RANGE) + offset


def run(*args):
    return subprocess.check_output(args, text=True, encoding="utf-8").strip()


def builds_this_week(branch, year2, week):
    """Counts nightly versions already published for this ISO week.

    Every nightly merge commit carries its version in the subject, so the history
    of the nightly branch is the authoritative record of which letters are taken.
    """
    try:
        log = run("git", "log", "--format=%s", f"origin/{branch}", "-n", "400")
    except (subprocess.SubprocessError, OSError):
        return 0
    prefix = f"{year2:02d}w{week:02d}"
    return sum(1 for line in log.splitlines() if re.search(rf"\b{prefix}[a-z]\b", line))


def main():
    branch = "nightly"
    gradle = Path("app/build.gradle").read_text(encoding="utf-8")
    base_code = release_version_code(gradle)

    year2, week = iso_week_version()
    index = builds_this_week(branch, year2, week)
    version = format_version(year2, week, index)
    code = version_code(base_code, year2, week, index)

    if index >= MAX_REVISION:
        print(
            f"::warning::{MAX_REVISION} nightly builds already published for "
            f"{year2:02d}w{week:02d}; wrapping to {version}. Consider skipping today's build.",
            file=sys.stderr,
        )

    output_path = os.environ.get("GITHUB_OUTPUT")
    if output_path:
        with Path(output_path).open("a", encoding="utf-8") as stream:
            stream.write(f"version={version}\n")
            stream.write(f"version_code={code}\n")
            stream.write(f"base_version_code={base_code}\n")
            stream.write(f"year={year2:02d}\n")
            stream.write(f"week={week:02d}\n")

    print(f"{version} (versionCode {code}, release {base_code})")


if __name__ == "__main__":
    main()
