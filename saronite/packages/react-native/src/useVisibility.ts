import { useEffect, useState } from 'react';
import { onVisibilityChanged } from '@itunda/saronite-brownfield-module';

/**
 * Tracks whether the host app currently has this mini-app's screen
 * foregrounded, via the native module's `onVisibilityChanged` event —
 * the same event Granite's real spec exposes.
 */
export function useVisibility(): boolean {
  const [visible, setVisible] = useState(true);

  useEffect(() => {
    const subscription = onVisibilityChanged((event) => setVisible(event.visible));
    return () => subscription.remove();
  }, []);

  return visible;
}
