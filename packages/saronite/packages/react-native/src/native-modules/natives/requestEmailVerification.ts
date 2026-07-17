import { SaroniteBrownfieldModule } from '@itunda/saronite-brownfield-module';

export async function requestEmailVerification(): Promise<void> {
  return SaroniteBrownfieldModule.requestEmailVerification();
}
