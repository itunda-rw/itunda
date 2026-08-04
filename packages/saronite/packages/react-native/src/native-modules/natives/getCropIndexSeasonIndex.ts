import { SaroniteBrownfieldModule } from '@itunda/saronite-brownfield-module';
import type { CropIndexSeasonIndexResult } from '@itunda/saronite-brownfield-module';

export async function getCropIndexSeasonIndex(district: string, season: string): Promise<CropIndexSeasonIndexResult> {
  return SaroniteBrownfieldModule.getCropIndexSeasonIndex(district, season);
}
