import UIKit
import CoreGraphics

enum ChaoscopeEngine {
    // Renders one attractor frame and returns a UIImage.
    // Call from a background thread — this is CPU-heavy.
    // Returns nil if the orbit diverged (blank render).
    static func render(params: inout ChaoscopeRenderParams) -> UIImage? {
        guard let pixelBuf = chaoscope_render(&params) else { return nil }

        let w = Int(params.width)
        let h = Int(params.height)
        let byteCount = w * h * 4

        // Copy into CF-owned data so the CGImage lifetime is independent of pixelBuf.
        guard let cfData = CFDataCreate(
            nil,
            UnsafeRawPointer(pixelBuf).assumingMemoryBound(to: UInt8.self),
            byteCount
        ) else {
            chaoscope_free(pixelBuf)
            return nil
        }
        chaoscope_free(pixelBuf)

        // The renderer emits 0xFFRRGGBB (ARGB_8888) packed as little-endian ints.
        // byteOrder32Little + noneSkipFirst interprets memory as [B][G][R][skip],
        // which matches that layout on ARM.
        let bitmapInfo = CGBitmapInfo(rawValue:
            CGImageAlphaInfo.noneSkipFirst.rawValue |
            CGBitmapInfo.byteOrder32Little.rawValue)

        guard let provider = CGDataProvider(data: cfData),
              let cgImage = CGImage(
                width: w, height: h,
                bitsPerComponent: 8,
                bitsPerPixel: 32,
                bytesPerRow: w * 4,
                space: CGColorSpaceCreateDeviceRGB(),
                bitmapInfo: bitmapInfo,
                provider: provider,
                decode: nil,
                shouldInterpolate: false,
                intent: .defaultIntent)
        else { return nil }

        return UIImage(cgImage: cgImage)
    }
}
