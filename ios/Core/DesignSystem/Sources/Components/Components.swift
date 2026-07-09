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
                .foregroundColor(isEnabled ? TdsColors.white : TdsColors.white.opacity(0.5))
                .frame(maxWidth: .infinity)
                .frame(height: 56)
                .background(isEnabled ? TdsColors.blue500 : TdsColors.gray300)
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
                        .foregroundColor(TdsColors.gray900)
                    
                    if let subtitle = subtitle {
                        Text(subtitle)
                            .font(TdsTypography.body2)
                            .foregroundColor(TdsColors.gray600)
                    }
                }
                Spacer()
                if let rightText = rightText {
                    Text(rightText)
                        .font(TdsTypography.subtitle1)
                        .foregroundColor(TdsColors.gray900)
                }
            }
            .padding(.horizontal, 24)
            .padding(.vertical, 16)
            .background(TdsColors.white)
        }
        .buttonStyle(PlainButtonStyle())
    }
}
