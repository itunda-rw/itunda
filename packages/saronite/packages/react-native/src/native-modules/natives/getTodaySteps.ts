import { SaroniteBrownfieldModule } from '@itunda/saronite-brownfield-module';
import type { TodayStepsResult } from '@itunda/saronite-brownfield-module';

export async function getTodaySteps(): Promise<TodayStepsResult> {
  return SaroniteBrownfieldModule.getTodaySteps();
}
