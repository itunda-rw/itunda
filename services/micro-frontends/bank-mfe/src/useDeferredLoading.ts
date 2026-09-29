import { useEffect, useState } from 'react';

// Real, sourced finding (KakaoPay tech blog, tech.kakaopay.com/post/skeleton-ui-idea) --
// unconditionally showing a skeleton the instant a fetch starts causes a real
// "flicker" for any response fast enough to already have data back before the human
// eye can register the skeleton as informative rather than broken. KakaoPay's own
// measured data: one of their services had 75% of requests complete within 192ms,
// 90% within 296ms -- meaning most of the time the skeleton appears and disappears in
// the same frame or two. This mirrors Nielsen Norman Group's own published guidance:
// progress indicators should only appear for operations expected to take 1+ second;
// below that, a loop/skeleton animation is distracting because the user can't
// actually follow what's happening. KakaoPay's fix was a `DeferredComponent` that
// delays skeleton display by 200ms, so a response that lands before that never shows
// a skeleton at all -- only a genuinely slow response (the minority) gets one.
//
// itunda has no equivalent primitive anywhere -- every one of this repo's ~105
// `className="skeleton"` call sites (bank-mfe alone; Android/iOS not yet audited)
// shows its skeleton the instant `data === null`, with no delay. This hook is the
// first, deliberately small step: a drop-in replacement for a raw `isLoading`
// boolean that only becomes true after `delayMs` has elapsed, so callers can render
// `{shouldShowSkeleton ? <Skeleton /> : ...}` instead of `{data === null ? ... }`
// without changing anything else about their own fetch logic. Migrating all ~105
// existing call sites is a real, separate, multi-session sweep (matching this
// codebase's own pressScaleClickable/money-formatting sweep precedent) -- this
// commit wires it into one real, live screen as a proven, verified first step, not
// a blanket rewrite.
export function useDeferredLoading(isLoading: boolean, delayMs = 200): boolean {
  const [shouldShow, setShouldShow] = useState(false);

  useEffect(() => {
    if (!isLoading) {
      setShouldShow(false);
      return;
    }
    const timer = setTimeout(() => setShouldShow(true), delayMs);
    return () => clearTimeout(timer);
  }, [isLoading, delayMs]);

  return shouldShow;
}
