import XCTest

final class LaunchSmokeTests: XCTestCase {
    func testAppLaunchesWithoutCrash() throws {
        let app = XCUIApplication()
        app.launch()

        XCTAssertTrue(
            app.wait(for: .runningForeground, timeout: 15),
            "ItundaApp should launch and remain in the foreground"
        )
    }
}
