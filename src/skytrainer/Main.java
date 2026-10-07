package skytrainer;

import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.FloatBuffer;

/** SkyTrainer — a procedural casual flight simulator in Java with LWJGL 3 / OpenGL 3.2 core. */
public final class Main {
    public static final FloatBuffer MAT_BUF = BufferUtils.createFloatBuffer(16);

    private static final int STATE_TITLE = 0, STATE_OPTIONS = 1, STATE_CONTROLS = 2,
            STATE_LOADING = 3, STATE_PLAY = 4, STATE_PAUSE = 5;

    private long window;
    private int fbW = 1024, fbH = 576, winW = 1024, winH = 576;

    private Input input = new Input();
    private Options opts;
    private Audio audio;
    private Shader guiShader, worldShader;
    private Atlas atlas;
    private Font font;
    private UI ui;
    private Terrain terrain;
    private Sky sky = new Sky();
    private Clouds clouds;
    private PlaneModel planeModel;
    private Aircraft plane;
    private Hud hud;

    private int state = STATE_TITLE;
    private Screens.TitleScreen title = new Screens.TitleScreen();
    private Screens.OptionsScreen optionsScreen;
    private Screens.ControlsScreen controlsScreen = new Screens.ControlsScreen();
    private Screens.LoadingScreen loading = new Screens.LoadingScreen();
    private Screens.PauseScreen pause = new Screens.PauseScreen();
    private Screens.CrashScreen crashScreen = new Screens.CrashScreen();

    private double worldTime;
    private long seed;
    private boolean cockpitView;
    private boolean hideHud, debug;
    private boolean crashAudioPlayed;

    // chase camera smoothing
    private float camX, camY, camZ;
    private boolean camInit;

    private double lastFrame;
    private int fps, fpsCount;
    private double fpsTimer;
    private boolean wasOnGround = true;

    // autotest (SKY_AUTOTEST=1): boots to menu, starts a flight, screenshots, exits
    private boolean autotest;
    private int autoFrame;
    private boolean sawAirborne;

    public static void main(String[] args) {
        new Main().run();
    }

    private void run() {
        autotest = "1".equals(System.getenv("SKY_AUTOTEST")) || "1".equals(System.getenv("VOXEL_AUTOTEST"));
        opts = Options.load();
        seed = 0xA17C0AF7L + (autotest ? 0 : System.nanoTime() % 100000);
        initWindow();
        initGl();
        audio = new Audio();
        audio.master = opts.masterVolume;
        audio.musicVol = opts.music ? 1f : 0f;
        audio.start();
        hud = new Hud(ui);
        newFlight(true); // menu panorama + runway scene behind the title screen
        if (autotest) System.out.println("[autotest] boot complete, state=TITLE");
        loop();
        dispose();
    }

    // ------------------------------------------------------------------ setup
    private void initWindow() {
        if (!GLFW.glfwInit()) throw new IllegalStateException("Cannot init GLFW");
        String wEnv = System.getenv("SKY_W"), hEnv = System.getenv("SKY_H");
        if (wEnv == null) wEnv = System.getenv("VOXEL_W");
        if (hEnv == null) hEnv = System.getenv("VOXEL_H");
        try { if (wEnv != null) winW = Integer.parseInt(wEnv); } catch (Exception ignored) { }
        try { if (hEnv != null) winH = Integer.parseInt(hEnv); } catch (Exception ignored) { }
        fbW = winW; fbH = winH;
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_TRUE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 2);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);
        window = GLFW.glfwCreateWindow(winW, winH, "SkyTrainer 0.1.2", 0, 0);
        if (window == 0) throw new IllegalStateException("Cannot create OpenGL 3.2 window");
        GLFW.glfwMakeContextCurrent(window);
        GL.createCapabilities();
        GLFW.glfwSwapInterval(1);
        GLFW.glfwShowWindow(window);

        GLFW.glfwSetFramebufferSizeCallback(window, (win, w, h) -> {
            fbW = Math.max(1, w); fbH = Math.max(1, h);
            GL11.glViewport(0, 0, fbW, fbH);
            input.scale = (float) fbW / Math.max(1, winW);
        });
        GLFW.glfwSetWindowSizeCallback(window, (win, w, h) -> { winW = Math.max(1, w); winH = Math.max(1, h); });
        GLFW.glfwSetKeyCallback(window, (win, key, sc, action, mods) -> input.onKey(key, action));
        GLFW.glfwSetMouseButtonCallback(window, (win, button, action, mods) -> input.onMouseButton(button, action));
        GLFW.glfwSetCursorPosCallback(window, (win, x, y) -> input.onMousePos(x, y));
        GLFW.glfwSetScrollCallback(window, (win, xoff, yoff) -> input.onScroll(yoff));
        int[] fw = new int[1], fh = new int[1];
        GLFW.glfwGetFramebufferSize(window, fw, fh);
        fbW = Math.max(1, fw[0]); fbH = Math.max(1, fh[0]);
        input.scale = (float) fbW / winW;
        input.rawMouseSupported = GLFW.glfwRawMouseMotionSupported();
        GL11.glViewport(0, 0, fbW, fbH);
    }

    private void initGl() {
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glCullFace(GL11.GL_BACK);
        GL11.glFrontFace(GL11.GL_CCW);
        atlas = new Atlas();
        atlas.upload();
        font = new Font();
        font.build();
        guiShader = new Shader(Shader.GUI_VERTEX, Shader.GUI_FRAGMENT);
        worldShader = new Shader(Shader.WORLD_VERTEX, Shader.WORLD_FRAGMENT);
        sky.initGl();
        ui = new UI(guiShader, atlas, font);
        clouds = new Clouds(worldShader);
        planeModel = new PlaneModel();
    }

    private void newFlight(boolean airStart) {
        if (terrain != null) terrain.dispose();
        terrain = new Terrain(seed, worldShader);
        plane = new Aircraft(terrain, !airStart);
        if (airStart) plane.resetAirStart();
        camInit = false;
        crashAudioPlayed = false;
    }

    // ------------------------------------------------------------------- loop
    private void loop() {
        lastFrame = GLFW.glfwGetTime();
        while (!GLFW.glfwWindowShouldClose(window)) {
            double now = GLFW.glfwGetTime();
            float dt = (float) Math.min(0.1, now - lastFrame);
            lastFrame = now;
            worldTime += dt;
            fpsCount++;
            fpsTimer += dt;
            if (fpsTimer >= 1) { fps = fpsCount; fpsCount = 0; fpsTimer -= 1; }

            GLFW.glfwPollEvents();
            handleGlobalKeys();

            // Scripted menu flight — plane orbits slowly in the sky
            if (state == STATE_TITLE && plane != null) {
                float angle = (float) (worldTime * 0.08);
                plane.x = (float) Math.cos(angle) * 800f;
                plane.z = (float) Math.sin(angle) * 800f;
                plane.y = 220f + (float) Math.sin(angle * 3) * 15f;
                plane.yaw = -angle - (float)(Math.PI / 2);
                plane.roll = (float) Math.toRadians(15);
                plane.pitch = 0f;
                plane.spinProp(dt, 2200f);
            }

            // day/night always ticks (slow), faster scaling from options
            sky.update(dt, (state == STATE_PLAY || state == STATE_PAUSE) ? 1f : 1.5f);
            sky.dayLengthMin = opts.dayLength;

            // fixed-step flight sim
            if (state == STATE_PLAY && plane != null) {
                double gap = now - (lastFrame - dt);
                int steps = 2; // two substeps for stability at any frame rate
                float sdt = dt / steps;
                for (int i = 0; i < steps; i++) plane.update(input, opts, sdt);
                if (plane.crashed && !crashAudioPlayed) {
                    crashAudioPlayed = true;
                    audio.crash();
                    input.unlockCursor(window);
                }
                if (!wasOnGround && plane.onGround) {
                    audio.touchdown(Math.min(1, Math.abs(plane.vsiFpm()) / 800f));
                }
                wasOnGround = plane.onGround;
            }

            // stream terrain
            if (terrain != null) {
                float fx = plane != null && (state == STATE_PLAY || state == STATE_PAUSE || state == STATE_TITLE) ? plane.x : 0;
                float fz = plane != null && (state == STATE_PLAY || state == STATE_PAUSE || state == STATE_TITLE) ? plane.z : 0;
                int budget = state == STATE_LOADING ? 6 : 2;
                terrain.stream(fx, fz, opts.renderDistance, budget);
                if (state == STATE_LOADING) {
                    loading.progress = 1f - Math.min(1f, terrain.pendingCount() / 60f);
                    if (terrain.pendingCount() == 0) {
                        state = STATE_PLAY;
                        input.lockCursor(window);
                        if (autotest) System.out.println("[autotest] flight ready, state=PLAY");
                    }
                }
            }

            // audio state
            if (plane != null) {
                audio.rpm = plane.crashed ? 0 : plane.rpm;
                audio.airspeed = plane.speed();
                audio.stallWarn = plane.stalled;
                audio.inFlight = state == STATE_PLAY || state == STATE_PAUSE;
            } else {
                audio.rpm = 0;
                audio.inFlight = false;
            }

            render(dt);
            GLFW.glfwSwapBuffers(window);
            input.endFrame();
            autotestTick();
        }
    }

    private void handleGlobalKeys() {
        if (input.keyPressed(GLFW.GLFW_KEY_F2)) screenshot();
        if (input.keyPressed(GLFW.GLFW_KEY_F11)) {
            long monitor = GLFW.glfwGetPrimaryMonitor();
            GLFWVidMode mode = GLFW.glfwGetVideoMode(monitor);
            boolean fullscreen = GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_DECORATED) == 0;
            if (fullscreen) {
                GLFW.glfwSetWindowMonitor(window, 0L, 60, 60, 1024, 576, 0);
            } else {
                GLFW.glfwSetWindowMonitor(window, monitor, 0, 0, mode.width(), mode.height(), mode.refreshRate());
            }
        }
        switch (state) {
            case STATE_PLAY:
                if (input.keyPressed(GLFW.GLFW_KEY_ESCAPE)) {
                    state = STATE_PAUSE;
                    input.unlockCursor(window);
                } else if (input.keyPressed(GLFW.GLFW_KEY_C) || input.keyPressed(GLFW.GLFW_KEY_F5)) {
                    cockpitView = !cockpitView;
                    audio.click();
                } else if (input.keyPressed(GLFW.GLFW_KEY_R)) {
                    plane.resetToRunway();
                    camInit = false;
                    crashAudioPlayed = false;
                    audio.click();
                } else if (input.keyPressed(GLFW.GLFW_KEY_T)) {
                    sky.setTimeOfDay((sky.clockHours() + 3) % 24);
                    audio.click();
                } else if (input.keyPressed(GLFW.GLFW_KEY_F1)) {
                    hideHud = !hideHud;
                } else if (input.keyPressed(GLFW.GLFW_KEY_F3)) {
                    debug = !debug;
                }
                break;
            case STATE_PAUSE:
                if (input.keyPressed(GLFW.GLFW_KEY_ESCAPE)) {
                    state = STATE_PLAY;
                    input.lockCursor(window);
                }
                break;
            case STATE_OPTIONS:
                if (input.keyPressed(GLFW.GLFW_KEY_ESCAPE)) {
                    opts.save();
                    state = optionsScreen != null && optionsScreen.fromPause ? STATE_PAUSE : STATE_TITLE;
                }
                break;
            case STATE_CONTROLS:
                if (input.keyPressed(GLFW.GLFW_KEY_ESCAPE)) state = optionsScreen != null && optionsScreen.fromPause ? STATE_PAUSE : STATE_TITLE;
                break;
            default:
                break;
        }
    }

    // ----------------------------------------------------------------- render
    private void render(float dt) {
        GL11.glClearColor(sky.horizon[0], sky.horizon[1], sky.horizon[2], 1);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        boolean menuWorld = state == STATE_TITLE || state == STATE_OPTIONS || state == STATE_CONTROLS;
        boolean showWorld = menuWorld || state == STATE_PLAY || state == STATE_PAUSE;

        if (showWorld && terrain != null) {
            float far = opts.renderDistance * (float) Terrain.TILE + 2600f;
            Mat4 proj, view;
            float[] eye;
            float fovDeg;
            if (menuWorld) {
                float dist = 22f;
                float dirX = (float) -Math.sin(worldTime * 0.08);
                float dirZ = (float)  Math.cos(worldTime * 0.08);
                eye = new float[]{plane.x - dirX * dist, plane.y + 4f, plane.z - dirZ * dist};
                view = Mat4.lookAt(eye[0], eye[1], eye[2], plane.x + dirX * 8f, plane.y + 2f, plane.z + dirZ * 8f, 0, 1, 0);
                proj = Mat4.perspective(70, (float) fbW / fbH, 1f, far);
                fovDeg = 70;
                camFwd = viewFwd(view);
            } else {
                fovDeg = opts.fov;
                proj = Mat4.perspective(fovDeg, (float) fbW / fbH, 0.5f, far);
                eye = cameraEye(dt);
                view = camView;
                camFwd = camFwdVec;
            }

            String skip = System.getenv("SKY_NO");
            boolean drawSky = skip == null || !skip.contains("sky");
            if (drawSky) {
                float aspect = (float) fbW / fbH;
                // camera basis from view matrix rows
                float[] r = new float[]{view.m[0], view.m[4], view.m[8]};
                float[] u = new float[]{-view.m[1], -view.m[5], -view.m[9]};
                float[] f = new float[]{-view.m[2], -view.m[6], -view.m[10]};
                sky.render(f, r, u, fovDeg, aspect);
            }

            boolean drawTerrain = skip == null || !skip.contains("terrain");
            if (drawTerrain) {
                atlas.bind(0); // terrain/water sample the white tile (vertex colors only)
                terrain.render(eye[0], eye[1], eye[2], proj, view, opts, sky, (float) worldTime);
            }
            if (opts.clouds && (skip == null || !skip.contains("clouds"))) {
                clouds.render(eye[0], eye[2], sky, opts.fog, far);
            }

            // the aircraft
            if (state == STATE_PLAY || state == STATE_PAUSE || state == STATE_TITLE) {
                boolean drawPlane = (skip == null || !skip.contains("plane")) && !(cockpitView && state == STATE_PLAY);
                if (drawPlane) {
                    atlas.bind(0); // plane samples the white tile after the cloud pass
                    Mat4 model = plane.bodyMatrix();
                    Mat4 pos = new Mat4().identity();
                    pos.m[12] = plane.x; pos.m[13] = plane.y; pos.m[14] = plane.z;
                    model = Mat4.mul(pos, model);
                    worldShader.use();
                    worldShader.setMat4(worldShader.loc("uProj"), Main.MAT_BUF.put(proj.m).flip());
                    worldShader.setMat4(worldShader.loc("uView"), Main.MAT_BUF.put(view.m).flip());
                    worldShader.setMat4(worldShader.loc("uModel"), Main.MAT_BUF.put(model.m).flip());
                    worldShader.setFloat(worldShader.loc("uTime"), 0);
                    worldShader.setFloat(worldShader.loc("uWave"), 0);
                    worldShader.setFloat(worldShader.loc("uSpecular"), 0);
                    worldShader.setFloat(worldShader.loc("uUnlit"), 0);
                    worldShader.setFloat(worldShader.loc("uFogStart"), opts.fog ? far * 0.55f : 1e9f);
                    worldShader.setFloat(worldShader.loc("uFogEnd"), opts.fog ? far * 0.98f : 2e9f);
                    terrain.setLightingUniforms(sky, opts);
                    worldShader.setVec4(worldShader.loc("uTint"), 1, 1, 1, 1);
                    GL11.glDisable(GL11.GL_BLEND);
                    planeModel.body.draw();
                    // prop + disc
                    Mat4 propM = Mat4.mul(model, Mat4.mul(
                            new Mat4().identity().translate(3.55f, 0.0f, 0), Mat4.rotateX(plane.propAngle())));
                    worldShader.setMat4(worldShader.loc("uModel"), Main.MAT_BUF.put(propM.m).flip());
                    planeModel.prop.draw();
                    if (plane.rpm > 900) {
                        Mat4 discM = Mat4.mul(model, new Mat4().identity().translate(3.66f, 0, 0));
                        worldShader.setMat4(worldShader.loc("uModel"), Main.MAT_BUF.put(discM.m).flip());
                        worldShader.setFloat(worldShader.loc("uUnlit"), 1);
                        GL11.glEnable(GL11.GL_BLEND);
                        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                        GL11.glDepthMask(false);
                        worldShader.setVec4(worldShader.loc("uTint"), 1, 1, 1, 0.22f);
                        planeModel.disc.draw();
                        worldShader.setVec4(worldShader.loc("uTint"), 1, 1, 1, 1);
                        GL11.glDepthMask(true);
                        GL11.glDisable(GL11.GL_BLEND);
                        worldShader.setFloat(worldShader.loc("uUnlit"), 0);
                    }
                }
            }
        }

        // ---------------- GUI ----------------
        float guiMx = input.mouseX / ui.scale, guiMy = input.mouseY / ui.scale;
        ui.begin(fbW, fbH, guiMx, guiMy);
        switch (state) {
            case STATE_TITLE: {
                String a = title.render(ui, input, opts, (float) worldTime);
                if (a != null) titleAction(a);
                break;
            }
            case STATE_OPTIONS: {
                ui.panelBackground();
                String a = optionsScreen.render(ui, input, opts, (float) worldTime);
                if (a != null) optionsAction(a);
                break;
            }
            case STATE_CONTROLS: {
                ui.panelBackground();
                String a = controlsScreen.render(ui, input, opts, (float) worldTime);
                if (a != null && a.equals("done")) {
                    state = optionsScreen != null && optionsScreen.fromPause ? STATE_PAUSE : STATE_TITLE;
                }
                break;
            }
            case STATE_LOADING: {
                ui.panelBackground();
                loading.render(ui, input, opts, (float) worldTime);
                break;
            }
            case STATE_PLAY: {
                if (!hideHud) hud.render(plane, opts, fps, sky, cockpitView, debug);
                if (plane != null && plane.crashed) {
                    ui.dim(0.45f);
                    crashScreen.render(ui, input, opts, (float) worldTime);
                    if (input.keyPressed(GLFW.GLFW_KEY_R) || input.keyPressed(GLFW.GLFW_KEY_ENTER)) {
                        plane.resetToRunway();
                        crashAudioPlayed = false;
                        camInit = false;
                        input.lockCursor(window);
                    }
                }
                break;
            }
            case STATE_PAUSE: {
                if (!hideHud) hud.render(plane, opts, fps, sky, cockpitView, debug);
                ui.dim(0.55f);
                String a = pause.render(ui, input, opts, (float) worldTime);
                if (a != null) pauseAction(a);
                break;
            }
        }
        ui.end();
    }

    private float[] camFwd = {0, 0, -1};

    private float[] viewFwd(Mat4 view) {
        return new float[]{-view.m[2], -view.m[6], -view.m[10]};
    }

    private final Mat4 camView = new Mat4();
    private final float[] camFwdVec = new float[3];

    /** Chase or cockpit camera; fills camView/camFwdVec, returns eye. */
    private float[] cameraEye(float dt) {
        if (plane == null) return new float[]{0, 200, 0};
        if (cockpitView) {
            Mat4 r = plane.bodyMatrix();
            // body offset (0.4 fwd, 0.55 up, 0 right) -> world: columns of r are body axes
            float[] eye = new float[]{
                    plane.x + r.m[0] * 0.4f + r.m[4] * 0.55f,
                    plane.y + r.m[1] * 0.4f + r.m[5] * 0.55f,
                    plane.z + r.m[2] * 0.4f + r.m[6] * 0.55f
            };
            float[] fwd = plane.fwd();
            float[] up = plane.up();
            camViewSet(Mat4.lookAt(eye[0], eye[1], eye[2],
                    eye[0] + fwd[0], eye[1] + fwd[1], eye[2] + fwd[2],
                    up[0], up[1], up[2]));
            camFwdVec[0] = fwd[0]; camFwdVec[1] = fwd[1]; camFwdVec[2] = fwd[2];
            return eye;
        }
        // chase: behind and above, smoothed, horizon-stable
        float[] fwd = plane.fwd();
        float dist = 17 + plane.speed() * 0.055f;
        float tx = plane.x - fwd[0] * dist;
        float tz = plane.z - fwd[2] * dist;
        float ty = plane.y + 5.5f + fwd[1] * 4;
        if (!camInit) {
            camX = tx; camY = ty; camZ = tz;
            camInit = true;
        }
        float k = 1 - (float) Math.exp(-6 * dt);
        camX += (tx - camX) * k;
        camY += (ty - camY) * k;
        camZ += (tz - camZ) * k;
        float ground = terrain.height(camX, camZ) + 2.5f;
        if (camY < ground) camY = ground;
        float lx = plane.x + fwd[0] * 12;
        float ly = plane.y + fwd[1] * 12;
        float lz = plane.z + fwd[2] * 12;
        camViewSet(Mat4.lookAt(camX, camY, camZ, lx, ly, lz, 0, 1, 0));
        camFwdVec[0] = (lx - camX); camFwdVec[1] = (ly - camY); camFwdVec[2] = (lz - camZ);
        float fl = (float) Math.sqrt(camFwdVec[0] * camFwdVec[0] + camFwdVec[1] * camFwdVec[1] + camFwdVec[2] * camFwdVec[2]);
        camFwdVec[0] /= fl; camFwdVec[1] /= fl; camFwdVec[2] /= fl;
        return new float[]{camX, camY, camZ};
    }

    private void camViewSet(Mat4 m) {
        System.arraycopy(m.m, 0, camView.m, 0, 16);
    }

    // ------------------------------------------------------------- UI actions
    private void titleAction(String a) {
        switch (a) {
            case "start":
            case "airstart":
                newFlight(a.equals("airstart"));
                loading.progress = 0;
                state = STATE_LOADING;
                audio.click();
                break;
            case "options":
                optionsScreen = new Screens.OptionsScreen(false, opts);
                state = STATE_OPTIONS;
                audio.click();
                break;
            case "controls":
                state = STATE_CONTROLS;
                audio.click();
                break;
            case "quit":
                GLFW.glfwSetWindowShouldClose(window, true);
                break;
        }
    }

    private void optionsAction(String a) {
        switch (a) {
            case "done":
                opts.save();
                state = optionsScreen.fromPause ? STATE_PAUSE : STATE_TITLE;
                audio.click();
                break;
            case "controls":
                state = STATE_CONTROLS;
                audio.click();
                break;
            case "music":
                opts.music = !opts.music;
                audio.musicVol = opts.music ? 1f : 0f;
                audio.click();
                break;
            case "fog":
                opts.fog = !opts.fog;
                audio.click();
                break;
            case "clouds":
                opts.clouds = !opts.clouds;
                audio.click();
                break;
            case "inverty":
                opts.invertY = !opts.invertY;
                audio.click();
                break;
        }
        audio.master = opts.masterVolume;
    }

    private void pauseAction(String a) {
        switch (a) {
            case "back":
                state = STATE_PLAY;
                input.lockCursor(window);
                audio.click();
                break;
            case "restart":
                plane.resetToRunway();
                crashAudioPlayed = false;
                camInit = false;
                state = STATE_PLAY;
                input.lockCursor(window);
                audio.click();
                break;
            case "airstart":
                plane.resetAirStart();
                crashAudioPlayed = false;
                camInit = false;
                state = STATE_PLAY;
                input.lockCursor(window);
                audio.click();
                break;
            case "options":
                optionsScreen = new Screens.OptionsScreen(true, opts);
                state = STATE_OPTIONS;
                audio.click();
                break;
            case "quit":
                opts.save();
                state = STATE_TITLE;
                audio.click();
                break;
        }
    }

    // -------------------------------------------------------------- utilities
    private void screenshot() {
        java.nio.ByteBuffer buf = BufferUtils.createByteBuffer(fbW * fbH * 4);
        GL11.glReadPixels(0, 0, fbW, fbH, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
        BufferedImage img = new BufferedImage(fbW, fbH, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < fbH; y++) {
            for (int x = 0; x < fbW; x++) {
                int i = ((fbH - 1 - y) * fbW + x) * 4;
                int r = buf.get(i) & 0xFF, g = buf.get(i + 1) & 0xFF, b = buf.get(i + 2) & 0xFF;
                img.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        try {
            File dir = new File("screenshots");
            if (!dir.exists()) dir.mkdirs();
            File out = new File(dir, "screenshot_" + System.currentTimeMillis() + ".png");
            ImageIO.write(img, "png", out);
            System.out.println("[screenshot] saved " + out.getAbsolutePath());
        } catch (Exception e) {
            System.out.println("[screenshot] failed: " + e);
        }
    }

    private void autotestTick() {
        if (!autotest) return;
        autoFrame++;
        if (autoFrame % 30 == 0 && plane != null) {
            System.err.println("[autotest] frame=" + autoFrame + " state=" + state
                    + " spd=" + String.format("%.2f", plane.speed())
                    + " alt=" + String.format("%.0f", plane.y)
                    + " agl=" + String.format("%.2f", plane.agl())
                    + " thr=" + String.format("%.2f", plane.throttle)
                    + " pitch=" + String.format("%.3f", plane.pitch)
                    + " aoa=" + String.format("%.3f", plane.aoa)
                    + " vy=" + String.format("%.2f", plane.vy)
                    + " mdy=" + String.format("%.0f", input.mouseDY)
                    + " air=" + (!plane.onGround && !plane.crashed)
                    + " tiles=" + terrain.tileCount() + " fps=" + fps);
        } else if (autoFrame % 30 == 0) {
            System.err.println("[autotest] frame=" + autoFrame + " state=" + state
                    + " tiles=" + (terrain != null ? terrain.tileCount() : 0) + " fps=" + fps);
        }
        if (plane != null && !plane.onGround && !plane.crashed && plane.agl() > 12) sawAirborne = true;

        if (autoFrame == 30) {
            System.err.println("[autotest] title screenshot, dayNorm=" + sky.dayNorm);
            screenshot();
        }
        if (autoFrame == 40) {
            System.out.println("[autotest] clicking Start Flight");
            newFlight(false);
            state = STATE_LOADING;
        }
        if (state == STATE_PLAY && playStartFrame < 0) {
            playStartFrame = autoFrame;
            pauseFrame = autoFrame + 560; // generous budget for the climb on slow renderers
        }
        if (state == STATE_PLAY && !throttleSet && autoFrame >= playStartFrame + 20) {
            throttleSet = true;
            plane.throttle = 1.0f;
            System.out.println("[autotest] full throttle");
        }
        if (state == STATE_PLAY && autoFrame == playStartFrame + 8) {
            System.err.println("[autotest] parked screenshot (stationary; ALT must read 0)");
            screenshot();
        }
        if (state == STATE_PLAY && autoFrame == playStartFrame + 90) {
            System.err.println("[autotest] runway screenshot");
            screenshot();
        }
        // pull the yoke while climbing out (injected through the real input path)
        // 6 px/frame ~= 360 px/s: a realistic smooth pull, HELD through rotation
        if (state == STATE_PLAY && plane != null && throttleSet && !plane.crashed
                && plane.agl() < 60) {
            input.mouseDY += 6; // stick back -> elevator up next update
        }
        if (sawAirborne && !climbSet) {
            climbSet = true;
            airborneFrame = autoFrame; // full throttle + auto-level = gentle cruise climb
        }
        if (sawAirborne && autoFrame == airborneFrame + 180) {
            System.err.println("[autotest] climbing screenshot, alt=" + plane.y);
            screenshot();
        }
        if (sawAirborne && autoFrame == airborneFrame + 200) {
            cockpitView = true;
        }
        if (sawAirborne && autoFrame == airborneFrame + 220) {
            System.err.println("[autotest] cockpit screenshot");
            screenshot();
        }
        if (autoFrame == pauseFrame) {
            cockpitView = false;
            state = STATE_PAUSE;
            input.unlockCursor(window);
        }
        if (autoFrame == pauseFrame + 20) {
            System.err.println("[autotest] pause screenshot");
            screenshot();
        }
        if (autoFrame == pauseFrame + 30) {
            optionsScreen = new Screens.OptionsScreen(true, opts);
            state = STATE_OPTIONS;
        }
        if (autoFrame == pauseFrame + 50) {
            System.err.println("[autotest] options screenshot");
            screenshot();
        }
        if (autoFrame == pauseFrame + 70) {
            System.out.println("[autotest] done, airborne=" + sawAirborne + ", exiting");
            GLFW.glfwSetWindowShouldClose(window, true);
        }
    }

    private boolean throttleSet, climbSet;
    private int airborneFrame = -1;
    private int playStartFrame = -1;
    private int pauseFrame = 100000;

    private void dispose() {
        audio.stop();
        if (terrain != null) terrain.dispose();
        clouds.dispose();
        GLFW.glfwDestroyWindow(window);
        GLFW.glfwTerminate();
    }
}
