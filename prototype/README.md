# Chaoscope Clone — Prototype (Phases 1–4)

Motor matemático + pipeline de renderização por histograma + sistema de cores, implementados em Python/NumPy como prova de conceito antes da portagem para C/C++.

---

## Estrutura

```
prototype/
├── attractors.py   ← Fase 1 — Motor matemático (7 atratores 2-D / 3-D)
├── renderer.py     ← Fase 2 — Histograma de densidade + tone-mapping logarítmico
├── colormap.py     ← Fase 4 — Sistema de gradientes / paletas de cores
├── render.py       ← Runner principal (CLI)
└── requirements.txt
```

---

## Instalação

```bash
cd prototype
pip install -r requirements.txt
```

---

## Uso rápido

```bash
# Preview: Clifford, 2 M iterações, 1024×1024, paleta nebula
python render.py --show

# Lorenz 3-D com rotação de câmera, salvo em PNG
python render.py -a lorenz -n 5000000 -s 1024 -p fire --yaw 30 --pitch 20 -o out/lorenz.png --show

# Listar todos os atratores e paletas disponíveis
python render.py --list
```

---

## Atratores disponíveis

| Chave          | Nome              | Dimensão |
|----------------|-------------------|----------|
| `clifford`     | Clifford          | 2-D      |
| `peterdejong`  | Peter de Jong     | 2-D      |
| `gumowskimira` | Gumowski-Mira     | 2-D      |
| `lorenz`       | Lorenz            | 3-D      |
| `rossler`      | Rössler           | 3-D      |
| `aizawa`       | Aizawa            | 3-D      |
| `thomas`       | Thomas            | 3-D      |

---

## Paletas disponíveis

`nebula` · `fire` · `electric` · `aurora` · `matrix` · `greyscale` · `greyscale_inv`

---

## Parâmetros do CLI

| Flag            | Padrão      | Descrição                                      |
|-----------------|-------------|------------------------------------------------|
| `--attractor`   | `clifford`  | Nome do atrator                                |
| `--iters`       | `2000000`   | Número de iterações                            |
| `--size`        | `1024`      | Tamanho do canvas em pixels (quadrado)         |
| `--palette`     | `nebula`    | Paleta de cores                                |
| `--gamma`       | `1.0`       | Correção de gamma aplicada à densidade         |
| `--tone`        | `log`       | Tone-mapping: `log` ou `linear`                |
| `--yaw/pitch/roll` | `0`      | Rotação de câmera para atratores 3-D (graus)   |
| `--zoom`        | `1.0`       | Zoom da câmera                                 |
| `--output`      | —           | Caminho de saída do PNG                        |
| `--show`        | —           | Exibir com matplotlib                          |
| `--batch-size`  | `10000`     | Tamanho do lote para iteração vectorizada      |

---

## Arquitetura do pipeline

```
Atrator (fórmula) → iterate_attractor_batch()
        ↓ xs, ys, zs  (N pontos)
     Camera.project()
        ↓ u, v  (coordenadas de tela)
  HistogramCanvas.accumulate()
        ↓ counts[H, W]  (inteiros)
   log_density_map()          ← escala logarítmica (estilo Chaoscope)
        ↓ density[H, W]  ∈ [0, 1]
      Palette.apply()
        ↓ rgba[H, W, 4]  (uint8)
    PIL.Image / matplotlib
```

---

## Próximos passos (Fases 3 e 5)

- **Fase 3 — Mobile**: Portar o motor para C/C++ via NDK; UI em Jetpack Compose (Android) ou SwiftUI (iOS).
- **Fase 5 — Otimização**: Preview a 1 M iterações enquanto slider é arrastado; botão "Renderizar Alta Qualidade" a 500 M iterações off-screen.
