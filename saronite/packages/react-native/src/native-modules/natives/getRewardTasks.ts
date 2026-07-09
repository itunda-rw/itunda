import { SaroniteBrownfieldModule } from '@itunda/saronite-brownfield-module';
import type { RewardTasksResult } from '@itunda/saronite-brownfield-module';

export async function getRewardTasks(): Promise<RewardTasksResult> {
  return SaroniteBrownfieldModule.getRewardTasks();
}
