import SwiftUI
import UIKit

// Real shared money-success/celebration screen -- ports Android's own
// core/designsystem/.../IdsCelebrationScreen.kt (built 2026-08-11/promoted
// 2026-08-12, the same real Toss motion research this file cites: real spring
// entrance + haptic + confetti-for-celebratory-moments). iOS had NO equivalent
// anywhere until this (2026-08-23, found while porting a real Toss "Sent"
// success-screen reference screenshot the user supplied) -- iOS's own transfer flow
// used to call onDone() immediately on a successful transfer with zero
// acknowledgment that real money had actually moved, the exact silent-close gap
// Android's own doc comment describes having already fixed on 2026-08-11.
//
// `UINotificationFeedbackGenerator().notificationOccurred(.success)` is the real,
// purpose-built iOS haptic for exactly this moment -- unlike Android's own doc
// comment (which had to reuse HapticFeedbackType.LongPress as a workaround, since
// Confirm wasn't available in that project's pinned Compose UI version), iOS's
// UIKit ships a native `.success` case, no workaround needed.
public struct IdsCelebrationScreen: View {
    let headline: String
    let message: String
    let onDone: () -> Void
    var celebratory: Bool = false
    // Real Toss "Sent" success-screen reference (2026-08-23): a money-transfer-
    // specific "To [name]" line and a Share action, neither of which apply to this
    // screen's other real/future callers (a savings deposit or claimed interest has
    // no "recipient" and nothing worth sharing). Both default to nil so this stays a
    // real, generic celebration primitive, not a transfer-only screen.
    var recipientLabel: String? = nil
    var onShare: (() -> Void)? = nil

    public init(headline: String, message: String, onDone: @escaping () -> Void, celebratory: Bool = false, recipientLabel: String? = nil, onShare: (() -> Void)? = nil) {
        self.headline = headline
        self.message = message
        self.onDone = onDone
        self.celebratory = celebratory
        self.recipientLabel = recipientLabel
        self.onShare = onShare
    }

    @State private var checkScale: CGFloat = 0.3
    @State private var checkOpacity: Double = 0
    @State private var confettiProgress: CGFloat = 0

    public var body: some View {
        ZStack {
            VStack(spacing: 0) {
                Spacer()
                ZStack {
                    Circle()
                        .fill(IDS.Colors.success)
                        .frame(width: 96, height: 96)
                    Image(systemName: "checkmark")
                        .font(IDS.scaledFont(size: 40, weight: .bold, relativeTo: .largeTitle))
                        .foregroundColor(.white)
                }
                .scaleEffect(checkScale)
                .opacity(checkOpacity)

                Text(headline)
                    .font(IDS.scaledFont(size: 26, weight: .heavy, relativeTo: .title1))
                    .foregroundColor(IDS.Colors.textPrimary)
                    .padding(.top, 24)
                    .multilineTextAlignment(.center)

                if !message.isEmpty {
                    Text(message)
                        .font(IDS.scaledFont(size: 15, weight: .regular, relativeTo: .subheadline))
                        .foregroundColor(IDS.Colors.textSecondary)
                        .multilineTextAlignment(.center)
                        .padding(.top, 8)
                }

                if let recipientLabel {
                    Text("To \(recipientLabel)")
                        .font(IDS.scaledFont(size: 18, weight: .semibold, relativeTo: .headline))
                        .foregroundColor(IDS.Colors.textPrimary)
                        .padding(.top, 4)
                }

                Spacer()

                if let onShare {
                    HStack(spacing: 10) {
                        Button(action: onShare) {
                            Text("Share")
                                .font(IDS.scaledFont(size: 16, weight: .semibold, relativeTo: .callout))
                                .foregroundColor(IDS.Colors.brand)
                                .frame(maxWidth: .infinity)
                                .frame(height: 56)
                                .background(IDS.Colors.chipBackground)
                                .cornerRadius(12)
                        }
                        .buttonStyle(PressScaleButtonStyle())
                        IdsButton(text: "Done", action: onDone)
                    }
                } else {
                    IdsButton(text: "Done", action: onDone)
                }
            }
            .padding(24)

            if celebratory {
                ConfettiBurst(progress: confettiProgress)
                    .allowsHitTesting(false)
            }
        }
        .background(IDS.Colors.background.ignoresSafeArea())
        .onAppear {
            UINotificationFeedbackGenerator().notificationOccurred(.success)
            withAnimation(.interpolatingSpring(stiffness: 220, damping: 14)) {
                checkScale = 1
                checkOpacity = 1
            }
            if celebratory {
                withAnimation(.linear(duration: 1.6)) {
                    confettiProgress = 1
                }
            }
            // Real Toss-style auto-advance -- a real acknowledgment moment, not a
            // dialog someone has to dismiss to get their money moving; "Done" above
            // still works immediately for anyone who doesn't want to wait it out.
            DispatchQueue.main.asyncAfter(deadline: .now() + (celebratory ? 0.8 : 2.2)) {
                onDone()
            }
        }
    }
}

// Real, pure-SwiftUI confetti burst -- mirrors Android's own Canvas-based
// ConfettiBurst exactly (one shared progress driving every particle's fall/drift/
// fade, no per-particle Animatable/asset pipeline).
private struct ConfettiParticle {
    let startX: CGFloat
    let fallSpeed: CGFloat
    let drift: CGFloat
    let color: Color
    let rotationSpeed: Double
    let sizePt: CGFloat
    let delay: CGFloat
}

private struct ConfettiBurst: View {
    let progress: CGFloat
    private let particles: [ConfettiParticle] = {
        let colors: [Color] = [IDS.Colors.brand, IDS.Colors.success, Color(red: 0.95, green: 0.66, blue: 0.23), Color(red: 0.49, green: 0.36, blue: 0.99), Color(red: 0.08, green: 0.68, blue: 0.52)]
        return (0..<28).map { _ in
            ConfettiParticle(
                startX: CGFloat.random(in: 0...1),
                fallSpeed: 0.7 + CGFloat.random(in: 0...0.6),
                drift: (CGFloat.random(in: 0...1) - 0.5) * 0.3,
                color: colors.randomElement()!,
                rotationSpeed: Double.random(in: -360...360),
                sizePt: 6 + CGFloat.random(in: 0...6),
                delay: CGFloat.random(in: 0...0.25)
            )
        }
    }()

    var body: some View {
        GeometryReader { geo in
            ForEach(0..<particles.count, id: \.self) { i in
                let p = particles[i]
                let localProgress = min(max((progress - p.delay) / (1 - p.delay), 0), 1)
                let fallen = localProgress * p.fallSpeed
                let x = (p.startX + p.drift * localProgress) * geo.size.width
                let y = fallen * geo.size.height * 1.1
                Rectangle()
                    .fill(p.color)
                    .frame(width: p.sizePt, height: p.sizePt)
                    .rotationEffect(.degrees(p.rotationSpeed * Double(localProgress)))
                    .opacity(localProgress <= 0 ? 0 : Double(1 - localProgress))
                    .position(x: x, y: y)
            }
        }
    }
}
