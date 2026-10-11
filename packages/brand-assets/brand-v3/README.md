# Itunda Brand v3 migration staging

This directory holds the Brand v3 semantic token proposal while the repository's existing `itunda-brand.zip` remains locked by the canonical-asset verification workflow.

## Current tokens

- Brand: `#8F89FF` in light and dark modes
- Pressed: `#7D77F7`
- On-brand text: `#121216`
- Brand text on light surfaces: `#6960F9`
- Brand text on dark surfaces: `#8F89FF`
- Light tint: `#F1F0FF`
- Dark tint: `#25234A`

## Migration boundary

Do not edit files under `packages/brand-assets/canonical/` independently. CI verifies those files byte-for-byte against the checked-in canonical ZIP and checksum. The supplied Brand v3 package must be promoted as one coordinated change: replace the source archive, update its expected Git blob identity, archive SHA-256, extracted assets and manifest, then update web, Android, and iOS references together.

The token files here are staged for review; they do not mean that Brand v3 app icons or native launch assets have already shipped. Verify Android and iOS rendered assets before release.
