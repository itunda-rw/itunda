import SwiftUI
import CoreDesignSystem
import CoreNetwork

/// Real Naver 지식iN (Knowledge iN) open-topic community Q&A (item 225) -- a genuinely
/// different shape from RideScreenView/DesignatedDriverScreenView/BikeRentalScreenView/
/// ParkingScreenView/BusScreenView: no account movement, no location -- just a real
/// question -> competing answers -> asker-adopts-one-best-answer content flow.
/// bank-mfe/Android already have this; this is the first iOS client, mirroring their
/// Browse/Mine toggle exactly.
struct KnowledgeScreenView: View {
    var onBack: () -> Void = {}

    private enum Tab { case browse, mine }

    @State private var tab: Tab = .browse
    @State private var categories: [KnowledgeCategory] = []
    @State private var activeCategory: String?
    @State private var questions: [KnowledgeQuestionDto]?
    @State private var myAnswers: [KnowledgeAnswerDto]?
    @State private var reputation: Int?
    @State private var openQuestionId: String?
    @State private var error: String?

    var body: some View {
        if let openId = openQuestionId {
            KnowledgeQuestionDetailScreen(questionId: openId, onBack: { openQuestionId = nil; load() })
        } else {
            VStack(spacing: 0) {
                HStack {
                    Button(action: onBack) { IDS.Icons.back(size: 17, color: IDS.Colors.textPrimary, relativeTo: .body) }.accessibilityLabel("Back")
                    Spacer()
                    Text("Q&A").font(.headline).foregroundColor(IDS.Colors.textPrimary)
                    Spacer()
                    Color.clear.frame(width: 20)
                }
                .padding()

                ScrollView {
                    VStack(alignment: .leading, spacing: 12) {
                        HStack {
                            Text("Your reputation").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                            Spacer()
                            Text(reputation.map { "\($0) adopted answer\($0 == 1 ? "" : "s")" } ?? "…")
                                .font(.subheadline).bold()
                        }
                        // Real fix (2026-08-24, flat-design sweep): dropped the Card wrapper --
                        // a stat row on an otherwise-flat screen, matching Android's identical
                        // KnowledgeScreen.kt conversion (commit 130a23b8).

                        Picker("", selection: $tab) {
                            Text("Browse").tag(Tab.browse)
                            Text("Mine").tag(Tab.mine)
                        }
                        .pickerStyle(.segmented)

                        if tab == .browse, !categories.isEmpty {
                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack(spacing: 6) {
                                    CategoryChip(label: "All", selected: activeCategory == nil) { activeCategory = nil }
                                    ForEach(categories) { c in
                                        CategoryChip(label: c.label, selected: activeCategory == c.id) { activeCategory = c.id }
                                    }
                                }
                            }
                        }

                        if tab == .browse {
                            KnowledgeAskCard(categories: categories, onAsked: load)
                        }

                        if let error { Text(error).font(.caption).foregroundColor(.red) }

                        if let list = questions {
                            if list.isEmpty {
                                EmptyStateView("No questions yet — be the first to ask.")
                            } else {
                                // Real fix (2026-08-24, flat-design sweep): dropped the per-row
                                // Card -- a question list separates entries with spacing alone,
                                // matching Android's identical conversion (130a23b8).
                                ForEach(list) { q in
                                    Button(action: { openQuestionId = q.id }) {
                                        VStack(alignment: .leading, spacing: 2) {
                                            Text((q.adoptedAnswerId != nil ? "✅ " : "") + q.title).bold().font(.subheadline)
                                            Text(categories.first(where: { $0.id == q.category })?.label ?? q.category)
                                                .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                                        }
                                        .frame(maxWidth: .infinity, alignment: .leading)
                                    }
                                    .padding(.vertical, 10)
                                }
                            }
                        } else {
                            Text("Loading…").font(.footnote).foregroundColor(IDS.Colors.textSecondary)
                        }

                        if tab == .mine, let answers = myAnswers, !answers.isEmpty {
                            Text("Your answers").bold()
                            // Real fix (2026-08-24, flat-design sweep): dropped the per-row Card.
                            ForEach(answers) { a in
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(a.isAdopted ? "✅ Adopted" : "Pending").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                                    Text(a.body).font(.subheadline)
                                }
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(.vertical, 10)
                            }
                        }
                    }
                    .padding(IDS.Layout.screenHorizontal)
                }
            }
            .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
            .task {
                if let cats = try? await NetworkClient.shared.getKnowledgeCategories(), cats.success { categories = cats.categories }
                if let rep = try? await NetworkClient.shared.getMyKnowledgeReputation(), rep.success { reputation = rep.adoptedAnswerCount }
                load()
            }
            .onChange(of: tab) { _, _ in load() }
            .onChange(of: activeCategory) { _, _ in load() }
        }
    }

    private func load() {
        error = nil
        questions = nil
        Task {
            do {
                if tab == .mine {
                    myAnswers = try await NetworkClient.shared.getMyKnowledgeAnswers().answers
                    questions = try await NetworkClient.shared.getMyKnowledgeQuestions().questions
                } else {
                    questions = try await NetworkClient.shared.getKnowledgeQuestions(category: activeCategory).questions
                }
            } catch {
                self.error = "Could not load questions."
            }
        }
    }
}

private struct CategoryChip: View {
    let label: String
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(label).font(.caption).bold()
                .padding(.horizontal, 12).padding(.vertical, 6)
                .background(selected ? IDS.Colors.brand : Color(.secondarySystemBackground))
                .foregroundColor(selected ? .white : IDS.Colors.textPrimary)
                .cornerRadius(8)
        }
    }
}

private struct KnowledgeAskCard: View {
    let categories: [KnowledgeCategory]
    let onAsked: () -> Void

    @State private var open = false
    @State private var category = ""
    @State private var title = ""
    @State private var questionBody = ""
    @State private var submitting = false
    @State private var error: String?

    var body: some View {
        if !open {
            Button(action: { open = true }) {
                Text("+ Ask a question").bold().font(.caption)
                    .frame(maxWidth: .infinity).padding(.vertical, 14)
                    .background(Color(.secondarySystemBackground)).cornerRadius(10)
            }
        } else {
            VStack(alignment: .leading, spacing: 8) {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        ForEach(categories) { c in
                            CategoryChip(label: c.label, selected: category == c.id) { category = c.id }
                        }
                    }
                }
                IdsTextField("Your question", text: $title)
                IdsTextField("Add more detail", text: $questionBody)
                if let error { Text(error).font(.caption).foregroundColor(.red) }
                Button(action: { Task { await submit() } }) {
                    Text(submitting ? "Posting…" : "Post question").bold().foregroundColor(.white)
                        .frame(maxWidth: .infinity).padding(.vertical, 12)
                        .background((category.isEmpty || title.trimmingCharacters(in: .whitespaces).isEmpty || questionBody.trimmingCharacters(in: .whitespaces).isEmpty) ? Color.gray : IDS.Colors.brand)
                        .cornerRadius(10)
                }
                .disabled(submitting || category.isEmpty || title.trimmingCharacters(in: .whitespaces).isEmpty || questionBody.trimmingCharacters(in: .whitespaces).isEmpty)
            }
            // Real fix (2026-08-24, flat-design sweep): dropped the Card wrapper -- an
            // inline form section, matching Android's identical KnowledgeAskCard
            // conversion (130a23b8).
        }
    }

    private func submit() async {
        submitting = true
        error = nil
        do {
            _ = try await NetworkClient.shared.postKnowledgeQuestion(category: category, title: title.trimmingCharacters(in: .whitespaces), body: questionBody.trimmingCharacters(in: .whitespaces))
            category = ""; title = ""; questionBody = ""; open = false
            onAsked()
        } catch {
            self.error = "Could not post this question."
        }
        submitting = false
    }
}

private struct KnowledgeQuestionDetailScreen: View {
    let questionId: String
    var onBack: () -> Void = {}

    @State private var question: KnowledgeQuestionDto?
    @State private var answers: [KnowledgeAnswerDto]?
    @State private var answerBody = ""
    @State private var answering = false
    @State private var busyAnswerId: String?
    @State private var error: String?

    private let currentUserId = KeychainTokenStore.shared.getUserId()

    private var isAsker: Bool { question != nil && currentUserId != nil && question?.askerId == currentUserId }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) { IDS.Icons.back(size: 17, color: IDS.Colors.textPrimary, relativeTo: .body) }.accessibilityLabel("Back")
                Spacer()
                Text("Question").font(.headline).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Color.clear.frame(width: 20)
            }
            .padding()

            ScrollView {
                VStack(alignment: .leading, spacing: 10) {
                    if let error { Text(error).font(.caption).foregroundColor(.red) }
                    // Real fix (2026-08-24, flat-design sweep): dropped this Card and the
                    // per-row Card below -- this screen's own main content, matching
                    // Android's identical KnowledgeQuestionDetailScreen conversion
                    // (130a23b8).
                    if let q = question {
                        VStack(alignment: .leading, spacing: 8) {
                            Text(q.title).font(.title3).bold()
                            Text(q.body).font(.subheadline).foregroundColor(IDS.Colors.textSecondary)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }

                    Text("Answers").bold()
                    if let list = answers {
                        if list.isEmpty {
                            EmptyStateView("No answers yet -- be the first to help.")
                        } else {
                            ForEach(list) { a in
                                VStack(alignment: .leading, spacing: 6) {
                                    if a.isAdopted { Text("✅ Adopted answer").font(.caption2).bold().foregroundColor(IDS.Colors.brand) }
                                    Text(a.body).font(.subheadline)
                                    if isAsker, question?.adoptedAnswerId == nil {
                                        Button(action: { Task { await adopt(a.id) } }) {
                                            Text(busyAnswerId == a.id ? "…" : "Adopt this answer").bold().font(.caption)
                                                .padding(.horizontal, 12).padding(.vertical, 8)
                                                .background(Color(.secondarySystemBackground)).cornerRadius(8)
                                        }
                                        .disabled(busyAnswerId == a.id)
                                    }
                                }
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(.vertical, 10)
                            }
                        }
                    } else {
                        Text("Loading…").font(.footnote).foregroundColor(IDS.Colors.textSecondary)
                    }

                    if question?.adoptedAnswerId == nil {
                        HStack {
                            IdsTextField("Write an answer", text: $answerBody)
                            Button(action: { Task { await sendAnswer() } }) {
                                Text(answering ? "…" : "Send").bold().foregroundColor(.white)
                                    .padding(.horizontal, 16).padding(.vertical, 12)
                                    .background(answerBody.trimmingCharacters(in: .whitespaces).isEmpty ? Color.gray : IDS.Colors.brand)
                                    .cornerRadius(10)
                            }
                            .disabled(answering || answerBody.trimmingCharacters(in: .whitespaces).isEmpty)
                        }
                    }
                }
                .padding(IDS.Layout.screenHorizontal)
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .task { load() }
    }

    private func load() {
        error = nil
        Task {
            do {
                question = try await NetworkClient.shared.getKnowledgeQuestion(questionId: questionId).question
                answers = try await NetworkClient.shared.getKnowledgeAnswers(questionId: questionId).answers
            } catch {
                self.error = "Could not load this question."
            }
        }
    }

    private func sendAnswer() async {
        answering = true
        error = nil
        do {
            _ = try await NetworkClient.shared.postKnowledgeAnswer(questionId: questionId, body: answerBody.trimmingCharacters(in: .whitespaces))
            answerBody = ""
            load()
        } catch {
            self.error = "Could not post your answer."
        }
        answering = false
    }

    private func adopt(_ answerId: String) async {
        busyAnswerId = answerId
        error = nil
        do {
            _ = try await NetworkClient.shared.adoptKnowledgeAnswer(questionId: questionId, answerId: answerId)
            load()
        } catch {
            self.error = "Could not adopt this answer."
        }
        busyAnswerId = nil
    }
}
