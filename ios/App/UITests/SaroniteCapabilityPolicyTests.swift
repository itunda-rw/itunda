import XCTest
@testable import ItundaApp

final class SaroniteCapabilityPolicyTests: XCTestCase {
    func testUninitializedRuntimeDeniesEveryCapability() {
        XCTAssertFalse(SaroniteCapabilityPolicy.allows(runtime: .uninitialized, capability: .accountRead))
        XCTAssertFalse(SaroniteCapabilityPolicy.allows(runtime: .uninitialized, capability: nil))
    }

    func testFirstPartyRuntimePreservesExistingBridgeBehavior() {
        XCTAssertTrue(SaroniteCapabilityPolicy.allows(runtime: .firstParty, capability: .accountRead))
        XCTAssertTrue(SaroniteCapabilityPolicy.allows(runtime: .firstParty, capability: nil))
    }

    func testPartnerWithAccountReadCanReadAccountOnly() {
        let runtime = SaroniteRuntimeTrust.partner(manifestVersion: 1, scopes: ["account:read"])
        XCTAssertTrue(SaroniteCapabilityPolicy.allows(runtime: runtime, capability: .accountRead))
        XCTAssertFalse(SaroniteCapabilityPolicy.allows(runtime: runtime, capability: nil))
    }

    func testMissingPartnerScopeDeniesAccountRead() {
        let runtime = SaroniteRuntimeTrust.partner(manifestVersion: 1, scopes: [])
        XCTAssertFalse(SaroniteCapabilityPolicy.allows(runtime: runtime, capability: .accountRead))
    }

    func testUnknownManifestVersionDeniesAllPartnerCapabilities() {
        let runtime = SaroniteRuntimeTrust.partner(manifestVersion: 99, scopes: ["account:read"])
        XCTAssertFalse(SaroniteCapabilityPolicy.allows(runtime: runtime, capability: .accountRead))
    }

    func testUnknownAndDuplicateScopesAreRejected() {
        XCTAssertNil(SaroniteCapabilityPolicy.validatedPartnerScopes(
            manifestVersion: 1,
            scopes: ["account:read", "payments:write"]
        ))
        XCTAssertNil(SaroniteCapabilityPolicy.validatedPartnerScopes(
            manifestVersion: 1,
            scopes: ["account:read", "account:read"]
        ))
    }

    func testOnlyKnownVersionAndScopeAreAccepted() {
        XCTAssertEqual(
            SaroniteCapabilityPolicy.validatedPartnerScopes(
                manifestVersion: 1,
                scopes: ["account:read"]
            ),
            Set(["account:read"])
        )
        XCTAssertNil(SaroniteCapabilityPolicy.validatedPartnerScopes(
            manifestVersion: 2,
            scopes: ["account:read"]
        ))
    }
}
