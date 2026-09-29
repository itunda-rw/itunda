import CoreNFC

// Real NFC "collector reads a rider's tapped phone" (2026-08-27, direct user
// follow-up: "for simplification we need nfc") -- see TransitCollectScreenView.swift's
// own doc comment for the full account. iOS can only ever be the READER side: unlike
// Android's real HostApduService, third-party card emulation is Apple-restricted to
// Apple Pay/Wallet, so this app never needs (and can't build) an iOS equivalent of
// Android's TransitHceService -- an iPhone can read an Android rider's NFC tap, but
// can never itself be tapped by another phone. An iPhone rider always falls back to
// the same QR TransitCollectScreenView.swift already scans with the camera
// (QrScanCamera.swift).
//
// AID must match Android's apduservice.xml/TransitHceService.kt and this app's own
// Project.swift select-identifiers entry exactly: 0xF0 (self-assigned, no ISO
// registration) + ASCII "ITUNDA".
final class TransitNfcReader: NSObject, NFCTagReaderSessionDelegate {
    // Full raw SELECT AID APDU: CLA=00 INS=A4 (SELECT) P1=04 (select by name) P2=00,
    // Lc=7 (AID length), the 7 AID bytes, Le=00 -- byte-for-byte the same command
    // TransitNfcReader.kt (Android) sends.
    private static let selectApdu: [UInt8] = [0x00, 0xA4, 0x04, 0x00, 0x07, 0xF0, 0x49, 0x54, 0x55, 0x4E, 0x44, 0x41, 0x00]

    private var session: NFCTagReaderSession?
    var onCodeRead: ((String) -> Void)?
    var onUnavailable: (() -> Void)?

    func start() {
        guard NFCTagReaderSession.readingAvailable else {
            onUnavailable?()
            return
        }
        let newSession = NFCTagReaderSession(pollingOption: [.iso14443], delegate: self, queue: nil)
        newSession?.alertMessage = "Hold your phone near the rider's phone"
        newSession?.begin()
        session = newSession
    }

    func stop() {
        session?.invalidate()
        session = nil
    }

    func tagReaderSessionDidBecomeActive(_ session: NFCTagReaderSession) {}

    func tagReaderSession(_ session: NFCTagReaderSession, didInvalidateWithError error: Error) {
        self.session = nil
    }

    func tagReaderSession(_ session: NFCTagReaderSession, didDetect tags: [NFCTag]) {
        guard let tag = tags.first, case .iso7816 = tag, let apdu = NFCISO7816APDU(data: Data(Self.selectApdu)) else {
            session.restartPolling()
            return
        }
        session.connect(to: tag) { [weak self] error in
            guard let self, error == nil, case let .iso7816(iso7816Tag) = tag else {
                session.restartPolling()
                return
            }
            iso7816Tag.sendCommand(apdu: apdu) { responseData, sw1, sw2, error in
                guard error == nil, sw1 == 0x90, sw2 == 0x00, let code = String(data: responseData, encoding: .utf8) else {
                    // Not itunda's own service, or nothing currently presented (the
                    // rider hasn't opened "My payment code" yet) -- same "found
                    // nothing yet, keep trying" non-error the camera scanner has.
                    session.restartPolling()
                    return
                }
                session.alertMessage = "Code read"
                session.invalidate()
                DispatchQueue.main.async { self.onCodeRead?(code) }
            }
        }
    }
}
