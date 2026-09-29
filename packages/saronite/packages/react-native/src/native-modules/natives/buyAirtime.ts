import { SaroniteBrownfieldModule } from '@itunda/saronite-brownfield-module';
import type { BuyAirtimeResult } from '@itunda/saronite-brownfield-module';

export async function buyAirtime(
  phoneNumber: string,
  amount: number,
  provider: string,
): Promise<BuyAirtimeResult> {
  return SaroniteBrownfieldModule.buyAirtime(phoneNumber, amount, provider);
}
