//
//  MyTabView.swift
//  Real Naver-style "My" personal hub (2026-07-22) -- at the user's direct request:
//  "My should be like Naver style My since we have shopping and eats and other products
//  where users need to easily get track of their orders, reservation, favorites." Every
//  number/row here is a real fetched count or preview, not decoration -- the same "no
//  fabricated numbers" discipline this app already follows elsewhere.
//
//  Trimmed down (2026-07-24) to ONLY this unique content -- its old "Quick links" and
//  "My account" sections are deleted, since both now fully duplicate rows already in
//  EntireMenuScreen's own catalog (now FeatureMenu). Mirrors Android's trimmed MyTab
//  exactly. This is now its own primary tab (ItundaTab.You, 2026-08-10, see
//  ContentView.swift's own doc comment) -- not reached via EntireMenuScreen's
//  profile icon anymore.
//
//  Moved here from App/Sources/BenefitsShopAllScreens.swift (2026-09-02, My
//  Feature-module decomposition, matching Android's own already-real
//  :features:my:impl) -- was originally rebuilt 2026-07-11 alongside
//  BenefitsScreen/DiscoverScreen/EntireMenuScreen (both retired or moved
//  separately; see FeatureMenu's own doc comment for that history).
//

import SwiftUI
import UIKit
import CoreDesignSystem
import CoreNetwork

// TalkScreen.errorMessage (App-only, 23 other real callers) isn't reachable from a
// Feature module, so this Feature keeps its own local copy.
private func errorMessage(_ statusCode: Int) -> String {
    switch statusCode {
    case 400: return "Please check what you entered and try again."
    case 401, 403: return "You don't have access to do that."
    case 404: return "That couldn't be found."
    case 409: return "That's already been done, or is being processed."
    case 422: return "Insufficient funds for this order."
    case 429: return "Too many attempts -- please wait a moment and try again."
    default: return "Something went wrong. Please try again."
    }
}

public struct MyTabView: View {
    var onBack: () -> Void = {}
    var onSwitchToShop: () -> Void = {}
    var onSwitchToEats: () -> Void = {}
    var onSwitchToMarketplace: () -> Void = {}
    var onSwitchToJobs: () -> Void = {}
    var onSwitchToProperty: () -> Void = {}
    var onUpdatePin: (_ currentCredential: String, _ newPin: String) async -> String? = { _, _ in nil }

    @State private var shopOrders: [OrderDto] = []
    @State private var eatsOrders: [EatsOrderDto] = []
    @State private var favoriteListingsCount = 0
    @State private var favoriteJobPostsCount = 0
    @State private var favoritePropertyListingsCount = 0
    @State private var favoriteRestaurantsCount = 0
    @State private var myListingsCount = 0
    @State private var myJobPostsCount = 0
    @State private var myPropertyListingsCount = 0
    // Real 쿠팡파트너스 (Coupang Partners)-style affiliate earnings read-back (item 229)
    // -- link creation itself happens inline on the Shop product card's Share icon;
    // this is purely the read-back, mirroring bank-mfe's own AffiliateEarningsCard and
    // Android's own port.
    @State private var affiliateLinks: [AffiliateLinkDto] = []
    @State private var affiliateCommissions: [AffiliateCommissionDto] = []
    @State private var myScamReports: [ScamReportDto] = []
    @State private var myBookingReviews: [MerchantBookingReviewDto] = []
    // Real published third-party mini-app catalog (Partners product-completeness
    // pass, 2026-09-07) -- see PartnerMiniAppCatalogCard.swift's own doc comment.
    @State private var miniApps: [PartnerMiniAppDto] = []

    public init(
        onBack: @escaping () -> Void = {},
        onSwitchToShop: @escaping () -> Void = {},
        onSwitchToEats: @escaping () -> Void = {},
        onSwitchToMarketplace: @escaping () -> Void = {},
        onSwitchToJobs: @escaping () -> Void = {},
        onSwitchToProperty: @escaping () -> Void = {},
        onUpdatePin: @escaping (_ currentCredential: String, _ newPin: String) async -> String? = { _, _ in nil }
    ) {
        self.onBack = onBack
        self.onSwitchToShop = onSwitchToShop
        self.onSwitchToEats = onSwitchToEats
        self.onSwitchToMarketplace = onSwitchToMarketplace
        self.onSwitchToJobs = onSwitchToJobs
        self.onSwitchToProperty = onSwitchToProperty
        self.onUpdatePin = onUpdatePin
    }

    public var body: some View {
        ScrollView {
            VStack(spacing: IDS.Layout.sectionSpacing) {
                HStack {
                    Button(action: onBack) { IDS.Icons.back(size: 17, color: IDS.Colors.textPrimary, relativeTo: .body) }.accessibilityLabel("Back")
                    Spacer()
                    Text("My").font(IDS.scaledFont(size: 20, weight: .bold, relativeTo: .title2)).foregroundColor(IDS.Colors.textPrimary)
                    Spacer()
                    Color.clear.frame(width: 20)
                }
                ProfilePhotoCard()
                // Real Toss-sourced passwordless-login rollout (2026-08-23) -- see
                // PinUpgradeCard.swift's own doc comment. Own file, not inline here,
                // matching this session's own file-size-lint discipline for this
                // already-large file.
                PinUpgradeCard(onUpdatePin: onUpdatePin)
                VerificationCard()
                if !affiliateLinks.isEmpty {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("Partner earnings").font(IDS.scaledFont(size: 15, weight: .bold, relativeTo: .subheadline)).foregroundColor(IDS.Colors.textPrimary)
                        Text("Earn 3% on any purchase made through a product link you've shared.").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        HStack {
                            Text("Links shared").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                            Spacer()
                            Text("\(affiliateLinks.count)").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                        }
                        HStack {
                            Text("Total clicks").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                            Spacer()
                            Text("\(affiliateLinks.reduce(0) { $0 + $1.clickCount })").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                        }
                        HStack {
                            Text("Total earned").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                            Spacer()
                            Text("\(Int(affiliateCommissions.reduce(0) { $0 + $1.commissionAmount })) RWF").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                        }
                    }
                    // Real fix (2026-08-24, flat-design sweep): dropped the Card wrapper --
                    // matches "My orders" below (already flat) and the identical Android/
                    // web Partner-earnings conversion (docs/UI_UX_GUIDELINES.md §10).
                    .padding(.vertical, 10).frame(maxWidth: .infinity, alignment: .leading)
                }
                // Real order tracking -- Naver Pay/Shopping's own "My" tab leads with
                // recent orders across every product, not a settings list. Tapping
                // switches to that product's own tab where the full order-history view
                // already lives.
                if !shopOrders.isEmpty || !eatsOrders.isEmpty {
                    VStack(alignment: .leading, spacing: 10) {
                        Text("My orders").font(IDS.scaledFont(size: 19, weight: .bold, relativeTo: .title2)).foregroundColor(IDS.Colors.textPrimary)
                        ForEach(shopOrders.prefix(3)) { order in
                            orderRow(label: "Shop order", status: order.status, amount: order.totalAmount, action: onSwitchToShop)
                        }
                        ForEach(eatsOrders.prefix(3)) { order in
                            orderRow(label: "Eats order", status: order.status, amount: order.totalAmount, action: onSwitchToEats)
                        }
                    }
                }
                // Real favorites/wishlist tracking across every product with one --
                // counts are real (GET .../favorites on each module); tapping switches
                // directly to that real destination's own dedicated wishlist view.
                // Each of Marketplace/Jobs/Property is its own flat destination now
                // (2026-08-10, Hood's segmented Picker retired), so this is a real,
                // direct deep link, not "one more tap" into a shared sub-view.
                FlatSection(title: "My favorites", rows: [
                    FlatRow(title: "Marketplace wishlist", trailing: "\(favoriteListingsCount)", glyph: { AnyView(WishlistHeart(favorited: true, size: 28)) }, action: onSwitchToMarketplace),
                    FlatRow(title: "Jobs wishlist", trailing: "\(favoriteJobPostsCount)", glyph: { AnyView(WishlistHeart(favorited: true, size: 28)) }, action: onSwitchToJobs),
                    FlatRow(title: "Property wishlist", trailing: "\(favoritePropertyListingsCount)", glyph: { AnyView(WishlistHeart(favorited: true, size: 28)) }, action: onSwitchToProperty),
                    FlatRow(title: "Restaurant favorites", trailing: "\(favoriteRestaurantsCount)", glyph: { AnyView(WishlistHeart(favorited: true, size: 28)) }, action: onSwitchToEats),
                ])
                // Real "my own posts" tracking (Marketplace/Jobs/Property listings I
                // created) -- same Naver-style "track your own activity" pattern.
                FlatSection(title: "My listings", rows: [
                    FlatRow(title: "Marketplace", trailing: "\(myListingsCount)", glyph: { AnyView(PlaceGlyph(category: "MARKET", size: 28)) }, action: onSwitchToMarketplace),
                    FlatRow(title: "Jobs posted", trailing: "\(myJobPostsCount)", glyph: { AnyView(BriefcaseGlyph(size: 28)) }, action: onSwitchToJobs),
                    FlatRow(title: "Property listed", trailing: "\(myPropertyListingsCount)", glyph: { AnyView(TravelHouse(size: 28)) }, action: onSwitchToProperty),
                ])
                // Real "My scam reports" history -- see MyScamReportsSection.swift's own
                // doc comment for the full account of this parity gap.
                MyBookingReviewsSection(reviews: myBookingReviews)
                MyScamReportsSection(reports: myScamReports)
                if !miniApps.isEmpty {
                    PartnerMiniAppCatalogCard(miniApps: miniApps)
                }
                // "My account" (My assets/Get a loan/Credit score/etc) deliberately
                // dropped here (2026-07-24) -- every one of those rows already lives in
                // EntireMenuScreen's own "Financial services" section now that All is
                // the primary bottom tab; keeping a second copy here would just be stale
                // duplication. Mirrors Android's identical MyTab cleanup.
            }
            .padding(.horizontal, IDS.Layout.screenHorizontal)
            .padding(.top, IDS.Layout.screenTop)
            .padding(.bottom, IDS.Layout.sectionSpacing)
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .task {
            // Each fetch independent and best-effort -- one product's API hiccup must
            // never blank the rest of this real personal-activity summary.
            if let res = try? await NetworkClient.shared.getMyOrders() { shopOrders = res.orders }
            if let res = try? await NetworkClient.shared.getMyEatsOrders() { eatsOrders = res.orders }
            if let res = try? await NetworkClient.shared.getMyFavoriteListings() { favoriteListingsCount = res.favorites.count }
            if let res = try? await NetworkClient.shared.getMyFavoriteJobPosts() { favoriteJobPostsCount = res.favorites.count }
            if let res = try? await NetworkClient.shared.getMyFavoritePropertyListings() { favoritePropertyListingsCount = res.favorites.count }
            if let res = try? await NetworkClient.shared.getMyFavoriteRestaurants() { favoriteRestaurantsCount = res.favorites.count }
            // Real accuracy fix (2026-09-09, same pass as getMyListings's
            // pagination fix): this badge previously showed page 1's item
            // count (capped at 20), not the real total, for any user with
            // more than 20 real listings.
            if let res = try? await NetworkClient.shared.getMyListings() { myListingsCount = res.totalElements }
            // Real accuracy fix (2026-09-09, same pass as getMyJobPosts's
            // pagination fix): this badge previously showed page 1's item
            // count (capped at 20), not the real total, for any user with
            // more than 20 real job posts.
            if let res = try? await NetworkClient.shared.getMyJobPosts() { myJobPostsCount = res.totalElements }
            if let res = try? await NetworkClient.shared.getMyPropertyListings() { myPropertyListingsCount = res.listings.count }
            if let res = try? await NetworkClient.shared.getMyAffiliateLinks() { affiliateLinks = res.links }
            if let res = try? await NetworkClient.shared.getMyAffiliateCommissions() { affiliateCommissions = res.commissions }
            if let res = try? await NetworkClient.shared.getMyScamReports() { myScamReports = res.reports }
            if let res = try? await NetworkClient.shared.getMyBookingReviews() { myBookingReviews = res.reviews }
            if let res = try? await NetworkClient.shared.getMiniAppCatalog() { miniApps = res.miniApps }
        }
    }

    @ViewBuilder
    private func orderRow(label: String, status: String, amount: Double, action: @escaping () -> Void) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(label).font(IDS.scaledFont(size: 15, weight: .semibold, relativeTo: .subheadline)).foregroundColor(IDS.Colors.textPrimary)
                Text(status).font(.caption).foregroundColor(IDS.Colors.textSecondary)
            }
            Spacer()
            Text("\(formatAmount(Int(amount))) RWF").foregroundColor(IDS.Colors.textPrimary)
        }
        .contentShape(Rectangle())
        .onTapGesture(perform: action)
        .padding(.vertical, 6)
    }
}

// Real email/phone verification (item 169/179) -- see NetworkClient.swift's own doc
// comment. bank-mfe (item 169) and Android (item 178) already have this; this is the
// iOS port. A real code is delivered via a real in-app Notification + push, no real
// SMS/email gateway exists.
// Real profile photo (URL, not a binary upload) -- also the real, buildable half of
// Rewards' task_profile. Found 2026-07-29 via a full-backend-endpoint sweep: a real,
// working `PUT /api/v1/auth/profile/photo` endpoint with zero client anywhere, and
// `PublicUser.profilePhotoUrl` wasn't even carried by this DTO until now.
private struct ProfilePhotoCard: View {
    @State private var profilePhotoUrl: String?
    @State private var urlInput = ""
    @State private var saving = false
    @State private var error: String?

    var body: some View {
        HStack(spacing: 12) {
            if let profilePhotoUrl, let url = URL(string: profilePhotoUrl) {
                AsyncImage(url: url) { image in
                    image.resizable().aspectRatio(contentMode: .fill)
                } placeholder: {
                    Circle().fill(IDS.Colors.chipBackground)
                }
                .frame(width: 56, height: 56)
                .clipShape(Circle())
            } else {
                Circle().fill(IDS.Colors.chipBackground).frame(width: 56, height: 56)
            }
            VStack(alignment: .leading, spacing: 6) {
                if let error { Text(error).font(.caption).foregroundColor(.red) }
                TextField("Profile photo URL", text: $urlInput)
                    .padding(8).background(IDS.Colors.chipBackground).cornerRadius(8)
                Button(action: { Task { await save() } }) {
                    Text(saving ? "Saving…" : "Save photo").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                        .padding(.horizontal, 10).padding(.vertical, 6)
                        .background(IDS.Colors.card).cornerRadius(10).idsCardBorder(cornerRadius: 10)
                }
                .disabled(saving)
            }
        }
        .padding(16).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius).idsCardBorder(cornerRadius: IDS.Layout.cardCornerRadius)
        .task {
            do {
                let user = try await NetworkClient.shared.getProfile().user
                profilePhotoUrl = user.profilePhotoUrl
                urlInput = user.profilePhotoUrl ?? ""
            } catch {
                // Real, non-critical -- the rest of "My" still works without this.
            }
        }
    }

    private func save() async {
        let trimmed = urlInput.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { error = "Enter a photo URL."; return }
        saving = true; error = nil
        defer { saving = false }
        do {
            profilePhotoUrl = try await NetworkClient.shared.updateProfilePhoto(profilePhotoUrl: trimmed).user.profilePhotoUrl
        } catch {
            self.error = "Could not update your profile photo."
        }
    }
}

private struct VerificationCard: View {
    @State private var email: String?
    @State private var emailVerified = true
    @State private var phoneVerified = true
    @State private var loaded = false

    private func load() async {
        do {
            let user = try await NetworkClient.shared.getProfile().user
            email = user.email
            emailVerified = user.emailVerified ?? true
            phoneVerified = user.phoneVerified ?? true
        } catch {
            // Best-effort, matching this card's own bank-mfe/Android precedent.
        }
        loaded = true
    }

    var body: some View {
        Group {
            if loaded && !(emailVerified && phoneVerified) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Verify your account").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                    if !phoneVerified { VerificationRow(kind: "phone", hasEmail: true, onVerified: { Task { await load() } }) }
                    if !emailVerified { VerificationRow(kind: "email", hasEmail: email != nil, onVerified: { Task { await load() } }) }
                }
                // Real fix (2026-08-24, flat-design sweep): dropped the Card wrapper --
                // matches Android's identical VerificationCard conversion, a lone
                // conditional section (docs/UI_UX_GUIDELINES.md §10).
                .padding(.vertical, 10)
            }
        }
        .task { await load() }
    }
}

private struct VerificationRow: View {
    let kind: String
    let hasEmail: Bool
    let onVerified: () -> Void

    @State private var sent = false
    @State private var code = ""
    @State private var busy = false
    @State private var error: String?
    @FocusState private var codeFieldFocused: Bool
    // Real Toss-style "OTP Successful Animation" + wrong-code shake (60fps.design's
    // own real catalog of Toss's named interactions, 2026-08-29) -- reuses
    // IdsCelebrationScreen's exact spring/haptic checkmark language and
    // AccountPinPad's exact shake sequence, matching Android's identical fix same
    // session.
    @State private var verified = false
    @State private var checkScale: CGFloat = 0.3
    @State private var shakeOffset: CGFloat = 0
    // Real Toss "Verification Code Shimmer Animation" equivalent (web/Android's own
    // VerificationRow got this same session -- closing the parity gap now): a
    // subtle pulse on the input while the submitted code is being verified.
    @State private var inputOpacity: Double = 1

    var body: some View {
        if kind == "email" && !hasEmail {
            Text("No email address on file to verify.").font(.caption).foregroundColor(IDS.Colors.textSecondary)
        } else {
            VStack(alignment: .leading, spacing: 4) {
                if verified {
                    HStack(spacing: 8) {
                        ZStack {
                            Circle().fill(IDS.Colors.success).frame(width: 22, height: 22)
                            Image(systemName: "checkmark").font(.system(size: 11, weight: .bold)).foregroundColor(.white)
                        }
                        .scaleEffect(checkScale)
                        Text(kind == "email" ? "Email verified" : "Phone number verified").font(.caption).bold().foregroundColor(IDS.Colors.success)
                    }
                } else if !sent {
                    HStack {
                        Text(kind == "email" ? "Email not verified" : "Phone number not verified").font(.caption).foregroundColor(IDS.Colors.textPrimary)
                        Spacer()
                        Button(action: { Task { await send() } }) {
                            Text(busy ? "…" : "Send code").font(.caption).bold().foregroundColor(.white)
                                .padding(.horizontal, 10).padding(.vertical, 6)
                                .background(busy ? IDS.Colors.textTertiary : IDS.Colors.brand).cornerRadius(8)
                        }
                        .disabled(busy)
                    }
                } else {
                    HStack(spacing: 8) {
                        TextField("Enter code", text: $code)
                            .padding(10).background(IDS.Colors.chipBackground).cornerRadius(8)
                            .keyboardType(.numberPad)
                            .focused($codeFieldFocused)
                            .opacity(inputOpacity)
                            .onChange(of: busy) { isBusy in
                                if isBusy {
                                    withAnimation(.easeInOut(duration: 0.45).repeatForever(autoreverses: true)) { inputOpacity = 0.55 }
                                } else {
                                    withAnimation(.linear(duration: 0.15)) { inputOpacity = 1 }
                                }
                            }
                            // Real "Minimum Input" simplicity fix, closing
                            // docs/DESIGN_REFERENCES.md §11 recommendation #2's iOS gap
                            // (rule #4: auto-focus so the keyboard appears without an extra
                            // tap, matching web's already-shipped autoFocus on this exact
                            // field). Triggered once, right when the code field first
                            // appears (the moment "sent" flips true), not on every
                            // recomposition.
                            .onAppear { codeFieldFocused = true }
                            // Real "Minimum Input" simplicity fix (Toss's own researched,
                            // sourced pattern -- toss.tech/article/4-ways-for-minimum-input,
                            // rule #2: "for fixed-digit fields like ID or phone numbers, the
                            // CTA button becomes unnecessary" -- see
                            // docs/DESIGN_REFERENCES.md §11). This code is a real, fixed
                            // 6-digit OTP (AuthService.kt's own doc comment). Auto-confirms
                            // the instant the 6th digit is typed; the button stays visible as
                            // a manual fallback rather than being removed outright, since this
                            // is a security-sensitive identity-verification step.
                            .onChange(of: code) { newValue in
                                let trimmed = newValue.trimmingCharacters(in: .whitespaces)
                                if trimmed.count == 6 && trimmed.allSatisfy({ $0.isNumber }) && !busy {
                                    Task { await confirm() }
                                }
                            }
                        // Real CTA-label-clarity fix (2026-08-24, docs/DESIGN_REFERENCES.md §11)
                        // -- same fix as Android/web's identical VerificationRow (commit
                        // 58d58259): a bare "Confirm" doesn't state the outcome, per Toss's own
                        // dark-pattern-prevention CTA rule.
                        Button(action: { Task { await confirm() } }) {
                            Text(busy ? "…" : (kind == "email" ? "Verify email" : "Verify phone number")).font(.caption).bold().foregroundColor(.white)
                                .padding(.horizontal, 12).padding(.vertical, 10)
                                .background((busy || code.trimmingCharacters(in: .whitespaces).isEmpty) ? IDS.Colors.textTertiary : IDS.Colors.brand).cornerRadius(8)
                        }
                        .disabled(busy || code.trimmingCharacters(in: .whitespaces).isEmpty)
                    }
                    .offset(x: shakeOffset)
                }
                if let error {
                    Text(error).font(.caption).foregroundColor(.red)
                }
            }
            .padding(.vertical, 6)
        }
    }

    private func send() async {
        busy = true
        error = nil
        defer { busy = false }
        do {
            if kind == "email" {
                _ = try await NetworkClient.shared.requestEmailVerification()
            } else {
                _ = try await NetworkClient.shared.requestPhoneVerification()
            }
            sent = true
        } catch NetworkError.httpError(let statusCode) where statusCode == 409 {
            // Real gap found live (Toss-style error-handling audit, 2026-08-30): only
            // reachable via stale client state (verified on another device/tab between
            // this row rendering and the tap) -- not really a failure, resolve forward.
            // 409 is unambiguous for this specific call: EMAIL_ALREADY_VERIFIED/
            // PHONE_ALREADY_VERIFIED are the only 409s either endpoint can return.
            onVerified()
        } catch let NetworkError.httpError(statusCode) {
            error = errorMessage(statusCode)
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    private func confirm() async {
        busy = true
        error = nil
        defer { busy = false }
        do {
            if kind == "email" {
                _ = try await NetworkClient.shared.confirmEmailVerification(token: code.trimmingCharacters(in: .whitespaces))
            } else {
                _ = try await NetworkClient.shared.confirmPhoneVerification(code: code.trimmingCharacters(in: .whitespaces))
            }
            UINotificationFeedbackGenerator().notificationOccurred(.success)
            verified = true
            withAnimation(IDS.Motion.springMedium) { checkScale = 1 }
            try? await Task.sleep(nanoseconds: 500_000_000)
            onVerified()
        } catch let NetworkError.httpError(statusCode) {
            error = errorMessage(statusCode)
            shake()
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
            shake()
        }
    }

    private func shake() {
        withAnimation(.linear(duration: 0.06)) { shakeOffset = 16 }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.06) {
            withAnimation(.linear(duration: 0.06)) { shakeOffset = -16 }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.06) {
                withAnimation(.linear(duration: 0.06)) { shakeOffset = 0 }
            }
        }
    }
}

