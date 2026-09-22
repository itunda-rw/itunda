import SwiftUI
import CoreSDUI

/// SDUI Renderer
/// Takes Server-Driven UI components and maps them to Toss Design System (TDS) views.
public struct SduiRenderer: View {
    let components: [SduiComponent]
    let onAction: (String, [String: String]) -> Void
    
    public init(components: [SduiComponent], onAction: @escaping (String, [String: String]) -> Void) {
        self.components = components
        self.onAction = onAction
    }
    
    public var body: some View {
        VStack(spacing: 0) {
            ForEach(0..<components.count, id: \.self) { index in
                let component = components[index]
                let action = component.actions?.first
                
                switch component.type {
                case "BUTTON":
                    let text = (component.data["text"]?.value as? String) ?? "Button"
                    IdsButton(text: text) {
                        if let action = action {
                            onAction(action.actionType, action.payload)
                        }
                    }
                    .padding(.horizontal, 24)
                    
                case "LIST_ROW":
                    let title = (component.data["title"]?.value as? String) ?? ""
                    let subtitle = component.data["subtitle"]?.value as? String
                    let rightText = component.data["rightText"]?.value as? String
                    IdsListRow(title: title, subtitle: subtitle, rightText: rightText) {
                        if let action = action {
                            onAction(action.actionType, action.payload)
                        }
                    }
                    
                case "SPACER":
                    let size = (component.data["size"]?.value as? Int) ?? 16
                    Spacer().frame(height: CGFloat(size))
                    
                default:
                    Text("Unknown component: \(component.type)")
                        .foregroundColor(IdsPalette.red500)
                }
            }
        }
    }
}