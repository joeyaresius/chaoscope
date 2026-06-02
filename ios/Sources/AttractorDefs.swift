import SwiftUI

// ── Attractor definitions ─────────────────────────────────────────────────────
// Ordinals must stay in lockstep with attractors.h constants.

struct AttractorDef {
    let name: String
    let params: [Float]   // up to 8; pad with 0s to match C array
}

let attractors: [AttractorDef] = [
    // 0 CLIFFORD
    AttractorDef(name: "Clifford",      params: [-1.4, 1.6, 1.0, 0.7, 1.5, 0.5, 0, 0]),
    // 1 PETER_DE_JONG
    AttractorDef(name: "Peter de Jong", params: [-2.0, -2.0, -1.2, 2.0, 1.8, -1.5, 0, 0]),
    // 2 GUMOWSKI_MIRA
    AttractorDef(name: "Gumowski-Mira", params: [0.008, -0.496, 0, 0, 0, 0, 0, 0]),
    // 3 LORENZ
    AttractorDef(name: "Lorenz",        params: [10, 28, 2.667, 0.005, 0, 0, 0, 0]),
    // 4 ROSSLER
    AttractorDef(name: "Rössler",       params: [0.2, 0.2, 5.7, 0.02, 0, 0, 0, 0]),
    // 5 AIZAWA
    AttractorDef(name: "Aizawa",        params: [0.95, 0.7, 0.6, 3.5, 0.25, 0.1, 0.01, 0]),
    // 6 THOMAS
    AttractorDef(name: "Thomas",        params: [0.208186, 0.05, 0, 0, 0, 0, 0, 0]),
    // 7 CHAOTIC_FLOW (Dadras)
    AttractorDef(name: "Chaotic Flow",  params: [3, 2.7, 1.7, 2, 9, 0.01, 0, 0]),
    // 8 ICON (Symmetry Icons)
    AttractorDef(name: "Icon",          params: [-2.5, 5.0, -1.8, 1.0, 0, 0, 0, 0]),
    // 9 IFS (Barnsley Fern)
    AttractorDef(name: "Barnsley Fern", params: [1.0, 0.0, 0.2, 0, 0, 0, 0, 0]),
    // 10 JULIA (quaternion)
    AttractorDef(name: "Julia",         params: [-0.2, 0.6, 0.3, 0, 0, 0, 0, 0]),
    // 11 PICKOVER
    AttractorDef(name: "Pickover",      params: [2.24, 0.43, -0.65, -2.43, 0, 0, 0, 0]),
    // 12 HALVORSEN
    AttractorDef(name: "Halvorsen",     params: [1.89, 0.005, 0, 0, 0, 0, 0, 0]),
    // 13 BURKE_SHAW
    AttractorDef(name: "Burke-Shaw",    params: [10, 4.272, 0.005, 0, 0, 0, 0, 0]),
    // 14 SPROTT_B
    AttractorDef(name: "Sprott-B",      params: [1, 1, 0.02, 0, 0, 0, 0, 0]),
]

// ── Palette definitions ───────────────────────────────────────────────────────
// Colors mirror renderer.cpp color stops exactly; used for gradient swatches.
// Ordinals must match renderer.h PALETTE_* constants.

struct PaletteDef {
    let name: String
    let stops: [Color]   // key colors left→right for gradient chip
}

let palettes: [PaletteDef] = [
    // 0 NEBULA
    PaletteDef(name: "Nebula",    stops: [.black, Color(r:0,g:20,b:80), Color(r:20,g:80,b:200), Color(r:120,g:180,b:255), .white]),
    // 1 FIRE
    PaletteDef(name: "Fire",      stops: [.black, Color(r:80,g:0,b:0), Color(r:200,g:60,b:0), Color(r:255,g:160,b:20), Color(r:255,g:255,b:200)]),
    // 2 ELECTRIC
    PaletteDef(name: "Electric",  stops: [.black, Color(r:0,g:40,b:60), Color(r:0,g:180,b:200), Color(r:80,g:240,b:255), .white]),
    // 3 AURORA
    PaletteDef(name: "Aurora",    stops: [.black, Color(r:20,g:0,b:60), Color(r:80,g:0,b:160), Color(r:180,g:80,b:240), Color(r:240,g:200,b:255)]),
    // 4 MATRIX
    PaletteDef(name: "Matrix",    stops: [.black, Color(r:0,g:40,b:0), Color(r:0,g:160,b:20), Color(r:80,g:240,b:80), Color(r:200,g:255,b:200)]),
    // 5 GREYSCALE
    PaletteDef(name: "Greyscale", stops: [.black, .white]),
    // 6 SPECTRUM
    PaletteDef(name: "Spectrum",  stops: [.black, Color(r:80,g:0,b:160), Color(r:0,g:40,b:220), Color(r:0,g:200,b:200), Color(r:220,g:220,b:0), Color(r:255,g:130,b:0)]),
    // 7 SUNSET
    PaletteDef(name: "Sunset",    stops: [.black, Color(r:60,g:0,b:80), Color(r:180,g:0,b:120), Color(r:255,g:80,b:20), Color(r:255,g:200,b:50)]),
    // 8 ICE
    PaletteDef(name: "Ice",       stops: [.black, Color(r:0,g:20,b:60), Color(r:0,g:80,b:140), Color(r:80,g:200,b:240), Color(r:200,g:240,b:255), .white]),
    // 9 NEON
    PaletteDef(name: "Neon",      stops: [.black, Color(r:60,g:0,b:60), Color(r:255,g:0,b:180), Color(r:0,g:255,b:120), Color(r:200,g:255,b:80), .white]),
]

// ── Render styles ─────────────────────────────────────────────────────────────
// Ordinals match renderer.h renderStyle values.
let renderStyles = ["Standard", "Gas", "Liquid", "Plasma", "Solid", "Light"]

// ── Color helper ──────────────────────────────────────────────────────────────

extension Color {
    /// Convenience initialiser from 0–255 integer channels.
    init(r: Int, g: Int, b: Int) {
        self.init(red: Double(r)/255, green: Double(g)/255, blue: Double(b)/255)
    }
}
