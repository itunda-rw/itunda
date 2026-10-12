import SwiftUI

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
                IdsButton(text: actionTitle, variant: .tinted, size: .medium, fullWidth: false, action: action)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.horizontal, 24)
        .padding(.vertical, 32)
        .accessibilityElement(children: .contain)
    }
}


// Shared skeleton primitive for loading layouts. Intentionally static: it preserves
// geometry without making essential information depend on animation, and respects
// Reduce Motion by default. Callers compose several blocks to match the final layout.
public struct IdsSkeletonBlock: View {
    private let width: CGFloat?
    private let height: CGFloat
    private let cornerRadius: CGFloat

    public init(width: CGFloat? = nil, height: CGFloat = 16, cornerRadius: CGFloat = 8) {
        self.width = width
        self.height = height
        self.cornerRadius = cornerRadius
    }

    public var body: some View {
        RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
            .fill(IDS.Colors.backgroundTertiary)
            .frame(width: width, height: height)
            .accessibilityHidden(true)
    }
}
