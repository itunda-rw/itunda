import { SaroniteBrownfieldModule } from '@itunda/saronite-brownfield-module';

/**
 * Mirrors Granite's real `getSchemeUri` constant-bridge: reads a value
 * exposed via the native module's `getConstants()` rather than calling
 * across the bridge for something that never changes at runtime.
 */
export function getSchemeUri(): string {
  return SaroniteBrownfieldModule.getConstants().schemeUri;
}
