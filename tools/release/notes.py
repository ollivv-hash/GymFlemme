"""Prints the French release notes of a version, taken from its line in docs/CHANGELOG.md.

Usage: python3 tools/release/notes.py 2.22.0 [path/to/CHANGELOG.md]
"""
import os
import re
import sys


def notes(version: str, changelog: str, repo: str = "ollivv-hash/GymFlemme") -> str:
    # "2.22.0" is listed as V2.22, "2.20.1" as V2.20.1.
    names = {version, re.sub(r"\.0$", "", version)}
    content = None
    for line in changelog.splitlines():
        m = re.match(r"\|\s*\*\*V([\d.]+)\*\*[^|]*\|\s*(.+?)\s*\|\s*$", line)
        if m and m.group(1) in names:
            content = m.group(2)
    lines = []
    if content:
        lines += ["## Nouveautés", "", content, ""]
    lines += [
        "## Installer ou mettre à jour",
        "",
        f"Sur le téléphone, téléchargez `gymflemme-{version}.apk` ci-dessous et ouvrez-le. Une mise à jour s'installe "
        "par-dessus la version précédente : les données sont conservées.",
        "",
        f"Historique complet : [docs/CHANGELOG.md](https://github.com/{repo}/blob/main/docs/CHANGELOG.md)",
    ]
    return "\n".join(lines) + "\n"


if __name__ == "__main__":
    path = sys.argv[2] if len(sys.argv) > 2 else "docs/CHANGELOG.md"
    with open(path, encoding="utf-8") as f:
        sys.stdout.write(notes(sys.argv[1], f.read(), os.environ.get("GITHUB_REPOSITORY", "ollivv-hash/GymFlemme")))
