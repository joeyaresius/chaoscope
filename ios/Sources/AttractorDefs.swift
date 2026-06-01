// Mirrors the Kotlin AttractorType / PaletteType enums.
// Ordinals must stay in lockstep with attractors.h and renderer.h constants.

struct AttractorDef {
    let name: String
    let params: [Float]  // up to 8; padded with 0s to match C array
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

let paletteNames = [
    "Nebula", "Fire", "Electric", "Aurora", "Matrix",
    "Greyscale", "Spectrum", "Sunset", "Ice", "Neon",
]
