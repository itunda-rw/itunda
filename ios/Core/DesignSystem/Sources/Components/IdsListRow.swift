import SwiftUI

public struct IdsListRow: View {
    let title: String
    let subtitle: String?
    let rightText: String?
    let action: () -> Void
    var isEnabled: Bool = true
    var isLoading: Bool = false
    var isSelected: Bool = false

    public init(
        title: String,
        subtitle: String? = nil,
        rightText: String? = nil,
        isEnabled: Bool = true,
        isLoading: Bool = false,
        isSelected: Bool = false,
        action: @escaping () -> Void
    ) {
        self.title = title
        self.subtitle = subtitle
        self.rightText = rightText
        self.isEnabled = isEnabled
        self.isLoading = isLoading
        self.isSelected = isSelected
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text(title)
                        .font(IdsTypeScale.subtitle1)
                        .foregroundColor(isEnabled ? IDS.Colors.textPrimary : IDS.Colors.textSecondary)

                    if let subtitle = subtitle {
                        Text(subtitle)
                            .font(IdsTypeScale.body2)
                            .foregroundColor(IDS.Colors.textSecondary)
                    }
                }
                Spacer()
                if isLoading {
                    ProgressView()
                        .tint(IDS.Colors.brand)
                        .accessibilityLabel("Loading")
                } else if let rightText = rightText {
                    Text(rightText)
                        .font(IdsTypeScale.subtitle1)
                        .foregroundColor(isEnabled ? IDS.Colors.textPrimary : IDS.Colors.textSecondary)
                }
            }
            .padding(.horizontal, 24)
            .padding(.vertical, 16)
            // Keep the flat row treatment and light/dark-aware IDS card surface.
            .background(IDS.Colors.card)
            .overlay(Rectangle().fill(IDS.Colors.divider).frame(height: 1), alignment: .bottom)
            .contentShape(Rectangle())
        }
        .buttonStyle(PressScaleButtonStyle(enabled: isEnabled && !isLoading))
        .disabled(!isEnabled || isLoading)
        .opacity(isEnabled ? 1 : 0.55)
        .accessibilityLabel(Text(title))
        .accessibilityValue(Text(
            isLoading ? "Loading" : [subtitle, rightText].compactMap { $0 }.joined(separator: ". ")
        ))
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}
