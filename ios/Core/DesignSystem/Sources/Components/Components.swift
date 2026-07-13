import SwiftUI

public struct TdsButton: View {
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
                .font(TdsTypography.button)
                // Real fix (2026-07-13): this read the static, non-theme-reactive
                // TdsColors before, so the button rendered light-mode #3182F6 even
                // in dark mode -- Android's own TdsButton.kt already correctly used
                // Tds.colors.brand (theme-reactive); this ports that exact pattern,
                // including the disabled-state colors (Android's disabledContainerColor
                // = Tds.colors.divider, disabledContentColor = Tds.colors.textTertiary).
                .foregroundColor(isEnabled ? TdsColors.white : IDS.Colors.textTertiary)
                .frame(maxWidth: .infinity)
                .frame(height: 56)
                .background(isEnabled ? IDS.Colors.brand : IDS.Colors.divider)
                .cornerRadius(12)
        }
        .disabled(!isEnabled)
    }
}

public struct TdsListRow: View {
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
                        .font(TdsTypography.subtitle1)
                        .foregroundColor(IDS.Colors.textPrimary)

                    if let subtitle = subtitle {
                        Text(subtitle)
                            .font(TdsTypography.body2)
                            .foregroundColor(IDS.Colors.textSecondary)
                    }
                }
                Spacer()
                if let rightText = rightText {
                    Text(rightText)
                        .font(TdsTypography.subtitle1)
                        .foregroundColor(IDS.Colors.textPrimary)
                }
            }
            .padding(.horizontal, 24)
            .padding(.vertical, 16)
            // Real fix (2026-07-13): same static-vs-reactive bug as TdsButton above --
            // this row's background stayed light-mode white in dark mode.
            .background(IDS.Colors.card)
        }
        .buttonStyle(PlainButtonStyle())
    }
}
