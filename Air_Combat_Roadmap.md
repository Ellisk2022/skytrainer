# Chuck Yeager's Air Combat: Build Roadmap and AI Handoff

A slice-by-slice plan for turning a rough Java flight simulator into a reimplementation of *Chuck Yeager's Air Combat* (Electronic Arts, 1991), using the original user manual as the design spec.

**How to use this file:** give it to any AI assistant (or developer) together with the project source and the manual PDF. Section 0 tells them what to inspect first. Sections 1-3 are the work. Section 8 is a ready-to-paste prompt.

## Design philosophy: authentic look, modern rendering

This project targets modern resolutions. Do NOT implement a fixed 320x200 framebuffer or pixel-doubling. The UI should match the original CYAC's look through palette, layout, spacing, bevel style, and typography choices, not through pixel-count fidelity.

Rendering strategy:
- Render directly to the native window at any resolution the player's monitor supports.
- UI scale is computed dynamically from framebuffer size (see UI.begin() in UI.java).
- Aspect ratio agnostic - supports 4:3, 16:10, 16:9, ultrawide.
- Text and widgets render crisply at modern DPI; do not artificially chunk them.

What "authentic look" means for this project:
- Palette: match the CYAC grays, greens, and bevel highlight/shadow values (see Theme.java).
- Layout: menu panels, button spacing, and header styles mirror the original screenshots.
- Bevels: raised and pressed states match the original's 2-3 layer depth.
- Typography: bitmap-style font is retained for era feel, but rendered at modern resolution.
- Effects: hover indicators (triangles), focus highlights, and animation timing feel like the original.

Rationale: modern players run 1080p and above. Chunky stretched pixels look broken to them, not nostalgic. The goal is "this looks like a 1991 DOS game", not "this literally is a 1991 DOS game."

---

## 0. Project snapshot

### Known (stated by the project owner)
- Language/stack: Java 11 (compiled with JDK 21, --release 11 flag)
- Current state: working flight simulator with procedural terrain, working flight model, working menus in progress
- Currently working on: the menus - specifically matching the DOS visual style and building the screen flow
- Source of truth for behavior: the original manual (Chuck_Yeager_s_Air_Combat_Manual.pdf, 183 PDF pages)
- Goal: a reimplementation (new code, same behavior), not a byte-matching decompilation

### Filled in from the code

| Question | Answer |
|---|---|
| UI toolkit (Swing, JavaFX, LibGDX, LWJGL, other)? | LWJGL 3.3.3 with OpenGL 3.2 core profile + GLFW for windowing. Custom immediate-mode UI drawn with a DynBatch quad batcher. Font is a custom bitmap font in Font.java. |
| Main loop location and timestep (fixed or variable)? | Main.loop() - variable timestep, dt clamped to 0.1s max. Flight physics runs 2 substeps of plane.update() per frame for stability at any framerate. |
| Rendering approach (software, OpenGL, other) and internal resolution? | OpenGL 3.2 core via LWJGL. No fixed framebuffer - renders directly to the window. Window defaults to 1024x576, resizable, respects SKY_W and SKY_H env vars. UI scale computed dynamically: min(fbW/320, fbH/240) clamped to 1..4. |
| Which aircraft is currently implemented? | One generic prop trainer (Cessna-style). P-51D Mustang is the target for the menu background and future flight. PlaneModel.java has the voxel mesh; Aircraft.java has physics. |
| How is flight state stored (position, velocity, orientation, throttle)? | Aircraft class. x, y, z position (meters), vx, vy, vz world velocity, yaw/pitch/roll in radians, throttle (0..1), stickPitch/stickRoll (spring-centered virtual stick). Derived: aoa, rpm, gLoad, onGround, stalled, crashed. Body axes: +X nose, +Y up, +Z right. |
| Is there any existing state machine or screen manager? | Yes. Main.java holds an int state with constants: STATE_TITLE(0), STATE_OPTIONS(1), STATE_CONTROLS(2), STATE_LOADING(3), STATE_PLAY(4), STATE_PAUSE(5), plus a crash overlay rendered during PLAY. Screen objects are nested classes in Screens.java. Screen actions return strings ("start", "options", "controls", "quit", etc.) that Main dispatches. |
| Build system (Maven, Gradle, plain javac)? | Plain javac. build.bat is broken (space-in-path bug). Canonical compile is a direct javac command run from PowerShell. No dependency manager. LWJGL jars live in libs/. |

### Additional facts
- Theming system exists: Theme.java centralizes palette and bevel dimensions. Retheming = edit one file + rebuild.
- Live menu background: STATE_TITLE renders the plane flying in a chase camera over terrain with prop spin and engine audio.
- Menu layout is CYAC-shaped already: framed panel, CHOOSE ACTIVITY underlined header, beveled buttons with arrows on hovered option. Palette not yet pixel-matched to real CYAC screenshot.
- Only the title screen uses the new cyacButton(). Options, Controls, Pause, Crash still use the older UI.Button widget.
- Environment variables: SKY_W, SKY_H, SKY_AUTOTEST=1, SKY_NO (comma-separated: sky, terrain, clouds, plane).
- GitHub: https://github.com/Ellisk2022/skytrainer
- Local path: C:\Users\Kevin\Projects\Voxel air combat
- Handoff doc: SKYTRAINER_HANDOFF.md

### Legal and asset note
Work from a copy you own. Do not commit or publish original art, sound, or data files, or text copied from the manual. Palette values, layout measurements, and gameplay rules are fine to record for your own use. Ship your own assets, or make the game load the player's own installed copy.

### Page reference convention
Page numbers below are the printed manual page numbers. The PDF page is printed page + 1.

---

## Where we actually are

A quick status map so a new assistant knows exactly what's done, what's partial, and what's untouched.

### Slice 1 - Menus: in progress, roughly 40% done

| Roadmap item | Status |
|---|---|
| 1.4.1 Game state machine | Done. Main.java has an int-based state machine with STATE_TITLE, STATE_OPTIONS, STATE_CONTROLS, STATE_LOADING, STATE_PLAY, STATE_PAUSE. No stack - transitions are manual. |
| 1.4.2 Fixed-resolution framebuffer | Not doing by design. See Design Philosophy section. Dynamic resolution scaling used instead. |
| 1.4.3 Theme object | Done. Theme.java centralizes palette and bevel values. |
| 1.4.4 Widgets | Partial. UI.Button (old Minecraft-style) and UI.cyacButton (new DOS-style with pressed/hover states + arrows) both exist. Title uses cyacButton; others use UI.Button. |
| 1.4.5 Navigation table | Not started. Transitions hard-coded in Main.titleAction(), optionsAction(), pauseAction(). |
| 1.4.6 Placeholders | N/A yet. |

Screens present: TitleScreen, OptionsScreen, ControlsScreen, LoadingScreen, PauseScreen, CrashScreen.
Screens required but not present: Conflict Selection, Mission Selection, Mission Description, Tactics, Debrief, Stats, Custom Scenario builder, Test Flight viewer, Location menu, Review Film picker.

### Slice 2 - One aircraft and flight dynamics: roughly 60% done

Mostly done: lift, drag, thrust, weight, stall (with nose drop and wing drop), flap effects, ground roll, takeoff rotation, hard-landing crash detection, terrain proximity crash, sideslip damping, auto-level on stick release, control authority scaled by airspeed.

Not started: aircraft data file (JSON), air brakes gating per aircraft type, blackouts/redouts, ejecting, weapon select, G gear, F flaps, Shift-E eject, Ctrl-Q end mission, Ctrl-R HUD color, Flight Menus, Test Flight harness, Location menu.

Model: currently flyable aircraft is a generic prop trainer, not the P-51D. The P-51D voxel model flies in the title screen background.

### Slice 3 - Custom scenario: not started

None of the builder steps, scenario data files, scenario runner, Stats screen, or enemy AI exist.

### After-slices work: not started

Historic missions, more aircraft, HUD fidelity, missiles, flight recorder, campaign, Ace's Challenge, audio polish.

### Immediate next step

Finish the title-screen palette match to a real CYAC screenshot (green text, triangle arrows). Then build: Conflict Selection -> Mission Selection -> Mission Description -> Tactics -> Test Flight viewer.

---

## 1. Slice 1: Menus

**Goal:** every menu screen in the Mission Menus chapter exists, can be navigated, and shares one theme. Screens can be placeholders behind the buttons, but the navigation must be real.

### 1.1 Screen flow (from the manual, pp. 19-31)

MAIN MENU
 |- Fly an historic mission  -> Conflict select -> Mission select (paged) -> Difficulty + Tactics -> [FLIGHT] -> Debrief / Stats -> MAIN MENU
 |- Create a custom scenario -> "There I was in my..." builder -> Done -> [FLIGHT] -> Stats -> MAIN MENU
 |- Take up an airplane (Test Flight) -> Aircraft viewer (3D/2D, rotate) -> Fly -> [FLIGHT] -> MAIN MENU
 |- Repeat your last mission -> [FLIGHT] directly
 |- Watch a flight recording (Review Film) -> File picker -> [PLAYBACK]
 |- Quit the game

Main menu entries read exactly: Fly an historic mission / Create a custom scenario / Take up an airplane, no enemies / Repeat your last mission / Watch a flight recording / Quit the game.

### 1.2 Input rules for menus (p. 20)
- Mouse: point and click.
- Keyboard: Tab moves between options, Space selects, Esc backs up one screen.
- Most options have a shortcut key = first letter of the option. Watch for collisions and resolve deliberately.
- In the custom scenario builder, Backspace or a "Back Up" button returns to the previous step.

### 1.3 Screen inventory

| Screen | Contents | Manual page |
|---|---|---|
| Main Menu | six options above | 20 |
| Conflict Selection | choose the era/war; Exit returns to Main Menu | 21 |
| Mission Selection | paged list with dates; Next Page / Previous Page / Exit; 1/2/3 small squares = easy/moderate/hard; checkmark = Ace's Challenge completed | 21 |
| Mission Description | Diff: button (cycles difficulty), Tactics button, OK to start | 21-23 |
| Tactics (comparison) | cycle your plane with left/right, opponent with up/down; compares weapons, ceiling, max weight; OK returns | 22 |
| Debrief | Yeager's message; Stats and Done buttons | 24 |
| Stats | kills and losses, weapon accuracy, mission length, airplane condition | 24, 28 |
| Custom Scenario builder | see Slice 3 | 25-28 |
| Test Flight viewer | arrow buttons cycle aircraft; 3d/2d toggle; rotate X and Y axes; Exit; Fly (greyed for non-flyable) | 29-30 |
| Location menu (in flight) | On Runway, Final Approach, 10,000 ft, 40,000 ft | 31 |
| Review Film picker | scrolling file list, select a saved film, Exit | 31 |

Difficulty levels (p. 22): Easy, Normal, Hard, Expert.

### 1.4 Implementation steps

1. Game state machine - one GameState interface with enter/update/render/handleInput/exit; StateManager holds a stack.
2. **Not doing by design.** The project renders directly to the native window at any resolution (see Design Philosophy section above). The CYAC look is achieved through palette, bevels, and layout rather than through pixel-count fidelity.
3. Theme object - one Theme holds palette entries, frame style, bevel colors, font, spacing.
4. Widgets - MenuButton (raised and pressed states, focus highlight), MenuFrame, Label, ListPicker, Toggle.
5. Navigation table - declare screen graph in data instead of hard-coded transitions.
6. Placeholders - any screen without a backend shows its real layout with stub data.

### 1.5 Done when
- Click or Tab through every screen in 1.1 and Esc back out.
- Shortcut keys work, no collisions.
- Changing one Theme value restyles every screen.
- Flight-bound options launch the flight sim; ending returns to correct menu.

---

## 2. Slice 2: One aircraft and its flight dynamics

**Goal:** one aircraft that behaves like its manual description, with data-driven parameters so more aircraft are later just new data files.

### 2.1 Recommended first aircraft
North American P-51D Mustang (manual p. 131) - simplest systems: guns only, no air brakes, no afterburner, no missiles. Six flyable in Test Flight (p. 30): P-51D, FW-190, F-86, MiG-15, F-4E, MiG-21.

### 2.2 What the manual specifies for the P-51D (p. 131)

| Property | Value |
|---|---|
| Type | single-seat fighter |
| Engine | one 1,590 hp Packard-built Rolls-Royce Merlin V-1650-7, liquid cooled |
| Wingspan / length / height | 37.0 ft / 32.2 ft / 13.7 ft |
| Weight | 7,125 lbs |
| Maximum speed | 437 mph |
| Climb | 3,475 ft/min |
| Ceiling | 41,900 ft |
| Armament | six Browning MG53-2 machine guns in the wings (.50 cal) |
| Notable | vulnerable to radiator hits |

Not in the manual (choose and tune yourself): stall speed, turn rates, roll rate, damage values, ammo count.

### 2.3 Aircraft data file (suggested JSON shape)
{
  "id": "p51d",
  "name": "North American P-51D Mustang",
  "era": "WW2",
  "mass_lbs": 7125,
  "max_speed_mph": 437,
  "climb_rate_fpm": 3475,
  "ceiling_ft": 41900,
  "engine": { "type": "prop", "hp": 1590, "afterburner": false },
  "controls": { "flaps": true, "gear": true, "air_brakes": false, "wheel_brakes": true },
  "weapons": [ { "id": "mg50", "type": "gun", "count": 6, "ammo": "TUNE" } ],
  "tuning": { "stall_speed_mph": "TUNE", "max_g": "TUNE", "roll_rate_dps": "TUNE" },
  "model": "path/to/your/own/model"
}

### 2.4 Flight model rules from Ground School (pp. 72-81)

- Control surfaces: ailerons roll, elevators pitch, rudder yaws. Stick auto-coordinates rudder.
- Four forces: lift, weight, thrust, drag. Level flight: lift = weight, thrust = drag.
- Angle of attack: more AoA = more lift and more drag. Too much AoA = stall.
- Stall recovery: stall warning, throttle 100%, stick forward. Low-altitude stalls are often fatal.
- Flaps: more lift and drag, lower stall speed, speed bleeds off faster.
- Air brakes: more drag. Korea and Vietnam only; props have none.
- Altitude: higher = lower air pressure, temp, oxygen. Minimum speed rises; thrust falls.
- Wing loading: high favors speed, low favors turning. Long thin wings do better at altitude.
- Banking and g: maneuvering costs energy.
- Turn measures: g-force, turn radius, rate of turn. Best rate = most g at lowest speed that allows it.
- Flight envelope: lift limit, thrust limit, structural limit. Sustained dive can rip wings.
- Energy state: altitude = potential, speed = kinetic. Diving and climbing trade one for the other.
- Blackouts and redouts (p. 54).
- Crashes: ground impact and hard landing crash you. Easy Landings disables it.
- Ejecting (p. 53): safe at 500 mph or less and 300 ft or more.

### 2.5 Cockpit controls to support (pp. 41-55)

| Function | Control |
|---|---|
| Flight stick | joystick, mouse, or keyboard numpad; auto-centers; / or numpad 5 centers |
| Throttle | keyboard only; 0/25/50/75/100%, +/- 5% steps; afterburner Vietnam only |
| Flaps / gear / brakes | F flaps, G gear, B wheel/air brakes |
| Weapon select | [ previous, ] next |
| Views | F1-F6 cockpit; Shift+F1-F6 external; F7/F8 target; F9 map; F10 fly-by |
| Pause / menus | Ctrl-P pause, Esc Flight Menus, Ctrl-Q end mission |
| Time compression | T cycles 1x, 2x, 4x |
| Instrument panel | Backspace toggles |
| HUD color | Ctrl-R cycles |
| Eject | Shift-E |

Full key list is on the Command Summary Card (not in manual). Add missing keys as found.

### 2.6 Flight Menus to build (pp. 55-60)
Open with Esc during flight. Menus: ?, System, View, Graphics, Help. Build only what Slice needs first: Invincible, Unlimited Ammo, No Blackout, Easy Landings, Envelope Window.

### 2.7 Test Flight as test harness
No enemies, with Location menu: On Runway, Final Approach, 10,000 ft, 40,000 ft. Build early.

### 2.8 Done when
- Takeoff, climb, level, turn, stall, recovery, landing all work with data-file values.
- Aircraft reaches roughly manual max speed and ceiling (5% tolerance).
- Stall warning before stall; flaps lower stall speed.
- Wings fail from overspeed or excess g.
- Swapping data file changes behavior with no code changes.

---

## 3. Slice 3: Custom scenario

**Goal:** "There I was in my..." builder produces a scenario object, flight sim runs it, Stats screen reports outcome.

### 3.1 Builder steps (pp. 25-28)

| Step | Choice |
|---|---|
| 1 | Create Mission from Main Menu |
| 2 | Your airplane |
| 3 | Starting altitude |
| 4 | Tactical position |
| 5 | Number of opponents |
| 6 | Opponent aircraft type |
| 7 | . to end sentence, or "and" to add another group |
| 8 | Opponent experience level |
| 9 | Done - flies scenario |

Tactical positions (p. 26): Easy (you above/behind, he unaware), Saw (head-on same altitude, both aware), Was joined by (he above/behind, ready to attack).

Experience levels (p. 28): inexperienced are less aggressive, try to evade; experienced confront you, better marksmen.

### 3.2 Scenario data file (suggested JSON shape)
{
  "id": "custom-0001",
  "player": { "aircraft": "p51d", "altitude_ft": 10000, "position": "EASY" },
  "opponents": [
    { "aircraft": "fw190", "count": 2, "experience": "AMATEUR" }
  ],
  "terrain_seed": 12345,
  "difficulty": null,
  "end_conditions": ["ALL_OPPONENTS_DOWN", "PLAYER_DOWN", "PLAYER_LANDED_AT_HOME", "PLAYER_EJECTED", "QUIT"],
  "meta": { "created": "ISO-8601 timestamp" }
}

### 3.3 Scenario runner
Add ScenarioLoader: spawn player at chosen altitude and position, spawn opponents per tactical rule, start clock and stats, watch end conditions, hand off to Stats.

### 3.4 Ending a scenario (p. 28)
Ctrl-Q, land at home base (throttle 0%, full stop), or eject. Stats screen shows kills, losses, accuracy, duration, plane condition. Done returns to Main Menu.

### 3.5 Enemy AI: two passes
1. Minimum: opponent flies, turns toward player, fires when lined up.
2. Full: use experience levels to change behavior.

### 3.6 Integration checklist
1. Wrap flight loop in FlightState taking Scenario, returning MissionResult.
2. Replace hard-coded spawn with ScenarioLoader.
3. Keep terrain procedural; pass terrain_seed.
4. Add StatsRecorder updated by flight loop.
5. MissionResult is the only thing passed back to Stats.
6. Save last scenario so "Repeat your last mission" works.

### 3.7 Done when
- Build scenario in menus, fly, end any of three ways, see accurate Stats, return to Main Menu.
- "Repeat your last mission" replays it.
- Changing scenario file changes encounter with no code changes.

---

## 4. After the three slices (suggested order)

1. Historic missions as authored scenarios (pp. 20-24)
2. More aircraft as data files
3. HUD and instrument panel fidelity (p. 33)
4. Missiles, countermeasures, RWR (pp. 47-50)
5. Flight recorder and Review Film (p. 63)
6. Campaign mode (p. 25)
7. Ace's Challenge (p. 23)
8. Audio and polish

---

## 5. Manual index (printed page numbers)

| Topic | Pages |
|---|---|
| Quickstart / Tactical Overview | 8 / 17 |
| Mission Menus | 19-31 |
| Instrument Check (HUD, panel) | 33-40 |
| In the Cockpit (controls, views, weapons, damage, eject) | 41-55 |
| Flight Menus | 55-60 |
| Flight Recorder | 63-69 |
| Ground School (flight dynamics) | 72-81 |
| Flight School (takeoff, landing, maneuvers) | 83-106 |
| Gunnery School | 107-112 |
| Fighter Tactics | 113-124 |
| Air Combat in Three Eras + aircraft descriptions | 125-157 |
| Copy protection answers | 129-158 (ignore) |
| Glossary, HUD message appendix | 163, 165 |

---

## 6. Open questions for the project owner

1. Which aircraft does the current build model? (Answer: generic prop trainer; P-51D is target)
2. Should menus target exact original proportions or modern resolution with same look? (Answer: modern, see Design Philosophy)
3. Do you have the original game files installed for reference, or are you drawing from the manual and screenshots? (TBD)
4. Joystick support: needed now, or keyboard and mouse first? (TBD)

---

## 7. Working agreements (for any assistant picking this up)

- Keep the game runnable at the end of every change.
- Prefer data files over hard-coded content.
- Change one slice at a time; do not start Slice 3 work while Slice 2 acceptance criteria fail.
- Record decisions (and the manual page they came from) in a DECISIONS.md in the repo.
- Mark every tuned number as tuned; never present a guess as a manual fact.

---

## 8. Ready-to-paste handoff prompt

I'm rebuilding Chuck Yeager's Air Combat as a Java reimplementation. I've attached Air_Combat_Roadmap.md, the original manual PDF, and my project source. Read the roadmap first. Start with Section 0: inspect my code and fill in the "Filled in from the code" table. Then summarize back to me what exists, which slice we are on, and the smallest next step. Work in slices (1 menus, 2 one aircraft and flight dynamics, 3 custom scenario), keep the game runnable after every change, use the manual's printed page numbers when citing it, and ask me before restructuring anything that already works. Do not copy original game assets or manual text into the repo.
