#!/usr/bin/env python3
"""Real, automated accessibility lint (item 247) -- itunda's own equivalent of Toss's
self-built "Ally" scanner (toss.im/tossfeed/article/ally): a repo-wide, one-click check
for the exact category of bug this project has already found and fixed by hand more than
once (iOS touch-target gaps, a dead-tap bug in bank-mfe's QuickActions). Toss's own stated
payoff for building this was developers self-catching ~100 errors/hour instead of relying
on a manual expert audit -- this is that same shape of tool, scoped to what a pure text
scan can check precisely (see docs/DESIGN_REFERENCES.md Section 14).

Deliberately narrow and high-precision over broad and noisy: each check here is chosen
because a violation is very likely a real bug, not a stylistic judgment call. A
"clickable Box with no semantics" check was considered and deliberately left out -- too
many legitimate custom row/card components would false-positive, and a noisy linter
teaches developers to ignore it, which is worse than not having one.

Checks:
  1. Android Compose: an IconButton whose only visible content is an Icon with
     contentDescription = null -- the icon IS the button's entire accessible name, so a
     null description leaves it silent to a screen reader.
  2. iOS SwiftUI: a Button whose only visible content is an Image(systemName:) with no
     .accessibilityLabel anywhere in the same button -- same failure mode as #1.
  3. Web (TSX/JSX): an <img> tag with no alt attribute at all (alt="" for a genuinely
     decorative image is fine and not flagged -- only a missing attribute is a real gap).

Pure text/brace-matching scan, no build tooling needed -- runs on a plain Linux CI
runner, matching ios-silo-boundary-check.py's own established pattern in this repo.
"""
import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent


def find_matching_brace(text: str, open_idx: int, open_char: str, close_char: str) -> int:
    """Returns the index just past the character matching the brace/paren at open_idx,
    or -1 if unbalanced. open_idx must point at open_char."""
    depth = 0
    i = open_idx
    while i < len(text):
        if text[i] == open_char:
            depth += 1
        elif text[i] == close_char:
            depth -= 1
            if depth == 0:
                return i + 1
        i += 1
    return -1


def block_after(text: str, match_end: int) -> str:
    """Given the text immediately after a call like `IconButton(` or `Button(action: ...) {`,
    returns the balanced `{ ... }` block that follows, or '' if none is found nearby."""
    brace_idx = text.find("{", match_end)
    if brace_idx == -1 or brace_idx - match_end > 200:
        return ""
    end = find_matching_brace(text, brace_idx, "{", "}")
    if end == -1:
        return ""
    return text[brace_idx:end]


ANDROID_ICON_BUTTON_RE = re.compile(r"\bIconButton\s*\(")
ANDROID_CONTENT_DESC_NULL_RE = re.compile(r"contentDescription\s*=\s*null\b")
ANDROID_TEXT_RE = re.compile(r"\bText\s*\(")


def check_android(root: Path) -> list[str]:
    violations = []
    for kt_file in sorted(root.rglob("*.kt")):
        if "/build/" in str(kt_file):
            continue
        text = kt_file.read_text(errors="replace")
        for match in ANDROID_ICON_BUTTON_RE.finditer(text):
            block = block_after(text, match.end())
            if not block:
                continue
            if ANDROID_CONTENT_DESC_NULL_RE.search(block) and not ANDROID_TEXT_RE.search(block):
                lineno = text.count("\n", 0, match.start()) + 1
                violations.append(
                    f"{kt_file.relative_to(REPO_ROOT)}:{lineno}: IconButton's only content is an "
                    f"Icon with contentDescription = null -- this icon is the button's entire "
                    f"accessible name, so it's silent to TalkBack. Give it a real description."
                )
    return violations


IOS_BUTTON_RE = re.compile(r"\bButton\s*\(\s*action\s*:")
IOS_IMAGE_SYSTEM_RE = re.compile(r"\bImage\s*\(\s*systemName\s*:")
IOS_ACCESSIBILITY_LABEL_RE = re.compile(r"\.accessibilityLabel\s*\(")
IOS_TEXT_RE = re.compile(r"\bText\s*\(")
# Chained modifiers after a trailing closure's `}` look like `.foo(...)` / `.bar` on the
# same statement -- keep consuming while the next non-comment, non-whitespace char is `.`,
# stopping at anything else (a real modifier chain never contains a bare
# newline-then-non-dot token that's still part of the same expression in this codebase's
# own formatting style). This codebase routinely puts a `// explanation` comment between
# a closing brace and its next chained modifier (see e.g. BenefitsShopAllScreens.swift's
# real, correct `}\n// comment...\n.accessibilityLabel(...)` — a real false positive this
# regex produced before comment-stripping was added), so line comments must be skipped,
# not just plain whitespace.
IOS_LINE_COMMENT_RE = re.compile(r"//[^\n]*")
IOS_CHAINED_MODIFIER_RE = re.compile(r"\A(\s*\.\w+(\([^()]*(?:\([^()]*\)[^()]*)*\))?)+", re.DOTALL)


def strip_line_comments(text: str) -> str:
    return IOS_LINE_COMMENT_RE.sub("", text)


def check_ios(root: Path) -> list[str]:
    violations = []
    for swift_file in sorted(root.rglob("*.swift")):
        if "/build/" in str(swift_file) or "/Tests/" in str(swift_file):
            continue
        text = swift_file.read_text(errors="replace")
        for match in IOS_BUTTON_RE.finditer(text):
            # The button body is the `{ ... }` trailing closure right after the action arg's
            # own closing paren -- find that paren first, then the trailing closure.
            paren_idx = text.find("(", match.start())
            if paren_idx == -1:
                continue
            paren_end = find_matching_brace(text, paren_idx, "(", ")")
            if paren_end == -1:
                continue
            brace_idx = text.find("{", paren_end - 1)
            if brace_idx == -1 or brace_idx - (paren_end - 1) > 200:
                continue
            block_end = find_matching_brace(text, brace_idx, "{", "}")
            if block_end == -1:
                continue
            block = text[brace_idx:block_end]
            if not (IOS_IMAGE_SYSTEM_RE.search(block) and not IOS_TEXT_RE.search(block)):
                continue
            if IOS_ACCESSIBILITY_LABEL_RE.search(block):
                continue
            # Idiomatic SwiftUI applies modifiers like .accessibilityLabel AFTER the
            # trailing closure's own closing brace, not inside it -- check that chain too
            # before flagging, or DeviceStepUpView.swift's own correct usage false-positives.
            chain_match = IOS_CHAINED_MODIFIER_RE.match(strip_line_comments(text[block_end : block_end + 2000]))
            chain = chain_match.group(0) if chain_match else ""
            if IOS_ACCESSIBILITY_LABEL_RE.search(chain):
                continue
            lineno = text.count("\n", 0, match.start()) + 1
            violations.append(
                f"{swift_file.relative_to(REPO_ROOT)}:{lineno}: Button's only content is an "
                f"Image(systemName:) with no .accessibilityLabel anywhere in the button -- "
                f"this icon is the button's entire accessible name, so it's silent to "
                f"VoiceOver. Add .accessibilityLabel(\"...\")."
            )
    return violations


WEB_IMG_RE = re.compile(r"<img\b[^>]*>", re.IGNORECASE)
WEB_ALT_RE = re.compile(r"\balt\s*=", re.IGNORECASE)
# `<img>`-shaped text inside a // or /* */ comment isn't a real JSX tag -- blank it out
# (preserving line count/offsets) before scanning, or a doc comment that merely mentions
# "a plain <img> tag" false-positives as a violation.
WEB_LINE_COMMENT_RE = re.compile(r"//[^\n]*")
WEB_BLOCK_COMMENT_RE = re.compile(r"/\*.*?\*/", re.DOTALL)


def blank_comments(text: str) -> str:
    text = WEB_BLOCK_COMMENT_RE.sub(lambda m: re.sub(r"[^\n]", " ", m.group(0)), text)
    text = WEB_LINE_COMMENT_RE.sub(lambda m: " " * len(m.group(0)), text)
    return text


def check_web(root: Path) -> list[str]:
    violations = []
    for ext in ("*.tsx", "*.jsx"):
        for web_file in sorted(root.rglob(ext)):
            if "/node_modules/" in str(web_file) or "/dist/" in str(web_file) or "/build/" in str(web_file):
                continue
            text = web_file.read_text(errors="replace")
            scan_text = blank_comments(text)
            for match in WEB_IMG_RE.finditer(scan_text):
                tag = match.group(0)
                if WEB_ALT_RE.search(tag):
                    continue
                lineno = text.count("\n", 0, match.start()) + 1
                violations.append(
                    f"{web_file.relative_to(REPO_ROOT)}:{lineno}: <img> has no alt attribute -- "
                    f"add alt=\"...\" (a real description) or alt=\"\" if this image is purely "
                    f"decorative."
                )
    return violations


def main() -> int:
    violations = (
        check_android(REPO_ROOT / "android")
        + check_ios(REPO_ROOT / "ios")
        + check_web(REPO_ROOT / "services")
        + check_web(REPO_ROOT / "packages")
    )
    if violations:
        print(f"Found {len(violations)} accessibility violation(s):\n")
        for v in violations:
            print(f"  {v}")
        print(
            "\nSee docs/DESIGN_REFERENCES.md Section 14 -- every interactive element needs a "
            "real accessible name, matching Toss's own A11y Fundamentals ruleset "
            "(toss.tech/article/A11y_Fundamentals)."
        )
        return 1
    print("No accessibility violations found.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
