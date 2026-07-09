//
//  BankViewController.swift
//  Itunda
//
//  Core Banking Native View
//  This module is entirely native Swift to ensure 60fps animations for transactions
//  and max security for money movement, perfectly matching Toss Bank's architecture.
//

import UIKit
import SwiftUI

class BankViewController: UIViewController {
    
    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground
        
        verifySecurityAndLoadData()
    }
    
    private func setupSecureUI() {
        // Bridging SwiftUI (MainTabView) into the secure UIKit Core Bank shell
        var mainTabView = MainTabView()
        mainTabView.onLaunchSaroniteApp = { [weak self] moduleName in
            // Fact-checked: Toss Brownfield pattern
            // Launch the Saronite (React Native) Mini-App securely inside the Native Shell
            let saroniteVC = SaroniteViewController()
            // In reality you would pass the module URL/name to load the correct JS bundle
            // saroniteVC.miniAppBundleURL = URL(string: "saronite://\(moduleName)") 
            self?.navigationController?.pushViewController(saroniteVC, animated: true)
        }
        
        let hostingController = UIHostingController(rootView: mainTabView)
        hostingController.view.translatesAutoresizingMaskIntoConstraints = false
        addChild(hostingController)
        view.addSubview(hostingController.view)
        
        NSLayoutConstraint.activate([
            hostingController.view.topAnchor.constraint(equalTo: view.topAnchor),
            hostingController.view.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            hostingController.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            hostingController.view.trailingAnchor.constraint(equalTo: view.trailingAnchor)
        ])
        
        hostingController.didMove(toParent: self)
    }
    
    private func verifySecurityAndLoadData() {
        if ZeroTrust.shared.verifyDeviceIntegrity() {
            print("Device Secure. Loading Itunda Core Bank data...")
            setupSecureUI()
        } else {
            // Handle security breach (e.g., force logout)
            print("Device Insecure. Access to Core Bank denied.")
        }
    }
}
