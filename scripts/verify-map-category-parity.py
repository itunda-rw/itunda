#!/usr/bin/env python3
"""Real, automated guard against a real, confirmed drift risk: itunda's real
GET /api/v1/maps/categories endpoint (MapsController.categories, backed by the
backend's own MapPlaceCategory enum) exists specifically to give every client one
shared source of truth for the map's category-chip list -- but a fresh
uncalled-endpoint sweep (2026-08-24, scripts/uncalled-endpoint-sweep.py) found it has
ZERO real callers on any of the 3 platforms. Each client instead hand-maintains its own
hardcoded copy of the same list (bank-mfe's maps-mfe/src/lib/maps.ts NEARBY_CATEGORIES,
Android's ApiService.kt MAP_NEARBY_CATEGORIES, iOS's NetworkClient.swift
mapNearbyCategories) -- currently all 4 lists (backend + 3 clients) happen to agree, but
nothing enforces that going forward. This is the exact same "manually-synced list must
match a source enum" bug shape scripts/verify-ledger-account-seeds.py already guards
against for LedgerAccountType/SEED_IDS (see feedback_ledger_account_seed_bug_class
memory, 9 confirmed real instances there) -- rewiring all 3 clients to fetch the real
endpoint at runtime was judged a bigger, separately-scoped refactor (network
round-trip + loading state added to a screen that currently renders instantly from a
local constant) than this specific gap warrants; a cheap static parity guard closes the
actual risk (silent drift) without that cost.

`ITUNDA_AGENT` is a deliberate, understood exception: it's itunda's own real cash-agent
discovery feature (AgentDiscoveryController, distinct from the OSM-backed categories
here), added as a client-only category id on all 3 platforms with no backend
MapPlaceCategory counterpart -- excluded from the parity check on both sides rather
than flagged.
"""

import re
import sys
from pathlib import Path

ROOT_DIR = Path(__file__).resolve().parent.parent
BACKEND_ENUM_FILE = ROOT_DIR / "services/backend/maps/src/main/kotlin/rw/itunda/maps/MapPlaceCategory.kt"
WEB_FILE = ROOT_DIR / "services/micro-frontends/maps-mfe/src/lib/maps.ts"
ANDROID_FILE = ROOT_DIR / "android/core/network/src/main/java/rw/itunda/core/network/ApiService.kt"
IOS_FILE = ROOT_DIR / "ios/Core/Network/Sources/NetworkClient.swift"

CLIENT_ONLY_EXCEPTIONS = {"ITUNDA_AGENT"}


def extract_backend_categories() -> set[str]:
    text = BACKEND_ENUM_FILE.read_text()
    match = re.search(r"enum class MapPlaceCategory\(.*?\)\s*\{(.*?)\n\s*;", text, re.DOTALL)
    if not match:
        print(f"Could not locate MapPlaceCategory enum body in {BACKEND_ENUM_FILE}", file=sys.stderr)
        sys.exit(1)
    return set(re.findall(r"^\s*([A-Z][A-Z0-9_]*)\(", match.group(1), re.MULTILINE))


def extract_list(file: Path, block_start: str, terminator: str, id_pattern: str) -> set[str]:
    """`terminator` is a regex for the line that closes the block (e.g. a bare `]` or
    `)`) -- deliberately NOT "first ']' or ')' after block_start", since a preceding
    type annotation (web's `MapPlaceCategory[]`) or the first list entry's own closing
    paren (Kotlin's `MapPlaceCategory("RESTAURANT", "Restaurants")`) both contain that
    character well before the block actually ends."""
    text = file.read_text()
    match = re.search(re.escape(block_start) + r"(.*?)\n\s*" + terminator, text, re.DOTALL)
    if not match:
        print(f"Could not locate a closed {block_start!r} block in {file}", file=sys.stderr)
        sys.exit(1)
    return set(re.findall(id_pattern, match.group(1)))


def main() -> int:
    for f in (BACKEND_ENUM_FILE, WEB_FILE, ANDROID_FILE, IOS_FILE):
        if not f.exists():
            print(f"Expected file not found: {f}", file=sys.stderr)
            return 1

    backend = extract_backend_categories()
    web = extract_list(WEB_FILE, "export const NEARBY_CATEGORIES", r"\]", r"id:\s*'([A-Z0-9_]+)'")
    android = extract_list(ANDROID_FILE, "val MAP_NEARBY_CATEGORIES", r"\)", r'MapPlaceCategory\("([A-Z0-9_]+)"')
    ios = extract_list(IOS_FILE, "public let mapNearbyCategories", r"\]", r'id:\s*"([A-Z0-9_]+)"')

    backend_checkable = backend
    problems: list[str] = []
    for name, client_ids in (("bank-mfe maps-mfe (lib/maps.ts)", web), ("Android (ApiService.kt)", android), ("iOS (NetworkClient.swift)", ios)):
        client_checkable = client_ids - CLIENT_ONLY_EXCEPTIONS
        missing_from_client = backend_checkable - client_checkable
        extra_in_client = client_checkable - backend_checkable
        if missing_from_client:
            problems.append(f"{name} is missing categor{'y' if len(missing_from_client) == 1 else 'ies'} the backend enum has: {sorted(missing_from_client)}")
        if extra_in_client:
            problems.append(f"{name} has categor{'y' if len(extra_in_client) == 1 else 'ies'} with no backend MapPlaceCategory match: {sorted(extra_in_client)}")

    if problems:
        print("Real drift found between the backend's MapPlaceCategory enum and a client's\n"
              "hand-maintained category list -- these are supposed to stay in lockstep (see\n"
              "this script's own doc comment for why GET /api/v1/maps/categories exists but\n"
              "isn't wired up to enforce this automatically):\n", file=sys.stderr)
        for p in problems:
            print(f"  {p}", file=sys.stderr)
        return 1

    print(f"OK: {len(backend)} backend MapPlaceCategory entries, all present (and no extras) across bank-mfe/Android/iOS's hand-maintained lists.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
