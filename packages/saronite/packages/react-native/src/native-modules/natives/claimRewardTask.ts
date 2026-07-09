import { SaroniteBrownfieldModule } from '@itunda/saronite-brownfield-module';
import type { ClaimRewardResult } from '@itunda/saronite-brownfield-module';

export async function claimRewardTask(taskId: string): Promise<ClaimRewardResult> {
  return SaroniteBrownfieldModule.claimRewardTask(taskId);
}
