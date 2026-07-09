import { SaroniteBrownfieldModule } from '@itunda/saronite-brownfield-module';

/**
 * Mirrors Granite's real bridge function (found in its open-source repo):
 * a thin async wrapper that just forwards to the native module's
 * `closeView()`. Asks the host app to dismiss this mini-app's screen.
 */
export async function closeView(): Promise<void> {
  await SaroniteBrownfieldModule.closeView();
}
