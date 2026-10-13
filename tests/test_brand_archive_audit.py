from __future__ import annotations

import importlib.util
import io
import pathlib
import stat
import unittest
import zipfile

MODULE_PATH = pathlib.Path("scripts/audit-brand-v3-1-archive.py")
SPEC = importlib.util.spec_from_file_location("brand_archive_audit", MODULE_PATH)
assert SPEC and SPEC.loader
audit = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(audit)


def zip_with(entries: list[tuple[str, bytes, int | None]]) -> zipfile.ZipFile:
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w") as zf:
        for name, data, mode in entries:
            info = zipfile.ZipInfo(name)
            if mode is not None:
                info.external_attr = mode << 16
            zf.writestr(info, data)
    buffer.seek(0)
    return zipfile.ZipFile(buffer)


class ArchivePathValidationTests(unittest.TestCase):
    def test_strips_only_unambiguous_shared_root(self) -> None:
        with zip_with([("brand/logo.svg", b"a", None), ("brand/icon.svg", b"b", None)]) as zf:
            self.assertEqual([key for _, key in audit.normalized_archive_paths(zf)], ["logo.svg", "icon.svg"])

    def test_preserves_mixed_roots(self) -> None:
        with zip_with([("brand/logo.svg", b"a", None), ("extras/icon.svg", b"b", None)]) as zf:
            self.assertEqual(
                [key for _, key in audit.normalized_archive_paths(zf)],
                ["brand/logo.svg", "extras/icon.svg"],
            )

    def test_rejects_parent_traversal(self) -> None:
        with zip_with([("../logo.svg", b"a", None)]) as zf:
            with self.assertRaisesRegex(SystemExit, "Unsafe archive path"):
                audit.normalized_archive_paths(zf)

    def test_rejects_windows_separators(self) -> None:
        with zip_with([("brand\\\\logo.svg", b"a", None)]) as zf:
            with self.assertRaisesRegex(SystemExit, "path separator"):
                audit.normalized_archive_paths(zf)

    def test_rejects_symlinks(self) -> None:
        with zip_with([("brand/link.svg", b"target", stat.S_IFLNK | 0o777)]) as zf:
            with self.assertRaisesRegex(SystemExit, "Symlink"):
                audit.normalized_archive_paths(zf)

    def test_rejects_duplicate_normalized_paths(self) -> None:
        with zip_with([("brand/logo.svg", b"a", None), ("brand/logo.svg", b"b", None)]) as zf:
            with self.assertRaisesRegex(SystemExit, "Duplicate normalized"):
                audit.normalized_archive_paths(zf)


if __name__ == "__main__":
    unittest.main()
