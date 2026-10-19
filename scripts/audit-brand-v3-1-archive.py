#!/usr/bin/env python3
"""Report exact contents and hashes for the supplied Itunda brand archive."""
from __future__ import annotations

import hashlib
import json
import pathlib
import stat
import zipfile

ARCHIVE = pathlib.Path("itunda-brand-v3.1.zip")
CANONICAL = pathlib.Path("packages/brand-assets/canonical/itunda-brand")
REPORT = pathlib.Path("brand-v3.1-archive-audit.md")


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def normalized_archive_paths(zf: zipfile.ZipFile) -> list[tuple[zipfile.ZipInfo, str]]:
    """Normalize paths, stripping a wrapper directory only when it is unambiguous."""
    files = [info for info in zf.infolist() if not info.is_dir()]
    parsed: list[tuple[zipfile.ZipInfo, pathlib.PurePosixPath]] = []
    for info in files:
        # ZIP paths use POSIX separators. Reject Windows separators instead of
        # allowing platform-dependent interpretation of a path.
        if "\\" in info.filename:
            raise SystemExit(f"Unsafe archive path separator: {info.filename}")
        # Validate raw segments before PurePosixPath can normalize away "." or
        # repeated separators. The audit must report the archive's actual names.
        raw_parts = info.filename.split("/")
        if any(part in ("", ".", "..") for part in raw_parts):
            raise SystemExit(f"Unsafe archive path: {info.filename}")
        path = pathlib.PurePosixPath(info.filename)
        mode = info.external_attr >> 16
        if stat.S_ISLNK(mode):
            raise SystemExit(f"Symlink archive member is not allowed: {info.filename}")
        if path.is_absolute() or not path.parts or any(part in ("", ".", "..") for part in path.parts):
            raise SystemExit(f"Unsafe archive path: {info.filename}")
        parsed.append((info, path))

    # Only remove a shared enclosing directory when every file is nested below
    # the same root. Do not silently strip the first component from mixed roots.
    roots = {path.parts[0] for _, path in parsed}
    has_root_level_file = any(len(path.parts) == 1 for _, path in parsed)
    strip_root = len(roots) == 1 and not has_root_level_file and bool(parsed)

    normalized: list[tuple[zipfile.ZipInfo, str]] = []
    seen: set[str] = set()
    for info, path in parsed:
        parts = path.parts[1:] if strip_root else path.parts
        if not parts:
            raise SystemExit(f"Archive entry resolves to an empty path: {info.filename}")
        key = pathlib.PurePosixPath(*parts).as_posix()
        if key in seen:
            raise SystemExit(f"Duplicate normalized archive path: {key}")
        seen.add(key)
        normalized.append((info, key))
    return normalized


def main() -> None:
    if not ARCHIVE.is_file():
        raise SystemExit(f"Missing archive: {ARCHIVE}")
    if not CANONICAL.is_dir():
        raise SystemExit(f"Missing canonical tree: {CANONICAL}")

    archive_files: dict[str, str] = {}
    with zipfile.ZipFile(ARCHIVE) as zf:
        bad = zf.testzip()
        if bad:
            raise SystemExit(f"Corrupt archive member: {bad}")
        for info, key in normalized_archive_paths(zf):
            archive_files[key] = sha256(zf.read(info))

    canonical_files = {
        p.relative_to(CANONICAL).as_posix(): sha256(p.read_bytes())
        for p in CANONICAL.rglob("*")
        if p.is_file()
    }
    a_keys, c_keys = set(archive_files), set(canonical_files)
    added = sorted(a_keys - c_keys)
    missing = sorted(c_keys - a_keys)
    changed = sorted(k for k in a_keys & c_keys if archive_files[k] != canonical_files[k])

    lines = [
        "# Itunda brand v3.1 archive audit",
        "",
        f"- Archive SHA-256: `{sha256(ARCHIVE.read_bytes())}`",
        f"- Archive files: {len(archive_files)}",
        f"- Canonical files: {len(canonical_files)}",
        f"- Exact path-and-byte matches: {len(a_keys & c_keys) - len(changed)}",
        f"- Archive-only paths: {len(added)}",
        f"- Canonical-only paths: {len(missing)}",
        f"- Same-path content mismatches: {len(changed)}",
        "",
        "> Diagnostic only: no canonical assets are modified by this audit.",
        "",
        "## Archive entries (normalized path and SHA-256)",
        "",
        "| Path | SHA-256 |",
        "|---|---|",
    ]
    lines.extend(f"| `{k}` | `{archive_files[k]}` |" for k in sorted(archive_files))
    for heading, items in (("Archive-only paths", added), ("Canonical-only paths", missing), ("Content mismatches", changed)):
        lines.extend(["", f"## {heading}", ""])
        lines.extend(f"- `{k}`" for k in items) if items else lines.append("- None")
    REPORT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(json.dumps({
        "archive_sha256": sha256(ARCHIVE.read_bytes()),
        "archive_files": len(archive_files),
        "canonical_files": len(canonical_files),
        "archive_only": added,
        "canonical_only": missing,
        "content_mismatches": changed,
        "report": str(REPORT),
    }, indent=2))


if __name__ == "__main__":
    main()
