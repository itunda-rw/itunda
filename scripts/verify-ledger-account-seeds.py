#!/usr/bin/env python3
"""Real, automated guard against a real, repeatedly-recurring bug class: a new
LedgerAccountType enum value used in a LedgerLeg(...) call with a NEW string accountId
that was never added to LedgerAccount.SEED_IDS. LedgerService.postLedgerTransaction has
no auto-create path -- it looks up every non-WALLET accountId against the real
ledger_accounts table and throws IllegalStateException("Unknown ledger account $it") if
the row doesn't exist. SeedDataRunner inserts every SEED_IDS row on startup, so this bug
is invisible to compile AND to this codebase's own convention of always mocking
LedgerService in unit tests -- it has only ever been caught by a real live request
against the real database, 9 confirmed times as of 2026-08-16 (see
LedgerAccount.kt's own doc comments for the first 8, and
docs/DESIGN_REFERENCES.md Section 83 / feedback_ledger_account_seed_bug_class memory for
the 9th, "promotion_expense").

Scope, deliberately narrow: only checks LedgerLeg(...) calls whose accountId argument is
a PLAIN string literal (e.g. "fee_revenue") -- these are exactly the ones a human chose a
fixed clearing-account id for and could simply forget to seed. accountId arguments built
from a variable (wallet.id -- always a real WALLET, never a clearing account in this
codebase) or from string interpolation (the two real fx_clearing_${currency} call sites)
are skipped, not flagged, since they can't be checked by a static string match and are a
real, deliberate, already-understood exception (see ForeignCurrencyWalletService.kt).
"""

import re
import sys
from pathlib import Path

ROOT_DIR = Path(__file__).resolve().parent.parent
BACKEND_DIR = ROOT_DIR / "services" / "backend"
LEDGER_ACCOUNT_FILE = BACKEND_DIR / "core/src/main/kotlin/rw/itunda/core/domain/LedgerAccount.kt"

# A LedgerLeg(...) call's accountId is always the first constructor argument, and a
# clearing-account id is always a bare, non-interpolated string literal -- interpolated
# ("...${...}...") accountIds are the one real, understood exception (fx_clearing_*).
LEDGER_LEG_CALL = re.compile(r'LedgerLeg\(\s*"([^"$]*)"', re.MULTILINE)


def extract_seed_ids() -> set[str]:
    text = LEDGER_ACCOUNT_FILE.read_text()
    match = re.search(r"SEED_IDS\s*=\s*listOf\((.*?)\n\s*\)\n\s*\}", text, re.DOTALL)
    if not match:
        print(f"Could not locate SEED_IDS list in {LEDGER_ACCOUNT_FILE}", file=sys.stderr)
        sys.exit(1)
    return set(re.findall(r'"([a-z0-9_]+)"\s+to\s+"', match.group(1)))


def find_referenced_account_ids() -> dict[str, list[str]]:
    """Maps each literal accountId to the file:line locations it's used from."""
    references: dict[str, list[str]] = {}
    for kt_file in BACKEND_DIR.rglob("*.kt"):
        if "/build/" in str(kt_file) or "/test/" in str(kt_file):
            continue
        text = kt_file.read_text()
        for line_no, line in enumerate(text.splitlines(), start=1):
            for match in LEDGER_LEG_CALL.finditer(line):
                account_id = match.group(1)
                references.setdefault(account_id, []).append(f"{kt_file.relative_to(ROOT_DIR)}:{line_no}")
    return references


def main() -> int:
    if not LEDGER_ACCOUNT_FILE.exists():
        print(f"LedgerAccount.kt not found at expected path: {LEDGER_ACCOUNT_FILE}", file=sys.stderr)
        return 1

    seed_ids = extract_seed_ids()
    references = find_referenced_account_ids()

    # "wallet" itself is never a clearing-account literal (real wallet ids are always a
    # variable like buyerWallet.id, never a string literal), so an unrecognized literal
    # that happens to match a real word here would be a coincidence, not a false
    # positive to special-case.
    missing = {account_id: locs for account_id, locs in references.items() if account_id and account_id not in seed_ids}

    if missing:
        print("Real gap found: LedgerLeg(...) call site(s) use an accountId with no matching\n"
              "LedgerAccount.SEED_IDS row -- the first real transaction using it will 500 live\n"
              "with \"Unknown ledger account <id>\" (see feedback_ledger_account_seed_bug_class\n"
              "memory for the 9 confirmed prior instances of this exact bug class):\n", file=sys.stderr)
        for account_id, locs in sorted(missing.items()):
            print(f"  \"{account_id}\" -- referenced from:", file=sys.stderr)
            for loc in locs:
                print(f"    {loc}", file=sys.stderr)
        return 1

    print(f"OK: {len(references)} distinct literal ledger accountId(s) referenced across the backend, all present in LedgerAccount.SEED_IDS.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
