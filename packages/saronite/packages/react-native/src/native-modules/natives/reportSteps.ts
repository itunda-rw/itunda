import { SaroniteBrownfieldModule } from '@itunda/saronite-brownfield-module';
import type { StepReportResult } from '@itunda/saronite-brownfield-module';

export async function reportSteps(steps: number): Promise<StepReportResult> {
  return SaroniteBrownfieldModule.reportSteps(steps);
}
