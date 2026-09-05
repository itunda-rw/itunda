import { animate, useMotionValue, useTransform } from 'framer-motion';
import { useEffect, useRef, useState } from 'react';

// Real Toss motion pattern -- see toss.im/tossfeed/article/why-motion-in-finance (fetched
// directly, 2026-08-19, prompted by direct user feedback that itunda's products don't yet
// FEEL like their real references): Toss deliberately animates balance/amount changes
// rather than having a number instantly jump to its new value -- one of several concrete,
// published "graphics with motion, not just a static number" techniques their own design
// team writes about. itunda had zero equivalent anywhere in bank-mfe before this: every
// `.toLocaleString('en-US')` balance display in this app re-renders as a flat instant jump.
//
// Tweens from whatever value was last rendered to the new one over a real, deliberately
// snappy 600ms (Toss's own real published motion principle is "가볍고 경쾌하게," light and
// brisk -- not a slow, heavy animation that makes the user wait to read their own balance).
// Skips the animation entirely on first mount (nothing to animate FROM yet -- an initial
// count-up from zero on every page load would be a gimmick, not the real pattern, which
// only fires on an actual CHANGE while the user is already looking at the screen).
export function useCountUp(value: number, durationSeconds = 0.6): number {
  const motionValue = useMotionValue(value);
  const rounded = useTransform(motionValue, (v) => Math.round(v));
  const isFirstRender = useRef(true);
  const [displayValue, setDisplayValue] = useState(value);

  useEffect(() => {
    const unsubscribe = rounded.on('change', (v) => setDisplayValue(v));
    return unsubscribe;
  }, [rounded]);

  useEffect(() => {
    if (isFirstRender.current) {
      isFirstRender.current = false;
      motionValue.set(value);
      setDisplayValue(value);
      return;
    }
    const controls = animate(motionValue, value, { duration: durationSeconds, ease: 'easeOut' });
    return () => controls.stop();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [value]);

  return displayValue;
}
