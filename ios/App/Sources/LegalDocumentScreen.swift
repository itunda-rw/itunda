import SwiftUI
import CoreNetwork
import CoreDesignSystem

/// Real itunda-branded Terms of Service/Privacy Policy/Credit Data Policy full-text
/// viewer -- closes SettingsScreen.swift's own missing "Legal Documents" section
/// (Android's own equivalent card existed since 2026-08-12 but was left honestly
/// inert; iOS never had the section at all). See LegalDocumentCatalog.kt's own doc
/// comment on the backend for the full sourced account of these documents' content.
struct LegalDocumentScreen: View {
    let documentId: String
    let fallbackTitle: String
    let onBack: () -> Void

    @State private var document: LegalDocument?
    @State private var loadError: String?

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) {
                    IDS.Icons.back(size: 18, relativeTo: .title3).frame(width: 44, height: 44)
                }
                Text(document?.title ?? fallbackTitle).font(IDS.scaledFont(size: 20, weight: .bold, relativeTo: .title2))
                Spacer()
            }
            .padding(.horizontal, 8)

            if let document {
                ScrollView {
                    Text(document.bodyMarkdown)
                        .font(IDS.scaledFont(size: 14, weight: .regular, relativeTo: .body))
                        .foregroundColor(IDS.Colors.textPrimary)
                        .lineSpacing(6)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 20)
                        .padding(.vertical, 8)
                }
            } else if let loadError {
                ErrorCardView(loadError, onRetry: { Task { await loadDocument() } })
                    .padding(.horizontal, 20)
            } else {
                Spacer()
                ProgressView().tint(IDS.Colors.brand)
                Spacer()
            }
        }
        .task { await loadDocument() }
    }

    private func loadDocument() async {
        do {
            let documents = try await NetworkClient.shared.getLegalDocuments()
            guard let matchingDocument = documents.first(where: { $0.id == documentId }) else {
                document = nil
                loadError = "This legal document is currently unavailable."
                return
            }
            document = matchingDocument
            loadError = nil
        } catch {
            loadError = "Couldn't load this document. Check your connection and try again."
        }
    }
}
