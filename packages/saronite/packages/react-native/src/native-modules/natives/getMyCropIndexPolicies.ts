import { SaroniteBrownfieldModule } from '@itunda/saronite-brownfield-module';
import type { MyCropIndexPoliciesResult } from '@itunda/saronite-brownfield-module';

export async function getMyCropIndexPolicies(): Promise<MyCropIndexPoliciesResult> {
  return SaroniteBrownfieldModule.getMyCropIndexPolicies();
}
