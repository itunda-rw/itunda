import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Real 이력서 (Karrot 당근알바-style résumé) builder (itunda Hood redesign, 2026-08-28)
// -- see backend Resume.kt's own doc comment. A real completion-percent nudge, same
// reference-sourced "이력서를 완성해보세요 X%" the backend's own completionPercent
// already computes -- this view just renders it, never re-derives it client-side.
// Mirrors Android's ResumeBuilderScreen.kt structure/copy exactly.
struct ResumeBuilderView: View {
    @State private var detail: ResumeDetailResponse?
    @State private var strengthsCatalog: [ResumeStrengthDto] = []
    @State private var error: String?

    var body: some View {
        Group {
            if let error, detail == nil {
                VStack(alignment: .leading, spacing: 10) {
                    Text(error).foregroundColor(.red).font(.subheadline)
                    Button("Retry") { Task { await reload() } }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.vertical, 10)
            } else if let detail {
                VStack(alignment: .leading, spacing: 16) {
                    VStack(alignment: .leading, spacing: 6) {
                        HStack {
                            Text("Complete your résumé").font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
                            Spacer()
                            Text("\(detail.completionPercent)%").font(.subheadline).bold().foregroundColor(IDS.Colors.brand)
                        }
                        GeometryReader { geo in
                            ZStack(alignment: .leading) {
                                Capsule().fill(IDS.Colors.chipBackground).frame(height: 6)
                                Capsule().fill(IDS.Colors.brand)
                                    .frame(width: geo.size.width * CGFloat(detail.completionPercent) / 100, height: 6)
                            }
                        }
                        .frame(height: 6)
                    }
                    ResumeProfileSection(detail: detail, onSaved: { Task { await reload() } })
                    ResumeExperienceSection(experiences: detail.experiences, onChanged: { Task { await reload() } })
                    ResumeEducationSection(educations: detail.educations, onChanged: { Task { await reload() } })
                    ResumeCertificationSection(certifications: detail.certifications, onChanged: { Task { await reload() } })
                }
                .padding(.vertical, 10)
            } else {
                HoodFeedSkeleton()
            }
        }
        .task { await loadStrengths(); await reload() }
    }

    private func loadStrengths() async {
        strengthsCatalog = (try? await NetworkClient.shared.getResumeStrengths().strengths) ?? []
    }

    private func reload() async {
        do {
            detail = try await NetworkClient.shared.getMyResume()
            error = nil
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }
}

private struct ResumeProfileSection: View {
    let detail: ResumeDetailResponse
    let onSaved: () -> Void

    @State private var selfIntro = ""
    @State private var additionalInfo = ""
    @State private var selectedStrengths: Set<String> = []
    @State private var strengthsCatalog: [ResumeStrengthDto] = []
    @State private var saving = false
    @State private var error: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("About you").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
            IdsTextField("Self-introduction", text: $selfIntro)
            if !strengthsCatalog.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(strengthsCatalog) { s in
                            let active = selectedStrengths.contains(s.id)
                            Text(s.label)
                                .font(.caption).bold()
                                .foregroundColor(active ? .white : IDS.Colors.textPrimary)
                                .padding(.horizontal, 12).padding(.vertical, 7)
                                .background(active ? IDS.Colors.brand : IDS.Colors.chipBackground)
                                .cornerRadius(999)
                                .onTapGesture { if active { selectedStrengths.remove(s.id) } else { selectedStrengths.insert(s.id) } }
                        }
                    }
                }
            }
            IdsTextField("Additional info (optional)", text: $additionalInfo)
            if let error { Text(error).font(.caption).foregroundColor(.red) }
            Button(action: { Task { await save() } }) {
                Text(saving ? "Saving…" : "Save").bold()
            }
            .disabled(saving)
        }
        .onAppear {
            selfIntro = detail.resume?.selfIntro ?? ""
            additionalInfo = detail.resume?.additionalInfo ?? ""
            selectedStrengths = Set((detail.resume?.strengths ?? "").split(separator: ",").map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty })
        }
        .task { strengthsCatalog = (try? await NetworkClient.shared.getResumeStrengths().strengths) ?? [] }
    }

    private func save() async {
        saving = true
        defer { saving = false }
        do {
            _ = try await NetworkClient.shared.updateResumeProfile(
                selfIntro: selfIntro.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : selfIntro,
                strengths: Array(selectedStrengths),
                additionalInfo: additionalInfo.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : additionalInfo
            )
            onSaved()
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }
}

private struct ResumeExperienceSection: View {
    let experiences: [ResumeExperienceDto]
    let onChanged: () -> Void

    @State private var adding = false
    @State private var company = ""
    @State private var role = ""
    @State private var period = ""
    @State private var error: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("Experience").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Button(adding ? "Cancel" : "+ Add") { adding.toggle() }
            }
            ForEach(experiences) { exp in
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("\(exp.role) · \(exp.company)").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                        Text(exp.period).font(.caption).foregroundColor(IDS.Colors.textSecondary)
                    }
                    Spacer()
                    // Real gap found 2026-09-05 (matches bank-mfe's identical
                    // HoodResumeBuilder.tsx fix, same session) -- try? silently
                    // discarded a failed remove, so a tap did nothing with zero
                    // feedback. Routes through this section's own error state
                    // (moved below, outside `if adding`, so it's visible even
                    // when the add form is closed).
                    Button("Remove") {
                        Task {
                            do {
                                _ = try await NetworkClient.shared.removeResumeExperience(exp.id)
                                error = nil
                                onChanged()
                            } catch {
                                self.error = "Couldn't remove this. Try again."
                            }
                        }
                    }
                        .font(.caption)
                }
            }
            if let error { Text(error).font(.caption).foregroundColor(.red) }
            if adding {
                IdsTextField("Company", text: $company)
                IdsTextField("Role", text: $role)
                IdsTextField("Period (e.g. 2023 – present)", text: $period)
                Button("Save") {
                    Task {
                        do {
                            _ = try await NetworkClient.shared.addResumeExperience(company: company, role: role, period: period, description: nil)
                            company = ""; role = ""; period = ""; adding = false; error = nil
                            onChanged()
                        } catch {
                            self.error = "Couldn't reach itunda."
                        }
                    }
                }
            }
        }
    }
}

private struct ResumeEducationSection: View {
    let educations: [ResumeEducationDto]
    let onChanged: () -> Void

    @State private var adding = false
    @State private var school = ""
    @State private var degree = ""
    @State private var error: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("Education").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Button(adding ? "Cancel" : "+ Add") { adding.toggle() }
            }
            ForEach(educations) { edu in
                HStack {
                    Text([edu.school, edu.degree].compactMap { $0 }.joined(separator: " · ")).font(.caption).foregroundColor(IDS.Colors.textPrimary)
                    Spacer()
                    Button("Remove") {
                        Task {
                            do {
                                _ = try await NetworkClient.shared.removeResumeEducation(edu.id)
                                error = nil
                                onChanged()
                            } catch {
                                self.error = "Couldn't remove this. Try again."
                            }
                        }
                    }
                        .font(.caption)
                }
            }
            if let error { Text(error).font(.caption).foregroundColor(.red) }
            if adding {
                IdsTextField("School", text: $school)
                IdsTextField("Degree (optional)", text: $degree)
                Button("Save") {
                    Task {
                        do {
                            _ = try await NetworkClient.shared.addResumeEducation(school: school, degree: degree.isEmpty ? nil : degree, major: nil)
                            school = ""; degree = ""; adding = false; error = nil
                            onChanged()
                        } catch {
                            self.error = "Couldn't reach itunda."
                        }
                    }
                }
            }
        }
    }
}

private struct ResumeCertificationSection: View {
    let certifications: [ResumeCertificationDto]
    let onChanged: () -> Void

    @State private var adding = false
    @State private var name = ""
    @State private var error: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("Certifications").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Button(adding ? "Cancel" : "+ Add") { adding.toggle() }
            }
            ForEach(certifications) { cert in
                HStack {
                    Text(cert.name).font(.caption).foregroundColor(IDS.Colors.textPrimary)
                    Spacer()
                    Button("Remove") {
                        Task {
                            do {
                                _ = try await NetworkClient.shared.removeResumeCertification(cert.id)
                                error = nil
                                onChanged()
                            } catch {
                                self.error = "Couldn't remove this. Try again."
                            }
                        }
                    }
                        .font(.caption)
                }
            }
            if let error { Text(error).font(.caption).foregroundColor(.red) }
            if adding {
                IdsTextField("Certification name", text: $name)
                Button("Save") {
                    Task {
                        do {
                            _ = try await NetworkClient.shared.addResumeCertification(name: name, issuedDate: nil)
                            name = ""; adding = false; error = nil
                            onChanged()
                        } catch {
                            self.error = "Couldn't reach itunda."
                        }
                    }
                }
            }
        }
    }
}
