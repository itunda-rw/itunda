import SwiftUI

/// SwiftUI bridge into a `SaroniteMiniAppViewController`, same shape as
/// `TransferQuoteScreen`'s own `.sheet` presentation pattern elsewhere in this app --
/// granite/RN mini-apps are UIKit view controllers, not SwiftUI views, so this is the one
/// unavoidable `UIViewControllerRepresentable` boundary.
struct SaronitePayBillsView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> PayBillsMiniAppViewController {
        PayBillsMiniAppViewController()
    }

    func updateUIViewController(_ uiViewController: PayBillsMiniAppViewController, context: Context) {}
}

struct SaroniteWalletBalanceView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> WalletBalanceMiniAppViewController {
        WalletBalanceMiniAppViewController()
    }

    func updateUIViewController(_ uiViewController: WalletBalanceMiniAppViewController, context: Context) {}
}

struct SaroniteRewardTasksView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> RewardTasksMiniAppViewController {
        RewardTasksMiniAppViewController()
    }

    func updateUIViewController(_ uiViewController: RewardTasksMiniAppViewController, context: Context) {}
}

struct SaroniteInsuranceView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> InsuranceMiniAppViewController {
        InsuranceMiniAppViewController()
    }

    func updateUIViewController(_ uiViewController: InsuranceMiniAppViewController, context: Context) {}
}
