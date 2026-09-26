# Native IDS Architecture

## Production rule

Itunda's production client architecture is native-first:

```
Itunda
├── Android
│   └── Kotlin / Jetpack Compose / Android IDS
├── iOS
│   └── Swift / SwiftUI / iOS IDS
├── Web
│   └── Web IDS
└── Shared design contracts
    ├── colors
    ├── typography
    ├── spacing
    ├── motion
    ├── accessibility semantics
    └── component contracts
```

The shared layer defines contracts and tokens. It does not force a cross-platform UI runtime.

## Expo boundary

Expo, Expo Go, Expo Snack, and EAS are **not production dependencies or verification evidence for the native Itunda apps**.

Any historical Expo/Snack prototype is legacy-only. Native production behavior must be exercised from the actual Android and iOS projects.

The repository may still contain React Native/Granite/Saronite infrastructure for mini-app content where explicitly required. That runtime is distinct from Expo and does not change the native IDS ownership of the Android/iOS shells.

## Build authority

| Platform | Authoritative build | Authoritative install | Authoritative verification |
|---|---|---|---|
| Android | Gradle + Android toolchain | Native APK/install | Limrun Android emulator/device |
| iOS | Xcode/xcodebuild | Native simulator/app install | Limrun iOS simulator/device |
| Web | Web toolchain | Browser | Browser verification |

## 100% IDS gate

A platform is not considered verified merely because source code exists or a prototype renders.

The native gate is:

1. Build the real native app.
2. Install the resulting native artifact.
3. Launch the installed app.
4. Traverse the IDS showcase/components.
5. Verify visual rendering and interaction.
6. Verify accessibility tree semantics.
7. Verify disabled/loading/error/selected/indeterminate states where supported.
8. Verify dynamic type/text-size behavior.
9. Verify dark mode.
10. Verify motion/transition behavior.
11. Verify semantic labels, roles, values, and descriptions.
12. Record platform-specific evidence.
13. Only then count the platform toward the 100% IDS gate.

Expo Go, Expo Snack, or an `exp://` launch does not satisfy this gate.

## Native ownership

Android IDS lives under the Android native source tree. iOS IDS lives under the iOS native source tree. Web IDS lives under the web design-system packages.

Shared contracts should be consumed by each platform rather than implemented through a shared UI runtime.

## Verification principle

The strongest evidence is the artifact that ships:

**Gradle-built Android app → installed native Android app → verified**

**Xcode-built iOS app → installed native iOS app → verified**

This is the baseline for future IDS parity work.
