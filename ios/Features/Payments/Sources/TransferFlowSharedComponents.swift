import SwiftUI
import CoreDesignSystem

// Real fix (2026-08-26): split out of TransferFlowScreens.swift once that file grew
// past its file-size-lint baseline. The small shared UI pieces used by both
// RecipientEntryScreen and TransferAmountScreen, which stay behind -- flipped from
// private (file-scoped in Swift too, at top level) to internal.

func transferFormatAmount(_ value: Int) -> String {
    let formatter = NumberFormatter()
    formatter.locale = Locale(identifier: "en_US_POSIX")
    formatter.numberStyle = .decimal
    formatter.groupingSeparator = ","
    return formatter.string(from: NSNumber(value: value)) ?? "0"
}

struct FlowTopBar: View {
    let onBack: () -> Void
    var body: some View {
        HStack {
            Button(action: onBack) {
                IDS.Icons.back(size: 18, color: IDS.Colors.textPrimary, relativeTo: .title3).frame(width: 44, height: 44)
            }
            .accessibilityLabel("Back")
            Spacer()
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
    }
}

struct RecentRecipientRow: View {
    let name: String
    let bankAndAccount: String
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 14) {
                Circle()
                    .fill(IDS.Colors.chipBackground)
                    .frame(width: 44, height: 44)
                    .overlay(Text(String(name.prefix(1))).font(IDS.scaledFont(size: 17, weight: .bold, relativeTo: .body)).foregroundColor(IDS.Colors.textPrimary))
                VStack(alignment: .leading, spacing: 2) {
                    Text(name).font(IDS.scaledFont(size: 16, weight: .semibold, relativeTo: .callout)).foregroundColor(IDS.Colors.textPrimary)
                    Text(bankAndAccount).font(IDS.scaledFont(size: 13, weight: .regular, relativeTo: .footnote)).foregroundColor(IDS.Colors.textTertiary)
                }
                Spacer()
            }
            .padding(.vertical, 10)
        }
        .buttonStyle(.plain)
    }
}

struct TransferPartyRow: View {
    let label: String
    let sublabel: String
    let symbol: String

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(label).font(IDS.scaledFont(size: 17, weight: .semibold, relativeTo: .body)).foregroundColor(IDS.Colors.textPrimary)
                Text(sublabel).font(IDS.scaledFont(size: 13, weight: .regular, relativeTo: .footnote)).foregroundColor(IDS.Colors.textTertiary)
            }
            Spacer()
            RoundedRectangle(cornerRadius: 14)
                .fill(IDS.Colors.chipBackground)
                .frame(width: 42, height: 42)
                .overlay(Image(systemName: symbol).font(IDS.scaledFont(size: 18, weight: .regular, relativeTo: .title3)).foregroundColor(IDS.Colors.textPrimary))
        }
        .padding(.vertical, 6)
    }
}

struct QuickAmountChip: View {
    let label: String
    let onTap: () -> Void
    var body: some View {
        Button(action: onTap) {
            Text(label)
                .font(IDS.scaledFont(size: 14, weight: .semibold, relativeTo: .subheadline))
                .foregroundColor(IDS.Colors.textPrimary)
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
                .background(IDS.Colors.chipBackground)
                .clipShape(Capsule())
        }
        .buttonStyle(.plain)
    }
}

struct FlowNextBar: View {
    let enabled: Bool
    let label: String
    let onTap: () -> Void
    var body: some View {
        Button(action: onTap) {
            Text(label)
                .font(IDS.scaledFont(size: 17, weight: .bold, relativeTo: .body))
                .foregroundColor(enabled ? .white : IDS.Colors.textTertiary)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 16)
                .background(enabled ? IDS.Colors.brand : IDS.Colors.chipBackground)
                .clipShape(RoundedRectangle(cornerRadius: 14))
        }
        .disabled(!enabled)
        .buttonStyle(.plain)
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
    }
}

struct NumericKeypad: View {
    let onDigit: (String) -> Void
    let onDelete: () -> Void
    private let rows = [["1", "2", "3"], ["4", "5", "6"], ["7", "8", "9"], ["00", "0", "DEL"]]

    var body: some View {
        VStack(spacing: 0) {
            ForEach(rows, id: \.self) { row in
                HStack(spacing: 0) {
                    ForEach(row, id: \.self) { key in
                        Button(action: { key == "DEL" ? onDelete() : onDigit(key) }) {
                            Group {
                                if key == "DEL" {
                                    // Real gap found live (2026-08-31, direct user
                                    // correction: "backspace button of keyboard should
                                    // be horizontal arrow (toss style) instead of those
                                    // weird icons") -- the stock "delete.left" SF Symbol
                                    // (a tag-with-X shape) is a visually different icon
                                    // concept from this app's own back button; reuses
                                    // the same real, already-cross-platform-shared
                                    // IDS.Icons.back chevron instead.
                                    IDS.Icons.back(size: 20, color: IDS.Colors.textPrimary)
                                } else {
                                    Text(key).font(IDS.scaledFont(size: 24, weight: .medium, relativeTo: .title2))
                                }
                            }
                            .foregroundColor(IDS.Colors.textPrimary)
                            .frame(maxWidth: .infinity)
                            .frame(height: 60)
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(key == "DEL" ? pt("deleteDigit") : key)
                    }
                }
            }
        }
        .padding(.bottom, 8)
    }
}
