#!/usr/bin/env python3
"""Report exact contents and hashes for the supplied Itunda brand archive."""
from __future__ import annotations

import hashlib
import json
import pathlib
import zipfile

ARCHIVE = pathlib.Path("itunda-brand-v3.1.zip")
CANONICAL = pathlib.Path("packages/brand-assets/canonical/itunda-brand")
REPORT = pathlib.Path("brand-v3.1-archive-audit.md")


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


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
        for info in zf.infolist():
            if info.is_dir():
                continue
            parts = pathlib.PurePosixPath(info.filename).parts
            # Strip the archive's single enclosing directory, if present.
            rel = pathlib.PurePosixPath(*parts[1:]) if len(parts) > 1 else pathlib.PurePosixPath(parts[0])
            if rel.is_absolute() or ".." in rel.parts:
                raise SystemExit(f"Unsafe archive path: {info.filename}")
            key = rel.as_posix()
            if key in archive_files:
                raise SystemExit(f"Duplicate normalized archive path: {key}")
            archive_files[key] = sha256(zf.read(info.filename))

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
