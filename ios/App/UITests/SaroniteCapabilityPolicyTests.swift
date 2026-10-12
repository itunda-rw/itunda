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
        let runtime = SaroniteRuntimeTrust.partner(policyVersion: 1, scopes: ["account:read"])
        XCTAssertTrue(SaroniteCapabilityPolicy.allows(runtime: runtime, capability: .accountRead))
        XCTAssertFalse(SaroniteCapabilityPolicy.allows(runtime: runtime, capability: nil))
    }

    func testWalletBalanceRemainsFirstPartyOnlyForPartnerRuntimes() {
        SaroniteRuntimeContext.activateFirstParty()
        XCTAssertTrue(SaroniteRuntimeContext.isFirstParty)

        XCTAssertTrue(SaroniteRuntimeContext.activatePartner(
            policyVersion: 1,
            scopes: ["account:read"]
        ))
        XCTAssertFalse(SaroniteRuntimeContext.isFirstParty)
        XCTAssertTrue(SaroniteRuntimeContext.allows(path: "api/v1/account", method: "GET"))
        XCTAssertFalse(SaroniteRuntimeContext.allows(nil))
    }

    func testMissingPartnerScopeDeniesAccountRead() {
        let runtime = SaroniteRuntimeTrust.partner(policyVersion: 1, scopes: [])
        XCTAssertFalse(SaroniteCapabilityPolicy.allows(runtime: runtime, capability: .accountRead))
    }

    func testUnknownPolicyVersionDeniesAllPartnerCapabilities() {
        let runtime = SaroniteRuntimeTrust.partner(policyVersion: 99, scopes: ["account:read"])
        XCTAssertFalse(SaroniteCapabilityPolicy.allows(runtime: runtime, capability: .accountRead))
    }

    func testUnknownAndDuplicateScopesAreRejected() {
        XCTAssertNil(SaroniteCapabilityPolicy.validatedPartnerScopes(
            policyVersion: 1,
            scopes: ["account:read", "payments:write"]
        ))
        XCTAssertNil(SaroniteCapabilityPolicy.validatedPartnerScopes(
            policyVersion: 1,
            scopes: ["account:read", "account:read"]
        ))
    }

    func testOnlyKnownVersionAndScopeAreAccepted() {
        XCTAssertEqual(
            SaroniteCapabilityPolicy.validatedPartnerScopes(
                policyVersion: 1,
                scopes: ["account:read"]
            ),
            Set(["account:read"])
        )
        XCTAssertNil(SaroniteCapabilityPolicy.validatedPartnerScopes(
            policyVersion: 2,
            scopes: ["account:read"]
        ))
    }

    func testRuntimeContextFailsClosedAfterInvalidPartnerActivation() {
        SaroniteRuntimeContext.activateFirstParty()
        XCTAssertTrue(SaroniteRuntimeContext.allows(nil))

        XCTAssertFalse(SaroniteRuntimeContext.activatePartner(
            policyVersion: 1,
            scopes: ["account:read", "account:read"]
        ))
        XCTAssertFalse(SaroniteRuntimeContext.allows(.accountRead))
        XCTAssertFalse(SaroniteRuntimeContext.allowsExternalNavigation)
    }

    func testPartnerContextAllowsOnlyItsApprovedReadCapability() {
        XCTAssertTrue(SaroniteRuntimeContext.activatePartner(
            policyVersion: 1,
            scopes: ["account:read"]
        ))
        XCTAssertTrue(SaroniteRuntimeContext.allows(.accountRead))
        XCTAssertFalse(SaroniteRuntimeContext.allows(nil))
        XCTAssertFalse(SaroniteRuntimeContext.allowsExternalNavigation)
        SaroniteRuntimeContext.deactivate()
        XCTAssertFalse(SaroniteRuntimeContext.allows(.accountRead))
    }

    override func tearDown() {
        SaroniteRuntimeContext.deactivate()
        super.tearDown()
    }
}
