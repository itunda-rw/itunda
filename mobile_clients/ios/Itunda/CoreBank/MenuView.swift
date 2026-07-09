//
//  MenuView.swift
//  Itunda
//
//  Replicates the Toss "All" tab.
//  Acts as a bridge launcher for React Native (Saronite) mini-apps in a Brownfield architecture.
//

import SwiftUI

struct MenuItem: Identifiable {
    let id = UUID()
    let title: String
    let moduleName: String
    let isNative: Bool
}

let menuItems: [MenuItem] = [
    MenuItem(title: "Get a Loan", moduleName: "loan_mini_app", isNative: false),
    MenuItem(title: "Insurance (Mutuelle)", moduleName: "insurance_mini_app", isNative: false),
    MenuItem(title: "Check Credit Score", moduleName: "credit_mini_app", isNative: false),
    MenuItem(title: "Buy Electricity", moduleName: "utility_mini_app", isNative: false),
    MenuItem(title: "Pay Taxes (RRA)", moduleName: "tax_mini_app", isNative: false),
    MenuItem(title: "Settings (Native)", moduleName: "settings", isNative: true)
]

struct MenuView: View {
    // This closure will be passed down from the bridging UIViewController to handle native routing
    var onLaunchSaroniteApp: ((String) -> Void)?
    
    var body: some View {
        ZStack {
            IDS.Colors.background.ignoresSafeArea()
            
            VStack(alignment: .leading, spacing: 0) {
                Text("All Services")
                    .font(IDS.Typography.header)
                    .foregroundColor(IDS.Colors.textPrimary)
                    .padding(.horizontal, 20)
                    .padding(.top, 16)
                    .padding(.bottom, 16)
                
                ScrollView(showsIndicators: false) {
                    VStack(spacing: 12) {
                        ForEach(menuItems) { item in
                            MenuRowView(item: item) {
                                if !item.isNative {
                                    print("Bridging to Saronite (React Native) module: \(item.moduleName)")
                                    onLaunchSaroniteApp?(item.moduleName)
                                } else {
                                    print("Navigating natively to Settings")
                                }
                            }
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.bottom, 100) // Padding for floating tab bar
                }
            }
        }
    }
}

struct MenuRowView: View {
    let item: MenuItem
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            HStack(spacing: 16) {
                Circle()
                    .fill(IDS.Colors.background)
                    .frame(width: 48, height: 48)
                
                Text(item.title)
                    .font(IDS.Typography.bodyBold)
                    .foregroundColor(IDS.Colors.textPrimary)
                
                Spacer()
            }
            .padding(16)
            .background(IDS.Colors.card)
            .cornerRadius(IDS.Layout.cardCornerRadius)
        }
        .buttonStyle(PlainButtonStyle())
    }
}
