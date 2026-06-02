import SwiftUI

// ── Root view ─────────────────────────────────────────────────────────────────

struct ContentView: View {
    @StateObject private var vm = RenderViewModel()

    // Gesture delta tracking (not cumulative)
    @State private var lastDrag:  CGSize = .zero
    @State private var lastScale: CGFloat = 1

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            canvasLayer
            overlayLayer
        }
        .ignoresSafeArea()
        .sheet(isPresented: .constant(true)) {
            ControlSheet(vm: vm)
                // Two snap points matching the Android panel stops
                .presentationDetents([.fraction(0.28), .fraction(0.58)])
                .presentationDragIndicator(.visible)
                .presentationBackground(.ultraThinMaterial)
                // Canvas remains interactive when sheet is at the smaller detent
                .presentationBackgroundInteraction(.enabled(upThrough: .fraction(0.28)))
                .interactiveDismissDisabled()
        }
    }

    // MARK: – Canvas (full-screen, behind sheet)

    private var canvasLayer: some View {
        ZStack {
            if let img = vm.renderedImage {
                Image(uiImage: img)
                    .resizable()
                    .scaledToFit()
            } else if let cloud = vm.dotCloud {
                DotCanvas(cloud: cloud)
            } else {
                emptyState
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .contentShape(Rectangle())
        // Drag → rotate
        .gesture(
            DragGesture(minimumDistance: 4)
                .onChanged { val in
                    let dx = Float(val.translation.width  - lastDrag.width)
                    let dy = Float(val.translation.height - lastDrag.height)
                    lastDrag = val.translation
                    vm.rotateBy(dx: dx, dy: dy)
                }
                .onEnded { _ in lastDrag = .zero }
        )
        // Pinch → zoom
        .simultaneousGesture(
            MagnificationGesture()
                .onChanged { scale in
                    vm.zoomBy(Double(scale / lastScale))
                    lastScale = scale
                }
                .onEnded { _ in lastScale = 1 }
        )
    }

    private var emptyState: some View {
        VStack(spacing: 12) {
            Image(systemName: "waveform.path.ecg")
                .font(.system(size: 52))
                .foregroundColor(.white.opacity(0.15))
            Text("Drag to rotate · pinch to zoom")
                .font(.caption)
                .foregroundColor(.white.opacity(0.25))
        }
    }

    // MARK: – HUD overlay (attractor badge + toast)

    private var overlayLayer: some View {
        VStack {
            HStack {
                // Current attractor + palette badge
                VStack(alignment: .leading, spacing: 2) {
                    Text(attractors[vm.attractorIndex].name)
                        .font(.subheadline.weight(.semibold))
                        .foregroundColor(.white)
                    Text(palettes[vm.paletteIndex].name)
                        .font(.caption2)
                        .foregroundColor(.white.opacity(0.6))
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 10))
                .padding(.leading, 16)
                .padding(.top, 56)   // below status bar

                Spacer()

                // Rendering spinner
                if vm.isRendering {
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle(tint: .white))
                        .scaleEffect(0.85)
                        .padding(.trailing, 20)
                        .padding(.top, 60)
                }
            }
            Spacer()

            // Toast
            if let toast = vm.saveToast {
                Text(toast)
                    .font(.subheadline)
                    .foregroundColor(.white)
                    .padding(.horizontal, 18)
                    .padding(.vertical, 10)
                    .background(.ultraThinMaterial, in: Capsule())
                    .transition(.opacity.combined(with: .move(edge: .bottom)))
                    .padding(.bottom, 340)
            }
        }
        .animation(.easeInOut(duration: 0.25), value: vm.saveToast)
    }
}

// ── Bottom sheet ──────────────────────────────────────────────────────────────

struct ControlSheet: View {
    @ObservedObject var vm: RenderViewModel
    @State private var selectedTab = 0

    private let tabs = [
        ("sparkles",       "Shape"),
        ("paintpalette",   "Look"),
        ("camera.rotate",  "Camera"),
        ("square.and.arrow.up", "Export"),
    ]

    var body: some View {
        VStack(spacing: 0) {
            tabBar
            Divider()
            tabContent
        }
        .background(Color.clear)
    }

    // MARK: – Tab bar with central FAB

    private var tabBar: some View {
        ZStack {
            HStack(spacing: 0) {
                ForEach(0..<4, id: \.self) { i in
                    // Gap in the middle for the FAB
                    if i == 2 { Spacer().frame(width: 64) }
                    tabButton(index: i)
                }
            }
            .frame(height: 52)

            // Central play FAB
            Button(action: vm.render) {
                ZStack {
                    Circle()
                        .fill(Color.accentColor)
                        .frame(width: 52, height: 52)
                        .shadow(color: .accentColor.opacity(0.4), radius: 8, y: 3)
                    if vm.isRendering {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: .white))
                            .scaleEffect(0.8)
                    } else {
                        Image(systemName: "play.fill")
                            .font(.system(size: 20, weight: .semibold))
                            .foregroundColor(.white)
                    }
                }
            }
            .disabled(vm.isRendering)
        }
        .padding(.horizontal, 8)
    }

    private func tabButton(index: Int) -> some View {
        let (icon, label) = tabs[index]
        let isSelected = selectedTab == index
        return Button {
            withAnimation(.easeInOut(duration: 0.15)) { selectedTab = index }
        } label: {
            VStack(spacing: 3) {
                Image(systemName: icon)
                    .font(.system(size: 16, weight: isSelected ? .semibold : .regular))
                Text(label)
                    .font(.system(size: 10, weight: isSelected ? .semibold : .regular))
            }
            .foregroundColor(isSelected ? .accentColor : .secondary)
            .frame(maxWidth: .infinity)
            .frame(height: 52)
        }
    }

    // MARK: – Tab content

    @ViewBuilder
    private var tabContent: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                switch selectedTab {
                case 0: shapeTab
                case 1: lookTab
                case 2: cameraTab
                default: exportTab
                }
                Spacer(minLength: 40)
            }
            .padding(.horizontal, 16)
            .padding(.top, 16)
        }
    }

    // MARK: – Shape tab

    private var shapeTab: some View {
        VStack(alignment: .leading, spacing: 16) {
            SectionLabel("Attractor")
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(attractors.indices, id: \.self) { i in
                        ChipButton(
                            label:    attractors[i].name,
                            selected: vm.attractorIndex == i
                        ) { vm.attractorIndex = i }
                    }
                }
                .padding(.horizontal, 2)
            }
        }
    }

    // MARK: – Look tab

    private var lookTab: some View {
        VStack(alignment: .leading, spacing: 16) {
            SectionLabel("Palette")
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    ForEach(palettes.indices, id: \.self) { i in
                        PaletteChip(
                            palette:  palettes[i],
                            selected: vm.paletteIndex == i
                        ) { vm.paletteIndex = i }
                    }
                }
                .padding(.horizontal, 2)
            }

            SectionLabel("Render Style")
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(renderStyles.indices, id: \.self) { i in
                        ChipButton(
                            label:    renderStyles[i],
                            selected: vm.renderStyle == i
                        ) { vm.renderStyle = i }
                    }
                }
                .padding(.horizontal, 2)
            }
        }
    }

    // MARK: – Camera tab

    private var cameraTab: some View {
        VStack(alignment: .leading, spacing: 4) {
            SectionLabel("Camera")
                .padding(.bottom, 4)
            CameraSlider(label: "Yaw",   value: $vm.yaw,   range: -180...180,  format: "%.0f°")
            CameraSlider(label: "Pitch", value: $vm.pitch, range: -90...90,     format: "%.0f°")
            CameraSlider(label: "Roll",  value: $vm.roll,  range: -180...180,   format: "%.0f°")
            CameraSlider(label: "Zoom",  value: $vm.zoom,  range: 0.25...5.0,   format: "%.2f×")
        }
    }

    // MARK: – Export tab

    private var exportTab: some View {
        VStack(alignment: .leading, spacing: 16) {
            SectionLabel("Quality")
            VStack(spacing: 2) {
                HStack {
                    Text("Iterations")
                        .font(.caption)
                        .foregroundColor(.secondary)
                    Spacer()
                    Text(formatIterations(vm.iterations))
                        .font(.caption.monospacedDigit())
                        .foregroundColor(.secondary)
                }
                Slider(value: $vm.iterationsExp, in: 5...7.7, step: 0.05)
            }

            Divider()
            SectionLabel("Actions")

            // Save button — only enabled when an image is available
            Button {
                vm.saveToPhotos()
            } label: {
                Label("Save to Photos", systemImage: "square.and.arrow.down")
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(vm.renderedImage != nil ? Color.accentColor : Color(.systemFill))
                    .foregroundColor(vm.renderedImage != nil ? .white : .secondary)
                    .cornerRadius(10)
            }
            .disabled(vm.renderedImage == nil)
        }
    }
}

// ── Reusable components ───────────────────────────────────────────────────────

private struct SectionLabel: View {
    let text: String
    init(_ text: String) { self.text = text }
    var body: some View {
        Text(text)
            .font(.caption.weight(.semibold))
            .foregroundColor(.secondary)
            .textCase(.uppercase)
            .tracking(0.5)
    }
}

private struct ChipButton: View {
    let label: String
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(label)
                .font(.caption.weight(selected ? .semibold : .regular))
                .padding(.horizontal, 12)
                .padding(.vertical, 7)
                .background(selected ? Color.accentColor : Color(.systemFill))
                .foregroundColor(selected ? .white : .primary)
                .cornerRadius(20)
        }
        .buttonStyle(.plain)
    }
}

/// Palette chip showing the actual gradient swatch + name.
private struct PaletteChip: View {
    let palette: PaletteDef
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 5) {
                LinearGradient(colors: palette.stops,
                               startPoint: .leading, endPoint: .trailing)
                    .frame(width: 64, height: 28)
                    .cornerRadius(6)
                    .overlay(
                        RoundedRectangle(cornerRadius: 6)
                            .stroke(selected ? Color.accentColor : Color.clear, lineWidth: 2)
                    )
                Text(palette.name)
                    .font(.system(size: 9, weight: selected ? .semibold : .regular))
                    .foregroundColor(selected ? .accentColor : .secondary)
            }
        }
        .buttonStyle(.plain)
    }
}

private struct CameraSlider: View {
    let label: String
    @Binding var value: Double
    let range: ClosedRange<Double>
    let format: String

    var body: some View {
        HStack(spacing: 8) {
            Text(label)
                .font(.caption)
                .foregroundColor(.secondary)
                .frame(width: 36, alignment: .leading)
            Slider(value: $value, in: range)
            Text(String(format: format, value))
                .font(.caption.monospacedDigit())
                .foregroundColor(.secondary)
                .frame(width: 52, alignment: .trailing)
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private func formatIterations(_ n: Int64) -> String {
    if n >= 1_000_000 { return String(format: "%.1fM", Double(n) / 1_000_000) }
    if n >= 1_000     { return "\(n / 1_000)K" }
    return "\(n)"
}

#Preview {
    ContentView()
}
