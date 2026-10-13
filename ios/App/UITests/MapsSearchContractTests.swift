import XCTest
import CoreNetwork

final class MapsSearchContractTests: XCTestCase {
    func testLegacyPayloadDefaultsAlternateNamesToEmpty() throws {
        let data = #"{"displayName":"Kigali, Rwanda","latitude":-1.9441,"longitude":30.0619}"#.data(using: .utf8)!
        let place = try JSONDecoder().decode(PlaceSearchResultDto.self, from: data)
        XCTAssertEqual(place.alternativeNames, [])
    }

    func testPayloadDecodesAlternateNames() throws {
        let data = #"{"displayName":"KCC","latitude":-1.95,"longitude":30.09,"alternativeNames":["Kigali Convention Center"]}"#.data(using: .utf8)!
        let place = try JSONDecoder().decode(PlaceSearchResultDto.self, from: data)
        XCTAssertEqual(place.alternativeNames, ["Kigali Convention Center"])
    }
}
