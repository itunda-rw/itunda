import { SaroniteBrownfieldModule } from '@itunda/saronite-brownfield-module';
import type { ReferralInfo } from '@itunda/saronite-brownfield-module';

export async function getReferralInfo(): Promise<ReferralInfo> {
  return SaroniteBrownfieldModule.getReferralInfo();
}
