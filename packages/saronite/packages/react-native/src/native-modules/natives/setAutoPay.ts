import { SaroniteBrownfieldModule } from '@itunda/saronite-brownfield-module';
import type { SetAutoPayResult } from '@itunda/saronite-brownfield-module';

export async function setAutoPay(
  providerId: string,
  accountNumber: string,
  maxAmount: number,
): Promise<SetAutoPayResult> {
  return SaroniteBrownfieldModule.setAutoPay(providerId, accountNumber, maxAmount);
}
