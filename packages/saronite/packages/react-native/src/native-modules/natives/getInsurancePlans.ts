import { SaroniteBrownfieldModule } from '@itunda/saronite-brownfield-module';
import type { InsurancePlansResult } from '@itunda/saronite-brownfield-module';

export async function getInsurancePlans(): Promise<InsurancePlansResult> {
  return SaroniteBrownfieldModule.getInsurancePlans();
}
