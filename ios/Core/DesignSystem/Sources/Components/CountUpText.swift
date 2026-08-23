import SwiftUI

// Real Toss motion pattern (toss.im/tossfeed/article/why-motion-in-finance) -- ported
// from bank-mfe's own real, sourced useCountUp hook (services/micro-frontends/bank-mfe/
// src/hooks/useCountUp.ts) and its already-shipped Android port
// (android/core/designsystem/.../IdsMotion.kt, `rememberCountUp`): Toss deliberately
// animates balance/amount changes rather than snapping instantly, over a real,
// deliberately snappy 600ms.
//
// `.contentTransition(.numericText())` is Apple's own native equivalent of Compose's
// `animateFloatAsState` for changing digit text (iOS 16+, already the repo's real
// floor -- `NavigationStack` is used unguarded elsewhere). It only animates when the
// *displayed* value actually changes inside an animation transaction, so -- like the
// Compose port, and unlike the original web hook -- no manual "skip first render"
// bookkeeping is needed: the first render has nothing to transition from yet.
public struct CountUpText: View {
    private let text: String

    public init(_ text: String) {
        self.text = text
    }

    public var body: some View {
        Text(text)
            .contentTransition(.numericText())
            .animation(.easeOut(duration: 0.6), value: text)
    }
}
