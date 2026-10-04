import Foundation
import CryptoKit

/// Immutable release descriptor consumed by the iOS partner loader.
/// The backend catalog is the source of truth; callers must copy every field exactly.
struct SaronitePartnerRelease {
    let releaseId: String
    let manifestUrl: URL
    let manifestSha256: String
    let bundleUrl: URL
    let bundleSha256: String
    let bundleSizeBytes: Int64
    let permissions: Set<String>
}

enum SaronitePartnerReleaseError: LocalizedError {
    case missing(String)
    case invalid(String)
    case integrity(String)
    case network(String)

    var errorDescription: String? {
        switch self {
        case .missing(let field): return "Mini-app release is missing \(field)"
        case .invalid(let field): return "Mini-app release has invalid \(field)"
        case .integrity(let field): return "Mini-app release failed \(field) verification"
        case .network(let message): return message
        }
    }
}

/// Fail-closed iOS half of the partner release-integrity contract.
///
/// This deliberately verifies the manifest before downloading the executable bundle,
/// verifies exact byte count and SHA-256, stores only verified bytes, and exposes the
/// verified file as the bundle source used by SaroniteHost. It does not silently fall
/// back to an unverified remote bundle.
final class SaronitePartnerReleaseLoader {
    static let shared = SaronitePartnerReleaseLoader()

    /// Read by SaroniteReactNativeFactoryDelegate.bundleURL().
    /// A nil value means normal first-party Metro/packaged-bundle behavior.
    static private(set) var activeBundleURL: URL?

    private let session: URLSession
    private let fileManager = FileManager.default

    private init() {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = 30
        configuration.timeoutIntervalForResource = 60
        session = URLSession(configuration: configuration)
    }

    func load(_ release: SaronitePartnerRelease) async throws -> URL {
        try validate(release)

        let manifestData = try await download(release.manifestUrl)
        guard sha256(manifestData) == normalized(release.manifestSha256) else {
            throw SaronitePartnerReleaseError.integrity("manifest SHA-256")
        }

        try validateManifest(manifestData, release: release)

        let bundle = try await verifiedBundleData(release)
        let url = try storeVerifiedBundle(bundle, releaseId: release.releaseId)

        // Only after every integrity check succeeds does the host become eligible to use
        // this bundle. Security scope is armed at the same boundary.
        Self.activeBundleURL = url
        MiniAppSecurityContext.activeScopes = release.permissions
        return url
    }

    func unload() {
        Self.activeBundleURL = nil
        MiniAppSecurityContext.activeScopes = nil
    }

    private func validate(_ release: SaronitePartnerRelease) throws {
        guard !release.releaseId.isEmpty else { throw SaronitePartnerReleaseError.missing("releaseId") }
        guard release.manifestUrl.scheme?.lowercased() == "https" else {
            throw SaronitePartnerReleaseError.invalid("manifestUrl (HTTPS required)")
        }
        guard release.bundleUrl.scheme?.lowercased() == "https" else {
            throw SaronitePartnerReleaseError.invalid("bundleUrl (HTTPS required)")
        }
        guard release.bundleSizeBytes >= 0 else {
            throw SaronitePartnerReleaseError.invalid("bundleSizeBytes")
        }
        guard isSHA256(release.manifestSha256), isSHA256(release.bundleSha256) else {
            throw SaronitePartnerReleaseError.invalid("SHA-256 metadata")
        }
    }

    private func validateManifest(_ data: Data, release: SaronitePartnerRelease) throws {
        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw SaronitePartnerReleaseError.invalid("manifest JSON")
        }

        // The manifest is authenticated by manifestSha256. These identity checks prevent
        // a valid manifest from a different release being paired with this catalog entry.
        if let id = json["releaseId"] as? String, id != release.releaseId {
            throw SaronitePartnerReleaseError.integrity("manifest releaseId")
        }
        if let bundleSha = json["bundleSha256"] as? String,
           normalized(bundleSha) != normalized(release.bundleSha256) {
            throw SaronitePartnerReleaseError.integrity("manifest bundleSha256")
        }
        if let size = json["bundleSizeBytes"] as? NSNumber,
           size.int64Value != release.bundleSizeBytes {
            throw SaronitePartnerReleaseError.integrity("manifest bundleSizeBytes")
        }
    }

    private func verifiedBundleData(_ release: SaronitePartnerRelease) async throws -> Data {
        let cache = try cacheURL(release.releaseId)
        if fileManager.fileExists(atPath: cache.path),
           let existing = try? Data(contentsOf: cache),
           Int64(existing.count) == release.bundleSizeBytes,
           sha256(existing) == normalized(release.bundleSha256) {
            return existing
        }

        let data = try await download(release.bundleUrl)
        guard Int64(data.count) == release.bundleSizeBytes else {
            throw SaronitePartnerReleaseError.integrity("bundle size")
        }
        guard sha256(data) == normalized(release.bundleSha256) else {
            throw SaronitePartnerReleaseError.integrity("bundle SHA-256")
        }
        return data
    }

    private func storeVerifiedBundle(_ data: Data, releaseId: String) throws -> URL {
        let url = try cacheURL(releaseId)
        let temporary = url.appendingPathExtension("tmp")
        try data.write(to: temporary, options: .atomic)
        if fileManager.fileExists(atPath: url.path) {
            try fileManager.removeItem(at: url)
        }
        try fileManager.moveItem(at: temporary, to: url)
        return url
    }

    private func cacheURL(_ releaseId: String) throws -> URL {
        let base = try fileManager.url(for: .cachesDirectory, in: .userDomainMask, appropriateFor: nil, create: true)
        let safe = releaseId.map { $0.isLetter || $0.isNumber || "._-".contains($0) ? String($0) : "_" }.joined()
        return base.appendingPathComponent("saronite-partner-(safe).jsbundle")
    }

    private func download(_ url: URL) async throws -> Data {
        do {
            let (data, response) = try await session.data(from: url)
            guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
                throw SaronitePartnerReleaseError.network("Download failed for \(url.absoluteString)")
            }
            return data
        } catch let error as SaronitePartnerReleaseError {
            throw error
        } catch {
            throw SaronitePartnerReleaseError.network(error.localizedDescription)
        }
    }

    private func sha256(_ data: Data) -> String {
        SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
    }

    private func normalized(_ value: String) -> String { value.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() }

    private func isSHA256(_ value: String) -> Bool {
        normalized(value).range(of: "^[0-9a-f]{64}$", options: .regularExpression) != nil
    }
}
