//
//  AppDelegate.swift
//  Itunda
//
//  Main entry point for Itunda iOS Native Shell
//

import UIKit

@main
class AppDelegate: UIResponder, UIApplicationDelegate {

    var window: UIWindow?

    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        
        window = UIWindow(frame: UIScreen.main.bounds)
        
        // Toss architecture dictates determining entry point based on security context
        // and routing appropriately. Core Banking goes to Native.
        let bankVC = BankViewController()
        window?.rootViewController = UINavigationController(rootViewController: bankVC)
        window?.makeKeyAndVisible()
        
        return true
    }
}
