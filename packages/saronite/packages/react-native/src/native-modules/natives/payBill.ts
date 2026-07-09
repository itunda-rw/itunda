import { SaroniteBrownfieldModule } from '@itunda/saronite-brownfield-module';
import type { PayBillResult } from '@itunda/saronite-brownfield-module';

export async function payBill(
  billId: string,
  amount: number,
  accountNumber: string,
  provider: string,
): Promise<PayBillResult> {
  return SaroniteBrownfieldModule.payBill(billId, amount, accountNumber, provider);
}
