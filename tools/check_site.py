#!/usr/bin/env python3
"""Check local website links, fragment IDs, download packaging and APK checksum."""

import hashlib
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import unquote, urlsplit
import zipfile


class Page(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.ids: set[str] = set()
        self.links: list[str] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        attributes = dict(attrs)
        if attributes.get("id"):
            self.ids.add(str(attributes["id"]))
        for key in ("href", "src"):
            if attributes.get(key):
                self.links.append(str(attributes[key]))


def main() -> None:
    root = Path(__file__).resolve().parents[1] / "docs"
    page = Page()
    page.feed((root / "index.html").read_text())
    for link in page.links:
        parts = urlsplit(link)
        if parts.scheme or parts.netloc:
            continue
        if parts.path:
            assert (root / unquote(parts.path)).is_file(), f"missing file: {link}"
        if parts.fragment and not parts.path:
            assert parts.fragment in page.ids, f"missing anchor: {link}"
    apk = root / "downloads/dsu-controller-debug.apk"
    expected = (root / "downloads/SHA256SUMS").read_text().split()[0]
    assert hashlib.sha256(apk.read_bytes()).hexdigest() == expected, "APK checksum mismatch"
    with zipfile.ZipFile(apk) as archive:
        assert "AndroidManifest.xml" in archive.namelist(), "not an APK"
        assert archive.testzip() is None, "damaged APK archive"
    print(f"PASS: {len(page.links)} website links, APK archive and SHA-256")


if __name__ == "__main__":
    main()
