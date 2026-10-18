#!/usr/bin/env python3
"""Builds the blesk.su website from site/ into a folder that Caddy serves as is.

    python3 scripts/build-site.py --out DIR [--release release.json] [--public] [--check]

Pages in site/ are templates: a front matter block (title, description, path, noindex,
download — the page offers the APK even before the public launch, like the invite page does),
partials from site/templates/ as {{> name}}, values as {{name}} (escaped) or {{{name}}} (raw),
and sections {{#flag}}...{{/flag}} and {{^flag}}...{{/flag}}. Only the Python standard library is
used, so the build runs on the server and in CI without installing anything.
"""

from __future__ import annotations

import argparse
import html
import json
import re
import shutil
import subprocess
import sys
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import unquote, urljoin, urlsplit

ROOT = Path(__file__).resolve().parent.parent
SITE = ROOT / "site"
DOCS = ROOT / "docs"
TEMPLATES = SITE / "templates"
NOT_PUBLISHED = {"prototype", "templates"}

ORIGIN_HOSTS = {"blesk.su", "www.blesk.su"}
DOWNLOAD_URL = "https://blesk.su/download"
PACKAGE = "app.blesk"
# Test signing key (signing/blesk-test.jks), published in README.md.
TEST_SIGNER = "bd7e78024ea47ca7e7de70c02c39beff5a2855be35560288cdd477e57f9d78bd"
DEFAULT_MIN_ANDROID = "8.0"
DASH = "—"

PAGE_LIMIT = 150 * 1024
# Served by Caddy next to the build: /download is the APK that deploy puts into DIR/download/.
SERVER_ROUTES = {"/download"}
# Left alone when the output folder is rebuilt.
KEPT_IN_OUT = {"download"}
MARKER = ".blesk-site"

LEGAL_PAGES = (
    ("privacy", DOCS / "PRIVACY.md"),
    ("terms", DOCS / "TERMS.md"),
)

HEX64 = re.compile(r"[0-9a-fA-F]{64}")
FRONT_MATTER = re.compile(r"\A---\n(.*?)\n---\n", re.DOTALL)
PARTIAL = re.compile(r"\{\{>\s*([\w-]+)\s*\}\}")
SECTION = re.compile(r"\{\{([#^])(\w+)\}\}(.*?)\{\{/\2\}\}", re.DOTALL)
VALUE = re.compile(r"\{\{(\{?)\s*(\w+)\s*\}?\}\}")


class BuildError(Exception):
    pass


# Release facts

def read_release(path: Path | None) -> dict | None:
    if path is None:
        return None
    try:
        release = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError) as error:
        raise BuildError(f"не удалось прочитать {path}: {error}") from error
    if not isinstance(release, dict):
        raise BuildError(f"{path}: ожидается объект JSON")
    problems = []
    if not isinstance(release.get("version"), str) or not release["version"]:
        problems.append("version — строка")
    if not isinstance(release.get("apkSize"), int) or release["apkSize"] <= 0:
        problems.append("apkSize — положительное число байт")
    for key in ("apkSha256", "signerSha256"):
        if not isinstance(release.get(key), str) or not HEX64.fullmatch(release[key]):
            problems.append(f"{key} — 64 шестнадцатеричных знака")
    if problems:
        raise BuildError(f"{path}: " + "; ".join(problems))
    return release


def format_size(size: int) -> str:
    megabytes = f"{size / (1024 * 1024):.1f}".replace(".", ",")
    return f"{megabytes} МБ"


def format_version(version: str) -> str:
    build = re.fullmatch(r"(.+)-build\.(\d+)", version)
    return f"{build.group(1)} · сборка {build.group(2)}" if build else version


def colon_pairs(hex_digest: str) -> str:
    digest = hex_digest.upper()
    return ":".join(digest[i:i + 2] for i in range(0, len(digest), 2))


def matching(value: object, pattern: str) -> str:
    text = str(value or "")
    return text if re.fullmatch(pattern, text) else ""


def release_values(release: dict | None) -> dict:
    if release is None:
        return {
            "version": DASH, "size": DASH, "sha256": DASH, "signer": DASH,
            "minAndroid": DEFAULT_MIN_ANDROID, "commit": "", "commitShort": "", "tag": "",
            "testSigned": False,
        }
    commit = matching(release.get("commit"), r"[0-9a-f]{7,40}")
    tag = matching(release.get("tag"), r"[\w.-]+")
    return {
        "version": format_version(release["version"]),
        "size": format_size(release["apkSize"]),
        "sha256": release["apkSha256"].lower(),
        "signer": colon_pairs(release["signerSha256"]),
        "minAndroid": str(release.get("minAndroid") or DEFAULT_MIN_ANDROID),
        "commit": commit,
        "commitShort": commit[:7],
        "tag": tag,
        "testSigned": release["signerSha256"].lower() == TEST_SIGNER,
    }


# Templates

def split_front_matter(text: str) -> tuple[dict, str]:
    match = FRONT_MATTER.match(text)
    if not match:
        return {}, text
    meta = {}
    for line in match.group(1).splitlines():
        key, _, value = line.partition(":")
        if key.strip():
            meta[key.strip()] = value.strip()
    return meta, text[match.end():]


def page_values(meta: dict, public: bool) -> dict:
    return {
        "title": meta.get("title", ""),
        "description": meta.get("description", ""),
        "path": meta.get("path", ""),
        "noindex": meta.get("noindex") == "true",
        "downloadOpen": public or meta.get("download") == "true",
    }


def expand_partials(text: str, depth: int = 0) -> str:
    if depth > 5:
        raise BuildError("партиалы вложены слишком глубоко")

    def include(match: re.Match) -> str:
        partial = TEMPLATES / f"{match.group(1)}.html"
        if not partial.is_file():
            raise BuildError(f"нет партиала {partial.relative_to(ROOT)}")
        return expand_partials(partial.read_text(encoding="utf-8").rstrip("\n"), depth + 1)

    return PARTIAL.sub(include, text)


def render(text: str, values: dict, source: str) -> str:
    text = expand_partials(text)

    def section(match: re.Match) -> str:
        inverted, key, body = match.group(1) == "^", match.group(2), match.group(3)
        if key not in values:
            raise BuildError(f"{source}: неизвестный флаг {key}")
        return body if bool(values[key]) != inverted else ""

    previous = None
    while previous != text:
        previous, text = text, SECTION.sub(section, text)

    def value(match: re.Match) -> str:
        raw, key = match.group(1) == "{", match.group(2)
        if key not in values:
            raise BuildError(f"{source}: неизвестное значение {key}")
        return str(values[key]) if raw else html.escape(str(values[key]))

    text = VALUE.sub(value, text)
    if "{{" in text:
        raise BuildError(f"{source}: незакрытая подстановка")
    return text


# Markdown for docs/PRIVACY.md and docs/TERMS.md: headings, paragraphs, lists, links, bold, code.

INLINE = re.compile(
    r"`(?P<code>[^`]+)`"
    r"|\[(?P<label>[^\]]+)\]\((?P<href>[^)\s]+)\)"
    r"|\*\*(?P<bold>.+?)\*\*"
    r"|(?P<url>https?://[^\s<>()«»\"]+)"
)
SAFE_HREF = re.compile(r"(https?://|mailto:|/|#)", re.IGNORECASE)


def inline_markdown(text: str) -> str:
    out = []
    position = 0
    for match in INLINE.finditer(text):
        out.append(html.escape(text[position:match.start()]))
        position = match.end()
        if match.group("code"):
            out.append(f"<code>{html.escape(match.group('code'))}</code>")
        elif match.group("label"):
            href = match.group("href")
            label = inline_markdown(match.group("label"))
            out.append(f'<a href="{html.escape(href)}">{label}</a>' if SAFE_HREF.match(href) else label)
        elif match.group("bold"):
            out.append(f"<strong>{inline_markdown(match.group('bold'))}</strong>")
        else:
            url = match.group("url")
            trimmed = url.rstrip(".,;:!?")
            position -= len(url) - len(trimmed)
            out.append(f'<a href="{html.escape(trimmed)}">{html.escape(trimmed)}</a>')
    out.append(html.escape(text[position:]))
    return "".join(out)


class MarkdownWriter:
    BULLET = re.compile(r"\s*[-*]\s+(.*)")
    NUMBERED = re.compile(r"\s*\d+[.)]\s+(.*)")
    HEADING = re.compile(r"(#{1,6})\s+(.*?)\s*#*\s*")

    def __init__(self) -> None:
        self.title = ""
        self.blocks: list[str] = []
        self.paragraph: list[str] = []
        self.items: list[str] = []
        self.list_tag = ""

    def flush(self) -> None:
        if self.paragraph:
            self.blocks.append(f"<p>{inline_markdown(' '.join(self.paragraph))}</p>")
            self.paragraph = []
        if self.items:
            items = "".join(f"<li>{inline_markdown(item)}</li>" for item in self.items)
            self.blocks.append(f"<{self.list_tag}>{items}</{self.list_tag}>")
            self.items = []

    def add_item(self, tag: str, text: str) -> None:
        if self.paragraph or (self.items and self.list_tag != tag):
            self.flush()
        self.list_tag = tag
        self.items.append(text)

    def feed(self, line: str) -> None:
        heading = self.HEADING.fullmatch(line)
        bullet = self.BULLET.fullmatch(line)
        numbered = self.NUMBERED.fullmatch(line)
        if not line.strip():
            self.flush()
        elif heading:
            self.flush()
            level = len(heading.group(1))
            if level == 1 and not self.title:
                self.title = heading.group(2)
            else:
                level = max(level, 2)
                self.blocks.append(f"<h{level}>{inline_markdown(heading.group(2))}</h{level}>")
        elif bullet:
            self.add_item("ul", bullet.group(1))
        elif numbered:
            self.add_item("ol", numbered.group(1))
        elif self.items and line.startswith((" ", "\t")):
            self.items[-1] += " " + line.strip()
        else:
            if self.items:
                self.flush()
            self.paragraph.append(line.strip())


def render_markdown(text: str) -> tuple[str, str, str]:
    """Returns the first-level heading, the body HTML and a plain-text summary."""
    writer = MarkdownWriter()
    for line in text.splitlines():
        writer.feed(line)
    writer.flush()
    first_paragraph = next((block for block in writer.blocks if block.startswith("<p>")), "")
    summary = html.unescape(re.sub(r"<[^>]+>", "", first_paragraph))
    if len(summary) > 160:
        summary = summary[:157].rsplit(" ", 1)[0].rstrip(",;:—") + "…"
    return writer.title, "\n".join(writer.blocks), summary


# Output

def prepare_out(out: Path) -> None:
    if out.exists() and not out.is_dir():
        raise BuildError(f"{out} — не папка")
    out.mkdir(parents=True, exist_ok=True)
    children = [child for child in out.iterdir() if child.name not in KEPT_IN_OUT]
    if children and not (out / MARKER).exists():
        raise BuildError(f"{out} не пустая и не похожа на прошлую сборку сайта: укажи пустую папку")
    for child in children:
        if child.is_dir() and not child.is_symlink():
            shutil.rmtree(child)
        else:
            child.unlink()
    (out / MARKER).write_text("Собрано scripts/build-site.py; при сборке папка очищается, кроме download/.\n",
                              encoding="utf-8")


def published_files() -> list[Path]:
    files = []
    for path in sorted(SITE.rglob("*")):
        relative = path.relative_to(SITE)
        if relative.parts[0] in NOT_PUBLISHED or any(part.startswith(".") for part in relative.parts):
            continue
        if path.is_file():
            files.append(path)
    return files


def build_pages(out: Path, values: dict) -> None:
    def context(meta: dict) -> dict:
        return {**values, **page_values(meta, values["public"])}

    for source in published_files():
        relative = source.relative_to(SITE)
        target = out / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        if source.suffix == ".html":
            meta, body = split_front_matter(source.read_text(encoding="utf-8"))
            target.write_text(render(body, context(meta), str(relative)), encoding="utf-8")
        else:
            shutil.copyfile(source, target)

    layout = (TEMPLATES / "legal.html").read_text(encoding="utf-8")
    for name, document in LEGAL_PAGES:
        title, body, summary = render_markdown(document.read_text(encoding="utf-8"))
        meta = {"title": title, "description": summary, "path": f"/{name}/"}
        page = render(layout, {**context(meta), "heading": title, "body": body}, name)
        target = out / name / "index.html"
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(page, encoding="utf-8")


def write_assetlinks(out: Path, release: dict | None) -> None:
    # Only the key the published APK is signed with: the test key is public, and listed next to an
    # official one it would hand blesk.su links, invite codes and profile keys to anyone's build.
    signer = release["signerSha256"] if release else TEST_SIGNER
    statement = [{
        "relation": ["delegate_permission/common.handle_all_urls"],
        "target": {"namespace": "android_app", "package_name": PACKAGE, "sha256_cert_fingerprints": [colon_pairs(signer)]},
    }]
    target = out / ".well-known" / "assetlinks.json"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(statement, indent=2) + "\n", encoding="utf-8")


def write_download_qr(out: Path) -> bool:
    qrencode = shutil.which("qrencode")
    if qrencode is None:
        return False
    target = out / "assets" / "qr-download.svg"
    target.parent.mkdir(parents=True, exist_ok=True)
    command = [qrencode, "-t", "SVG", "-l", "M", "-m", "2", "-o", str(target), DOWNLOAD_URL]
    # --rle merges neighbouring modules into one shape and makes the file several times smaller,
    # but not every qrencode build knows it.
    for attempt in (command[:1] + ["--rle"] + command[1:], command):
        result = subprocess.run(attempt, capture_output=True, text=True, check=False)
        svg = target.read_text(encoding="utf-8") if result.returncode == 0 and target.is_file() else ""
        if "<svg" in svg:
            # Without crisp edges the browser anti-aliases the seams between modules into grey lines.
            target.write_text(svg.replace("<svg", '<svg shape-rendering="crispEdges"', 1), encoding="utf-8")
            return True
    print(f"QR-код не создан: {result.stderr.strip() or 'qrencode не справился'}", file=sys.stderr)
    target.unlink(missing_ok=True)
    return False


def build(out: Path, release: dict | None, public: bool) -> None:
    prepare_out(out)
    qr = write_download_qr(out)
    values = {**release_values(release), "public": public, "qr": public and qr}
    build_pages(out, values)
    write_assetlinks(out, release)


# Checks

RESOURCE_ATTRIBUTES = {
    "script": ("src",), "img": ("src", "srcset"), "source": ("src", "srcset"), "video": ("src", "poster"),
    "audio": ("src",), "track": ("src",), "iframe": ("src",), "embed": ("src",), "object": ("data",),
    "input": ("src",),
}
RESOURCE_RELS = {"stylesheet", "preload", "modulepreload", "prefetch", "icon", "apple-touch-icon", "manifest"}
CSS_URL = re.compile(r"url\(\s*(['\"]?)([^'\")]+)\1\s*\)|@import\s+(['\"])([^'\"]+)\3")
SCRIPT_URL = re.compile(r"(?:https?:)?//[\w.-]+\.[a-z]{2,}", re.IGNORECASE)


class PageScan(HTMLParser):
    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.lang = ""
        self.title = ""
        self.description = False
        self.ids: set[str] = set()
        self.links: list[str] = []
        self.resources: list[str] = []
        self.problems: list[str] = []
        self.in_title = False

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        attributes = {name: value or "" for name, value in attrs}
        self.check_attributes(tag, attributes)
        if "id" in attributes:
            self.ids.add(attributes["id"])
        if tag == "html":
            self.lang = attributes.get("lang", "")
        elif tag == "title":
            self.in_title = True
        elif tag == "style":
            self.problems.append("элемент <style> (CSP запрещает встроенные стили)")
        elif tag == "meta" and attributes.get("name") == "description" and attributes.get("content"):
            self.description = True
        elif tag in ("a", "area") and "href" in attributes:
            self.links.append(attributes["href"])
        elif tag == "link" and RESOURCE_RELS & set(attributes.get("rel", "").lower().split()):
            self.resources.append(attributes.get("href", ""))
        if tag == "script" and not attributes.get("src"):
            self.problems.append("<script> без src (CSP запрещает встроенные скрипты)")
        for name in RESOURCE_ATTRIBUTES.get(tag, ()):
            if attributes.get(name):
                candidates = (candidate.split() for candidate in attributes[name].split(","))
                self.resources.extend(parts[0] for parts in candidates if parts)

    def check_attributes(self, tag: str, attributes: dict[str, str]) -> None:
        for name, value in attributes.items():
            if name == "style":
                self.problems.append(f"атрибут style у <{tag}>")
            elif name.startswith("on"):
                self.problems.append(f"обработчик {name} у <{tag}>")
            elif value.strip().lower().startswith("javascript:"):
                self.problems.append(f"javascript:-ссылка у <{tag}>")

    def handle_endtag(self, tag: str) -> None:
        if tag == "title":
            self.in_title = False

    def handle_data(self, data: str) -> None:
        if self.in_title:
            self.title += data


def url_path(page: Path, out: Path) -> str:
    relative = page.relative_to(out).as_posix()
    return "/" + (relative[: -len("index.html")] if relative.endswith("index.html") else relative)


def classify(url: str, base: str) -> tuple[str, str, str]:
    """(kind, path, fragment) where kind is internal, external, inline or other."""
    parts = urlsplit(url)
    if parts.scheme in ("http", "https") or url.startswith("//"):
        if parts.hostname in ORIGIN_HOSTS:
            return "internal", parts.path or "/", parts.fragment
        return "external", "", ""
    if parts.scheme == "data":
        return "inline", "", ""
    if parts.scheme:
        return "other", "", ""
    return "internal", urljoin(base, parts.path) if parts.path else base, parts.fragment


def resolve(out: Path, path: str) -> Path | None:
    candidate = (out / unquote(path).lstrip("/")).resolve()
    if not candidate.is_relative_to(out.resolve()):
        return None
    if path.endswith("/"):
        candidate = candidate / "index.html"
    if candidate.is_file():
        return candidate
    if (candidate / "index.html").is_file():
        return candidate / "index.html"
    return None


class SiteCheck:
    def __init__(self, out: Path) -> None:
        # Resolved like every page and resource it is compared with, so a relative --out works too.
        self.out = out.resolve()
        self.scans: dict[Path, PageScan] = {}
        self.problems: list[str] = []
        self.weights: list[tuple[str, int]] = []

    def scan(self, page: Path) -> PageScan:
        if page not in self.scans:
            scanner = PageScan()
            scanner.feed(page.read_text(encoding="utf-8"))
            scanner.close()
            self.scans[page] = scanner
        return self.scans[page]

    def report(self, where: str, problem: str) -> None:
        line = f"{where}: {problem}"
        if line not in self.problems:
            self.problems.append(line)

    def run(self) -> list[str]:
        for page in sorted(self.out.rglob("*.html")):
            self.check_page(page)
        self.check_assetlinks()
        return self.problems

    def check_page(self, page: Path) -> None:
        name = page.relative_to(self.out).as_posix()
        scan = self.scan(page)
        for problem in scan.problems:
            self.report(name, problem)
        if scan.lang != "ru":
            self.report(name, 'нет <html lang="ru">')
        if not scan.title.strip():
            self.report(name, "нет <title>")
        if not scan.description:
            self.report(name, "нет meta description")
        base = url_path(page, self.out)
        for link in scan.links:
            self.check_link(name, page, base, link)
        weight = page.stat().st_size
        for resource in self.page_resources(name, base, scan.resources):
            weight += resource.stat().st_size
        self.weights.append((name, weight))
        if weight > PAGE_LIMIT:
            self.report(name, f"весит {weight / 1024:.1f} КБ вместе с ресурсами, больше {PAGE_LIMIT // 1024} КБ")

    def page_resources(self, name: str, base: str, urls: list[str]) -> set[Path]:
        found: set[Path] = set()
        pending = [(name, base, url) for url in urls]
        while pending:
            owner, owner_base, url = pending.pop()
            target = self.internal_target(owner, owner_base, url, resource=True)
            if target is None or target in found:
                continue
            found.add(target)
            if target.suffix == ".js" and SCRIPT_URL.search(target.read_text(encoding="utf-8")):
                self.report(target.relative_to(self.out).as_posix(), "в скрипте есть адрес другого сайта")
            if target.suffix == ".css":
                css_base = url_path(target, self.out)
                for match in CSS_URL.finditer(target.read_text(encoding="utf-8")):
                    pending.append((target.relative_to(self.out).as_posix(), css_base, match.group(2) or match.group(4)))
        return found

    def internal_target(self, owner: str, base: str, url: str, resource: bool) -> Path | None:
        kind, path, _ = classify(url, base)
        if kind == "external":
            if resource:
                self.report(owner, f"внешний ресурс {url}")
            return None
        if kind != "internal" or path in SERVER_ROUTES:
            return None
        if not url.startswith(("/", "#", "https://", "http://")):
            self.report(owner, f"относительный адрес {url}: страницы открываются и по чужим путям, нужен путь от корня")
        target = resolve(self.out, path)
        if target is None:
            self.report(owner, f"ссылка {url} ведёт в никуда")
        return target

    def check_link(self, name: str, page: Path, base: str, link: str) -> None:
        if link in ("", "#"):
            self.report(name, "пустая ссылка")
            return
        fragment = classify(link, base)[2]
        target = page if link.startswith("#") else self.internal_target(name, base, link, resource=False)
        if target is None or not fragment or target.suffix != ".html":
            return
        if fragment not in self.scan(target).ids:
            self.report(name, f"ссылка {link}: на странице нет id «{fragment}»")

    def check_assetlinks(self) -> None:
        path = self.out / ".well-known" / "assetlinks.json"
        try:
            statement = json.loads(path.read_text(encoding="utf-8"))
            target = statement[0]["target"]
            ok = target["package_name"] == PACKAGE and bool(target["sha256_cert_fingerprints"])
        except (OSError, ValueError, KeyError, IndexError, TypeError):
            ok = False
        if not ok:
            self.report(".well-known/assetlinks.json", f"нет или неверный (нужен пакет {PACKAGE} и отпечатки ключей)")


def main() -> int:
    parser = argparse.ArgumentParser(description="Собирает сайт blesk.su из site/.")
    parser.add_argument("--out", required=True, type=Path, help="папка, куда собрать сайт")
    parser.add_argument("--release", type=Path, help="release.json релиза, чей APK лежит в /download")
    parser.add_argument("--public", action="store_true", help="после публичного запуска: «Скачать» вместо «Скоро»")
    parser.add_argument("--check", action="store_true", help="проверить собранный сайт")
    args = parser.parse_args()

    try:
        release = read_release(args.release)
        build(args.out, release, args.public)
    except BuildError as error:
        print(f"Ошибка: {error}", file=sys.stderr)
        return 1
    print(f"Сайт собран в {args.out}" + ("" if release else " (без release.json: вместо сведений о сборке прочерки)"))

    if not args.check:
        return 0
    check = SiteCheck(args.out)
    problems = check.run()
    for name, weight in check.weights:
        print(f"  {name}: {weight / 1024:.1f} КБ")
    if problems:
        print(f"Проверка не пройдена, проблем: {len(problems)}", file=sys.stderr)
        for problem in problems:
            print(f"  - {problem}", file=sys.stderr)
        return 1
    print("Проверка пройдена")
    return 0


if __name__ == "__main__":
    sys.exit(main())
