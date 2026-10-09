#!/usr/bin/env python3
"""
Update README metrics and contributors dynamically.
Fetches repository statistics (stars, forks, total downloads) from GitHub API
and updates the metrics and contributors block in README.md.
"""

import os
import re
import sys
import urllib.request
import json

REPO = "yunfie-twitter/Palleria"
README_PATH = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "README.md")

def get_headers():
    token = os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN")
    headers = {"User-Agent": "Palleria-Metrics-Bot"}
    if token:
        headers["Authorization"] = f"token {token}"
    return headers

def fetch_json(url):
    req = urllib.request.Request(url, headers=get_headers())
    try:
        with urllib.request.urlopen(req) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except Exception as e:
        print(f"Warning: Failed to fetch {url}: {e}", file=sys.stderr)
        return None

def main():
    repo_data = fetch_json(f"https://api.github.com/repos/{REPO}")
    stars = repo_data.get("stargazers_count", 0) if repo_data else 0
    forks = repo_data.get("forks_count", 0) if repo_data else 0

    # Calculate total downloads across all releases
    releases = fetch_json(f"https://api.github.com/repos/{REPO}/releases?per_page=100") or []
    total_downloads = 0
    for r in releases:
        for asset in r.get("assets", []):
            total_downloads += asset.get("download_count", 0)

    print(f"Fetched stats for {REPO}: Stars={stars}, Forks={forks}, Total Downloads={total_downloads}")

    metrics_section = f"""<!-- METRICS-STATS:START -->
<p align="center">
  <img src="https://github-readme-stats.vercel.app/api/pin/?username=yunfie-twitter&repo=Palleria&theme=transparent" alt="Palleria Repo Stats" />
</p>

<p align="center">
  <a href="https://github.com/yunfie-twitter/Palleria/stargazers">
    <img src="https://img.shields.io/badge/Stars-{stars}-blue.svg?style=for-the-badge&logo=github" alt="Stars: {stars}" />
  </a>
  <a href="https://github.com/yunfie-twitter/Palleria/releases">
    <img src="https://img.shields.io/badge/Total%20Downloads-{total_downloads}-2ea44f.svg?style=for-the-badge&logo=github" alt="Total Downloads: {total_downloads}" />
  </a>
  <a href="https://github.com/yunfie-twitter/Palleria/network/members">
    <img src="https://img.shields.io/badge/Forks-{forks}-orange.svg?style=for-the-badge&logo=github" alt="Forks: {forks}" />
  </a>
</p>
<!-- METRICS-STATS:END -->"""

    if not os.path.exists(README_PATH):
        print(f"Error: {README_PATH} not found", file=sys.stderr)
        sys.exit(1)

    with open(README_PATH, "r", encoding="utf-8") as f:
        content = f.read()

    pattern = r"<!-- METRICS-STATS:START -->.*?<!-- METRICS-STATS:END -->"
    if re.search(pattern, content, re.DOTALL):
        updated_content = re.sub(pattern, metrics_section, content, flags=re.DOTALL)
    else:
        # If not present yet, place it above Contributing
        anchor = "## Contributing"
        if anchor in content:
            replacement = f"## Metrics & Community\n\n{metrics_section}\n\n---\n\n## Contributors\n\n<p align=\"center\">\n  <a href=\"https://github.com/{REPO}/graphs/contributors\">\n    <img src=\"https://contrib.rocks/image?repo={REPO}\" alt=\"Contributors\" />\n  </a>\n</p>\n\n---\n\n{anchor}"
            updated_content = content.replace(anchor, replacement, 1)
        else:
            updated_content = content + f"\n\n## Metrics & Community\n\n{metrics_section}\n"

    with open(README_PATH, "w", encoding="utf-8") as f:
        f.write(updated_content)

    print("Successfully updated README.md with latest metrics.")

if __name__ == "__main__":
    main()
