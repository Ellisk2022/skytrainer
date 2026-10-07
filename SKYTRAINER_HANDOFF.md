# SkyTrainer - Project Handoff

## CRITICAL: Master Reference Document
The file **Chuck_Yeagers_Air_Combat_-_Manual.pdf** is the design bible for this entire project. Every menu, mission, aircraft, damage system, and tactic in this game is being built to match it.

**ACTION REQUIRED AT SESSION START:** If you (the AI) do not already have the manual's contents in your context, ASK THE USER to paste it before doing any design work. Do not guess or invent mechanics from memory - the manual is the ground truth.

Key manual sections to know:
- Pages 8-17: Quickstart (menu walkthrough, targeting, HUD basics)
- Pages 19-31: Mission Menus (Historic Mission select, Custom Mission creation, Test Flight, Review Film)
- Pages 33-38: Instrument Check (HUD layout, instrument panel, per-aircraft gauges)
- Pages 41-55: In the Cockpit (controls, view keys, targeting, missiles, radar, RWR, countermeasures, ejecting, blackouts)
- Pages 63-69: Flight Recorder (VCR-style playback, save/load flight films)
- Pages 72-81: Ground School (aerodynamics, four forces, flight envelope, energy state)
- Pages 83-89: Flight School (Level Flight, Climb, Dive, Break, Takeoff, Landing)
- Pages 91-97: Basic Maneuvers (Jink, Scissors, Loop, Split S, Immelmann, Yo-Yo, Barrel Roll)
- Pages 107-110: Gunnery School (deflection angle, leading, gun sight types)
- Pages 113-123: Fighter Tactics (detection, closing, attack, maneuver, missile evasion, bomber attacks)
- Pages 126-158: Air Combat in Three Eras (WW2, Korea, Vietnam history + 18 aircraft profiles)
- Pages 165-168: Aircraft Damage messages (what to display when the plane is hit)
- Pages 176-178: Index

## Project Overview
Voxel-based flight simulator being transformed into a tribute to Chuck Yeager's Air Combat (1991, Electronic Arts).
- Language: Java 11 (compiled with Java 21 JDK, --release 11 flag)
- Libraries: LWJGL 3.3.3 (OpenGL 3.2 core, GLFW)
- Project root: C:\Users\Kevin\Projects\Voxel air combat
- GitHub: https://github.com/Ellisk2022/skytrainer
- Main class: src/skytrainer/Main.java

## Build and Run
cd "C:\Users\Kevin\Projects\Voxel air combat"
javac --release 11 -encoding UTF-8 -cp "libs\*" -d out (Get-ChildItem -Recurse -Filter *.java -Path src | ForEach-Object { $_.FullName })
.\run.bat

NOTE: build.bat has a space-in-path bug and should NOT be used. Use the javac command above.

## Current State (Working)
1. Voxel terrain with procedural generation, streaming chunks, day/night cycle.
2. Aircraft physics: lift, drag, stall, ground roll, takeoff, crash detection.
3. HUD with airspeed, altitude, throttle, VSI.
4. Main menu with title, logo, buttons (Start Flight / Air Start / Options / Controls / Quit).
5. Options screen with FOV, render distance, sensitivity, volume, day length, clock.
6. Controls screen listing key bindings.
7. Pause screen, crash overlay, loading screen.
8. Audio: engine, stall warning, touchdown, crash, click, music.
9. Live P-51 flying in the main menu background (orbiting at 220 feet, banking 15 degrees into the turn, prop spinning, engine audio).

## Live Menu Implementation Details
- The plane is scripted in the loop() method of Main.java inside a block commented "Scripted menu flight".
- Orbit: x = cos(angle)*800, z = sin(angle)*800, y = 220 + sin(angle*3)*15.
- angle = worldTime * 0.08. This is the orbit speed.
- Yaw is set to point the nose along the direction of travel: yaw = -angle - PI/2.
- Roll = 15 degrees (positive rotates right wing down, banking into the turn).
- Prop spin: Aircraft.spinProp(dt, 2200f) is called each frame from the menu script.
- Camera (in Main.java render() method, inside "if (menuWorld)") is a chase view:
  float dist = 22f;
  float dirX = -sin(worldTime * 0.08);
  float dirZ = cos(worldTime * 0.08);
  eye = {plane.x - dirX*dist, plane.y + 4f, plane.z - dirZ*dist};
  lookAt = {plane.x + dirX*8f, plane.y + 2f, plane.z + dirZ*8f};
- The plane renders during STATE_TITLE because the draw guard in render() was changed to include it.

## Aircraft Coordinate System (IMPORTANT)
Body axes: +X = nose, +Y = up, +Z = right.
- yaw: rotation about Y, positive = turn left.
- pitch: rotation about Z, positive = nose up.
- roll: rotation about X, positive = right wing down (confirmed empirically).
Heading in degrees: atan2(fwd.x, -fwd.z).

## Menu Design Direction (from the manual, pages 9 and 20)
Classic CYAC menu:
- Header: "CHOOSE ACTIVITY"
- Buttons: Fly Historic Mission / Create Mission / Test Flight / Last Mission / Review Film / Exit to DOS
- Yeager portrait in the lower-right corner.
Current decision: Keep existing working buttons for now (so we can test flight quickly), add Yeager portrait next, and build the CYAC layout once mission-select screens are built.

## Planned Work (Next Steps)
1. Add Chuck Yeager portrait to the menu (voxel-style or sprite).
2. Redesign menu to CYAC layout (see manual pages 9, 20).
3. Build mission-select screen - "Fly Historic Mission" leads to WW2 / Korea / Vietnam choice (manual pages 20-22).
4. Build Custom Mission screen - choose plane, altitude, tactical position, opponents, experience (manual pages 25-28).
5. Build Historic Mission list with difficulty, descriptions, and Ace's Challenge tracking (manual pages 20-23).
6. Implement aircraft damage system - display the message strings from manual pages 165-168.
7. Implement target window (manual pages 13, 60).
8. Implement flight recorder with VCR controls (manual pages 63-69).
9. Build per-era aircraft roster (P-51, P-47, B-17, FW-190, Me-109, Me-262, F-86, MiG-15, F-4, MiG-21, B-29, B-52 - see manual pages 128-158).
10. Implement radar, RWR, chaff/flare, missiles (manual pages 47-51).

## Known Issues / Gotchas
- build.bat does not work (space in folder path breaks javac source list). Use the direct javac command.
- Claude Code leaves orphaned lines sometimes when editing; always read back after edits to verify.
- Avoid em-dashes and special characters in code comments; they break Claude Code's string matcher.
- Runtime runs stale .class files if you forget to recompile. Always run javac before run.bat.

## AI Session Rules
- Give the user ONE prompt at a time. Say clearly whether it goes in Claude Code or PowerShell.
- If a manual edit is needed, provide the ENTIRE file in one block so the user can Ctrl+A, Delete, Ctrl+V.
- Special characters (em-dash, ellipsis, degree symbol) in code comments cause Claude Code to fail edits.
- Do not re-ask questions already answered in this handoff.
- AT SESSION START: If the manual is not already in context, ASK THE USER TO PASTE IT (or the relevant pages) before starting any design or feature work. The manual is the source of truth for all mechanics.
- WHEN A SPECIFIC PAGE IS NEEDED: Ask the user to paste that page instead of trying to recall from memory.

## Git Workflow (to save progress)
cd "C:\Users\Kevin\Projects\Voxel air combat"
git add -A
git commit -m "descriptive message"
git push