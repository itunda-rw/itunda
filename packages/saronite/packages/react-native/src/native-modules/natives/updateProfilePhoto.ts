import { SaroniteBrownfieldModule } from '@itunda/saronite-brownfield-module';
import type { ProfileResult } from '@itunda/saronite-brownfield-module';

export async function updateProfilePhoto(profilePhotoUrl: string): Promise<ProfileResult> {
  return SaroniteBrownfieldModule.updateProfilePhoto(profilePhotoUrl);
}
