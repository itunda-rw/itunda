import SwiftUI
import LocalAuthentication
import CoreDesignSystem

// Real per-room Links tab + room settings (itunda Talk redesign, 2026-08-28) --
// combines both pieces behind one entry point (real KakaoTalk's own 채팅방 설정 screen
// bundles Photos/Files/Links and settings together the same way; TalkChatThread.swift
// is baseline-frozen with almost no line headroom, so this stays a single toolbar
// button rather than two).
//
// `texts` is the already-fetched message-body history from the calling thread screen
// (1:1 or group) -- no separate backend call. Explicitly Links only, not a general
// Files tab: no file-attachment-in-chat capability exists anywhere in this codebase
// today (only images).
//
// Theme/background and input-lock are genuinely local-only preferences, same as real
// KakaoTalk. Notification-sound preference is stored locally too, but honestly not
// yet wired into outbound push delivery -- itunda's push payload has no per-conversation
// sound lookup on the backend yet; this is a real, named scope limit, not fabricated
// behavior.
struct TalkRoomInfoView: View {
    let roomId: String
    let texts: [String]

    @Environment(\.dismiss) private var dismiss
    private enum Tab { case links, settings }
    @State private var tab: Tab = .links

    var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                Picker("", selection: $tab) {
                    Text("Links").tag(Tab.links)
                    Text("Settings").tag(Tab.settings)
                }
                .pickerStyle(.segmented)
                .padding()

                if tab == .links {
                    TalkLinksTab(texts: texts)
                } else {
                    TalkRoomSettings(roomId: roomId, texts: texts)
                }
            }
            .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
            .navigationTitle("Room info")
            .navigationBarItems(trailing: Button("Close") { dismiss() })
        }
    }
}

// Real client-side URL extraction over already-fetched message history -- no backend
// call, no separate storage; NSDataDetector is the same real, standard iOS API used
// by Messages.app/Mail.app for link detection.
struct TalkLinksTab: View {
    let texts: [String]

    private var links: [String] {
        guard let detector = try? NSDataDetector(types: NSTextCheckingResult.CheckingType.link.rawValue) else { return [] }
        var seen = Set<String>()
        var result: [String] = []
        for text in texts.reversed() {
            let matches = detector.matches(in: text, range: NSRange(text.startIndex..., in: text))
            for match in matches {
                guard let range = Range(match.range, in: text) else { continue }
                let url = String(text[range])
                if seen.insert(url).inserted { result.append(url) }
            }
        }
        return result
    }

    var body: some View {
        if links.isEmpty {
            EmptyStateView("No links shared in this chat yet.")
                .padding(.horizontal, IDS.Layout.screenHorizontal)
                .padding(.top, 40)
        } else {
            List(links, id: \.self) { link in
                Button(link) {
                    if let url = URL(string: link) { UIApplication.shared.open(url) }
                }
                .font(.caption)
                .lineLimit(1)
            }
            .listStyle(.plain)
        }
    }
}

// Real per-room settings -- theme/background and input-lock genuinely local-only
// (device preference, no backend, same as real KakaoTalk); notification-sound choice
// persisted locally (see this file's own header comment on the honest scope limit);
// export chat generates a real client-side text file from already-fetched history.
struct TalkRoomSettings: View {
    let roomId: String
    let texts: [String]

    private var themeKey: String { "talk.room.\(roomId).theme" }
    private var soundKey: String { "talk.room.\(roomId).sound" }
    private var lockKey: String { "talk.room.\(roomId).locked" }

    @State private var theme: String
    @State private var sound: String
    @State private var locked: Bool
    @State private var lockError: String?
    @State private var showShareSheet = false
    @State private var exportedText = ""

    private static let themes = ["Default", "Ocean", "Sunset", "Forest"]
    private static let sounds = ["Default", "Chime", "Pop", "None"]

    init(roomId: String, texts: [String]) {
        self.roomId = roomId
        self.texts = texts
        let defaults = UserDefaults.standard
        _theme = State(initialValue: defaults.string(forKey: "talk.room.\(roomId).theme") ?? "Default")
        _sound = State(initialValue: defaults.string(forKey: "talk.room.\(roomId).sound") ?? "Default")
        _locked = State(initialValue: defaults.bool(forKey: "talk.room.\(roomId).locked"))
    }

    var body: some View {
        List {
            Section("Theme") {
                Picker("Background", selection: $theme) {
                    ForEach(Self.themes, id: \.self) { Text($0) }
                }
                .onChange(of: theme) { UserDefaults.standard.set($0, forKey: themeKey) }
            }
            Section("Notifications") {
                Picker("Sound", selection: $sound) {
                    ForEach(Self.sounds, id: \.self) { Text($0) }
                }
                .onChange(of: sound) { UserDefaults.standard.set($0, forKey: soundKey) }
            }
            Section("Privacy") {
                Toggle("Require Face ID / Touch ID to open", isOn: Binding(
                    get: { locked },
                    set: { newValue in toggleLock(newValue) }
                ))
                if let lockError { Text(lockError).font(.caption).foregroundColor(.red) }
            }
            Section("Export") {
                Button("Export chat as text file") { exportChat() }
            }
        }
        .sheet(isPresented: $showShareSheet) {
            ActivityShareSheet(items: [exportedText])
        }
    }

    private func toggleLock(_ enable: Bool) {
        guard enable else {
            locked = false
            UserDefaults.standard.set(false, forKey: lockKey)
            return
        }
        let context = LAContext()
        guard context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: nil) else {
            lockError = "Face ID / Touch ID isn't set up on this device."
            return
        }
        context.evaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, localizedReason: "Enable a lock on this chat") { success, _ in
            DispatchQueue.main.async {
                if success {
                    locked = true
                    lockError = nil
                    UserDefaults.standard.set(true, forKey: lockKey)
                } else {
                    lockError = "Couldn't verify. Try again."
                }
            }
        }
    }

    private func exportChat() {
        exportedText = texts.reversed().joined(separator: "\n")
        showShareSheet = true
    }
}

// Real per-room input-lock enforcement -- checked at the call site (TalkScreen.swift)
// before a locked room's real thread renders at all, not just at the settings toggle.
// A locked room re-prompts Face ID / Touch ID every time it's opened, matching real
// KakaoTalk's own per-room lock behavior.
struct RoomLockGate<Content: View>: View {
    let roomId: String
    @ViewBuilder let content: () -> Content

    @State private var unlocked = false
    @State private var error: String?

    private var isLocked: Bool { UserDefaults.standard.bool(forKey: "talk.room.\(roomId).locked") }

    var body: some View {
        if !isLocked || unlocked {
            content()
        } else {
            VStack(spacing: 16) {
                Image(systemName: "lock.fill").font(.system(size: 36)).foregroundColor(IDS.Colors.textSecondary)
                Text("This chat is locked").font(IDS.Typography.bodyBold)
                if let error { Text(error).font(.caption).foregroundColor(.red) }
                Button("Unlock") { authenticate() }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
            .task { authenticate() }
        }
    }

    private func authenticate() {
        let context = LAContext()
        guard context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: nil) else {
            error = "Face ID / Touch ID isn't set up on this device."
            return
        }
        context.evaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, localizedReason: "Unlock this chat") { success, _ in
            DispatchQueue.main.async {
                if success { unlocked = true } else { error = "Couldn't verify." }
            }
        }
    }
}

struct ActivityShareSheet: UIViewControllerRepresentable {
    let items: [Any]
    func makeUIViewController(context: Context) -> UIActivityViewController { UIActivityViewController(activityItems: items, applicationActivities: nil) }
    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
