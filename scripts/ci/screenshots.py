#!/usr/bin/env python3
"""Screenshots checked against the previous release: its PNGs are the reference.

    screenshots.py fetch              puts the screenshots of the latest release into app/build/screenshots
    screenshots.py check <out dir>    after `./gradlew :app:compareRoborazziDebug`: fails on a change nobody
                                      declared, and leaves the screenshots of this build in <out dir>

A screen may change only when docs/RELEASE_NOTES.md says so against the same reference build:

    Вид изменён (сравнение со сборкой 222): 02_conversation, 33_motion_showcase

A name covers its themes and variants (02_conversation covers 02_conversation_dark and the rest);
«все экраны» covers everything, for a change of the whole look. After the names, « — » may start an
explanation for the reader. The line goes into the release
description with the notes, so a changed reference is always visible there. New screens need no line.
"""

import json
import os
import re
import shutil
import sys
import time
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SHOTS = ROOT / "app/build/screenshots"
RESULTS = ROOT / "app/build/test-results/roborazzi/debug/results-summary.json"
NOTES = ROOT / "docs/RELEASE_NOTES.md"
BASELINE = ".baseline"
ARCHIVE = "screenshots.zip"
EVERYTHING = "все экраны"
DECLARATION = re.compile(r"^Вид изменён \(сравнение со сборкой (\d+)\):\s*(.+?)(?:\s+—\s.*)?$", re.MULTILINE)


def request(url, accept="application/vnd.github+json"):
    headers = {"Accept": accept, "User-Agent": "blesk-ci"}
    token = os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN")
    if token and url.startswith("https://api.github.com/"):
        headers["Authorization"] = f"Bearer {token}"
    return urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=120)


def download(url, target):
    # GitHub drops a connection now and then; a build should not fail on that alone.
    for attempt in range(3):
        try:
            with request(url, accept="application/octet-stream") as answer, open(target, "wb") as out:
                shutil.copyfileobj(answer, out)
            return
        except OSError:
            if attempt == 2:
                raise
            time.sleep(5)


def latest_release():
    repo = os.environ.get("GITHUB_REPOSITORY", "gotblesk/blesk")
    with request(f"https://api.github.com/repos/{repo}/releases?per_page=20") as answer:
        releases = json.load(answer)
    for release in releases:
        names = {asset["name"] for asset in release["assets"]}
        if not release["draft"] and release["tag_name"].startswith("build-") and any(n.endswith(".png") for n in names):
            return release
    raise SystemExit("no release with screenshots to compare with")


def fetch():
    release = latest_release()
    shutil.rmtree(SHOTS, ignore_errors=True)
    SHOTS.mkdir(parents=True)
    assets = {asset["name"]: asset["browser_download_url"] for asset in release["assets"]}
    if ARCHIVE in assets:
        archive = SHOTS / ARCHIVE
        download(assets[ARCHIVE], archive)
        with zipfile.ZipFile(archive) as zipped:
            zipped.extractall(SHOTS)
        archive.unlink()
    else:
        # Releases before screenshots.zip carry each PNG on its own.
        for name, url in assets.items():
            if name.endswith(".png"):
                download(url, SHOTS / name)
    (SHOTS / BASELINE).write_text(release["tag_name"])
    print(f"reference: {release['tag_name']}, {len(list(SHOTS.glob('*.png')))} screenshots")


def declared(build):
    """The screens RELEASE_NOTES.md declares changed against [build]; None when it declares all of them."""
    names = set()
    for number, listed in DECLARATION.findall(NOTES.read_text(encoding="utf-8")):
        if int(number) != build:
            continue
        for name in (part.strip() for part in listed.split(",")):
            if name == EVERYTHING:
                return None
            if name:
                names.add(name.removesuffix(".png"))
    return names


def covered(screen, names):
    return names is None or any(screen == name or screen.startswith(name + "_") for name in names)


def check(out):
    tag = (SHOTS / BASELINE).read_text().strip()
    build = int(tag.removeprefix("build-"))
    results = json.loads(RESULTS.read_text())["results"]
    names = declared(build)
    out.mkdir(parents=True, exist_ok=True)
    changed, added, undeclared = [], [], []
    for result in results:
        golden = Path(result["golden_file_path"])
        screen = golden.stem
        kind = result["type"]
        source = golden if kind == "unchanged" else Path(result["actual_file_path"])
        shutil.copyfile(source, out / golden.name)
        if kind == "changed":
            changed.append(screen)
            if not covered(screen, names):
                undeclared.append(screen)
        elif kind == "added":
            added.append(screen)
    with zipfile.ZipFile(out / ARCHIVE, "w", zipfile.ZIP_STORED) as zipped:
        for png in sorted(out.glob("*.png")):
            zipped.write(png, png.name)
    summary = f"Снимки экранов по сравнению со сборкой {build}: изменено {len(changed)}, новых {len(added)}, всего {len(results)}."
    (out / "summary.txt").write_text(summary + "\n", encoding="utf-8")
    print(summary)
    for screen in changed:
        print(f"  changed: {screen}" + ("  <- not declared" if screen in undeclared else ""))
    for screen in added:
        print(f"  new: {screen}")
    if undeclared:
        print(
            f"\n{len(undeclared)} screenshots changed against {tag} without a line in docs/RELEASE_NOTES.md.\n"
            f"If the change is meant, add: Вид изменён (сравнение со сборкой {build}): "
            + ", ".join(sorted({re.sub(r"_(dark|light|lilac|lime)$", "", s) for s in undeclared}))
            + "\nThe *_compare.png images of app/build/outputs/roborazzi show what changed.",
            file=sys.stderr,
        )
        return 1
    return 0


def main(argv):
    command = argv[0] if argv else ""
    if command == "fetch":
        fetch()
        return 0
    if command == "check" and len(argv) == 2:
        return check(Path(argv[1]))
    print(__doc__, file=sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
