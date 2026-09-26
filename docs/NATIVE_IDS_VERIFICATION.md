# Native IDS Verification Gate

This document defines the evidence required before calling Itunda's native IDS verification complete.

## Android

- Build with Gradle from android/.
- Install the generated native APK on the verification emulator/device.
- Do not launch through Expo Go or an exp:// URL.
- Traverse the native IDS showcase.
- Verify component semantics through the Android accessibility/UI hierarchy.
- Cover interactive states: default, pressed, focused, disabled, loading/updating, error, selected, and indeterminate/mixed where applicable.
- Verify dark mode.
- Verify large text/font scaling and layout resilience.
- Verify motion/transition behavior.
- Capture build/install/runtime evidence.

## iOS

- Build with Xcode/xcodebuild from ios/.
- Install the generated native simulator/device app.
- Do not use Expo Go as evidence.
- Traverse the native IDS showcase.
- Verify VoiceOver/accessibility semantics exposed by the native accessibility tree.
- Cover the same state matrix, including platform-specific semantics.
- Verify dark mode.
- Verify Dynamic Type and layout resilience.
- Verify motion/transition behavior.
- Capture build/install/runtime evidence.

## Cross-platform contract

The Android and iOS implementations may differ internally, but they must satisfy the same shared design contracts:

- token values and semantic color roles
- typography hierarchy
- spacing and sizing
- component state contracts
- accessibility semantics
- interaction/motion contracts
- localization/text expansion behavior

## Completion rule

100% means the required contract has been verified on the actual native artifact for both Android and iOS. Source inspection alone, a web rendering, a React Native prototype, Expo Go, or Expo Snack is not sufficient evidence.

Web IDS verification is tracked separately and must not be substituted for native evidence.
