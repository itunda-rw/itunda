import SwiftUI

// Real Toss Bank precedent (namu.wiki: 5 real named colorways, e.g. 레몬 블루/오렌지 밀크/나이트
// 핑크), direct user instruction 2026-08-27: "update itunda bank with all those cards
// designs allowing users to choose from those designs... that's how toss does it too".
// itunda's own 5, each a real front/back color pair validated in the standalone
// card-lineup design pass -- this is the single source of truth CardDesignPicker and
// CardScreenView's issued-card rendering both read from, matching bank-mfe's identical
// CARD_DESIGNS/cardDesign() in lib/card.ts and Android's CardDesigns.kt byte-for-byte
// on `id`, which is exactly what the backend's DebitCardDesign whitelist accepts.
struct CardDesign: Identifiable {
    let id: String
    let displayName: String
    let front: Color
    let back: Color
    let frontLight: Bool

    init(id: String, displayName: String, front: Color, back: Color, frontLight: Bool = false) {
        self.id = id
        self.displayName = displayName
        self.front = front
        self.back = back
        self.frontLight = frontLight
    }
}

enum CardDesigns {
    static let onyxIndigo = CardDesign(id: "onyx_indigo", displayName: "Onyx Indigo", front: Color(hex: 0x191F28), back: Color(hex: 0x7472F4))
    static let indigoOnyx = CardDesign(id: "indigo_onyx", displayName: "Indigo Onyx", front: Color(hex: 0x7472F4), back: Color(hex: 0x191F28))
    static let roseForest = CardDesign(id: "rose_forest", displayName: "Rose Forest", front: Color(hex: 0xDF466C), back: Color(hex: 0x05804A))
    static let frostOnyx = CardDesign(id: "frost_onyx", displayName: "Frost Onyx", front: Color(hex: 0xF4F4F2), back: Color(hex: 0x191F28), frontLight: true)
    static let forestRose = CardDesign(id: "forest_rose", displayName: "Forest Rose", front: Color(hex: 0x05804A), back: Color(hex: 0xDF466C))

    static let all: [CardDesign] = [onyxIndigo, indigoOnyx, roseForest, frostOnyx, forestRose]
    static let `default` = onyxIndigo

    static func byId(_ id: String?) -> CardDesign {
        all.first { $0.id == id } ?? `default`
    }
}
