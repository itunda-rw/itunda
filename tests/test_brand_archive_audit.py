from __future__ import annotations

import importlib.util
import pathlib
import stat
import zipfile

MODULE_PATH = pathlib.Path("scripts/audit-brand-v3-1-archive.py")
SPEC = importlib.util.spec_from_file_location("brand_archive_audit", MODULE_PATH)
assert SPEC and SPEC.loader
audit = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(audit)


def zip_with(entries: list[tuple[str, bytes, int | None]]) -> zipfile.ZipFile:
    import io

    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w") as zf:
        for name, data, mode in entries:
            info = zipfile.ZipInfo(name)
            if mode is not None:
                info.external_attr = mode << 16
            zf.writestr(info, data)
    buffer.seek(0)
    return zipfile.ZipFile(buffer)


def test_strips_only_unambiguous_shared_root() -> None:
    with zip_with([("brand/logo.svg", b"a", None), ("brand/icon.svg", b"b", None)]) as zf:
        assert [key for _, key in audit.normalized_archive_paths(zf)] == ["logo.svg", "icon.svg"]


def test_preserves_mixed_roots() -> None:
    with zip_with([("brand/logo.svg", b"a", None), ("extras/icon.svg", b"b", None)]) as zf:
        assert [key for _, key in audit.normalized_archive_paths(zf)] == ["brand/logo.svg", "extras/icon.svg"]


def test_rejects_parent_traversal() -> None:
    with zip_with([("../logo.svg", b"a", None)]) as zf:
        try:
            audit.normalized_archive_paths(zf)
        except SystemExit as exc:
            assert "Unsafe archive path" in str(exc)
        else:
            raise AssertionError("parent traversal must be rejected")


def test_rejects_windows_separators() -> None:
    with zip_with([("brand\\logo.svg", b"a", None)]) as zf:
        try:
            audit.normalized_archive_paths(zf)
        except SystemExit as exc:
            assert "path separator" in str(exc)
        else:
            raise AssertionError("Windows-style separators must be rejected")


def test_rejects_symlinks() -> None:
    with zip_with([("brand/link.svg", b"target", stat.S_IFLNK | 0o777)]) as zf:
        try:
            audit.normalized_archive_paths(zf)
        except SystemExit as exc:
            assert "Symlink" in str(exc)
        else:
            raise AssertionError("symlinks must be rejected")


def test_rejects_duplicate_normalized_paths() -> None:
    with zip_with([("brand/logo.svg", b"a", None), ("brand/logo.svg", b"b", None)]) as zf:
        try:
            audit.normalized_archive_paths(zf)
        except SystemExit as exc:
            assert "Duplicate normalized" in str(exc)
        else:
            raise AssertionError("duplicate paths must be rejected")
