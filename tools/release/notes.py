"""Prints the French release notes of a version, taken from its line in docs/CHANGELOG.md.

Usage: python3 tools/release/notes.py [--plain] 2.22.0 [path/to/CHANGELOG.md]
--plain: the line alone, as plain text (the "notes" of update.json, shown in the app).
"""
import os
import re
import sys


def line(version: str, changelog: str) -> str | None:
    """The version's line of the changelog ("2.22.0" is listed as V2.22, "2.20.1" as V2.20.1), or None."""
    names = {version, re.sub(r"\.0$", "", version)}
    content = None
    for row in changelog.splitlines():
        m = re.match(r"\|\s*\*\*V([\d.]+)\*\*[^|]*\|\s*(.+?)\s*\|\s*$", row)
        if m and m.group(1) in names:
            content = m.group(2)
    return content


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
    args = sys.argv[1:]
    plain = "--plain" in args
    args = [a for a in args if a != "--plain"]
    path = args[1] if len(args) > 1 else "docs/CHANGELOG.md"
    with open(path, encoding="utf-8") as f:
        text = f.read()
    if plain:
        sys.stdout.write((line(args[0], text) or f"GymFlemme {args[0]}").replace("`", "") + "\n")
    else:
        sys.stdout.write(notes(args[0], text, os.environ.get("GITHUB_REPOSITORY", "ollivv-hash/GymFlemme")))
