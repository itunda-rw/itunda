package rw.itunda.app.ui

import androidx.compose.ui.graphics.Color

// Real Toss Bank precedent (namu.wiki: 5 named colorways, e.g. 레몬 블루/오렌지 밀크/나이트
// 핑크), direct user instruction 2026-08-27: "update itunda bank with all those cards
// designs allowing users to choose from those designs... that's how toss does it too".
// itunda's own 5, each a real front/back color pair validated in the standalone
// card-lineup design pass -- this is the single source of truth CardScreen's picker
// (NO_CARD state) and issued-card rendering (ACTIVE state) both read from, matching
// bank-mfe's identical CARD_DESIGNS/cardDesign() in lib/card.ts byte-for-byte on `id`,
// which is exactly what the backend's DebitCardDesign whitelist (DebitCard.kt) accepts.
data class CardDesign(
    val id: String,
    val displayName: String,
    val front: Color,
    val back: Color,
    val frontLight: Boolean = false,
)

object CardDesigns {
    val ONYX_INDIGO = CardDesign("onyx_indigo", "Onyx Indigo", Color(0xFF191F28), Color(0xFF7472F4))
    val INDIGO_ONYX = CardDesign("indigo_onyx", "Indigo Onyx", Color(0xFF7472F4), Color(0xFF191F28))
    val ROSE_FOREST = CardDesign("rose_forest", "Rose Forest", Color(0xFFDF466C), Color(0xFF05804A))
    val FROST_ONYX = CardDesign("frost_onyx", "Frost Onyx", Color(0xFFF4F4F2), Color(0xFF191F28), frontLight = true)
    val FOREST_ROSE = CardDesign("forest_rose", "Forest Rose", Color(0xFF05804A), Color(0xFFDF466C))

    val ALL: List<CardDesign> = listOf(ONYX_INDIGO, INDIGO_ONYX, ROSE_FOREST, FROST_ONYX, FOREST_ROSE)
    val DEFAULT: CardDesign = ONYX_INDIGO

    fun byId(id: String?): CardDesign = ALL.find { it.id == id } ?: DEFAULT
}
