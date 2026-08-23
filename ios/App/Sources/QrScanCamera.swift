import SwiftUI
import AVFoundation
import CoreImage.CIFilterBuiltins

// Real camera QR scanning (Pay-parity port, §237) -- first camera capability
// anywhere in this app target (see the memory this closes: 9 days after Android's
// CameraX+ML Kit scanner and bank-mfe's web `QrScanCamera` shipped, iOS still had
// zero camera code, confirmed via a real grep sweep before starting). AVFoundation's
// native `AVCaptureMetadataOutput` decodes QR directly -- no third-party scanning
// library needed, matching itunda's own "don't depend on third-party if it's not
// open source" standing rule with a genuinely first-party framework instead.
// Mirrors bank-mfe's `QrScanCamera`/Android's scanner exactly in contract: a live
// preview that calls `onDetect` exactly once with the decoded string, then stops
// itself -- the caller re-mounts this view to scan again, same as both siblings.

/// Same real fallback contract bank-mfe's `parseQrParam` establishes: a scanned
/// `itunda://pay?intentId=...` URL yields just the `intentId` value; a raw opaque
/// code (this app's dynamic per-sale codes aren't URL-wrapped) passes through
/// trimmed, unchanged.
func parseQrParam(_ raw: String, key: String) -> String {
    if let range = raw.range(of: "[?&]\(key)=([^&]+)", options: .regularExpression) {
        let matched = String(raw[range])
        if let value = matched.split(separator: "=", maxSplits: 1).last {
            return String(value).removingPercentEncoding ?? String(value)
        }
    }
    return raw.trimmingCharacters(in: .whitespacesAndNewlines)
}

/// Real QR generation for the customer-presented payment code (`MyPaymentCodeCard`
/// in ShopPay.swift) -- Core Image's native `CIFilter.qrCodeGenerator`, no
/// third-party library, the generation-side counterpart to this file's own
/// scanning capability above.
func generateQrImage(from string: String, size: CGFloat) -> UIImage? {
    let filter = CIFilter.qrCodeGenerator()
    filter.message = Data(string.utf8)
    filter.correctionLevel = "M"
    guard let outputImage = filter.outputImage else { return nil }
    let scale = size / outputImage.extent.width
    let scaled = outputImage.transformed(by: CGAffineTransform(scaleX: scale, y: scale))
    let context = CIContext()
    guard let cgImage = context.createCGImage(scaled, from: scaled.extent) else { return nil }
    return UIImage(cgImage: cgImage)
}

/// Real barcode generation (product-feel correction, §241) -- see
/// `MyPaymentCodeCard`'s own doc comment for the sourced correction: KakaoPay's
/// own real screenshots show the primary payment code is a linear barcode
/// (Code128, Korea's real 바코드결제 standard), with a small QR secondary next to
/// it, not QR alone. Core Image's native `CIFilter.code128BarcodeGenerator` --
/// same first-party, no-third-party-library discipline as `generateQrImage`
/// above.
func generateBarcodeImage(from string: String, width: CGFloat, height: CGFloat) -> UIImage? {
    let filter = CIFilter.code128BarcodeGenerator()
    filter.message = Data(string.utf8)
    guard let outputImage = filter.outputImage else { return nil }
    let scaleX = width / outputImage.extent.width
    let scaleY = height / outputImage.extent.height
    let scaled = outputImage.transformed(by: CGAffineTransform(scaleX: scaleX, y: scaleY))
    let context = CIContext()
    guard let cgImage = context.createCGImage(scaled, from: scaled.extent) else { return nil }
    return UIImage(cgImage: cgImage)
}

struct QrScanCameraView: UIViewControllerRepresentable {
    let onDetect: (String) -> Void
    let onUnavailable: () -> Void

    func makeUIViewController(context: Context) -> QrScannerViewController {
        let controller = QrScannerViewController()
        controller.onDetect = onDetect
        controller.onUnavailable = onUnavailable
        return controller
    }

    func updateUIViewController(_ uiViewController: QrScannerViewController, context: Context) {}
}

final class QrScannerViewController: UIViewController, AVCaptureMetadataOutputObjectsDelegate {
    var onDetect: ((String) -> Void)?
    var onUnavailable: (() -> Void)?

    private let session = AVCaptureSession()
    private var previewLayer: AVCaptureVideoPreviewLayer?
    private var hasDetected = false
    // Real Toss-sourced accessibility fix (2026-08-23, toss.tech/article/accessibility_face) --
    // see bank-mfe's identical QrScanCamera/Android's identical CameraQrScanner.kt doc comments
    // for the full sourced account. This raw AVFoundation preview had zero
    // UIAccessibility.post calls anywhere -- a silent, purely-visual dead end for VoiceOver.
    private let foundLabel: UILabel = {
        let label = UILabel()
        label.text = "QR code found"
        label.textColor = .white
        label.font = .preferredFont(forTextStyle: .caption1)
        label.textAlignment = .center
        label.isHidden = true
        return label
    }()

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            configureSession()
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
                DispatchQueue.main.async {
                    if granted {
                        self?.configureSession()
                    } else {
                        self?.onUnavailable?()
                    }
                }
            }
        default:
            // .denied / .restricted -- same honest "no camera? enter code instead"
            // fallback bank-mfe's own scanUnavailable path already gives, not a
            // dead end.
            onUnavailable?()
        }
    }

    private func configureSession() {
        guard let device = AVCaptureDevice.default(for: .video),
              let input = try? AVCaptureDeviceInput(device: device),
              session.canAddInput(input) else {
            onUnavailable?()
            return
        }
        session.addInput(input)

        let output = AVCaptureMetadataOutput()
        guard session.canAddOutput(output) else {
            onUnavailable?()
            return
        }
        session.addOutput(output)
        output.setMetadataObjectsDelegate(self, queue: .main)
        output.metadataObjectTypes = [.qr]

        let preview = AVCaptureVideoPreviewLayer(session: session)
        preview.videoGravity = .resizeAspectFill
        preview.frame = view.bounds
        view.layer.addSublayer(preview)
        previewLayer = preview

        view.addSubview(foundLabel)

        let session = self.session
        DispatchQueue.global(qos: .userInitiated).async {
            session.startRunning()
        }
        UIAccessibility.post(notification: .announcement, argument: "Camera ready. Point at a QR code.")
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        previewLayer?.frame = view.bounds
        foundLabel.frame = CGRect(x: 0, y: view.bounds.height - 34, width: view.bounds.width, height: 20)
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        if session.isRunning {
            let session = self.session
            DispatchQueue.global(qos: .userInitiated).async {
                session.stopRunning()
            }
        }
    }

    func metadataOutput(_ output: AVCaptureMetadataOutput, didOutput metadataObjects: [AVMetadataObject], from connection: AVCaptureConnection) {
        guard !hasDetected,
              let object = metadataObjects.first as? AVMetadataMachineReadableCodeObject,
              object.type == .qr,
              let value = object.stringValue else { return }
        hasDetected = true
        let session = self.session
        DispatchQueue.global(qos: .userInitiated).async {
            session.stopRunning()
        }
        // Real Toss-sourced fix: a brief, announced "found" moment (matching the real
        // article's distinct completion cue) before handing off to onDetect -- 500ms is
        // enough for VoiceOver to pick up and start speaking the announcement before the
        // caller reacts (typically navigating away, tearing this view controller down).
        // Sighted users get the same visual confirmation via foundLabel instead of an
        // instant, jarring cut.
        foundLabel.isHidden = false
        UIAccessibility.post(notification: .announcement, argument: "QR code found")
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { [onDetect] in
            onDetect?(value)
        }
    }
}
