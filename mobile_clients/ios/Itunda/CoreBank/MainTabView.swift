//
//  MainTabView.swift
//  Itunda
//
//  Fact-checked Toss Bottom Navigation Structure for iOS.
//  Uses a custom floating tab bar over a ZStack to mirror Toss Design System (TDS).
//

import SwiftUI

enum BottomTab: String, CaseIterable {
    case home = "Home"
    case benefits = "Benefits"
    case transfer = "Transfer"
    case pay = "Pay"
    case menu = "Menu"

    var symbol: String {
        switch self {
        case .home:
            return "house"
        case .benefits:
            return "gift"
        case .transfer:
            return "arrow.left.arrow.right"
        case .pay:
            return "creditcard"
        case .menu:
            return "square.grid.2x2"
        }
    }
}

struct MainTabView: View {
    @State private var currentTab: BottomTab = .home
    
    // Callback for native navigation bridge
    var onLaunchSaroniteApp: ((String) -> Void)?
    
    var body: some View {
        ZStack(alignment: .bottom) {
            // Main Content Area
            TabView(selection: $currentTab) {
                BankView()
                    .tag(BottomTab.home)
                
                PlaceholderView(title: "Benefits", subtitle: "Cashback, coupons, and daily rewards")
                    .tag(BottomTab.benefits)
                
                PlaceholderView(title: "Transfer", subtitle: "Send to bank, wallet, or mobile money")
                    .tag(BottomTab.transfer)
                
                PlaceholderView(title: "Pay", subtitle: "Scan QR and pay merchants around Kigali")
                    .tag(BottomTab.pay)
                
                MenuView(onLaunchSaroniteApp: onLaunchSaroniteApp)
                    .tag(BottomTab.menu)
            }
            
            // Floating Tab Bar
            TossFloatingTabBar(currentTab: $currentTab)
        }
        .ignoresSafeArea(.keyboard, edges: .bottom)
    }
}

struct TossFloatingTabBar: View {
    @Binding var currentTab: BottomTab
    
    var body: some View {
        HStack {
            ForEach(BottomTab.allCases, id: \.self) { tab in
                let isSelected = currentTab == tab
                
                Button(action: {
                    withAnimation(.spring(response: 0.3, dampingFraction: 0.7)) {
                        currentTab = tab
                    }
                }) {
                    VStack(spacing: 4) {
                        Image(systemName: tab.symbol)
                            .font(.system(size: 18, weight: .medium))
                            .foregroundColor(isSelected ? IDS.Colors.iconPrimary : IDS.Colors.iconSecondary)
                            .frame(width: IDS.Layout.tabBarIconSize, height: IDS.Layout.tabBarIconSize)
                        
                        Text(tab.rawValue)
                            .font(.system(size: 10, weight: isSelected ? .bold : .medium))
                            .foregroundColor(isSelected ? IDS.Colors.textPrimary : IDS.Colors.textSecondary)
                    }
                    .frame(maxWidth: .infinity)
                }
            }
        }
        .padding(.vertical, 12)
        .padding(.horizontal, 8)
        .background(IDS.Colors.card)
        .cornerRadius(IDS.Layout.tabBarCornerRadius)
        .shadow(color: IDS.Colors.shadow, radius: IDS.Layout.floatingTabShadowRadius, x: 0, y: 8)
        .padding(.horizontal, 16)
        .padding(.bottom, 16)
    }
}

struct PlaceholderView: View {
    let title: String
    let subtitle: String
    
    var body: some View {
        ZStack {
            IDS.Colors.backgroundPrimary.ignoresSafeArea()
            VStack(spacing: IDS.Layout.tightGap) {
                Text(title)
                    .font(IDS.Typography.title)
                    .foregroundColor(IDS.Colors.textPrimary)
                Text(subtitle)
                    .font(IDS.Typography.bodyMedium)
                    .foregroundColor(IDS.Colors.textSecondary)
            }
            .padding(IDS.Layout.cardPadding)
            .background(IDS.Colors.card)
            .cornerRadius(IDS.Layout.sectionCornerRadius)
            .padding(.horizontal, IDS.Layout.screenHorizontal)
        }
    }
}

struct MainTabView_Previews: PreviewProvider {
    static var previews: some View {
        MainTabView()
    }
}
