import { SaroniteBrownfieldModule } from '@itunda/saronite-brownfield-module';

export async function clearAutoPay(providerId: string): Promise<void> {
  return SaroniteBrownfieldModule.clearAutoPay(providerId);
}
