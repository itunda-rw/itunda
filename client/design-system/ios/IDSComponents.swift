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


public struct IDSCard<Content: View>: View {
    let content: () -> Content
    public init(@ViewBuilder content: @escaping () -> Content) { self.content = content }
    public var body: some View { content().padding(16).frame(maxWidth: .infinity, alignment: .leading).background(.background, in: RoundedRectangle(cornerRadius: IDSRadii.lg)).overlay(RoundedRectangle(cornerRadius: IDSRadii.lg).stroke(.quaternary)) }
}
public struct IDSAlert: View {
    let title: String; let message: String; let dismiss: (() -> Void)?
    public init(title:String,message:String,dismiss:(() -> Void)?=nil){self.title=title;self.message=message;self.dismiss=dismiss}
    public var body: some View { VStack(alignment:.leading,spacing:6){Text(title).font(.headline);Text(message).font(.subheadline).foregroundStyle(.secondary);if let dismiss { Button("Dismiss",action:dismiss).font(.subheadline.weight(.semibold)) }}.padding(16).frame(maxWidth:.infinity,alignment:.leading).background(.thinMaterial,in:RoundedRectangle(cornerRadius:IDSRadii.md)) }
}
