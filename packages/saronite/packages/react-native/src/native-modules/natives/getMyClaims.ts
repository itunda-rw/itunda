import { SaroniteBrownfieldModule } from '@itunda/saronite-brownfield-module';
import type { MyClaimsResult } from '@itunda/saronite-brownfield-module';

export async function getMyClaims(): Promise<MyClaimsResult> {
  return SaroniteBrownfieldModule.getMyClaims();
}
