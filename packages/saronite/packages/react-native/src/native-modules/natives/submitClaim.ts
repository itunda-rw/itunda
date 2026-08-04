import { SaroniteBrownfieldModule } from '@itunda/saronite-brownfield-module';
import type { SubmitClaimResult } from '@itunda/saronite-brownfield-module';

export async function submitClaim(
  policyId: string,
  description: string,
  amount: number,
): Promise<SubmitClaimResult> {
  return SaroniteBrownfieldModule.submitClaim(policyId, description, amount);
}
