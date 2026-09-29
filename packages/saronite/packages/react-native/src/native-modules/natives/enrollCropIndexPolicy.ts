import { SaroniteBrownfieldModule } from '@itunda/saronite-brownfield-module';
import type { CropIndexPolicyResult, WeatherIndexCropType } from '@itunda/saronite-brownfield-module';

export async function enrollCropIndexPolicy(
  cropType: WeatherIndexCropType,
  district: string,
  season: string,
  insuredAmount: number,
): Promise<CropIndexPolicyResult> {
  return SaroniteBrownfieldModule.enrollCropIndexPolicy(cropType, district, season, insuredAmount);
}
