import { SaroniteBrownfieldModule } from '@itunda/saronite-brownfield-module';
import type { MyPremiumFundsResult } from '@itunda/saronite-brownfield-module';

export async function getMyPremiumFunds(): Promise<MyPremiumFundsResult> {
  return SaroniteBrownfieldModule.getMyPremiumFunds();
}
