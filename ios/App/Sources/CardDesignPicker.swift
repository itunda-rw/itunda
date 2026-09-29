import SwiftUI
import CoreDesignSystem

// Real Toss Bank "which color do you like?" issuance step (namu.wiki: 5 real named
// colorways; toss.tech's own engineering post on the picker's 3D touch-and-rotate
// interaction) -- direct user instruction 2026-08-27: "update itunda bank with all
// those cards designs allowing users to choose from those designs... that's how toss
// does it too". Picks from CardDesigns.all, itunda's own real front/back colorways
// validated in the standalone card-lineup design pass and already shipped identically
// in bank-mfe's CardExplainer.tsx and Android's CardDesignPicker.kt (same pass).
// Front-only during picking, matching that same pass's own real-photo-sourced finding:
// the real card's front is color and chip, nothing else -- no fabricated printed
// number here either, same "fully masked, no card exists yet" reasoning
// CardScreenView's previous single-design mockup already established.
struct CardDesignPicker: View {
    let busy: Bool
    let onIssue: (String) -> Void

    @State private var selected = CardDesigns.default.id

    private var design: CardDesign { CardDesigns.byId(selected) }
    private var lockupColor: Color { design.frontLight ? Color(hex: 0x191F28).opacity(0.5) : .white.opacity(0.6) }

    var body: some View {
        VStack(spacing: 0) {
            Text("Which finish do you like?")
                .font(.footnote).bold().foregroundColor(IDS.Colors.textSecondary)
                .padding(.bottom, 14)

            ZStack(alignment: .topLeading) {
                RoundedRectangle(cornerRadius: 14)
                    .fill(design.front)
                    .overlay(
                        design.frontLight ? RoundedRectangle(cornerRadius: 14).stroke(Color(hex: 0xE2E2DE), lineWidth: 1) : nil
                    )
                // Diagonal sheen -- the same "flat color read as a card" fix applied
                // to every card-shaped visual in this app.
                RoundedRectangle(cornerRadius: 14)
                    .fill(LinearGradient(colors: [Color.white.opacity(0.18), .clear], startPoint: .topLeading, endPoint: .bottomTrailing))

                VStack {
                    HStack {
                        BankCardChip(size: 30)
                        Spacer()
                    }
                    Spacer()
                    HStack(spacing: 4) {
                        PetalMark(size: 12, color: lockupColor)
                        Text("itunda bank").font(.caption2).bold().foregroundColor(lockupColor)
                        Spacer()
                    }
                }
                .padding(EdgeInsets(top: 46, leading: 18, bottom: 16, trailing: 18))
            }
            .frame(width: 138, height: 219)

            Spacer().frame(height: 18)
            HStack(spacing: 10) {
                ForEach(CardDesigns.all) { d in
                    let isSelected = d.id == selected
                    Button {
                        selected = d.id
                    } label: {
                        HStack(spacing: 0) {
                            Rectangle().fill(d.front)
                            Rectangle().fill(d.back)
                        }
                        .frame(width: 30, height: 30)
                        .clipShape(Circle())
                        .overlay(Circle().stroke(isSelected ? IDS.Colors.brand : .clear, lineWidth: 2))
                    }
                    .buttonStyle(PressScaleButtonStyle())
                    .accessibilityLabel(d.displayName)
                }
            }

            Spacer().frame(height: 24)
            Text("Your own itunda card, in seconds").font(.title3).bold().foregroundColor(IDS.Colors.textPrimary)
            Spacer().frame(height: 6)
            Text("A real debit card for your itunda balance -- no paperwork, no waiting.")
                .font(.footnote).foregroundColor(IDS.Colors.textSecondary)
            Spacer().frame(height: 24)

            VStack(spacing: 2) {
                FeatureRow(label: "No annual fee, ever") { MoneyBagGlyph(size: 20) }
                FeatureRow(label: "Issued instantly in the app -- no branch visit") {
                    Image(systemName: "bolt.fill").font(.system(size: 18)).foregroundColor(IDS.Colors.brand)
                }
                FeatureRow(label: "Set your own daily and monthly spend limits") {
                    Image(systemName: "shield.fill").font(.system(size: 18)).foregroundColor(IDS.Colors.brand)
                }
                FeatureRow(label: "One-tap freeze if it's ever lost") { LockGlyph(size: 20) }
            }
            Spacer().frame(height: 20)

            Button {
                onIssue(selected)
            } label: {
                Text(busy ? "Issuing…" : "Get your \(design.displayName) card")
                    .bold().foregroundColor(.white)
                    .frame(maxWidth: .infinity).padding(.vertical, 14)
                    .background(IDS.Colors.brand).cornerRadius(10)
            }
            .disabled(busy)
        }
        .frame(maxWidth: .infinity)
    }
}

private struct FeatureRow<Icon: View>: View {
    let label: String
    @ViewBuilder let icon: () -> Icon

    var body: some View {
        HStack(spacing: 12) {
            ZStack {
                RoundedRectangle(cornerRadius: 10).fill(IDS.Colors.brand.opacity(0.12))
                icon()
            }
            .frame(width: 36, height: 36)
            Text(label).font(.footnote).bold().foregroundColor(IDS.Colors.textPrimary)
            Spacer()
        }
        .padding(.vertical, 8)
    }
}
