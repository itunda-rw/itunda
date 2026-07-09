import { SaroniteBrownfieldModule } from '@itunda/saronite-brownfield-module';

/**
 * Mirrors Granite's real `openURL` bridge function, which wraps React
 * Native's own `Linking.openURL`. Saronite routes through the native
 * module instead so the host app controls how external links open
 * (e.g. always in an external browser, never inside the mini-app's
 * own WebView).
 */
export async function openURL(url: string): Promise<void> {
  await SaroniteBrownfieldModule.openURL(url);
}
