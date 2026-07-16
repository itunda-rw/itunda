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
