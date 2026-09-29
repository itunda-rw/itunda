import { SaroniteBrownfieldModule } from '@itunda/saronite-brownfield-module';
import type { CropIndexCatalogResult } from '@itunda/saronite-brownfield-module';

export async function getCropIndexCatalog(): Promise<CropIndexCatalogResult> {
  return SaroniteBrownfieldModule.getCropIndexCatalog();
}
