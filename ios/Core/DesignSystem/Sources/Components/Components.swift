import SwiftUI

// Real Toss micro-interaction reference (2026-08-22, "toss interactions" --
// direct user directive to bring itunda's real tap feedback up to Toss's own
// published standard, toss.im's motion-strategy writeup: "시각적 신호가 탭이
// 발생하는 정확한 순간에 햅틱/사용자 액션과 동기화되어야 한다" -- visual cues
// synchronized precisely with the tap). Android's IdsButton.kt/IdsListRow.kt/
// IdsIconButton already had this (`rememberPressScale`, 0.96 scale + a medium-
// bouncy high-stiffness spring) and web's global `button:active { transform:
// scale(0.96) }` already had it too -- iOS's IdsButton/IdsListRow were the one
// real gap, plain `Button`s with zero press feedback despite being the app's
// two most-tapped shared components. `ButtonStyle` (not a raw `.scaleEffect`
// tacked onto each view) is the real, idiomatic SwiftUI mechanism for this --
// `configuration.isPressed` already tracks touch-down/up, and any custom
// `Button` using this style gets a real, correctly-timed press animation for
// free, matching Android's `rememberPressScale`'s "one shared spec, not a
// per-call-site value that can drift" principle exactly.
public struct PressScaleButtonStyle: ButtonStyle {
    var enabled: Bool = true

    public init(enabled: Bool = true) {
        self.enabled = enabled
    }

    // Real fix (2026-08-24, Toss motion-curve sourcing pass): was a hand-picked
    // .spring(response:dampingFraction:) -- now IDS.Motion.springQuick, the real
    // Toss "quick" spring preset (see IDS.swift's own Motion struct sourcing
    // comment), matching web's identical button:active timing and Android's
    // pressScaleClickable spring.
    public func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed && enabled ? 0.96 : 1)
            .animation(IDS.Motion.springQuick, value: configuration.isPressed)
    }
}

// Real shared empty/error-state components (2026-08-04) -- closes
// docs/DESIGN_REFERENCES.md Section 9's #1 recommendation on iOS, which had
// literally zero shared component for this (Android already built EmptyState/
// ErrorCard in core/designsystem's HoodShared.kt in July; iOS never got the
// equivalent, so every screen's own bare `Text("No X yet.")`/
// `Text(error).foregroundColor(.red)` never had anywhere shared to migrate to).
// Same visual language as Android's EmptyState: an icon in a soft circular
// badge, centered title text below -- SF Symbols standing in for Android's
// Material icon set (no icon asset parity attempted, just the same shape).
public struct EmptyStateView: View {
    let message: String
    let systemImage: String

    public init(_ message: String, systemImage: String = "tray") {
        self.message = message
        self.systemImage = systemImage
    }

    public var body: some View {
        VStack(spacing: 12) {
            ZStack {
                Circle()
                    .fill(IDS.Colors.backgroundTertiary)
                    .frame(width: 56, height: 56)
                Image(systemName: systemImage)
                    .foregroundColor(IDS.Colors.textSecondary)
            }
            Text(message)
                .font(IdsTypeScale.body2)
                .foregroundColor(IDS.Colors.textSecondary)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 32)
    }
}

// Real fix, found live 2026-08-05: EmptyStateView got the real icon-in-circle
// treatment above, but its own sibling ErrorCardView -- shown right next to it in
// the exact same load-failure branches across the app -- stayed plain red text + a
// bare "Retry" text link, the exact gap Android's own identical ErrorCard fix (same
// date) closed. Mirrors EmptyStateView's centered icon-circle layout exactly, using
// IDS.Colors.dangerTint for the circle since this is an error, not a neutral empty
// state, and a real IdsButton for Retry instead of a plain text Button.
public struct ErrorCardView: View {
    let message: String
    let onRetry: () -> Void

    public init(_ message: String, onRetry: @escaping () -> Void) {
        self.message = message
        self.onRetry = onRetry
    }

    public var body: some View {
        VStack(spacing: 12) {
            ZStack {
                Circle()
                    .fill(IDS.Colors.dangerTint)
                    .frame(width: 56, height: 56)
                Image(systemName: "exclamationmark.circle")
                    .foregroundColor(IDS.Colors.danger)
            }
            Text(message)
                .font(IdsTypeScale.body2)
                .foregroundColor(IDS.Colors.textSecondary)
                .multilineTextAlignment(.center)
            IdsButton(text: "Retry", action: onRetry)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 24)
        .padding(.horizontal, 20)
        .background(IDS.Colors.card)
        .cornerRadius(IDS.Layout.cardCornerRadius).idsCardBorder(cornerRadius: IDS.Layout.cardCornerRadius)
    }
}

public enum IdsButtonVariant {
    case filled
    case tinted
    case tertiary
    case destructive
}

public enum IdsButtonSize {
    case large
    case medium
    case small

    var height: CGFloat {
        switch self {
        case .large: return 56
        case .medium: return 48
        case .small: return 44
        }
    }
}

public struct IdsButton: View {
    let text: String
    let action: () -> Void
    var isEnabled: Bool = true
    var isLoading: Bool = false
    var variant: IdsButtonVariant = .filled
    var size: IdsButtonSize = .large
    var fullWidth: Bool = true

    public init(
        text: String,
        isEnabled: Bool = true,
        isLoading: Bool = false,
        variant: IdsButtonVariant = .filled,
        size: IdsButtonSize = .large,
        fullWidth: Bool = true,
        action: @escaping () -> Void
    ) {
        self.text = text
        self.isEnabled = isEnabled
        self.isLoading = isLoading
        self.variant = variant
        self.size = size
        self.fullWidth = fullWidth
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            Group {
                if isLoading {
                    ProgressView()
                        .tint(variant == .filled ? IDS.Colors.textBrand : IDS.Colors.brand)
                } else {
                    Text(text)
                        .font(IdsTypeScale.button)
                }
            }
            .frame(maxWidth: fullWidth ? .infinity : nil)
            .frame(minWidth: fullWidth ? nil : 44, minHeight: size.height)
        }
        .foregroundColor(
            !isEnabled ? IDS.Colors.textTertiary :
            variant == .filled ? IDS.Colors.textBrand :
            variant == .destructive ? IDS.Colors.danger :
            IDS.Colors.brand
        )
        .background(
            !isEnabled ? IDS.Colors.divider :
            variant == .filled ? IDS.Colors.brand :
            variant == .destructive ? IDS.Colors.dangerTint :
            variant == .tinted ? IDS.Colors.chipBackground :
            Color.clear
        )
        .cornerRadius(size == .small ? 10 : 12)
        .buttonStyle(PressScaleButtonStyle(enabled: isEnabled && !isLoading))
        .disabled(!isEnabled || isLoading)
        .accessibilityAddTraits(.isButton)
        .accessibilityValue(isLoading ? "Loading" : "")
        .accessibilityHint(variant == .destructive ? "Destructive action" : "")
    }
}

// Real Toss FixedBottomCTA port (2026-08-26, direct user follow-up against real Toss
// Bank product-intro screenshots: "toss always keep those confirm buttons in bottom
// in many cases regardless of contents... when contents are many users needs to
// scroll to see other contents they still see that buttons to click anytime they
// make up their mind"). Web already has this exact pattern (`FullScreenFlow` in
// BankDashboard.tsx: flex column, scrollable content in flex:1, a bottomCTA pinned
// below with a border-top separator) and it was already the standard for every
// multi-step Bank flow there -- iOS never got a port (a real, previously self-named
// open gap, see CreateSavingsGoalScreen.swift's own doc comment and
// project_itunda_product_feel roadmap item 10), so every iOS "create X" screen put
// its primary button INLINE at the end of the scrolling form content instead, where
// it's invisible until the user scrolls all the way down. This wraps that same
// flex-column layout: `content` scrolls in the available space, `cta` stays fixed
// below it with the same divider-colored top border and safe-area-aware bottom
// padding as web's version.
public struct FixedBottomCTA<Content: View, CTA: View>: View {
    let content: Content
    let cta: CTA

    public init(@ViewBuilder content: () -> Content, @ViewBuilder cta: () -> CTA) {
        self.content = content()
        self.cta = cta()
    }

    public var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                content
            }
            VStack(spacing: 0) {
                Divider().background(IDS.Colors.divider)
                cta
                    .padding(.horizontal, 20)
                    .padding(.top, 12)
                    .padding(.bottom, 12)
            }
            .background(IDS.Colors.backgroundPrimary)
        }
    }
}

// Real fix, found live 2026-08-05 auditing the app (user flagged the whole app still
// looks unstyled): this design system had IdsButton/IdsListRow but no text field at
// all -- 51 files / 247 raw `TextField(...)` call sites (confirmed via a repo-wide
// grep), every one hand-rolling its own `.padding().background(Color(.systemXyz) or
// IDS.Colors.chipBackground).cornerRadius()` combination, none sharing one real
// style. Matches Android's own IdsTextField.kt fix from 2026-08-03 (same root cause,
// same shape of fix): a filled field, muted at rest, lifted with a brand-colored
// ring on focus -- not Android's floating Material label (SwiftUI/iOS convention is
// a fixed label above the field, not an animated inset one), but the same intent.
public struct IdsTextField: View {
    let label: String
    @Binding var text: String
    var isSecure: Bool = false
    var keyboardType: UIKeyboardType = .default
    var supportingText: String? = nil
    var errorText: String? = nil
    var successText: String? = nil
    // Real "No More Loading"/"Minimum Input" simplicity addition (Toss's own researched,
    // sourced pattern -- toss.tech/article/4-ways-for-minimum-input, rule #4: "Activate
    // focus state upon entering the page so the keyboard appears automatically" -- see
    // docs/DESIGN_REFERENCES.md §11 recommendation #2, closing the Android-only gap left
    // by that same-day fix). Defaults to false so every existing call site keeps its
    // current behavior unchanged -- only a field that's the obvious, sole next action on
    // its screen (e.g. a just-sent verification code) should opt in.
    var autoFocus: Bool = false

    @FocusState private var isFocused: Bool
    // Real "Minimum Input" simplicity addition (docs/DESIGN_REFERENCES.md §11/§12): a
    // show/hide toggle cuts mistyped-password retries -- a local, client-only UI
    // affordance (the value never leaves this field either way), not a security
    // control, matching §12's own "security and simplicity together" framing. Only
    // meaningful when isSecure is true; ignored otherwise.
    @State private var passwordVisible = false

    public init(_ label: String, text: Binding<String>, isSecure: Bool = false, keyboardType: UIKeyboardType = .default, supportingText: String? = nil, errorText: String? = nil, successText: String? = nil, autoFocus: Bool = false) {
        self.label = label
        self._text = text
        self.isSecure = isSecure
        self.keyboardType = keyboardType
        self.supportingText = supportingText
        self.errorText = errorText
        self.successText = successText
        self.autoFocus = autoFocus
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label)
                .font(IdsTypeScale.label)
                .foregroundColor(
                    errorText != nil ? IDS.Colors.danger :
                    successText != nil ? IDS.Colors.success :
                    isFocused ? IDS.Colors.brand : IDS.Colors.textSecondary
                )
            HStack(spacing: 8) {
                Group {
                    if isSecure && !passwordVisible {
                        SecureField(label, text: $text)
                    } else {
                        TextField(label, text: $text)
                            .keyboardType(keyboardType)
                    }
                }
                .focused($isFocused)
                if isSecure {
                    // Real touch-target-size fix (WCAG 2.5.8, this session's own already-
                    // established convention): a bare Image(systemName:) inside a Button has
                    // no automatic minimum tap area on iOS, unlike Android's IconButton --
                    // explicit frame required.
                    Button(action: { passwordVisible.toggle() }) {
                        Group {
                            if passwordVisible {
                                IDS.Icons.eyeOff(size: 20, color: IDS.Colors.textTertiary)
                            } else {
                                IDS.Icons.eye(size: 20, color: IDS.Colors.textTertiary)
                            }
                        }
                        .frame(width: 24, height: 24)
                    }
                    .buttonStyle(PressScaleButtonStyle())
                    .accessibilityLabel(passwordVisible ? "Hide password" : "Show password")
                }
            }
            .font(IdsTypeScale.subtitle1)
            .foregroundColor(IDS.Colors.textPrimary)
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .background(isFocused ? IDS.Colors.card : IDS.Colors.chipBackground)
            .cornerRadius(12)
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(
                        errorText != nil ? IDS.Colors.danger :
                        successText != nil ? IDS.Colors.success :
                        isFocused ? IDS.Colors.brand : Color.clear,
                        lineWidth: 1.5
                    )
            )
            if let validation = errorText ?? successText ?? supportingText {
                Text(validation)
                    .font(IdsTypeScale.caption)
                    .foregroundColor(
                        errorText != nil ? IDS.Colors.danger :
                        successText != nil ? IDS.Colors.success :
                        IDS.Colors.textSecondary
                    )
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .accessibilityHint(errorText ?? successText ?? supportingText ?? "")
        .onAppear {
            if autoFocus { isFocused = true }
        }
    }
}

// Real Coupang badge system (2026-08-05) -- see docs/DESIGN_REFERENCES.md Section 9's
// own account: a two-tier delivery badge tied to a real, named benefit tier, not a
// decorative label. Ports Android's own IdsBadge/StatusBadge (core/designsystem/
// components/HoodShared.kt) to iOS -- itunda's own real Time Deal countdown/remaining-
// quantity already used this shape on Android; iOS/web still rendered plain text.
public struct IdsBadge: View {
    let text: String
    var filled: Bool = true
    var tint: Color = IDS.Colors.brand

    public init(_ text: String, filled: Bool = true, tint: Color = IDS.Colors.brand) {
        self.text = text
        self.filled = filled
        self.tint = tint
    }

    public var body: some View {
        Text(text)
            .font(.caption2).fontWeight(.bold)
            .foregroundColor(filled ? .white : tint)
            .padding(.horizontal, 6).padding(.vertical, 2)
            .background(filled ? tint : tint.opacity(0.12))
            .cornerRadius(6)
    }
}

// Real shared shimmer skeleton loading state (item 239) -- closes
// docs/DESIGN_REFERENCES.md Section 7's cross-platform-consistency note: bank-mfe
// (a real `.skeleton` CSS shimmer class) and Android (`SkeletonBlock`,
// `core/designsystem`'s `HoodShared.kt`) both already had a real animated shaped
// placeholder for loading states; iOS had no shared equivalent at all and fell back to
// a plain `ProgressView()` spinner everywhere -- a real cross-platform inconsistency in
// the loading-state visual language, not a missing capability (iOS always showed
// *something* while loading, just not the same shaped-placeholder shape as the other
// two platforms). Same animated left-to-right gradient sweep as bank-mfe/Android's own.
// Real KakaoPay finding (tech.kakaopay.com/post/skeleton-ui-idea, ported to bank-mfe's
// useDeferredLoading and Android's SkeletonBlock 2026-09-03): showing a skeleton the
// instant a fetch starts flickers on any response fast enough to already have data back
// before a human can register the shimmer as informative rather than broken -- KakaoPay's
// own measured data showed most of their requests completing in 110-300ms. Since every
// real call site already only shows this view while its own data is nil, the 200ms delay
// lives here once rather than needing a per-call-site wrapper: nothing renders until the
// delay elapses, so a response that lands first never shows a skeleton at all.
public struct SkeletonBlock: View {
    let height: CGFloat
    @State private var animating = false
    @State private var showSkeleton = false

    public init(height: CGFloat = 120) {
        self.height = height
    }

    public var body: some View {
        Group {
            if showSkeleton {
                RoundedRectangle(cornerRadius: IDS.Layout.cardCornerRadius)
                    .fill(IDS.Colors.chipBackground)
                    .overlay(
                        LinearGradient(
                            colors: [IDS.Colors.chipBackground, IDS.Colors.card, IDS.Colors.chipBackground],
                            startPoint: animating ? .trailing : .leading,
                            endPoint: animating ? UnitPoint(x: 2, y: 0.5) : UnitPoint(x: -1, y: 0.5)
                        )
                        .clipShape(RoundedRectangle(cornerRadius: IDS.Layout.cardCornerRadius))
                    )
                    .frame(maxWidth: .infinity)
                    .frame(height: height)
                    .onAppear {
                        withAnimation(.linear(duration: 1).repeatForever(autoreverses: false)) {
                            animating = true
                        }
                    }
            }
        }
        .task {
            try? await Task.sleep(nanoseconds: 200_000_000)
            showSkeleton = true
        }
    }
}

/** IDS 3.0 shared loading primitive for screen-level and inline async states. */
public struct IdsLoading: View {
    let label: String?

    public init(label: String? = nil) {
        self.label = label
    }

    public var body: some View {
        VStack(spacing: 10) {
            ProgressView()
                .tint(IDS.Colors.brand)
            if let label {
                Text(label)
                    .font(IdsTypeScale.body2)
                    .foregroundColor(IDS.Colors.textSecondary)
                    .multilineTextAlignment(.center)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 24)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(Text(label ?? "Loading"))
    }
}

/** IDS 3.0 shared inline error primitive. */
public struct IdsErrorText: View {
    let message: String

    public init(_ message: String) {
        self.message = message
    }

    public var body: some View {
        Text(message)
            .font(IdsTypeScale.body2)
            .foregroundColor(IDS.Colors.danger)
            .fixedSize(horizontal: false, vertical: true)
            .accessibilityLabel(Text(message))
    }
}

public struct IdsListRow: View {
    let title: String
    let subtitle: String?
    let rightText: String?
    let action: () -> Void

    public init(title: String, subtitle: String? = nil, rightText: String? = nil, action: @escaping () -> Void) {
        self.title = title
        self.subtitle = subtitle
        self.rightText = rightText
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text(title)
                        .font(IdsTypeScale.subtitle1)
                        .foregroundColor(IDS.Colors.textPrimary)

                    if let subtitle = subtitle {
                        Text(subtitle)
                            .font(IdsTypeScale.body2)
                            .foregroundColor(IDS.Colors.textSecondary)
                    }
                }
                Spacer()
                if let rightText = rightText {
                    Text(rightText)
                        .font(IdsTypeScale.subtitle1)
                        .foregroundColor(IDS.Colors.textPrimary)
                }
            }
            .padding(.horizontal, 24)
            .padding(.vertical, 16)
            // Real fix (2026-07-13): same static-vs-reactive bug as IdsButton above --
            // this row's background stayed light-mode white in dark mode.
            .background(IDS.Colors.card)
            // Real fix (2026-08-24): a flat full-width row, not a rounded card (no
            // cornerRadius anywhere on this component) -- IDS.Colors.backgroundPrimary
            // is now white in light mode too, so this needed a hairline bottom divider,
            // not idsCardBorder's rounded-rect stroke, matching web's identical
            // .itunda-flat-section row-divider treatment for the same flat-row shape.
            .overlay(Rectangle().fill(IDS.Colors.divider).frame(height: 1), alignment: .bottom)
        }
        .buttonStyle(PressScaleButtonStyle())
        .accessibilityLabel(Text(title))
        .accessibilityValue(Text([subtitle, rightText].compactMap { $0 }.joined(separator: ". ")))
    }
}

// Real shared 5-star input row -- moved here from App/Sources/ShopBrowseComponents.swift
// (2026-08-25, real reviews needed this from a Features/ module too, see
// MapsBooking.swift's BookingReviewButton, but Features/ can't depend back on App/ --
// same shared home Android's identical StarRatingRow already has in
// core.designsystem.components). Any real review-writing flow can reuse this now,
// not just Shop's own.
public struct StarRatingRow: View {
    let value: Int
    let onChange: (Int) -> Void

    public init(value: Int, onChange: @escaping (Int) -> Void) {
        self.value = value
        self.onChange = onChange
    }

    public var body: some View {
        HStack(spacing: 4) {
            ForEach(1...5, id: \.self) { n in
                Button(action: { onChange(n) }) {
                    IDS.Icons.star(size: 24, color: n <= value ? .yellow : IDS.Colors.textTertiary)
                }
                .accessibilityLabel("Rate \(n) star\(n == 1 ? "" : "s")")
            }
        }
    }
}


public struct IdsSelect: View {
    let label: String
    @Binding var selection: String
    let options: [String]
    var supportingText: String?
    var errorText: String?
    var isEnabled: Bool

    public init(
        _ label: String,
        selection: Binding<String>,
        options: [String],
        supportingText: String? = nil,
        errorText: String? = nil,
        isEnabled: Bool = true
    ) {
        self.label = label
        self._selection = selection
        self.options = options
        self.supportingText = supportingText
        self.errorText = errorText
        self.isEnabled = isEnabled
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label)
                .font(IdsTypeScale.label)
                .foregroundColor(errorText == nil ? IDS.Colors.textSecondary : IDS.Colors.danger)
            Picker(label, selection: $selection) {
                ForEach(options, id: \.self) { option in
                    Text(option).tag(option)
                }
            }
            .pickerStyle(.menu)
            .frame(maxWidth: .infinity, minHeight: 56)
            .padding(.horizontal, 12)
            .background(IDS.Colors.card)
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(errorText == nil ? IDS.Colors.divider : IDS.Colors.danger, lineWidth: errorText == nil ? 1 : 1.5)
            )
            .contentShape(RoundedRectangle(cornerRadius: 12))
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .disabled(!isEnabled)
            if let message = errorText ?? supportingText {
                Text(message)
                    .font(IdsTypeScale.caption)
                    .foregroundColor(errorText == nil ? IDS.Colors.textSecondary : IDS.Colors.danger)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .opacity(isEnabled ? 1 : 0.55)
        .accessibilityLabel(Text(label))
    }
}

public struct IdsCheckbox: View {
    let label: String
    @Binding var isChecked: Bool
    var isEnabled: Bool

    public init(_ label: String, isChecked: Binding<Bool>, isEnabled: Bool = true) {
        self.label = label
        self._isChecked = isChecked
        self.isEnabled = isEnabled
    }

    public var body: some View {
        Button {
            isChecked.toggle()
        } label: {
            HStack(spacing: 12) {
                Image(systemName: isChecked ? "checkmark.square.fill" : "square")
                    .font(.system(size: 22))
                    .foregroundColor(isChecked ? IDS.Colors.brand : IDS.Colors.iconSecondary)
                    .frame(width: 44, height: 44)
                Text(label)
                    .font(IdsTypeScale.body2)
                    .foregroundColor(IDS.Colors.textPrimary)
                    .multilineTextAlignment(.leading)
                Spacer(minLength: 0)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(PressScaleButtonStyle(enabled: isEnabled))
        .disabled(!isEnabled)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(label))
        .accessibilityValue(Text(isChecked ? "Checked" : "Unchecked"))
        .accessibilityAddTraits(isChecked ? .isSelected : [])
        .accessibilityRemoveTraits(.isButton)
    }
}

public struct IdsRadio: View {
    let label: String
    @Binding var isSelected: Bool
    var isEnabled: Bool

    public init(_ label: String, isSelected: Binding<Bool>, isEnabled: Bool = true) {
        self.label = label
        self._isSelected = isSelected
        self.isEnabled = isEnabled
    }

    public var body: some View {
        Button {
            isSelected = true
        } label: {
            HStack(spacing: 12) {
                Image(systemName: isSelected ? "largecircle.fill.circle" : "circle")
                    .font(.system(size: 22))
                    .foregroundColor(isSelected ? IDS.Colors.brand : IDS.Colors.iconSecondary)
                    .frame(width: 44, height: 44)
                Text(label)
                    .font(IdsTypeScale.body2)
                    .foregroundColor(IDS.Colors.textPrimary)
                    .multilineTextAlignment(.leading)
                Spacer(minLength: 0)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(PressScaleButtonStyle(enabled: isEnabled))
        .disabled(!isEnabled)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(label))
        .accessibilityValue(Text(isSelected ? "Selected" : "Not selected"))
        .accessibilityAddTraits(isSelected ? .isSelected : [])
        .accessibilityRemoveTraits(.isButton)
    }
}

public struct IdsSwitch: View {
    let label: String
    @Binding var isOn: Bool
    var isEnabled: Bool

    public init(_ label: String, isOn: Binding<Bool>, isEnabled: Bool = true) {
        self.label = label
        self._isOn = isOn
        self.isEnabled = isEnabled
    }

    public var body: some View {
        Toggle(isOn: $isOn) {
            Text(label)
                .font(IdsTypeScale.body2)
                .foregroundColor(IDS.Colors.textPrimary)
                .multilineTextAlignment(.leading)
        }
        .toggleStyle(.switch)
        .frame(minHeight: 44)
        .disabled(!isEnabled)
        .accessibilityLabel(Text(label))
    }
}

public struct IdsTabs: View {
    let labels: [String]
    @Binding var selectedIndex: Int

    public init(_ labels: [String], selectedIndex: Binding<Int>) {
        self.labels = labels
        self._selectedIndex = selectedIndex
    }

    public var body: some View {
        Picker("Tabs", selection: $selectedIndex) {
            ForEach(Array(labels.enumerated()), id: \.offset) { index, label in
                Text(label).tag(index)
            }
        }
        .pickerStyle(.segmented)
        .frame(minHeight: 44)
        .accessibilityLabel(Text("Tabs"))
    }
}

public struct IdsEmptyState: View {
    let title: String
    let message: String?
    let systemImage: String
    let actionTitle: String?
    let action: (() -> Void)?

    public init(
        title: String,
        message: String? = nil,
        systemImage: String = "tray",
        actionTitle: String? = nil,
        action: (() -> Void)? = nil
    ) {
        self.title = title
        self.message = message
        self.systemImage = systemImage
        self.actionTitle = actionTitle
        self.action = action
    }

    public var body: some View {
        VStack(spacing: 12) {
            Image(systemName: systemImage)
                .font(.system(size: 28, weight: .medium))
                .foregroundColor(IDS.Colors.textSecondary)
                .accessibilityHidden(true)

            Text(title)
                .font(IdsTypeScale.subtitle1)
                .foregroundColor(IDS.Colors.textPrimary)
                .multilineTextAlignment(.center)

            if let message {
                Text(message)
                    .font(IdsTypeScale.body2)
                    .foregroundColor(IDS.Colors.textSecondary)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
            }

            if let actionTitle, let action {
                IdsButton(actionTitle, action: action, variant: .tinted, size: .medium, fullWidth: false)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.horizontal, 24)
        .padding(.vertical, 32)
        .accessibilityElement(children: .contain)
    }
}
