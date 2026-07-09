import { SaroniteBrownfieldModule } from '@itunda/saronite-brownfield-module';
import type { PendingBillsResult } from '@itunda/saronite-brownfield-module';

export async function getPendingBills(): Promise<PendingBillsResult> {
  return SaroniteBrownfieldModule.getPendingBills();
}
