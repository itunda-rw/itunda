import { SaroniteBrownfieldModule } from '@itunda/saronite-brownfield-module';
import type { CancelFundResult } from '@itunda/saronite-brownfield-module';

export async function cancelFund(fundId: string): Promise<CancelFundResult> {
  return SaroniteBrownfieldModule.cancelFund(fundId);
}
