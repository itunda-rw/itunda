import XCTest
@testable import ItundaApp

final class SaronitePartnerReleaseLoaderTests: XCTestCase {
    private let manifestSha = String(repeating: "0", count: 64)
    private let bundleSha = String(repeating: "1", count: 64)

    private func release(
        manifestUrl: String = "https://cdn.example.com/release/manifest.json",
        bundleUrl: String = "https://cdn.example.com/release/app.jsbundle",
        bundleSizeBytes: Int64 = 3
    ) -> SaronitePartnerRelease {
        SaronitePartnerRelease(
            releaseId: "release-123",
            manifestUrl: URL(string: manifestUrl)!,
            manifestSha256: manifestSha,
            bundleUrl: URL(string: bundleUrl)!,
            bundleSha256: bundleSha,
            bundleSizeBytes: bundleSizeBytes,
            permissions: ["account:read", "profile:read"]
        )
    }

    private func manifest(
        releaseId: String = "release-123",
        bundleSha256: String? = nil,
        bundleUrl: String = "https://cdn.example.com/release/app.jsbundle",
        type: String = "saronite",
        permissions: [String] = ["account:read", "profile:read"],
        bundleSizeBytes: Int = 3
    ) throws -> Data {
        let object: [String: Any] = [
            "releaseId": releaseId,
            "bundleSha256": bundleSha256 ?? bundleSha,
            "entry": ["bundleUrl": bundleUrl, "type": type],
            "permissions": permissions,
            "bundleSizeBytes": bundleSizeBytes
        ]
        return try JSONSerialization.data(withJSONObject: object)
    }

    func testValidateRequiresHTTPSForManifest() {
        XCTAssertThrowsError(try SaronitePartnerReleaseLoader.shared.validate(release(manifestUrl: "http://cdn.example.com/manifest.json")))
    }

    func testValidateRequiresHTTPSForBundle() {
        XCTAssertThrowsError(try SaronitePartnerReleaseLoader.shared.validate(release(bundleUrl: "http://cdn.example.com/app.jsbundle")))
    }

    func testValidateRejectsInvalidSha256Metadata() {
        let value = SaronitePartnerRelease(
            releaseId: "release-123",
            manifestUrl: URL(string: "https://cdn.example.com/release/manifest.json")!,
            manifestSha256: "not-a-sha",
            bundleUrl: URL(string: "https://cdn.example.com/release/app.jsbundle")!,
            bundleSha256: bundleSha,
            bundleSizeBytes: 3,
            permissions: ["account:read", "profile:read"]
        )
        XCTAssertThrowsError(try SaronitePartnerReleaseLoader.shared.validate(value))
    }

    func testValidateRejectsNegativeBundleSize() {
        XCTAssertThrowsError(try SaronitePartnerReleaseLoader.shared.validate(release(bundleSizeBytes: -1)))
    }

    func testValidateManifestAcceptsMatchingRelease() throws {
        try SaronitePartnerReleaseLoader.shared.validateManifest(try manifest(), release: release())
    }

    func testValidateManifestRejectsReleaseIdMismatch() throws {
        XCTAssertThrowsError(try SaronitePartnerReleaseLoader.shared.validateManifest(try manifest(releaseId: "other"), release: release()))
    }

    func testValidateManifestRejectsBundleShaMismatch() throws {
        XCTAssertThrowsError(try SaronitePartnerReleaseLoader.shared.validateManifest(
            try manifest(bundleSha256: String(repeating: "2", count: 64)), release: release()
        ))
    }

    func testValidateManifestRejectsBundleUrlMismatch() throws {
        XCTAssertThrowsError(try SaronitePartnerReleaseLoader.shared.validateManifest(
            try manifest(bundleUrl: "https://cdn.example.com/other.jsbundle"), release: release()
        ))
    }

    func testValidateManifestRejectsWrongEntryType() throws {
        XCTAssertThrowsError(try SaronitePartnerReleaseLoader.shared.validateManifest(
            try manifest(type: "web"), release: release()
        ))
    }

    func testValidateManifestRejectsPermissionMismatch() throws {
        XCTAssertThrowsError(try SaronitePartnerReleaseLoader.shared.validateManifest(
            try manifest(permissions: ["account:read"]), release: release()
        ))
    }

    func testValidateManifestRejectsBundleSizeMismatch() throws {
        XCTAssertThrowsError(try SaronitePartnerReleaseLoader.shared.validateManifest(
            try manifest(bundleSizeBytes: 4), release: release()
        ))
    }

    func testValidateManifestRejectsMalformedJSON() {
        XCTAssertThrowsError(try SaronitePartnerReleaseLoader.shared.validateManifest(Data("{not-json".utf8), release: release()))
    }

    func testCacheIsolatedByReleaseId() throws {
        let loader = SaronitePartnerReleaseLoader.shared
        let first = try loader.cacheURL("release/one")
        let second = try loader.cacheURL("release-two")
        XCTAssertNotEqual(first, second)
        XCTAssertTrue(first.lastPathComponent.contains("release_one"))
        XCTAssertTrue(second.lastPathComponent.contains("release-two"))
    }
}
