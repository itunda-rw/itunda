//
// IDSCoreComponents.swift
// Canonical IDS primitive surface for iOS.
// Semantic parity with the Web IDS contract; native SwiftUI controls preserve platform conventions.
//

import SwiftUI

public enum IDSButtonVariant { case primary, secondary, tertiary, danger }

public struct IDSButton: View {
    public let title: String
    public var variant: IDSButtonVariant = .primary
    public var loading = false
    public var disabled = false
    public let action: () -> Void

    public init(title: String, variant: IDSButtonVariant = .primary, loading: Bool = false, disabled: Bool = false, action: @escaping () -> Void) {
        self.title = title; self.variant = variant; self.loading = loading; self.disabled = disabled; self.action = action
    }

    public var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                if loading { ProgressView().controlSize(.small) }
                Text(title).font(IDS.Typography.body.weight(.semibold)).multilineTextAlignment(.center)
            }
            .frame(minWidth: 44, minHeight: 48).frame(maxWidth: .infinity)
        }
        .buttonStyle(IDSButtonStyle(variant: variant))
        .disabled(disabled || loading)
        .accessibilityValue(loading ? Text("Loading") : Text(""))
    }
}

private struct IDSButtonStyle: ButtonStyle {
    let variant: IDSButtonVariant
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .foregroundStyle(foreground)
            .background(background)
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            .opacity(configuration.isPressed ? 0.82 : 1)
            .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).strokeBorder(border, lineWidth: variant == .secondary ? 1 : 0))
            .animation(.easeOut(duration: 0.14), value: configuration.isPressed)
    }
    private var foreground: Color {
        switch variant { case .primary, .danger: return .white; case .secondary, .tertiary: return IDS.Colors.textBrand }
    }
    private var background: Color {
        switch variant { case .primary: return IDS.Colors.brand; case .danger: return IDS.Colors.error; case .secondary: return IDS.Colors.backgroundSecondary; case .tertiary: return .clear }
    }
    private var border: Color { variant == .secondary ? IDS.Colors.divider : .clear }
}

public struct IDSTextField: View {
    public let label: String
    @Binding public var text: String
    public var helpText: String? = nil
    public var error: String? = nil
    public var success: String? = nil
    public var required = false
    public var disabled = false
    public var secure = false

    public init(label: String, text: Binding<String>, helpText: String? = nil, error: String? = nil, success: String? = nil, required: Bool = false, disabled: Bool = false, secure: Bool = false) {
        self.label = label; self._text = text; self.helpText = helpText; self.error = error; self.success = success; self.required = required; self.disabled = disabled; self.secure = secure
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(required ? "\(label) *" : label).font(IDS.Typography.label)
            Group {
                if secure { SecureField(label, text: $text) } else { TextField(label, text: $text) }
            }
            .textFieldStyle(.plain).padding(.horizontal, 14).frame(minHeight: 52)
            .background(IDS.Colors.backgroundSecondary)
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).strokeBorder(error == nil ? IDS.Colors.divider : IDS.Colors.error, lineWidth: 1))
            .disabled(disabled)
            if let message = error ?? success ?? helpText {
                Text(message).font(IDS.Typography.caption).foregroundStyle(error != nil ? IDS.Colors.error : success != nil ? IDS.Colors.success : IDS.Colors.textSecondary)
            }
        }
        .accessibilityElement(children: .contain).accessibilityLabel(Text(label)).accessibilityHint(Text(error ?? success ?? helpText ?? ""))
    }
}

public struct IDSSelect<Option: Hashable>: View {
    public let label: String
    public let options: [Option]
    @Binding public var selection: Option?
    public let title: (Option) -> String
    public var disabled = false
    public var success: String? = nil
    public var disabledOptions: Set<Option> = []

    public init(label: String, options: [Option], selection: Binding<Option?>, disabled: Bool = false, success: String? = nil, disabledOptions: Set<Option> = [], title: @escaping (Option) -> String) {
        self.label = label; self.options = options; self._selection = selection; self.disabled = disabled; self.success = success; self.disabledOptions = disabledOptions; self.title = title
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label).font(IDS.Typography.label)
            Picker(label, selection: $selection) {
                Text("Select").tag(Optional<Option>.none)
                ForEach(options, id: \.self) { option in
                    Text(title(option))
                        .tag(Optional(option))
                        .disabled(disabledOptions.contains(option))
                }
            }
            .pickerStyle(.menu).frame(minHeight: 48).disabled(disabled)
            if let success { Text(success).font(IDS.Typography.caption).foregroundStyle(IDS.Colors.success) }
        }
    }
}

public struct IDSCheckbox: View {
    public let title: String
    @Binding public var isOn: Bool
    public var disabled = false
    public var description: String? = nil
    public var error: String? = nil

    public init(title: String, isOn: Binding<Bool>, disabled: Bool = false, description: String? = nil, error: String? = nil) {
        self.title = title; self._isOn = isOn; self.disabled = disabled; self.description = description; self.error = error
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Toggle(title, isOn: $isOn).frame(minHeight: 44).disabled(disabled)
            if let description { Text(description).font(IDS.Typography.caption).foregroundStyle(IDS.Colors.textSecondary) }
            if let error { Text(error).font(IDS.Typography.caption).foregroundStyle(IDS.Colors.error).accessibilityAddTraits(.isStaticText) }
        }
        .accessibilityElement(children: .contain)
    }
}

public struct IDSRadio: View {
    public let title: String
    public let selected: Bool
    public let action: () -> Void
    public var disabled = false
    public var description: String? = nil
    public var error: String? = nil
    public var groupLabel: String? = nil

    public init(title: String, selected: Bool, disabled: Bool = false, description: String? = nil, error: String? = nil, groupLabel: String? = nil, action: @escaping () -> Void) {
        self.title = title; self.selected = selected; self.disabled = disabled; self.description = description; self.error = error; self.groupLabel = groupLabel; self.action = action
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Button(action: action) {
                HStack(spacing: 12) {
                    Image(systemName: selected ? "largecircle.fill.circle" : "circle")
                    Text(title).multilineTextAlignment(.leading)
                    Spacer(minLength: 0)
                }.frame(minWidth: 44, minHeight: 44)
            }
            .buttonStyle(.plain)
            .disabled(disabled)
            .accessibilityAddTraits(selected ? .isSelected : [])
            if let description { Text(description).font(IDS.Typography.caption).foregroundStyle(IDS.Colors.textSecondary) }
            if let error { Text(error).font(IDS.Typography.caption).foregroundStyle(IDS.Colors.error) }
        }
        .accessibilityElement(children: .contain)
        .accessibilityLabel(Text(groupLabel.map { "\($0): \(title)" } ?? title))
        .accessibilityValue(Text(selected ? "Selected" : "Not selected"))
        .accessibilityHint(Text(error ?? description ?? ""))
    }
}

public struct IDSSwitch: View {
    public let title: String
    @Binding public var isOn: Bool
    public var loading = false
    public var disabled = false
    public var error: String? = nil
    public init(title: String, isOn: Binding<Bool>, loading: Bool = false, disabled: Bool = false, error: String? = nil) { self.title = title; self._isOn = isOn; self.loading = loading; self.disabled = disabled; self.error = error }
    public var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Toggle(title, isOn: $isOn).frame(minHeight: 44).disabled(disabled || loading)
                .accessibilityValue(loading ? Text("Updating") : Text(isOn ? "On" : "Off"))
            if let error { Text(error).font(IDS.Typography.caption).foregroundStyle(IDS.Colors.error) }
        }
        .accessibilityElement(children: .contain)
        .accessibilityHint(Text(error ?? ""))
    }
}

public struct IDSTabs: View {
    public struct Item: Identifiable {
        public let id: String
        public let title: String
        public init(id: String, title: String) { self.id = id; self.title = title }
    }
    public let items: [Item]
    @Binding public var selection: String
    public init(items: [Item], selection: Binding<String>) { self.items = items; self._selection = selection }
    public var body: some View {
        Picker("Tabs", selection: $selection) {
            ForEach(items) { item in Text(item.title).tag(item.id) }
        }.pickerStyle(.segmented).frame(minHeight: 44).accessibilityLabel("Tabs")
    }
}

public struct IDSEmptyState: View {
    public let title: String
    public let message: String
    public var actions: [IDSButtonAction] = []
    public init(title: String, message: String, actions: [IDSButtonAction] = []) { self.title = title; self.message = message; self.actions = Array(actions.prefix(2)) }
    public var body: some View {
        VStack(spacing: 12) {
            Text(title).font(IDS.Typography.title).multilineTextAlignment(.center)
            Text(message).font(IDS.Typography.body).foregroundStyle(IDS.Colors.textSecondary).multilineTextAlignment(.center)
            ForEach(actions) { action in IDSButton(title: action.title, variant: action.variant, action: action.action) }
        }.padding(24).frame(maxWidth: 520).accessibilityElement(children: .contain)
    }
}

public struct IDSButtonAction: Identifiable {
    public let id = UUID()
    public let title: String
    public let variant: IDSButtonVariant
    public let action: () -> Void
    public init(title: String, variant: IDSButtonVariant = .primary, action: @escaping () -> Void) { self.title = title; self.variant = variant; self.action = action }
}
