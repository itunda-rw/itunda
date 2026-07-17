import { SaroniteBrownfieldModule } from '@itunda/saronite-brownfield-module';
import type { ProfileResult } from '@itunda/saronite-brownfield-module';

export async function confirmEmailVerification(token: string): Promise<ProfileResult> {
  return SaroniteBrownfieldModule.confirmEmailVerification(token);
}
