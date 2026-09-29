#!/usr/bin/env python3
r"""Cross-references every real backend endpoint against every real client call site to
find endpoints with zero callers -- see feedback_uncalled_endpoint_sweep.md (auto memory)
for the full history: 9+ real, shippable gaps found this way since 2026-08-05, plus
5 distinct extractor-bug classes hit and fixed across independent from-scratch rewrites.
This script exists specifically so nobody has to rewrite (and re-break) that regex again.

Known pitfalls this script already handles (do not "simplify" these away):
  1. A single .kt file can define MORE THAN ONE @RestController class (e.g.
     HoodReportController.kt) -- endpoints must be attributed to the @RequestMapping
     of THEIR OWN class, not the first one found in the file.
  2. Client path strings use single, double, AND backtick quotes.
  3. Client path strings often have a leading slash the backend base path doesn't
     repeat literally in @RequestMapping annotations the same way.
  4. Path variables differ by language: Kotlin/Spring {id}, TS/JS template ${id},
     Swift string interpolation \(id) -- all must normalize to one placeholder.
  5. Client paths sometimes carry a trailing query string (?page=${page}) that must be
     stripped before comparison.
  6. Scan ALL client dirs, not just bank-mfe -- native-only or admin-only endpoints
     called from Android/iOS/ops-mfe/merchant-mfe/kyc-mfe/pay-checkout/maps-mfe will
     false-positive as "uncalled" if any one of those is skipped.
  7. A client sometimes deliberately passes a literal `_` as a path segment instead of
     a real id (when the backend endpoint ignores the id entirely and fans out some
     other way, e.g. MapsController.updateLocationShare's own real "one push updates
     every active share" doc comment) -- normalize() treats a bare `_` segment the
     same as a real {id}, or this shows up as a false "uncalled" positive.

KNOWN, UNFIXED LIMITATION -- do not spend more regex effort chasing this: a client
call site with a NESTED template literal inside a ternary inside the outer template
literal (e.g. `` `/api/v1/x${qs ? `?${qs}` : ''}` `` -- a real example that produced a
false "uncalled" positive for `merchant/reports/top-products`, already fully wired in
merchant-mfe's ReportsScreen.tsx) will not be extracted correctly. PATH_STRING_RE's
character class deliberately excludes backtick (it has to, to find the OUTER string's
own closing quote), so it can't see past the inner backtick without genuinely parsing
nested string literals -- which regex fundamentally can't do reliably. If a "real
uncalled" candidate looks surprising, grep the client source directly for that literal
path substring before trusting the script -- this class of false positive won't self-
correct with more regex tweaking.

Usage: python3 scripts/uncalled-endpoint-sweep.py
"""
import re
import glob

REPO_ROOT = "."

BACKEND_GLOB = "services/backend/**/*.kt"
CLIENT_GLOBS = [
    "services/micro-frontends/bank-mfe/src/**/*.ts",
    "services/micro-frontends/bank-mfe/src/**/*.tsx",
    "services/micro-frontends/merchant-mfe/src/**/*.ts",
    "services/micro-frontends/merchant-mfe/src/**/*.tsx",
    "services/micro-frontends/ops-mfe/src/**/*.ts",
    "services/micro-frontends/ops-mfe/src/**/*.tsx",
    "services/micro-frontends/kyc-mfe/src/**/*.ts",
    "services/micro-frontends/kyc-mfe/src/**/*.tsx",
    "services/micro-frontends/pay-checkout/src/**/*.ts",
    "services/micro-frontends/pay-checkout/src/**/*.tsx",
    "services/micro-frontends/maps-mfe/src/**/*.ts",
    "services/micro-frontends/maps-mfe/src/**/*.tsx",
    "android/**/*.kt",
    "ios/**/*.swift",
]

# Deliberately excludes @RequestMapping -- in this codebase it's only ever used at the
# class level (confirmed: zero method-level `@RequestMapping(method = ...)` usages via
# grep), and BASE_MAPPING_RE already extracts that separately. Including it here caused
# a real bug: the class-level annotation got double-counted as its own "endpoint" and
# self-concatenated with the base path (e.g. "api/v1/accounts/api/v1/accounts").
MAPPING_RE = re.compile(
    r'@(?:Get|Post|Put|Patch|Delete)Mapping\(\s*(?:value\s*=\s*)?"([^"]*)"'
)
CONTROLLER_RE = re.compile(r'@RestController\b')
BASE_MAPPING_RE = re.compile(
    r'@RequestMapping\(\s*(?:value\s*=\s*)?"([^"]*)"'
)


def normalize(path: str) -> str:
    # Strip query string.
    path = path.split('?', 1)[0]
    # Strip a leading slash (client strings often have one, backend annotations don't
    # always repeat the base's leading slash the same way once joined).
    path = path.lstrip('/')
    # Normalize every path-variable style to one placeholder: Kotlin/Spring {id},
    # TS/JS `${id}`, Swift `\(id)`.
    # Order matters: ${id} must be matched as a whole BEFORE the bare {id} pattern,
    # or the bare pattern partially consumes it (matches just "{id}" inside "${id}"),
    # leaving a stray "$" and a broken "${}" that never matches Kotlin's plain "{}".
    path = re.sub(r'\$\{[^}/]+\}', '{}', path)
    path = re.sub(r'\{[^}/]+\}', '{}', path)
    path = re.sub(r'\\\([^)/]+\)', '{}', path)
    # A client sometimes deliberately passes a literal underscore as a path segment
    # instead of a real id, when the backend endpoint ignores which id was passed
    # (see pitfall #7 in this file's own docstring).
    path = re.sub(r'/_(?=/|$)', '/{}', path)
    return path.rstrip('/')


def extract_backend_endpoints():
    endpoints = []  # (path, file, class_base)
    for fname in glob.glob(BACKEND_GLOB, recursive=True):
        if '/build/' in fname or '/test/' in fname:
            continue
        with open(fname, encoding='utf-8', errors='ignore') as fh:
            content = fh.read()
        if '@RestController' not in content:
            continue
        # Split into per-class chunks at each @RestController occurrence -- a single
        # file can define more than one controller class (pitfall #1 above).
        indices = [m.start() for m in CONTROLLER_RE.finditer(content)]
        indices.append(len(content))
        for i in range(len(indices) - 1):
            chunk = content[indices[i]:indices[i + 1]]
            base_match = BASE_MAPPING_RE.search(chunk)
            base = base_match.group(1) if base_match else ''
            for m in MAPPING_RE.finditer(chunk):
                # Skip the base @RequestMapping match itself if it's the only mapping.
                full = (base.rstrip('/') + '/' + m.group(1).lstrip('/')).strip('/')
                endpoints.append((normalize(full), fname))
    return endpoints


# Deliberately does NOT anchor "api/v1" right after the opening quote -- real call
# sites commonly use template literals like `${BASE_URL}/api/v1/...` where a variable
# interpolation precedes the literal path, so this searches for "api/v1/..." ANYWHERE
# inside a quoted string instead, up to the closing quote.
# Character class must include query-string punctuation (?&=) -- a real call site like
# `/api/v1/system/compliance/queue?page=${page}` was invisible to extraction without it,
# since the closing quote check right after the capture group failed the moment a `?`
# sat in between. normalize() strips the query string afterward.
PATH_STRING_RE = re.compile(
    r'''['"`][^'"`]*?(/?api/v1/[a-zA-Z0-9_\-{}/${}().\\?&=]*)['"`]'''
)


def extract_client_paths():
    paths = set()
    for pattern in CLIENT_GLOBS:
        for fname in glob.glob(pattern, recursive=True):
            if '/build/' in fname or '/dist/' in fname or '/.yarn/' in fname:
                continue
            with open(fname, encoding='utf-8', errors='ignore') as fh:
                content = fh.read()
            for m in PATH_STRING_RE.finditer(content):
                paths.add(normalize(m.group(1)))
    return paths


def main():
    endpoints = extract_backend_endpoints()
    client_paths = extract_client_paths()

    seen = set()
    uncalled = []
    for path, fname in endpoints:
        key = path
        if key in seen:
            continue
        seen.add(key)
        if path and path not in client_paths:
            uncalled.append((path, fname))

    uncalled.sort()
    print(f"Total distinct backend endpoints: {len(seen)}")
    print(f"Total distinct client path strings: {len(client_paths)}")
    print(f"Raw uncalled candidates: {len(uncalled)}\n")
    for path, fname in uncalled:
        print(f"  {path}   ({fname})")


if __name__ == "__main__":
    main()
