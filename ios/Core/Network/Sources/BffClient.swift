import Foundation
import CoreSDUI

/// BFF (Backend For Frontend) Client Protocol
public protocol BffClient {
    /// Fetches the Server-Driven UI layout and data for a specific screen.
    func getScreen(screenName: String, params: [String: String]) async throws -> SduiResponse
    
    /// Standard API execution for specific feature intentions.
    func executeAction(action: SduiAction) async throws -> [String: AnyCodable]
}