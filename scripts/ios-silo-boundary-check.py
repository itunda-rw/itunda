#!/usr/bin/env python3
"""Swift/Tuist equivalent of android/architecture-test's Konsist check.

Enforces the silo boundary in docs/MULTI_AGENT_ISOLATION.md: a feature's
`Feature<Name>/Sources/**` may not import another feature's `Feature<Name>`
implementation module directly -- only that feature's `Feature<Name>Interface`
is safe to depend on cross-feature. Pure text scan (no Xcode/Tuist needed), so
it runs on a plain Linux CI runner instead of requiring the macOS ios-build job.
"""
import re
import sys
from pathlib import Path

IOS_ROOT = Path(__file__).resolve().parent.parent / "ios"
FEATURES_ROOT = IOS_ROOT / "Features"

IMPORT_RE = re.compile(r"^\s*import\s+Feature([A-Za-z0-9]+?)(Interface|Testing)?\s*$")


def own_feature_name(sources_dir: Path) -> str:
    # .../Features/<Name>/Sources -> <Name>
    return sources_dir.parent.name


def find_violations() -> list[str]:
    violations = []
    if not FEATURES_ROOT.is_dir():
        return violations

    for feature_dir in sorted(FEATURES_ROOT.iterdir()):
        sources_dir = feature_dir / "Sources"
        if not sources_dir.is_dir():
            continue
        own_feature = own_feature_name(sources_dir)

        for swift_file in sorted(sources_dir.rglob("*.swift")):
            for lineno, line in enumerate(swift_file.read_text(errors="replace").splitlines(), start=1):
                match = IMPORT_RE.match(line)
                if not match:
                    continue
                imported_feature, suffix = match.group(1), match.group(2)
                if imported_feature == own_feature:
                    continue  # importing your own Interface/Testing is fine
                if suffix == "Interface":
                    continue  # cross-feature Interface imports are the whole point
                violations.append(
                    f"{swift_file.relative_to(IOS_ROOT)}:{lineno}: Feature{own_feature} imports "
                    f"Feature{imported_feature}{suffix or ''} directly -- depend on "
                    f"Feature{imported_feature}Interface instead."
                )
    return violations


def main() -> int:
    violations = find_violations()
    if violations:
        print(f"Found {len(violations)} silo boundary violation(s):\n")
        for v in violations:
            print(f"  {v}")
        print(
            "\nSee docs/MULTI_AGENT_ISOLATION.md -- a feature's Sources/** may only "
            "depend on another feature's *Interface module, never its impl directly."
        )
        return 1
    print("No iOS silo boundary violations found.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
