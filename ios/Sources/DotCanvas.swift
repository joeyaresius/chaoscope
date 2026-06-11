import SwiftUI

/// Full-screen canvas that draws a depth-shaded dot cloud.
/// Each bucket is drawn with `CGContextFillRects` — one C call per color
/// so 40K dots across 24 buckets = 24 fill calls, very fast.
struct DotCanvas: View {
    let cloud: DotCloud

    var body: some View {
        Canvas { ctx, size in
            let halfW   = size.width  / 2
            let halfH   = size.height / 2
            let dotSide = CGFloat(1.4)

            ctx.withCGContext { cgCtx in
                for bucket in cloud.buckets {
                    let pts = bucket.pts
                    guard pts.count >= 2 else { continue }

                    cgCtx.setFillColor(bucket.color.cgColor)

                    let n = pts.count / 2
                    var rects = [CGRect]()
                    rects.reserveCapacity(n)
                    var j = 0
                    while j + 1 < pts.count {
                        let x = halfW + CGFloat(pts[j])     * halfW
                        let y = halfH + CGFloat(pts[j + 1]) * halfH
                        rects.append(CGRect(x: x - dotSide / 2,
                                            y: y - dotSide / 2,
                                            width:  dotSide,
                                            height: dotSide))
                        j += 2
                    }
                    // Single C call for the whole bucket
                    rects.withUnsafeBufferPointer { buf in
                        CGContextFillRects(cgCtx, buf.baseAddress!, buf.count)
                    }
                }
            }
        }
        .ignoresSafeArea()
    }
}
