import UIKit
import CoreGraphics

// ── DotCloud ──────────────────────────────────────────────────────────────────

/// Pre-bucketed depth-shaded dot cloud, ready for direct Canvas drawing.
struct DotCloud {
    struct Bucket {
        let color: UIColor
        let pts: [Float]   // interleaved u,v pairs for every dot in this depth bucket
    }
    let buckets: [Bucket]
    var isEmpty: Bool { buckets.allSatisfy { $0.pts.isEmpty } }
}

// ── Engine ────────────────────────────────────────────────────────────────────

enum ChaoscopeEngine {

    // MARK: – Full render

    /// CPU histogram render → UIImage.  Call from a background thread.
    /// Returns nil if the orbit diverged (blank render).
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

        // Renderer emits 0xFFRRGGBB packed int32 (ARGB_8888).
        // byteOrder32Little + noneSkipFirst reads memory as [B][G][R][skip] on ARM —
        // which matches that layout exactly.
        let bitmapInfo = CGBitmapInfo(rawValue:
            CGImageAlphaInfo.noneSkipFirst.rawValue |
            CGBitmapInfo.byteOrder32Little.rawValue)

        guard let provider = CGDataProvider(data: cfData),
              let cgImage  = CGImage(
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

    // MARK: – Dot preview

    /// Fast live dot preview (no histogram, ~5 ms). Call from a background thread.
    /// Returns nil if the orbit diverged.
    static func dotCloud(
        params: inout ChaoscopeRenderParams,
        nPts: Int = 40_000,
        bucketCount: Int = 24
    ) -> DotCloud? {
        // Get raw (u,v,depth) triples
        guard let rawBuf = chaoscope_get_points_depth(&params, Int32(nPts)) else { return nil }
        defer { chaoscope_free_float(rawBuf) }

        // Get palette LUT
        guard let lutBuf = chaoscope_palette_lut(
            params.paletteIndex, Int32(bucketCount), nil, 0
        ) else { return nil }
        defer { chaoscope_free(lutBuf) }

        // Convert LUT int32 (ARGB_8888) → UIColor
        var colors = [UIColor]()
        colors.reserveCapacity(bucketCount)
        for i in 0..<bucketCount {
            let argb = UInt32(bitPattern: lutBuf[i])
            let r = CGFloat((argb >> 16) & 0xFF) / 255
            let g = CGFloat((argb >> 8)  & 0xFF) / 255
            let b = CGFloat( argb        & 0xFF) / 255
            colors.append(UIColor(red: r, green: g, blue: b, alpha: 1))
        }

        // Bucket (u,v) pairs by depth-mapped LUT index
        var bucketedPts = Array(repeating: [Float](), count: bucketCount)
        for i in 0..<nPts {
            let base  = i * 3
            let u     = rawBuf[base]
            let v     = rawBuf[base + 1]
            let d     = rawBuf[base + 2]
            let idx   = min(Int(d * Float(bucketCount)), bucketCount - 1)
            bucketedPts[idx].append(u)
            bucketedPts[idx].append(v)
        }

        let buckets = (0..<bucketCount).map { i in
            DotCloud.Bucket(color: colors[i], pts: bucketedPts[i])
        }
        return DotCloud(buckets: buckets)
    }
}
