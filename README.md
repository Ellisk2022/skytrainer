# SkyTrainer 0.1.2

A **procedural flight simulator** in **Java** with **LWJGL 3 / OpenGL 3.2 core** —
a Cessna-style prop trainer, casual-sim flight physics (lift, drag, gravity, stall),
an infinite seeded world of mountains, forests, lakes and coastline, a full day/night
cycle, and Minecraft-style menus (title screen with live panorama + splash text,
stone buttons, draggable sliders). Every texture, sound and font is synthesized in
code — no external assets.

![gameplay](screenshots/game.png)

## What's new in 0.1.2

- **Altimeter reads 0 FT on the ground** — ALT is now field-referenced (QFE-style,
  height above the home airfield datum) instead of mean sea level, and AGL shows
  next to it. On the runway: ALT 0 FT; in flight it reads height above the field.
- **Distant mountains no longer speckle with bright/dark "pinholes"** — a rewrite
  of the terrain shading pipeline:
  - one LOD version per tile (no more double-render z-fighting),
  - per-vertex normals and biome colors sampled over a span that grows with LOD
    and ring distance, so 32–64 m quads shade like smooth macro slopes,
  - biome rock/snow thresholds driven by a lightly blurred height at far LODs
    (per-sample height jitter no longer flickers the bands),
  - snow on far LODs renders as a wide pale haze with a capped amount, near LODs
    keep crisp white caps,
  - skirt walls (seam fillers) now inherit the terrain color and normal of their
    top edge instead of a flat dark green, so seam pixels blend invisibly,
  - shader: soft tone shoulder above 0.75 (no more hard clip to pure white) and
    a wrapped terminator so slopes nearly perpendicular to the sun shade smoothly.

## Quick start

Requirements: **Java 11+** (a JRE is enough — prebuilt classes are included).
Windows, macOS and Linux (x86_64 + Apple Silicon) are supported.

```bash
./run.sh        # Linux / macOS
run.bat         # Windows (double-click works too)
```

The scripts compile the sources into `out/` (first run only) and launch the game.
LWJGL 3.3.3 jars are bundled in `libs/` — no internet needed. If you delete them,
`setup-libs.sh` / `setup-libs.bat` re-download from Maven Central.

Manual build, if you prefer:

```bash
bash build.sh                     # javac --release 11 -cp "libs/*" -d out @out/sources.txt
java -Xmx2G -cp "libs/*:out" skytrainer.Main
```

## How to fly

1. **Start Flight** puts you at the east end of runway 09, engine idle.
2. Hold **W** to push the throttle to 100%.
3. At ~52 kt (27 m/s) **pull the mouse down** (stick back) to rotate.
4. Climb out, then trim with small mouse moves — the trainer auto-levels gently.
5. **Landing**: line up with the runway, cut throttle (**S**), glide down, flare
   gently and touch down under 500 fpm. Land off-field, too hard or sideways and
   you will crash — press **R** to reset.

| Input                | Action                                  |
|----------------------|-----------------------------------------|
| Mouse                | Pitch (stick) and roll (yoke)           |
| Mouse down           | Pull nose up (stick style; invertible)  |
| W / S                | Throttle up / down                      |
| A / D                | Rudder (ground steering)                |
| Arrow keys           | Elevator / ailerons (backup)            |
| B or Space           | Wheel brakes                            |
| C or F5              | Toggle chase / cockpit view             |
| R                    | Reset to runway                         |
| T                    | Skip time +3 hours                      |
| F1 / F2 / F3         | Hide HUD / screenshot / debug           |
| F11                  | Borderless fullscreen                   |
| Esc                  | Pause menu                              |

## What's inside

- **Casual-sim flight model** — thrust with prop falloff, lift from angle of attack
  with a proper stall (nose drop + wing drop + beeper), induced + parasitic drag,
  sideslip damping, ground roll with steering, brakes and rotate speed, landing /
  crash rules based on sink rate, bank and terrain.
- **Infinite procedural terrain** — 1 km tiles streamed in three LOD rings with
  skirted seams; continent / hills / ridged-mountain Perlin stack; beaches, forests
  with baked cone trees, rocky slopes, snow caps and a guarded coastline around the
  home airfield so the approaches stay dry.
- **Airfield** — 1.2 km asphalt runway with piano keys, edge lines and centerline
  dashes, taxiway, apron, hangars and a control tower, all vertex-colored boxes.
- **Day/night cycle** — screen-space sky with sun disc, stars and a dawn/dusk
  palette; sun position drives terrain lighting, fog color, cloud tint and the HUD
  clock. Day length and start time are options.
- **Procedural everything** — aircraft model, 5×7 pixel font, GUI skin, cloud
  texture, and all audio: RPM-linked engine synth, wind rush, stall horn, touchdown
  thud, UI clicks and sparse menu music.
- **Options persist** to `options.txt` (FOV, render distance, sensitivity, volume,
  day length, start time, invert-Y, fog, clouds, music).

## Debug switches (optional)

| Env var           | Effect                                          |
|-------------------|-------------------------------------------------|
| `SKY_W` / `SKY_H` | Window size (default 1024×576)                  |
| `SKY_NO=sky,terrain,clouds,plane,tiles,runway,water` | Skip render passes (debug) |
| `SKY_NO_SKIRTS=1` / `SKY_NO_TREES=1` / `SKY_FLATCOL=1` / `SKY_FLATLIGHT=1` / `SKY_LOD_FORCE=0..2` | Terrain diagnostics |
| `SKY_AUTOTEST=1`  | Boot → title → takeoff → screenshots → exit (CI smoke test) |

## Troubleshooting

- **"Cannot init GLFW"** — make sure a display/GL driver is available. On Linux,
  the usual X11 libraries suffice; Mesa software rendering also works.
- **Low fps** — Options → Render Distance (each tile is 1 km; 10 covers 10 km).
- **macOS** — `run.sh` adds `-XstartOnFirstThread` automatically; don't remove it.
