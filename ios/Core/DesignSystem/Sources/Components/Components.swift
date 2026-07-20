import SwiftUI

public struct IdsButton: View {
    let text: String
    let action: () -> Void
    var isEnabled: Bool = true

    public init(text: String, isEnabled: Bool = true, action: @escaping () -> Void) {
        self.text = text
        self.isEnabled = isEnabled
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            Text(text)
                .font(IdsTypeScale.button)
                // Real fix (2026-07-13): this read the static, non-theme-reactive
                // IdsPalette before, so the button rendered light-mode #3182F6 even
                // in dark mode -- Android's own IdsButton.kt already correctly used
                // Ids.colors.brand (theme-reactive); this ports that exact pattern,
                // including the disabled-state colors (Android's disabledContainerColor
                // = Ids.colors.divider, disabledContentColor = Ids.colors.textTertiary).
                .foregroundColor(isEnabled ? IdsPalette.white : IDS.Colors.textTertiary)
                .frame(maxWidth: .infinity)
                .frame(height: 56)
                .background(isEnabled ? IDS.Colors.brand : IDS.Colors.divider)
                .cornerRadius(12)
        }
        .disabled(!isEnabled)
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
        }
        .buttonStyle(PlainButtonStyle())
    }
}
