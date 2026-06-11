import SwiftUI
import Photos

@MainActor
final class RenderViewModel: ObservableObject {

    // MARK: – Published state

    @Published var renderedImage: UIImage?
    @Published var dotCloud:      DotCloud?
    @Published var isRendering    = false
    @Published var saveToast:     String?    // non-nil briefly after a save

    // Shape tab
    @Published var attractorIndex: Int = 3 {   // Lorenz default
        didSet { guard oldValue != attractorIndex else { return }
                 renderedImage = nil; schedulePreviewRefresh() }
    }

    // Look tab
    @Published var paletteIndex: Int = 0 {
        didSet { guard oldValue != paletteIndex else { return }
                 schedulePreviewRefresh() }
    }
    @Published var renderStyle: Int = 0

    // Camera tab
    @Published var yaw:   Double = 20  { didSet { schedulePreviewRefresh() } }
    @Published var pitch: Double = -20 { didSet { schedulePreviewRefresh() } }
    @Published var roll:  Double = 0   { didSet { schedulePreviewRefresh() } }
    @Published var zoom:  Double = 1.0 { didSet { schedulePreviewRefresh() } }

    // Export tab
    @Published var iterationsExp: Double = 6.7   // 10^6.7 ≈ 5 M

    var iterations: Int64 { Int64(pow(10.0, iterationsExp)) }

    // MARK: – Private

    private var previewTask: Task<Void, Never>?

    init() { schedulePreviewRefresh() }

    // MARK: – Dot preview

    func schedulePreviewRefresh() {
        previewTask?.cancel()
        previewTask = Task { [weak self] in
            guard let self else { return }
            try? await Task.sleep(nanoseconds: 80_000_000)   // 80 ms debounce
            guard !Task.isCancelled else { return }
            await self.doRefreshPreview()
        }
    }

    private func doRefreshPreview() async {
        var rp = buildParams(width: 1, height: 1, iterations: 0)
        let cloud = await Task.detached(priority: .userInitiated) {
            ChaoscopeEngine.dotCloud(params: &rp)
        }.value
        if let cloud { dotCloud = cloud }
    }

    // MARK: – Render

    func render() {
        guard !isRendering else { return }
        isRendering = true
        var rp = buildParams(width: 1080, height: 1920, iterations: iterations)
        Task.detached(priority: .userInitiated) { [weak self] in
            let img = ChaoscopeEngine.render(params: &rp)
            await MainActor.run { [weak self] in
                self?.renderedImage = img
                self?.isRendering   = false
            }
        }
    }

    // MARK: – Save to Photos

    func saveToPhotos() {
        guard let image = renderedImage else { return }
        PHPhotoLibrary.requestAuthorization(for: .addOnly) { [weak self] status in
            guard status == .authorized || status == .limited else {
                Task { @MainActor [weak self] in
                    await self?.showToast("Permission denied")
                }
                return
            }
            PHPhotoLibrary.shared().performChanges({
                PHAssetChangeRequest.creationRequestForAsset(from: image)
            }) { [weak self] success, _ in
                Task { @MainActor [weak self] in
                    await self?.showToast(success ? "Saved to Photos" : "Save failed")
                }
            }
        }
    }

    // MARK: – Gestures

    /// Call from a DragGesture delta on the canvas.
    func rotateBy(dx: Float, dy: Float) {
        yaw   = (yaw + Double(dx) * 0.45).truncatingRemainder(dividingBy: 360)
        pitch = (pitch + Double(dy) * 0.45).clamped(to: -90...90)
    }

    /// Call from a MagnificationGesture delta on the canvas.
    func zoomBy(_ factor: Double) {
        zoom = (zoom * factor).clamped(to: 0.25...5.0)
    }

    // MARK: – Helpers

    func buildParams(width: Int32, height: Int32, iterations: Int64) -> ChaoscopeRenderParams {
        let def = attractors[attractorIndex]
        var rp  = ChaoscopeRenderParams()
        rp.attractorType  = Int32(attractorIndex)
        rp.width          = width
        rp.height         = height
        rp.iterations     = iterations
        rp.yaw            = Float(yaw)
        rp.pitch          = Float(pitch)
        rp.roll           = Float(roll)
        rp.zoom           = Float(zoom)
        rp.paletteIndex   = Int32(paletteIndex)
        rp.gamma          = 1.0
        rp.renderStyle    = Int32(renderStyle)
        rp.bgColor        = Int32(bitPattern: 0xFF000000)
        withUnsafeMutableBytes(of: &rp.params) { rawPtr in
            let floatPtr = rawPtr.bindMemory(to: Float.self)
            for i in 0..<min(def.params.count, 8) { floatPtr[i] = def.params[i] }
        }
        return rp
    }

    private func showToast(_ msg: String) async {
        saveToast = msg
        try? await Task.sleep(nanoseconds: 2_500_000_000)
        saveToast = nil
    }
}

// MARK: – Utility

extension Comparable {
    func clamped(to range: ClosedRange<Self>) -> Self {
        min(max(self, range.lowerBound), range.upperBound)
    }
}
