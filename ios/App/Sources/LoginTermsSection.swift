import SwiftUI
import CoreDesignSystem
import CoreNetwork

/// Real Toss/Korean-fintech-style 약관 동의 (terms consent) step -- see
/// NetworkClient.swift's RegisterRequest.acceptedTermsIds own doc comment for why this
/// only exists now (bank-mfe's RegisterPage.tsx has carried the identical UX since
/// 2026-08-18; this app's registration has been silently 400ing without it since that
/// date). Every checkbox starts unchecked -- never pre-ticked, the exact dark pattern
/// Korea's real 2025-02-14 전자상거래법 amendment bans -- required terms grouped before
/// optional ones, mirroring the web version field-for-field. No expandable summary
/// (unlike bank-mfe's chevron-to-expand) -- the summary is short enough to show inline
/// on this platform's narrower form, one fewer interaction for the same information.
///
/// Extracted out of LoginScreen.swift (2026-08-30) the moment that file first crossed
/// the file-size-lint 500-line guideline -- a self-contained, single-purpose view with
/// no dependency back on LoginScreen's own state beyond what's passed in, the same
/// "extract, don't baseline a first-time crossing" discipline this repo's own
/// docs/ARCHITECTURE_GUIDELINES.md §2 asks for.
struct TermsSection: View {
    let t: (String) -> String
    let terms: [TermsDocument]
    @Binding var acceptedTermsIds: Set<String>
    let canContinue: Bool
    let onContinue: () -> Void

    private var orderedTerms: [TermsDocument] { terms.filter { $0.required } + terms.filter { !$0.required } }
    private var allAccepted: Bool { !terms.isEmpty && terms.allSatisfy { acceptedTermsIds.contains($0.id) } }

    private func toggle(_ id: String) {
        if acceptedTermsIds.contains(id) { acceptedTermsIds.remove(id) } else { acceptedTermsIds.insert(id) }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(t("termsHeadline")).font(IDS.Typography.bodyMedium).foregroundColor(IDS.Colors.textPrimary)
            Text(t("termsSubtitle")).font(IDS.Typography.caption).foregroundColor(IDS.Colors.textSecondary)
                .padding(.bottom, 6)

            Button(action: {
                if allAccepted { acceptedTermsIds = [] } else { acceptedTermsIds = Set(terms.map { $0.id }) }
            }) {
                HStack(spacing: 8) {
                    Image(systemName: allAccepted ? "checkmark.square.fill" : "square")
                        .foregroundColor(allAccepted ? IDS.Colors.brand : IDS.Colors.textTertiary)
                    Text(t("termsAgreeAll")).font(IDS.Typography.bodyMedium).fontWeight(.bold).foregroundColor(IDS.Colors.textPrimary)
                }
            }
            .padding(.vertical, 6)

            ForEach(orderedTerms) { term in
                Button(action: { toggle(term.id) }) {
                    HStack(spacing: 8) {
                        Image(systemName: acceptedTermsIds.contains(term.id) ? "checkmark.square.fill" : "square")
                            .foregroundColor(acceptedTermsIds.contains(term.id) ? IDS.Colors.brand : IDS.Colors.textTertiary)
                        Text(term.required ? t("termsRequired") : t("termsOptional"))
                            .font(IDS.Typography.caption).fontWeight(.bold)
                            .foregroundColor(term.required ? IDS.Colors.danger : IDS.Colors.textTertiary)
                        VStack(alignment: .leading, spacing: 1) {
                            Text(term.title).font(IDS.Typography.bodyMedium).foregroundColor(IDS.Colors.textPrimary)
                            Text(term.summary).font(IDS.Typography.caption).foregroundColor(IDS.Colors.textSecondary)
                        }
                        Spacer()
                    }
                }
                .padding(.vertical, 4)
            }

            Button(action: onContinue) {
                Text(t("termsContinue"))
                    .font(IDS.Typography.bodyMedium).fontWeight(.bold)
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(canContinue ? IDS.Colors.brand : IDS.Colors.textTertiary)
                    .cornerRadius(10)
            }
            .disabled(!canContinue)
            .padding(.top, 8)
        }
    }
}
