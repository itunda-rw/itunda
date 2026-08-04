import { SaroniteBrownfieldModule } from '@itunda/saronite-brownfield-module';
import type { CropIndexPolicyResult } from '@itunda/saronite-brownfield-module';

export async function cancelCropIndexPolicy(policyId: string): Promise<CropIndexPolicyResult> {
  return SaroniteBrownfieldModule.cancelCropIndexPolicy(policyId);
}
