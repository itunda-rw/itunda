import SwiftUI

// Promoted here from App/Sources/ExploreMenuSharedComponents.swift (2026-09-02,
// Menu Feature-module decomposition) -- FeatureMenu's EntireMenuScreen needs
// FlatRow/FlatSection/CollapsibleFlatSection/IconGridSection/IdsSearchBar, while
// MyTabView (App/Sources/BenefitsShopAllScreens.swift) still needs FlatRow/
// FlatSection too -- a FeatureMenu-to-App import isn't possible (backwards
// dependency direction), so these move to CoreDesignSystem instead. Mirrors
// Android's IdsPlainTopBar/SearchBar/FlatSection/FlatRow/IconGridSection/
// SmallBlueButton in ItundaAppScreen.kt (IdsPlainTopBar/SmallBlueButton/
// SearchAndCategoryChips stay in ExploreMenuSharedComponents.swift -- only used by
// other App-only screens, not by FeatureMenu).

public struct FlatRow {
    public let title: String
    public var subtitle: String? = nil
    public var trailing: String? = nil
    public var trailingIsLink: Bool = false
    public var symbol: String? = nil
    public var tint: Color = .accentIndigo
    // Real Toss icon-language fix (2026-08-26, matching Android's own FlatRow
    // doc comment/live-verified pass) -- Toss never wraps its category icons
    // in a colored background square; every glyph in TossFace is itself a
    // distinct, full-color illustration. Takes priority over symbol/tint
    // below when set; itundaface only covers a real subset of this screen's
    // rows today, so rows without a real glyph yet keep the SF Symbol
    // fallback rather than getting a fabricated one. Type-erased (`AnyView`)
    // since Swift has no direct equivalent of Android's `@Composable () ->
    // Unit` -- each call site wraps its itundaface View literally, e.g.
    // `glyph: { AnyView(BriefcaseGlyph(size: 28)) }`.
    public var glyph: (() -> AnyView)? = nil
    public var showChevron: Bool = false
    // Real granite mini-app launch (2026-07-16) -- see this file's own header for the
    // "MiniAppsSection...plain, non-functional list rows" note this closes for "Pay
    // bills" specifically. Optional, defaulting nil, so every other FlatRow call site
    // in this file is unaffected.
    public var action: (() -> Void)? = nil

    public init(
        title: String,
        subtitle: String? = nil,
        trailing: String? = nil,
        trailingIsLink: Bool = false,
        symbol: String? = nil,
        tint: Color = .accentIndigo,
        glyph: (() -> AnyView)? = nil,
        showChevron: Bool = false,
        action: (() -> Void)? = nil
    ) {
        self.title = title
        self.subtitle = subtitle
        self.trailing = trailing
        self.trailingIsLink = trailingIsLink
        self.symbol = symbol
        self.tint = tint
        self.glyph = glyph
        self.showChevron = showChevron
        self.action = action
    }
}

// Real Toss-style press feedback (2026-08-22, "toss interactions" directive) --
// `.onTapGesture` (what FlatSection/CollapsibleFlatSection's own rows used
// exclusively before this) has no SwiftUI-native pressed-state at all, unlike a
// real `Button`; this file's own FlatSection doc comment even said so directly
// ("row.action?() is already a safe no-op with no visible pressed-state in
// SwiftUI") when fixing an unrelated bug, without treating the missing
// pressed-state itself as the real gap it was. Can't just swap to
// `Button.buttonStyle(PressScaleButtonStyle())` here -- `row.action` is
// legitimately optional (rows with no real destination yet, e.g.
// Notifications/Privacy policy, per that same fix's own doc comment) and a
// disabled/actionless Button would fight the existing trait-gating logic below.
// Real fix instead: a dedicated `DragGesture(minimumDistance: 0)` tracks
// press/release directly (the same primitive SwiftUI's own tap gesture is built
// on), scaling only rows that actually have a real action -- matching Android's
// `rememberPressScale`'s own "no feedback on a dead row" rule from the same fix.
// Extracted into its own View (content can't hold @State inside a ForEach
// closure) and shared by both FlatSection and CollapsibleFlatSection, which
// were duplicating this exact row body before.
private struct PressableFlatRow: View {
    let row: FlatRow

    @State private var isPressed = false

    var body: some View {
        HStack {
            if let glyph = row.glyph {
                glyph()
                    .frame(width: 34, height: 34)
            } else if let symbol = row.symbol {
                ZStack {
                    RoundedRectangle(cornerRadius: 10).fill(row.tint)
                    Image(systemName: symbol)
                        .font(IDS.scaledFont(size: 19, weight: .regular, relativeTo: .body))
                        .foregroundColor(.white)
                }
                .frame(width: 34, height: 34)
            }
            VStack(alignment: .leading, spacing: 2) {
                Text(row.title)
                    .font(IDS.scaledFont(size: 17, weight: .medium, relativeTo: .body))
                    .foregroundColor(IDS.Colors.textPrimary)
                if let subtitle = row.subtitle {
                    Text(subtitle)
                        .font(IDS.scaledFont(size: 13, weight: .regular, relativeTo: .caption1))
                        .foregroundColor(IDS.Colors.textTertiary)
                }
            }
            Spacer()
            if let trailing = row.trailing {
                Text(trailing)
                    .font(IDS.scaledFont(size: 15, weight: row.trailingIsLink ? .semibold : .regular, relativeTo: .subheadline))
                    .foregroundColor(row.trailingIsLink ? IDS.Colors.brand : IDS.Colors.textSecondary)
            } else if row.showChevron && row.action != nil {
                Image(systemName: "chevron.right")
                    .foregroundColor(IDS.Colors.textTertiary)
            }
        }
        .padding(.vertical, 10)
        .contentShape(Rectangle())
        .scaleEffect(isPressed && row.action != nil ? 0.97 : 1)
        .animation(.spring(response: 0.25, dampingFraction: 0.6), value: isPressed)
        .simultaneousGesture(
            DragGesture(minimumDistance: 0)
                .onChanged { _ in if row.action != nil { isPressed = true } }
                .onEnded { _ in isPressed = false }
        )
        // Real dead-tap fix (item 247 follow-up, docs/DESIGN_REFERENCES.md §14
        // recommendation #2): the chevron/button-trait affordance above used to
        // render for rows with no real action too (Notifications/Privacy policy/
        // FAQ/etc. -- confirmed via Android's SupportScreen.kt doc comment that
        // no backend exists for any of them, same real gap on this screen's
        // Android port), matching Android's identical FlatRow bug fixed same day.
        .onTapGesture { row.action?() }
        .accessibilityAddTraits(row.action != nil ? [.isButton] : [])
    }
}

public struct FlatSection: View {
    let title: String
    let rows: [FlatRow]

    public init(title: String, rows: [FlatRow]) {
        self.title = title
        self.rows = rows
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title)
                .font(IDS.scaledFont(size: 19, weight: .bold, relativeTo: .title2))
                .foregroundColor(IDS.Colors.textPrimary)
                .padding(.bottom, 6)
            ForEach(rows, id: \.title) { row in
                PressableFlatRow(row: row)
            }
        }
    }
}

// Real fix (2026-08-10): matches Android's identical fix to ItundaAppScreen.kt (see
// its own CollapsibleFlatSection doc comment for the full Hick's Law citation) --
// EntireMenuScreen showed every one of its non-"Quick links"/"Mini apps" categories
// fully expanded, always. Collapsed by default, one open at a time, real item count
// in the header so collapsing doesn't hide that the content exists. Row rendering
// below is a literal copy of FlatSection's own body -- only the header gained a tap
// target and a chevron.up/chevron.down icon, and the rows are wrapped in `if isExpanded`.
public struct CollapsibleFlatSection: View {
    let title: String
    let rows: [FlatRow]
    let isExpanded: Bool
    let onToggle: () -> Void

    @State private var headerPressed = false

    public init(title: String, rows: [FlatRow], isExpanded: Bool, onToggle: @escaping () -> Void) {
        self.title = title
        self.rows = rows
        self.isExpanded = isExpanded
        self.onToggle = onToggle
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("\(title)  ·  \(rows.count)")
                    .font(IDS.scaledFont(size: 19, weight: .bold, relativeTo: .title2))
                    .foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Image(systemName: isExpanded ? "chevron.up" : "chevron.down")
                    .foregroundColor(IDS.Colors.textTertiary)
            }
            .padding(.bottom, isExpanded ? 6 : 0)
            .contentShape(Rectangle())
            .scaleEffect(headerPressed ? 0.98 : 1)
            .animation(.spring(response: 0.25, dampingFraction: 0.6), value: headerPressed)
            .simultaneousGesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { _ in headerPressed = true }
                    .onEnded { _ in headerPressed = false }
            )
            .onTapGesture(perform: onToggle)
            if isExpanded {
                ForEach(rows, id: \.title) { row in
                    PressableFlatRow(row: row)
                }
            }
        }
    }
}

// Real Toss-style press feedback, same DragGesture-based technique as
// PressableFlatRow above (`.onTapGesture` alone has no pressed-state).
// Extracted for the same reason -- @State can't live in a ForEach closure.
private struct PressableIconGridItem: View {
    let label: String
    let symbol: String
    let onTap: (() -> Void)?

    @State private var isPressed = false

    var body: some View {
        VStack(spacing: 8) {
            ZStack {
                RoundedRectangle(cornerRadius: 18).fill(IDS.Colors.chipBackground)
                Image(systemName: symbol)
                    .font(IDS.scaledFont(size: 24, weight: .regular, relativeTo: .title2))
                    .foregroundColor(IDS.Colors.textPrimary)
            }
            .frame(width: 54, height: 54)
            Text(label)
                .font(IDS.scaledFont(size: 13, weight: .regular, relativeTo: .caption1))
                .foregroundColor(IDS.Colors.textSecondary)
        }
        .frame(maxWidth: .infinity)
        .contentShape(Rectangle())
        .scaleEffect(isPressed && onTap != nil ? 0.94 : 1)
        .animation(.spring(response: 0.25, dampingFraction: 0.6), value: isPressed)
        .simultaneousGesture(
            DragGesture(minimumDistance: 0)
                .onChanged { _ in if onTap != nil { isPressed = true } }
                .onEnded { _ in isPressed = false }
        )
        .onTapGesture { onTap?() }
    }
}

public struct IconGridSection: View {
    let title: String
    let items: [(String, String)]
    // Real click support (2026-07-22) -- optional, defaulting nil, so every existing
    // call site (purely decorative) is unaffected. Only "Verify" (EntireMenuScreen)
    // passes one, to open the real Identity screen.
    var onItemClick: ((String) -> Void)? = nil

    public init(title: String, items: [(String, String)], onItemClick: ((String) -> Void)? = nil) {
        self.title = title
        self.items = items
        self.onItemClick = onItemClick
    }

    private var rows: [[(String, String)]] {
        stride(from: 0, to: items.count, by: 4).map { Array(items[$0..<min($0 + 4, items.count)]) }
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(title)
                .font(IDS.scaledFont(size: 14, weight: .semibold, relativeTo: .footnote))
                .foregroundColor(IDS.Colors.textSecondary)
            ForEach(Array(rows.enumerated()), id: \.offset) { _, rowItems in
                HStack {
                    ForEach(rowItems, id: \.0) { label, symbol in
                        PressableIconGridItem(label: label, symbol: symbol, onTap: onItemClick.map { click in { click(label) } })
                    }
                }
            }
        }
    }
}

// Real fix (2026-08-10, matching Android's identical fix to ItundaAppScreen.kt's
// own SearchBar): this was pure decoration -- a Text label, no TextField, nothing
// typed into it ever did anything. Worse than no search bar at all: it promised a
// feature that wasn't there. Now a real bound text field; EntireMenuScreen wires it
// to actually filter every FlatSection row by title.
public struct IdsSearchBar: View {
    @Binding var text: String
    let placeholder: String

    public init(text: Binding<String>, placeholder: String) {
        self._text = text
        self.placeholder = placeholder
    }

    public var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "magnifyingglass")
                .foregroundColor(IDS.Colors.textTertiary)
                .font(IDS.scaledFont(size: 15, weight: .regular, relativeTo: .body))
            TextField(placeholder, text: $text)
                .font(IDS.scaledFont(size: 16, weight: .regular, relativeTo: .body))
                .foregroundColor(IDS.Colors.textPrimary)
            if !text.isEmpty {
                Button(action: { text = "" }) {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundColor(IDS.Colors.textTertiary)
                }
                .accessibilityLabel("Clear search")
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(IDS.Colors.chipBackground)
        .cornerRadius(14)
    }
}

public extension Color {
    static let accentIndigo = IdsPalette.accentIndigo
    static let accentTeal = IdsPalette.accentTeal
    static let accentPurple = IdsPalette.accentPurple
    static let accentOrange = IdsPalette.accentOrange
    static let accentRed = IdsPalette.accentRed
    static let accentGray = IdsPalette.accentGray
}
