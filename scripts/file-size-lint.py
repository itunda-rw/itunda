#!/usr/bin/env python3
"""Real, automated "one file doing everything" guardrail -- the mechanical enforcement
docs/ARCHITECTURE_GUIDELINES.md §2 and docs/AI_AGENT_SELF_CHECK.md's checklist item 6 name
in prose. Prose alone didn't prevent it: BankDashboard.tsx is 23,468 lines holding every
product, and a repo-wide sweep (2026-08-19) found the SAME shape of violation on every
platform -- ShopScreen.kt (3,629), TalkScreen.kt (3,595), HoodScreen.swift (4,058),
TalkScreen.swift (3,368), and dozens more, not just the two already-being-decomposed Maps
files. Matches the exact discipline docs/ARCHITECTURE_GUIDELINES.md §6 itself demands:
"module boundaries need real enforcement, not just intent."

Real, standard baseline-and-freeze ("ratchet") design, chosen because a one-shot "fail if
any file exceeds N lines" would immediately fail CI on ~50 pre-existing files with no path
to green -- exactly the kind of noisy gate that teaches people to ignore it (same
"deliberately narrow and high-precision" principle accessibility-lint.py's own doc comment
states). Instead:

  - file-size-baseline.json (same directory) records every file that was already over the
    ceiling (THRESHOLD_LINES) when this tool was introduced, at its real, actual line count.
  - Any file NOT in the baseline that crosses THRESHOLD_LINES is a real, new violation --
    exactly the mistake this tool exists to catch going forward: a screen/module quietly
    growing into the next BankDashboard.tsx.
  - Any file IN the baseline that grows PAST its recorded baseline count is also a real
    violation -- freezing the existing giants doesn't mean they're allowed to keep growing;
    it means further growth must be a deliberate, visible, reviewed decision
    (`--update-baseline`), not silent creep.
  - A baseline file may always shrink freely and silently -- decomposition is never
    blocked, only growth requires a conscious act.

Usage:
  python3 scripts/file-size-lint.py                  # check (CI mode, exit 1 on violation)
  python3 scripts/file-size-lint.py --update-baseline # regenerate the baseline from
                                                       # current reality -- only run this
                                                       # after deliberately reviewing WHY a
                                                       # file needs to grow past its
                                                       # recorded baseline, never reflexively
                                                       # to make a red run green.
"""
import json
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
BASELINE_PATH = Path(__file__).resolve().parent / "file-size-baseline.json"
THRESHOLD_LINES = 500

SCAN_ROOTS = ["services", "packages", "android", "ios"]
EXTENSIONS = (".ts", ".tsx", ".kt", ".swift")
EXCLUDE_SUBSTRINGS = (
    "/node_modules/", "/dist/", "/build/", "/.yarn/", "/Pods/", "/DerivedData/",
    "/__snapshots__/",
)


def scan_files() -> dict[str, int]:
    counts: dict[str, int] = {}
    for root_name in SCAN_ROOTS:
        root = REPO_ROOT / root_name
        if not root.exists():
            continue
        for path in sorted(root.rglob("*")):
            if not path.is_file() or path.suffix not in EXTENSIONS:
                continue
            rel = str(path.relative_to(REPO_ROOT))
            if any(seg in f"/{rel}/" for seg in EXCLUDE_SUBSTRINGS):
                continue
            try:
                counts[rel] = sum(1 for _ in path.open(errors="replace"))
            except OSError:
                continue
    return counts


def load_baseline() -> dict[str, int]:
    if not BASELINE_PATH.exists():
        return {}
    return json.loads(BASELINE_PATH.read_text())


def write_baseline(counts: dict[str, int]) -> None:
    baseline = {path: n for path, n in sorted(counts.items()) if n > THRESHOLD_LINES}
    BASELINE_PATH.write_text(json.dumps(baseline, indent=2) + "\n")


def main() -> int:
    counts = scan_files()

    if "--update-baseline" in sys.argv:
        write_baseline(counts)
        print(f"Wrote {BASELINE_PATH.relative_to(REPO_ROOT)} with {len(load_baseline())} entries.")
        return 0

    baseline = load_baseline()
    violations: list[str] = []

    for rel, n in sorted(counts.items()):
        limit = baseline.get(rel, THRESHOLD_LINES)
        if n > limit:
            if rel in baseline:
                violations.append(
                    f"{rel}: {n} lines, grew past its recorded baseline of {limit} -- "
                    f"either extract instead of adding more to this file, or if the growth "
                    f"is a deliberate, reviewed decision, run "
                    f"`python3 scripts/file-size-lint.py --update-baseline` and explain why "
                    f"in the commit."
                )
            else:
                violations.append(
                    f"{rel}: {n} lines, crosses the {THRESHOLD_LINES}-line guideline for "
                    f"the first time -- this is the exact 'everything in one file' mistake "
                    f"docs/ARCHITECTURE_GUIDELINES.md §2 and "
                    f"docs/AI_AGENT_SELF_CHECK.md warn about. Extract before this grows "
                    f"further, don't add it to the baseline reflexively."
                )

    if violations:
        print(f"Found {len(violations)} file-size violation(s):\n")
        for v in violations:
            print(f"  {v}")
        print(
            "\nSee docs/ARCHITECTURE_GUIDELINES.md §2 (\"code that changes together "
            "lives together\") and docs/AI_AGENT_SELF_CHECK.md."
        )
        return 1
    print(f"No file-size violations found ({len(baseline)} pre-existing files frozen at their baseline).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
