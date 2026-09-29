import SwiftUI
import AVFoundation

// Real fix (2026-08-30, no-manual-code-UX sweep) -- AgentApp is a separate Tuist
// target (ItundaAgentApp, sources: AgentApp/Sources/** only, no dependency on
// ItundaApp) so it can't reuse App/Sources/QrScanCamera.swift's QrScanCameraView
// directly. Same duplication precedent as Android's own merchantapp CameraQrScanner.kt
// and PayQrCodeUtil.kt ("separate Gradle applications with no shared ui utils
// module") -- a scanning-only copy (this target never generates its own QR, only
// scans a customer's), not the full file including QR/barcode generation.

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
        foundLabel.isHidden = false
        UIAccessibility.post(notification: .announcement, argument: "QR code found")
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { [onDetect] in
            onDetect?(value)
        }
    }
}
