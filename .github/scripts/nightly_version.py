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


def version_code(year2, week, index):
    """Builds a monotonically increasing integer for Android.

    Android compares versionCode, not versionName, so the ISO year, week and
    revision have to be packed into an integer that always grows. Week 53 and
    revision 25 are the maximums, keeping the value inside a 32-bit int.
    """
    return ((year2 * 100 + week) * MAX_REVISION) + index


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
    year2, week = iso_week_version()
    index = builds_this_week(branch, year2, week)
    version = format_version(year2, week, index)

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
            stream.write(f"version_code={version_code(year2, week, index)}\n")
            stream.write(f"year={year2:02d}\n")
            stream.write(f"week={week:02d}\n")

    print(version)


if __name__ == "__main__":
    main()
