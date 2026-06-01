import SwiftUI

struct ContentView: View {
    @State private var renderedImage: UIImage?
    @State private var isRendering = false
    @State private var attractorIndex = 3        // Lorenz default
    @State private var paletteIndex = 0
    @State private var iterationsExp: Double = 6.7   // 10^6.7 ≈ 5 M
    @State private var zoom: Double = 1.0
    @State private var yaw: Double = 20
    @State private var pitch: Double = -20
    @State private var renderStyle = 0

    private static let renderStyles = ["Standard", "Gas", "Liquid", "Plasma", "Solid", "Light"]

    private var iterations: Int64 { Int64(pow(10, iterationsExp)) }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    renderPreview
                    controlPanel
                    renderButton
                }
                .padding(.bottom, 32)
            }
            .navigationTitle("Chaoscope")
            .navigationBarTitleDisplayMode(.inline)
            .background(Color(.systemGroupedBackground))
        }
    }

    // MARK: – Render preview

    private var renderPreview: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 12)
                .fill(Color.black)
                .aspectRatio(9 / 16, contentMode: .fit)

            if let img = renderedImage {
                Image(uiImage: img)
                    .resizable()
                    .scaledToFit()
                    .cornerRadius(12)
            } else if !isRendering {
                VStack(spacing: 8) {
                    Image(systemName: "waveform.path.ecg")
                        .font(.system(size: 44))
                        .foregroundColor(.gray)
                    Text("Tap Render")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                }
            }

            if isRendering {
                ProgressView()
                    .progressViewStyle(CircularProgressViewStyle(tint: .white))
                    .scaleEffect(1.5)
            }
        }
        .padding(.horizontal)
    }

    // MARK: – Controls

    private var controlPanel: some View {
        VStack(spacing: 0) {
            GroupBox("Attractor") {
                Picker("Attractor", selection: $attractorIndex) {
                    ForEach(attractors.indices, id: \.self) { i in
                        Text(attractors[i].name).tag(i)
                    }
                }
                .pickerStyle(.menu)
                .frame(maxWidth: .infinity, alignment: .leading)
            }

            GroupBox("Palette") {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(paletteNames.indices, id: \.self) { i in
                            Text(paletteNames[i])
                                .font(.caption)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 5)
                                .background(paletteIndex == i ? Color.blue : Color(.systemFill))
                                .foregroundColor(paletteIndex == i ? .white : .primary)
                                .cornerRadius(8)
                                .onTapGesture { paletteIndex = i }
                        }
                    }
                }
            }

            GroupBox("Render Style") {
                Picker("Style", selection: $renderStyle) {
                    ForEach(ContentView.renderStyles.indices, id: \.self) { i in
                        Text(ContentView.renderStyles[i]).tag(i)
                    }
                }
                .pickerStyle(.segmented)
            }

            GroupBox("Camera") {
                SliderRow(label: "Yaw",   value: $yaw,   range: -180...180, unit: "°", step: 1)
                SliderRow(label: "Pitch", value: $pitch, range:  -90...90,  unit: "°", step: 1)
                SliderRow(label: "Zoom",  value: $zoom,  range:   0.3...4,  unit: "×", step: 0.05)
            }

            GroupBox("Quality") {
                SliderRow(
                    label: "Iterations",
                    value: $iterationsExp,
                    range: 5...7.7,
                    unit: "",
                    step: 0.05,
                    display: { "\(formatIterations(Int64(pow(10, $0))))" }
                )
            }
        }
        .padding(.horizontal)
    }

    // MARK: – Render button

    private var renderButton: some View {
        Button(action: startRender) {
            Label(isRendering ? "Rendering…" : "Render",
                  systemImage: isRendering ? "hourglass" : "play.fill")
                .frame(maxWidth: .infinity)
                .padding()
                .background(isRendering ? Color.gray : Color.blue)
                .foregroundColor(.white)
                .cornerRadius(12)
        }
        .disabled(isRendering)
        .padding(.horizontal)
    }

    // MARK: – Render logic

    private func startRender() {
        isRendering = true
        let idx      = attractorIndex
        let def      = attractors[idx]
        let iters    = iterations
        let pal      = paletteIndex
        let style    = renderStyle
        let yawF     = Float(yaw)
        let pitchF   = Float(pitch)
        let zoomF    = Float(zoom)

        Task.detached(priority: .userInitiated) {
            var rp = ChaoscopeRenderParams()
            rp.attractorType = Int32(idx)
            rp.width   = 1080
            rp.height  = 1920
            rp.iterations = iters
            rp.yaw     = yawF
            rp.pitch   = pitchF
            rp.roll    = 0
            rp.zoom    = zoomF
            rp.paletteIndex = Int32(pal)
            rp.gamma   = 1.0
            rp.renderStyle = Int32(style)
            rp.bgColor = Int32(bitPattern: 0xFF000000)

            // Write default params into the C fixed-size array via unsafe bytes.
            withUnsafeMutableBytes(of: &rp.params) { rawPtr in
                let floatPtr = rawPtr.bindMemory(to: Float.self)
                for i in 0..<min(def.params.count, 8) {
                    floatPtr[i] = def.params[i]
                }
            }

            let img = ChaoscopeEngine.render(params: &rp)

            await MainActor.run {
                renderedImage = img
                isRendering   = false
            }
        }
    }
}

// MARK: – Helpers

private func formatIterations(_ n: Int64) -> String {
    if n >= 1_000_000 { return String(format: "%.1fM", Double(n) / 1_000_000) }
    if n >= 1_000     { return "\(n / 1_000)K" }
    return "\(n)"
}

struct SliderRow: View {
    let label: String
    @Binding var value: Double
    let range: ClosedRange<Double>
    let unit: String
    let step: Double
    var display: ((Double) -> String)?

    init(label: String, value: Binding<Double>, range: ClosedRange<Double>,
         unit: String, step: Double, display: ((Double) -> String)? = nil) {
        self.label = label
        self._value = value
        self.range = range
        self.unit = unit
        self.step = step
        self.display = display
    }

    var body: some View {
        HStack(spacing: 8) {
            Text(label)
                .font(.caption)
                .foregroundColor(.secondary)
                .frame(width: 64, alignment: .leading)
            Slider(value: $value, in: range, step: step)
            Text(display?(value) ?? String(format: "%.2g\(unit)", value))
                .font(.caption.monospacedDigit())
                .frame(width: 52, alignment: .trailing)
        }
    }
}

#Preview {
    ContentView()
}
