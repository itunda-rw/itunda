import { SaroniteBrownfieldModule } from '@itunda/saronite-brownfield-module';
import type { BillProvidersResult } from '@itunda/saronite-brownfield-module';

export async function getBillProviders(): Promise<BillProvidersResult> {
  return SaroniteBrownfieldModule.getBillProviders();
}
