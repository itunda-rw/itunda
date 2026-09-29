import { SaroniteBrownfieldModule } from '@itunda/saronite-brownfield-module';
import type { CreatePremiumFundResult } from '@itunda/saronite-brownfield-module';

export async function createPremiumFund(policyId: string, dailyContribution: number): Promise<CreatePremiumFundResult> {
  return SaroniteBrownfieldModule.createPremiumFund(policyId, dailyContribution);
}
