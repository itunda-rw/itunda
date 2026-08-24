import SwiftUI
import UIKit
import CoreLocation
import CoreDesignSystem
import CoreNetwork


struct NewJobPostForm: View {
    let categories: [JobCategoryDto]
    let onCreated: () -> Void
    let onCancel: () -> Void

    @State private var category: String
    @State private var title = ""
    @State private var description = ""
    @State private var payType = "HOURLY"
    @State private var payAmount = ""
    @State private var error: String?
    @State private var submitting = false
    @State private var shareLocation = false
    @State private var myLocation: CLLocationCoordinate2D?
    @StateObject private var locationFetcher = HoodLocationFetcher()

    init(categories: [JobCategoryDto], onCreated: @escaping () -> Void, onCancel: @escaping () -> Void) {
        self.categories = categories
        self.onCreated = onCreated
        self.onCancel = onCancel
        _category = State(initialValue: categories.first?.id ?? "")
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Post a job").font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(categories) { c in
                        let selected = category == c.id
                        Text(c.label)
                            .font(.caption).bold()
                            .foregroundColor(selected ? .white : IDS.Colors.textPrimary)
                            .padding(.horizontal, 12).padding(.vertical, 6)
                            .background(selected ? IDS.Colors.brand : IDS.Colors.chipBackground)
                            .cornerRadius(999)
                            .onTapGesture { category = c.id }
                    }
                }
            }
            IdsTextField("What do you need done?", text: $title)
            IdsTextField("Describe the work", text: $description)
            HStack {
                Picker("", selection: $payType) {
                    Text("Per hour").tag("HOURLY")
                    Text("Fixed price").tag("FIXED")
                }
                .pickerStyle(.segmented)
                IdsTextField("Pay (RWF)", text: $payAmount, keyboardType: .numberPad)
            }
            Button(action: {
                if shareLocation { shareLocation = false } else { locationFetcher.requestLocation() }
            }) {
                Text(shareLocation ? "📍 Work location shared with nearby applicants" : "📍 Share work location for nearby applicants (optional)")
                    .font(.caption).foregroundColor(shareLocation ? IDS.Colors.brand : IDS.Colors.textSecondary)
                    .frame(maxWidth: .infinity, alignment: .leading).padding(.horizontal, 14).padding(.vertical, 12)
                    .background(IDS.Colors.chipBackground).cornerRadius(12)
            }
            if let error {
                Text(error).font(.caption).foregroundColor(.red)
            }
            HStack {
                Button("Cancel", action: onCancel).frame(maxWidth: .infinity)
                Button(action: { Task { await submit() } }) {
                    Text(submitting ? "Posting…" : "Post job")
                        .foregroundColor(.white).frame(maxWidth: .infinity).padding(.vertical, 12)
                        .background(IDS.Colors.brand).cornerRadius(14)
                }
                .disabled(submitting)
            }
        }
        // Real fix (2026-08-24, flat-design sweep): dropped the Card wrapper -- matches
        // HoodProperty.swift/HoodMarketplace.swift's identical form conversions.
        .padding(.vertical, 10)
        .onAppear {
            locationFetcher.onLocation = { coordinate in
                myLocation = coordinate
                shareLocation = true
            }
        }
        .onChange(of: locationFetcher.errorMessage) { message in
            if let message { error = message }
        }
    }

    private func submit() async {
        guard let amount = Double(payAmount), amount > 0, !title.isEmpty, !description.isEmpty, !category.isEmpty else {
            error = "Fill in every field with a real pay amount."
            return
        }
        submitting = true
        error = nil
        defer { submitting = false }
        do {
            let location = shareLocation ? myLocation : nil
            _ = try await NetworkClient.shared.createJobPost(category: category, title: title, description: description, payType: payType, payAmount: amount, latitude: location?.latitude, longitude: location?.longitude)
            onCreated()
        } catch let NetworkError.httpError(statusCode) {
            error = TalkScreen.errorMessage(statusCode)
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }
}

struct JobPostCard: View {
    let post: JobPostDto
    let categoryLabel: String
    let isMine: Bool
    let onChanged: () -> Void
    let onContact: () -> Void
    var favorited: Bool = false
    var favoriteBusy: Bool = false
    var onToggleFavorite: () -> Void = {}
    var posterTrustScore: Int?
    var currentUserId: String?

    @State private var busy = false
    @State private var error: String?
    @State private var myLocation: CLLocationCoordinate2D?
    @State private var showRoute = false
    @StateObject private var locationFetcher = HoodLocationFetcher()
    @State private var showingReportOptions = false

    // Real optional worker identification at mark-filled time (2026-07-24) -- see
    // backend JobPostService.markFilled's own doc comment.
    @State private var markingFilled = false
    @State private var workerPhone = ""

    // Real post-transaction review with asymmetric public/private visibility
    // (2026-07-24) -- see backend HoodReviewService's own doc comment.
    @State private var showReviewSheet = false
    @State private var selectedGoodPoints: Set<String> = []
    @State private var selectedUncomfortablePoints: Set<String> = []
    @State private var submittingReview = false
    @State private var reviewSubmitted = false
    // Real read-back for the review above (item 192/198/199).
    @State private var hoodReviews: [HoodReviewDto]?

    // Real 당근알바-style structured application (2026-07-25 on Android, ported here
    // 2026-07-29) -- see ApplyToJobRequest's own doc comment. "Message poster" above
    // still exists as a separate, unstructured hand-off.
    @State private var applying = false
    @State private var applicationMessage = ""
    @State private var applicationSubmitted = false
    @State private var submittingApplication = false
    @State private var showApplicants = false
    @State private var applications: [JobApplicationDto]?
    @State private var respondingToId: String?

    private var payLabel: String {
        let base = "\(Int(post.payAmount)) RWF"
        return post.payType == "HOURLY" ? "\(base)/hr" : base
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                HStack(spacing: 6) {
                    Text("\(categoryLabel) · \(hoodRelativeTime(post.createdAt))").font(.caption2).bold().foregroundColor(IDS.Colors.brand)
                    if post.status == "FILLED" {
                        Text("FILLED").font(.caption2).bold().foregroundColor(IDS.Colors.textSecondary)
                            .padding(.horizontal, 8).padding(.vertical, 2)
                            .background(IDS.Colors.chipBackground).cornerRadius(8)
                    }
                }
                Spacer()
                if !isMine {
                    Button(action: onToggleFavorite) {
                        WishlistHeart(favorited: favorited, size: 18)
                    }
                    .accessibilityLabel(favorited ? "Remove from favorites" : "Add to favorites")
                    .disabled(favoriteBusy)
                    .padding(.trailing, 6)
                }
                Text(payLabel).font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
            }
            Text(post.title).font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
            // Real Karrot-Score trust badge (2026-07-24) -- see TrustBadge's own doc
            // comment. Only shown for someone else's post.
            if !isMine, let posterTrustScore {
                TrustBadge(score: posterTrustScore)
            }
            Text(post.description).font(.subheadline).foregroundColor(IDS.Colors.textSecondary)
            if let error {
                Text(error).font(.caption).foregroundColor(.red)
            }
            // Real optional "who did you hire?" prompt (2026-07-24) -- see backend
            // JobPostService.markFilled's own doc comment.
            if markingFilled {
                IdsTextField("Worker's phone (optional)", text: $workerPhone, keyboardType: .phonePad)
                HStack(spacing: 10) {
                    actionButton("Skip", filled: false) { await markFilled(workerPhoneNumber: nil) }
                    actionButton("Confirm", filled: true) { await markFilled(workerPhoneNumber: workerPhone.trimmingCharacters(in: .whitespaces)) }
                }
            }
            if isMine, post.status == "FILLED", post.workerId != nil, reviewSubmitted, let hoodReviews {
                HoodReviewResultView(reviews: hoodReviews, myUserId: currentUserId)
            }
            // Real post-transaction review, preset checklist with asymmetric public/
            // private visibility (2026-07-24) -- see backend HoodReviewService's own
            // doc comment. Only offered once a real worker was recorded at mark-filled
            // time.
            if isMine, post.status == "FILLED", post.workerId != nil, !reviewSubmitted {
                if showReviewSheet {
                    HoodReviewForm(
                        selectedGoodPoints: $selectedGoodPoints,
                        selectedUncomfortablePoints: $selectedUncomfortablePoints,
                        submitting: submittingReview,
                        onCancel: { showReviewSheet = false },
                        onSubmit: { await submitReview() }
                    )
                } else {
                    actionButton("Rate this worker", filled: true) { showReviewSheet = true }
                }
            }
            HStack(spacing: 10) {
                if isMine {
                    if post.status == "OPEN" && !markingFilled {
                        actionButton("Mark filled", filled: false) { markingFilled = true }
                    }
                    if post.status != "REMOVED" {
                        actionButton("Remove", filled: false) { await remove() }
                    }
                } else if post.status == "OPEN" {
                    actionButton("Message poster", filled: false) { onContact() }
                    if !applicationSubmitted && !applying {
                        actionButton("Apply", filled: true) { applying = true }
                    }
                    actionButton("Report", filled: false) { showingReportOptions = true }
                }
            }
            if !isMine, applying {
                TextField("Why should the poster pick you? (required)", text: $applicationMessage, axis: .vertical)
                    .lineLimit(3...6)
                    .padding(10)
                    .background(IDS.Colors.chipBackground)
                    .cornerRadius(10)
                HStack(spacing: 10) {
                    actionButton("Cancel", filled: false) { applying = false }
                    actionButton("Submit application", filled: true) { await submitApplication() }
                        .disabled(submittingApplication || applicationMessage.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
            if !isMine, applicationSubmitted {
                Text("Application sent — you'll hear back once the poster reviews it").font(.caption).foregroundColor(IDS.Colors.textSecondary)
            }
            // Poster's real "review applicants, then decide" step -- lazily fetched
            // only when opened.
            if isMine, post.status == "OPEN" {
                actionButton(showApplicants ? "Hide applicants" : "View applicants", filled: false) {
                    showApplicants.toggle()
                    if showApplicants, applications == nil { await loadApplications() }
                }
                if showApplicants {
                    if let applications {
                        let pending = applications.filter { $0.status == "PENDING" }
                        if pending.isEmpty {
                            Text("No applications yet").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        }
                        ForEach(pending) { app in
                            VStack(alignment: .leading, spacing: 6) {
                                Text(app.message).font(.subheadline)
                                HStack(spacing: 10) {
                                    actionButton("Decline", filled: false) { await respond(app, accept: false) }
                                        .disabled(respondingToId == app.id)
                                    actionButton("Accept & message", filled: true) { await respond(app, accept: true) }
                                        .disabled(respondingToId == app.id)
                                }
                            }
                            .padding(12)
                            .background(IDS.Colors.chipBackground)
                            .cornerRadius(IDS.Layout.cardCornerRadius)
                        }
                    } else {
                        ProgressView()
                    }
                }
            }
            if !isMine, post.status == "OPEN", let toLat = post.latitude, let toLng = post.longitude {
                Button(action: {
                    if showRoute { showRoute = false } else if myLocation != nil { showRoute = true } else { locationFetcher.requestLocation() }
                }) {
                    Text(showRoute ? "Hide directions" : "🚗 Directions to this work")
                        .font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                        .padding(.horizontal, 16).padding(.vertical, 10)
                        .background(IDS.Colors.chipBackground).cornerRadius(12)
                }
                if showRoute, let myLocation {
                    RouteMiniMap(fromLat: myLocation.latitude, fromLng: myLocation.longitude, toLat: toLat, toLng: toLng, fromLabel: "You", toLabel: post.title)
                }
            }
        }
        .padding(18)
        .background(IDS.Colors.card)
        .cornerRadius(IDS.Layout.cardCornerRadius).idsCardBorder(cornerRadius: IDS.Layout.cardCornerRadius)
        .confirmationDialog("Report this job", isPresented: $showingReportOptions, titleVisibility: .visible) {
            Button("Asks for money or a fee") { Task { await report("The post asks applicants to pay money or a fee") } }
            Button("Pay or work details are misleading") { Task { await report("The pay or work details appear misleading") } }
            Button("Looks unsafe or illegal") { Task { await report("The post appears unsafe or illegal") } }
        } message: { Text("Reports go to Itunda’s review queue.") }
        .onAppear {
            locationFetcher.onLocation = { coordinate in
                myLocation = coordinate
                showRoute = true
            }
        }
        .onChange(of: locationFetcher.errorMessage) { message in
            if let message { error = message }
        }
        .task { await loadHoodReviews() }
    }

    // Real read-back for the review above (item 192/198/199).
    private func loadHoodReviews() async {
        guard isMine, post.status == "FILLED", post.workerId != nil else { return }
        do {
            let reviews = try await NetworkClient.shared.getJobPostReviews(post.id).reviews
            hoodReviews = reviews
            if reviews.contains(where: { $0.reviewerId == currentUserId }) { reviewSubmitted = true }
        } catch {
            // Real, non-critical -- the review form itself still works without this.
        }
    }

    private func actionButton(_ label: String, filled: Bool, action: @escaping () async -> Void) -> some View {
        Button(action: { Task { await action() } }) {
            Text(label)
                .font(.subheadline).bold()
                .foregroundColor(filled ? .white : IDS.Colors.textPrimary)
                .padding(.horizontal, 16).padding(.vertical, 10)
                .background(filled ? IDS.Colors.brand : IDS.Colors.chipBackground)
                .cornerRadius(12)
        }
        .disabled(busy)
    }

    private func markFilled(workerPhoneNumber: String?) async {
        busy = true
        defer { busy = false }
        do {
            _ = try await NetworkClient.shared.markJobPostFilled(post.id, workerPhoneNumber: workerPhoneNumber?.isEmpty == true ? nil : workerPhoneNumber)
            markingFilled = false
            onChanged()
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    // Real post-transaction review with asymmetric public/private visibility
    // (2026-07-24) -- see backend HoodReviewService's own doc comment.
    private func submitReview() async {
        submittingReview = true
        defer { submittingReview = false }
        do {
            let res = try await NetworkClient.shared.submitJobPostReview(
                post.id, goodPoints: Array(selectedGoodPoints), uncomfortablePoints: Array(selectedUncomfortablePoints),
            )
            reviewSubmitted = true
            showReviewSheet = false
            hoodReviews = (hoodReviews ?? []) + [res.review]
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    private func remove() async {
        busy = true
        defer { busy = false }
        do {
            _ = try await NetworkClient.shared.removeJobPost(post.id)
            onChanged()
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    // Real 당근알바-style structured application (2026-07-25 on Android, ported here
    // 2026-07-29) -- see ApplyToJobRequest's own doc comment.
    private func submitApplication() async {
        submittingApplication = true
        defer { submittingApplication = false }
        do {
            _ = try await NetworkClient.shared.applyToJob(post.id, message: applicationMessage.trimmingCharacters(in: .whitespacesAndNewlines))
            applying = false
            applicationSubmitted = true
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    private func loadApplications() async {
        do {
            applications = try await NetworkClient.shared.getApplicationsForJobPost(post.id).applications
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    private func respond(_ application: JobApplicationDto, accept: Bool) async {
        respondingToId = application.id
        defer { respondingToId = nil }
        do {
            _ = try await NetworkClient.shared.respondToJobApplication(application.id, accept: accept)
            applications = applications?.filter { $0.id != application.id }
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    private func report(_ reason: String) async {
        busy = true
        defer { busy = false }
        do {
            _ = try await NetworkClient.shared.reportHoodContent(targetType: "JOB_POST", targetId: post.id, reason: reason)
            error = "Thanks. Your report was sent for review."
        } catch let NetworkError.httpError(statusCode) where statusCode == 409 {
            error = "You already reported this job."
        } catch {
            self.error = "Couldn't send the report. Check your connection and try again."
        }
    }
}

// ============================== PROPERTY (당근부동산) ==============================

