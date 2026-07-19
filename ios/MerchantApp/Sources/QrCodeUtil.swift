import CoreImage.CIFilterBuiltins
import SwiftUI

/// Real QR code rendering for the register/POS checkout flow -- Core Image's built-in
/// generator, no third-party dependency needed on iOS. A customer's own itunda app
/// scans this and resolves it into a real POST /api/v1/merchant/collect/{intentId}
/// call, the same payload convention merchant-mfe's web POS screen and Android's own
/// merchantapp already established.
func generateQrImage(content: String, size: CGFloat = 240) -> Image? {
    let filter = CIFilter.qrCodeGenerator()
    filter.message = Data(content.utf8)
    guard let outputImage = filter.outputImage else { return nil }
    let scale = size / outputImage.extent.width
    let scaled = outputImage.transformed(by: CGAffineTransform(scaleX: scale, y: scale))
    let context = CIContext()
    guard let cgImage = context.createCGImage(scaled, from: scaled.extent) else { return nil }
    return Image(decorative: cgImage, scale: 1.0)
}
