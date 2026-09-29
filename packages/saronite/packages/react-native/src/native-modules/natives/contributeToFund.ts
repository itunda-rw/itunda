import { SaroniteBrownfieldModule } from '@itunda/saronite-brownfield-module';
import type { ContributeToFundResult } from '@itunda/saronite-brownfield-module';

export async function contributeToFund(fundId: string, amount: number): Promise<ContributeToFundResult> {
  return SaroniteBrownfieldModule.contributeToFund(fundId, amount);
}
