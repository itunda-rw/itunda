import { SaroniteBrownfieldModule } from '@itunda/saronite-brownfield-module';
import type { WalletBalanceResult } from '@itunda/saronite-brownfield-module';

/**
 * itunda-specific bridge call — has no Granite equivalent in the public
 * repo (Toss's real money-data bridges live in their private
 * `@apps-in-toss/framework`, not open Granite). Added here the same way:
 * one more method on the same single native module, not a new module.
 *
 * Rejects with `SARONITE_NOT_AUTHENTICATED` if the host app has no signed-in
 * itunda session — mini-apps should treat that as "show a sign-in prompt",
 * not a generic error.
 */
export async function getWalletBalance(): Promise<WalletBalanceResult> {
  return SaroniteBrownfieldModule.getWalletBalance();
}
