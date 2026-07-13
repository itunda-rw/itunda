import { SaroniteBrownfieldModule } from '@itunda/saronite-brownfield-module';
import type { EnrollInsuranceResult } from '@itunda/saronite-brownfield-module';

export async function enrollInsurance(planId: string): Promise<EnrollInsuranceResult> {
  return SaroniteBrownfieldModule.enrollInsurance(planId);
}
