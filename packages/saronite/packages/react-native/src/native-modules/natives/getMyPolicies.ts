import { SaroniteBrownfieldModule } from '@itunda/saronite-brownfield-module';
import type { MyPoliciesResult } from '@itunda/saronite-brownfield-module';

export async function getMyPolicies(): Promise<MyPoliciesResult> {
  return SaroniteBrownfieldModule.getMyPolicies();
}
