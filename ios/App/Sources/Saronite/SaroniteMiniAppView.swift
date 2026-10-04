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

struct SaroniteAccountBalanceView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> AccountBalanceMiniAppViewController {
        AccountBalanceMiniAppViewController()
    }

    func updateUIViewController(_ uiViewController: AccountBalanceMiniAppViewController, context: Context) {}
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
struct SaronitePartnerMiniAppView: UIViewControllerRepresentable {
    let app: PartnerMiniAppDto

    func makeUIViewController(context: Context) -> UIViewController {
        do {
            return try PartnerSaroniteMiniAppViewController(app: app)
        } catch {
            return SaronitePartnerLoadFailureViewController(message: error.localizedDescription)
        }
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

private final class SaronitePartnerLoadFailureViewController: UIViewController {
    private let message: String

    init(message: String) {
        self.message = message
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground
        let label = UILabel()
        label.text = message
        label.numberOfLines = 0
        label.textAlignment = .center
        label.textColor = .label
        label.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(label)
        NSLayoutConstraint.activate([
            label.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 24),
            label.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -24),
            label.centerYAnchor.constraint(equalTo: view.centerYAnchor)
        ])
    }
}
