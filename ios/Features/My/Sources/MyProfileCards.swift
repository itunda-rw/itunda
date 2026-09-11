//
//  MyProfileCards.swift
//  Extracted from MyTabView.swift (2026-09-11, file-size-lint pass during the
//  pagination-discard sweep -- adding mini-app-catalog pagination state pushed
//  MyTabView.swift past 500 lines for the first time). ProfilePhotoCard/
//  VerificationCard/VerificationRow are a genuinely self-contained cluster --
//  each owns its own state and fetches its own data -- mirroring bank-mfe's
//  own MyProfileCards.tsx naming for this exact same cluster.
//

import SwiftUI
import UIKit
import CoreDesignSystem
import CoreNetwork

// TalkScreen.errorMessage (App-only, 23 other real callers) isn't reachable from a
// Feature module, so this Feature keeps its own local copy.
func errorMessage(_ statusCode: Int) -> String {
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

// Real email/phone verification (item 169/179) -- see NetworkClient.swift's own doc
// comment. bank-mfe (item 169) and Android (item 178) already have this; this is the
// iOS port. A real code is delivered via a real in-app Notification + push, no real
// SMS/email gateway exists.
// Real profile photo (URL, not a binary upload) -- also the real, buildable half of
// Rewards' task_profile. Found 2026-07-29 via a full-backend-endpoint sweep: a real,
// working `PUT /api/v1/auth/profile/photo` endpoint with zero client anywhere, and
// `PublicUser.profilePhotoUrl` wasn't even carried by this DTO until now.
struct ProfilePhotoCard: View {
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

struct VerificationCard: View {
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

struct VerificationRow: View {
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
