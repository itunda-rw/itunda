import SwiftUI

/// Real Toss "밀어서 결제하기" (swipe to pay) primitive (2026-08-25, direct user
/// screenshot of Toss Shopping's real checkout sheet) -- Toss's own signature payment
/// gesture: a deliberate, hard-to-mis-tap drag instead of a single tap, used at every
/// real money-moving confirmation across their apps. Mirrors Android's
/// SwipeToConfirmButton exactly (see that file's own doc comment for the full
/// account): `enabled` gates whether the handle can be dragged at all, `busy` freezes
/// it mid-swipe (label swaps to `busyLabel`) while an async action runs. If `busy`
/// clears without the caller navigating away (a real failure, not a success), the
/// handle springs back to the start so the buyer can retry -- it never gets stuck
/// pinned at the end from a failed attempt.
public struct SwipeToConfirmButton: View {
    private let label: String
    private let busyLabel: String
    private let enabled: Bool
    private let busy: Bool
    private let onConfirm: () -> Void

    @State private var dragOffset: CGFloat = 0
    @State private var trackWidth: CGFloat = 0
    private let handleSize: CGFloat = 48
    private let handlePadding: CGFloat = 4

    public init(label: String, busyLabel: String, enabled: Bool, busy: Bool, onConfirm: @escaping () -> Void) {
        self.label = label
        self.busyLabel = busyLabel
        self.enabled = enabled
        self.busy = busy
        self.onConfirm = onConfirm
    }

    private var maxOffset: CGFloat {
        max(0, trackWidth - handleSize - handlePadding * 2)
    }

    public var body: some View {
        ZStack(alignment: .leading) {
            RoundedRectangle(cornerRadius: 28)
                .fill(enabled || busy ? IDS.Colors.brand : IDS.Colors.textTertiary)
            Text(busy ? busyLabel : label)
                .font(IDS.Typography.bodyBold)
                .foregroundColor(.white)
                .frame(maxWidth: .infinity)
                .padding(.leading, handleSize)
            Circle()
                .fill(Color.white)
                .frame(width: handleSize, height: handleSize)
                .overlay(IDS.Icons.chevronRight(size: 20, color: IDS.Colors.brand))
                .padding(handlePadding)
                .offset(x: dragOffset)
                .gesture(
                    enabled && !busy ?
                    DragGesture()
                        .onChanged { value in
                            dragOffset = min(max(0, value.translation.width), maxOffset)
                        }
                        .onEnded { _ in
                            if maxOffset > 0, dragOffset >= maxOffset * 0.8 {
                                withAnimation(IDS.Motion.springQuick) { dragOffset = maxOffset }
                                onConfirm()
                            } else {
                                withAnimation(IDS.Motion.springBounce) { dragOffset = 0 }
                            }
                        }
                    : nil
                )
        }
        .frame(height: 56)
        .background(
            GeometryReader { proxy in
                Color.clear.onAppear { trackWidth = proxy.size.width }
                    .onChange(of: proxy.size.width) { _, newValue in trackWidth = newValue }
            }
        )
        .onChange(of: busy) { _, isBusy in
            if !isBusy, enabled, dragOffset > 0 {
                withAnimation(IDS.Motion.springBounce) { dragOffset = 0 }
            }
        }
    }
}
