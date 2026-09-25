import SwiftUI

/// IDS component contract shared with Android and Web.
public struct IDSPrimaryButton: View {
    let title: String; let loading: Bool; let action: () -> Void
    public init(_ title: String, loading: Bool = false, action: @escaping () -> Void) { self.title=title; self.loading=loading; self.action=action }
    public var body: some View { Button(action: action) { if loading { ProgressView() } else { Text(title).fontWeight(.semibold) } }.frame(minHeight: 48).frame(maxWidth: .infinity).buttonStyle(.borderedProminent).tint(IDSColor.indigo500).clipShape(RoundedRectangle(cornerRadius: IDSRadii.md)) }
}

public struct IDSField: View {
    @Binding var text: String; let title: String; let error: String?
    public init(_ title: String, text: Binding<String>, error: String? = nil) { self.title=title; self._text=text; self.error=error }
    public var body: some View { VStack(alignment: .leading, spacing: 6) { TextField(title, text: $text).textFieldStyle(.roundedBorder); if let error { Text(error).font(.caption).foregroundStyle(.red) } } }
}
