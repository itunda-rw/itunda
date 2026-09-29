# IDS Platform Implementations

The component contract is the source of truth. These are the current reference implementations on the shared branch.

| Component | Web | Android | iOS |
|---|---|---|---|
| Button | `packages/design-system-web/src/index.ts#Button` | `android/core/designsystem/src/main/java/rw/itunda/core/designsystem/components/IdsButton.kt#IdsButton` | `ios/Core/DesignSystem/Sources/Components/Components.swift#IdsButton` |
| TextField | `packages/design-system-web/src/index.ts#TextField` | `android/core/designsystem/src/main/java/rw/itunda/core/designsystem/components/IdsButton.kt#IdsTextField` | `ios/Core/DesignSystem/Sources/Components/Components.swift#IdsTextField` |
| Select | `packages/design-system-web/src/index.ts#Select` | `android/core/designsystem/src/main/java/rw/itunda/core/designsystem/components/IdsButton.kt#IdsSelect` | `ios/Core/DesignSystem/Sources/Components/Components.swift#IdsSelect` |
| Checkbox | `packages/design-system-web/src/index.ts#Checkbox` | `android/core/designsystem/src/main/java/rw/itunda/core/designsystem/components/IdsButton.kt#IdsCheckbox` | `ios/Core/DesignSystem/Sources/Components/Components.swift#IdsCheckbox` |
| Radio | `packages/design-system-web/src/index.ts#Radio` | `android/core/designsystem/src/main/java/rw/itunda/core/designsystem/components/IdsButton.kt#IdsRadio` | `ios/Core/DesignSystem/Sources/Components/Components.swift#IdsRadio` |
| Switch | `packages/design-system-web/src/index.ts#Switch` | `android/core/designsystem/src/main/java/rw/itunda/core/designsystem/components/IdsButton.kt#IdsSwitch` | `ios/Core/DesignSystem/Sources/Components/Components.swift#IdsSwitch` |
| Tabs | `packages/design-system-web/src/index.ts#Tabs` | `android/core/designsystem/src/main/java/rw/itunda/core/designsystem/components/IdsButton.kt#IdsTabs` | `ios/Core/DesignSystem/Sources/Components/Components.swift#IdsTabs` |
| Empty state | `packages/design-system-web/src/index.ts#EmptyState` | `android/core/designsystem/src/main/java/rw/itunda/core/designsystem/components/IdsButton.kt#IdsEmptyState` | `ios/Core/DesignSystem/Sources/Components/Components.swift#IdsEmptyState` |

## Verification levels

**Mapped** means the shared primitive exists at the referenced source location.

**Contract-checked** means its API and documented states have been reviewed against the IDS contract.

**Build-verified** requires a successful platform build and is intentionally not claimed here.

The laboratory must not treat "mapped" as "build-verified".
