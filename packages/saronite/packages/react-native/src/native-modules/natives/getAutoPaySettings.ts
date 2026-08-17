import { SaroniteBrownfieldModule } from '@itunda/saronite-brownfield-module';
import type { AutoPaySettingsResult } from '@itunda/saronite-brownfield-module';

export async function getAutoPaySettings(): Promise<AutoPaySettingsResult> {
  return SaroniteBrownfieldModule.getAutoPaySettings();
}
