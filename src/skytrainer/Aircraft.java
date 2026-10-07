package skytrainer;

/**
 * Casual-sim flight model: thrust, lift with stall, drag, gravity, sideslip damping,
 * ground roll with steering and brakes, takeoff rotation, landing/crash rules.
 * Body axes: +X nose, +Y up, +Z right. Yaw about Y (positive = turn left), pitch about Z
 * (positive = nose up), roll about X (positive = left wing down... resolved by signs below).
 */
public final class Aircraft {
    public float x, y, z;            // CG position (meters)
    public float vx, vy, vz;         // world velocity
    public float yaw, pitch, roll;   // radians
    public float throttle = 0f;      // 0..1
    public boolean brake;
    // persistent virtual stick (-1..1): mouse deltas accumulate here and the
    // spring re-centers it, so smooth human pulls build real elevator authority
    public float stickPitch, stickRoll;

    public boolean onGround = true;
    public boolean stalled = false;
    public boolean crashed = false;
    public float crashTimer;

    // derived, exposed for HUD/audio
    public float aoa;                // rad
    public float rpm = 700;
    public float gLoad = 1;

    public static final float MASS = 750f;
    public static final float WING_S = 16.2f;
    public static final float RHO = 1.225f;
    public static final float G = 9.81f;
    public static final float STALL_AOA = (float) Math.toRadians(16);
    public static final float ROTATE_SPEED = 27f;   // m/s ~= 52 kt

    private final Terrain terrain;
    private float propAngle;
    private float engineRpmSmooth = 700;

    // scratch body axes
    private final float[] fwd = new float[3], up = new float[3], right = new float[3];

    public Aircraft(Terrain terrain, boolean onRunwayStart) {
        this.terrain = terrain;
        resetToRunway();
    }

    public void resetToRunway() {
        x = Terrain.RUNWAY_X0 + 80; z = 0; y = Terrain.RUNWAY_H + PlaneModel.GEAR_H;
        vx = vy = vz = 0;
        yaw = 0; pitch = 0; roll = 0;
        throttle = 0;
        stickPitch = 0; stickRoll = 0;
        onGround = true; stalled = false; crashed = false; crashTimer = 0;
    }

    public void resetAirStart() {
        x = Terrain.RUNWAY_X0 + 200; z = 0;
        y = 320;                       // ~1000 ft over the field
        vx = 55; vy = 0; vz = 0;
        yaw = 0; pitch = 0; roll = 0;
        throttle = 0.65f;
        stickPitch = 0; stickRoll = 0;
        onGround = false; stalled = false; crashed = false; crashTimer = 0;
    }

    // ------------------------------------------------------------- axes
    private void computeAxes() {
        float cy = (float) Math.cos(yaw), sy = (float) Math.sin(yaw);
        float cp = (float) Math.cos(pitch), sp = (float) Math.sin(pitch);
        float cr = (float) Math.cos(roll), sr = (float) Math.sin(roll);
        // R = Ry(yaw) * Rx(roll) * Rz(pitch); fwd = R*(1,0,0), up = R*(0,1,0), right = R*(0,0,1)
        // roll about X: (1,0,0)->(1,0,0); (0,1,0)->(0,cr,sr); (0,0,1)->(0,-sr,cr)
        // pitch about Z: (1,0,0)->(cp,sp,0); (0,1,0)->(-sp,cp,0); (0,0,1)->(0,0,1)
        // compose pitch then roll (body): local fwd after pitch+roll = (cp, sp, 0) -> roll -> (cp, sp*cr, sp*sr)
        float fx = cp, fy = sp * cr, fz = sp * sr;
        // then yaw: (x,y,z) -> (x*cy + z*sy, y, -x*sy + z*cy)
        fwd[0] = fx * cy + fz * sy; fwd[1] = fy; fwd[2] = -fx * sy + fz * cy;
        // local up = (-sp, cp*cr, cp*sr) -> yaw
        float ux = -sp, uy = cp * cr, uz = cp * sr;
        up[0] = ux * cy + uz * sy; up[1] = uy; up[2] = -ux * sy + uz * cy;
        // local right = (0, -sr, cr) -> yaw
        float rx2 = 0, ry2 = -sr, rz2 = cr;
        right[0] = rx2 * cy + rz2 * sy; right[1] = ry2; right[2] = -rx2 * sy + rz2 * cy;
    }

    public float[] fwd() { computeAxes(); return new float[]{fwd[0], fwd[1], fwd[2]}; }
    public float[] up() { computeAxes(); return new float[]{up[0], up[1], up[2]}; }

    public float speed() { return (float) Math.sqrt(vx * vx + vy * vy + vz * vz); }

    public float agl() { return y - terrain.height(x, z); }

    public float headingDeg() {
        computeAxes();
        float h = (float) Math.toDegrees(Math.atan2(fwd[0], -fwd[2]));
        return (h + 360) % 360;
    }

    public float speedKt() { return speed() * 1.94384f; }

    public float altFt() { return y * 3.28084f; }

    /** QFE altimeter: height above the airfield datum (reads 0 FT on the runway). */
    public float altFieldFt() {
        if (onGround) return 0f;
        return Math.max((y - Terrain.RUNWAY_H) * 3.28084f, 0f);
    }

    public float vsiFpm() { return vy * 196.85f; }

    // ------------------------------------------------------------- update
    public void update(Input in, Options opts, float dt) {
        if (crashed) {
            crashTimer += dt;
            // slide to a stop
            speedDecay(dt, 6f);
            vx *= (float) Math.exp(-2.2 * dt);
            vz *= (float) Math.exp(-2.2 * dt);
            vy = 0;
            float th = terrain.height(x, z);
            if (y > th + 0.6f) y = Math.max(th + 0.6f, y - 20 * dt);
            x += vx * dt; z += vz * dt;
            return;
        }

        computeAxes();

        // ---- controls
        // Per-frame mouse deltas are tiny at human pull speeds, so they are
        // accumulated into a persistent spring-centered stick instead of being
        // used directly. Holding a pull = holding the yoke back.
        float sens = 0.0026f * opts.sensitivity;
        float mx = in.cursorLocked ? in.mouseDX * sens : 0;
        float my = in.cursorLocked ? in.mouseDY * sens : 0;
        float centerP = Math.abs(my) > 1e-4f ? 0.5f : 3.0f;  // gentle recenter while pulling
        stickPitch -= stickPitch * Math.min(1, centerP * dt);
        stickPitch = clamp(stickPitch + my * 2.2f, -1, 1);
        float centerR = Math.abs(mx) > 1e-4f ? 0.5f : 3.0f;
        stickRoll -= stickRoll * Math.min(1, centerR * dt);
        stickRoll = clamp(stickRoll + mx * 2.2f, -1, 1);
        float ail = clamp(stickRoll, -1, 1);
        float elev = clamp(opts.invertY ? -stickPitch : stickPitch, -1, 1);
        float rud = 0;
        if (in.key(org.lwjgl.glfw.GLFW.GLFW_KEY_A)) rud += 1;
        if (in.key(org.lwjgl.glfw.GLFW.GLFW_KEY_D)) rud -= 1;
        if (in.key(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT)) ail -= 0.8f;
        if (in.key(org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT)) ail += 0.8f;
        if (in.key(org.lwjgl.glfw.GLFW.GLFW_KEY_UP)) elev += 0.8f;
        if (in.key(org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN)) elev -= 0.8f;
        ail = clamp(ail, -1, 1);
        elev = clamp(elev, -1, 1);
        if (in.key(org.lwjgl.glfw.GLFW.GLFW_KEY_W)) throttle += 0.55f * dt;
        if (in.key(org.lwjgl.glfw.GLFW.GLFW_KEY_S)) throttle -= 0.55f * dt;
        throttle = clamp(throttle, 0, 1);
        brake = in.key(org.lwjgl.glfw.GLFW.GLFW_KEY_B) || in.key(org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE);

        float speed = speed();
        float cf = clamp(speed / 45f, 0.12f, 1.15f);   // control effectiveness

        if (onGround) {
            // steering with rudder, scaled by speed
            float steer = rud * clamp(speed / 22f, 0f, 1.4f) * 0.55f;
            yaw += steer * dt;
            // wings level on the ground
            roll -= roll * Math.min(1, 6 * dt);
        } else {
            // air: rates with damping, mild auto-level for casual flying
            roll += ail * 2.1f * cf * dt;
            pitch += elev * 1.05f * cf * dt;
            yaw += rud * 0.42f * cf * dt;
            if (Math.abs(ail) < 0.04f) {
                float maxBank = (float) Math.toRadians(38);
                if (Math.abs(roll) < maxBank) roll -= roll * Math.min(1, 0.55f * dt);
            }
            // pitch stability: auto-level — hold the AoA that sustains lift at current speed
            float qNow = 0.5f * 1.225f * Math.max(speed * speed, 1f) * 16.2f;
            float clNeed = clamp(750f * 9.81f / qNow, 0.02f, 0.75f);
            float aoaTarget = clNeed / 5.2f;
            pitch += -(aoa - aoaTarget) * 1.5f * dt;
            // stall: nose drop + wing drop
            if (stalled) {
                pitch -= 0.55f * dt;
                roll += Math.sin(yaw * 7f) * 0.25f * dt;
            }
            pitch = clamp(pitch, -(float) Math.PI / 2 + 0.05f, (float) Math.PI / 2 - 0.05f);
            if (roll > Math.PI) roll -= 2 * (float) Math.PI;
            if (roll < -Math.PI) roll += 2 * (float) Math.PI;
            yaw = (float) ((yaw + Math.PI) % (2 * Math.PI) - Math.PI);
        }

        computeAxes();

        // ---- aerodynamics
        float vfwd = vx * fwd[0] + vy * fwd[1] + vz * fwd[2];
        float vup = vx * up[0] + vy * up[1] + vz * up[2];
        float vright = vx * right[0] + vy * right[1] + vz * right[2];
        float vSq = speed * speed;
        aoa = speed > 2 ? (float) Math.atan2(-vup, Math.max(vfwd, 4f)) : 0;
        stalled = !onGround && speed > 4 && aoa > STALL_AOA * 1.05f;

        float cl;
        if (aoa < STALL_AOA) cl = 5.2f * aoa;
        else cl = 1.45f * Math.max(0.35f, 1f - (aoa - STALL_AOA) * 3.2f);
        cl = clamp(cl, -0.7f, 1.45f);

        float q = 0.5f * RHO * Math.max(vSq, 0);
        float lift = q * WING_S * cl;
        float cd = 0.028f + 0.052f * cl * cl;
        float drag = q * WING_S * cd;
        float thrust = throttle * 3200f * (1f - 0.45f * Math.min(speed / 90f, 1f));

        float fx = fwd[0] * thrust - vx / Math.max(speed, 0.5f) * drag;
        float fy = fwd[1] * thrust - vy / Math.max(speed, 0.5f) * drag;
        float fz = fwd[2] * thrust - vz / Math.max(speed, 0.5f) * drag;
        // lift along body up
        fx += up[0] * lift; fy += up[1] * lift; fz += up[2] * lift;
        // gravity
        fy -= MASS * G;
        // sideslip damping (fuselage resisting sideways flow)
        float sideDecay = Math.min(speed * 0.05f, 1.2f);
        fx -= right[0] * vright * MASS * sideDecay * 0.55f;
        fy -= right[1] * vright * MASS * sideDecay * 0.55f;
        fz -= right[2] * vright * MASS * sideDecay * 0.55f;

        vx += fx / MASS * dt;
        vy += fy / MASS * dt;
        vz += fz / MASS * dt;

        // ---- ground interaction
        float th = terrain.height(x, z);
        float gearY = y - PlaneModel.GEAR_H;
        boolean overWater = th < 0.5f;
        float contactH = overWater ? 0f : th;
        if (gearY <= contactH + 0.05f) {
            float slope = terrain.slopeAt(x, z);
            float vs = -vy;
            boolean hardHit = vs > 6.5f;
            boolean badAttitude = Math.abs(roll) > (float) Math.toRadians(16)
                    || pitch < (float) Math.toRadians(-7) || pitch > (float) Math.toRadians(18);
            boolean rough = !terrain.onRunway(x, z) && (overWater || slope > 0.30f || th > 260f);
            if (hardHit || badAttitude || rough) {
                crash();
                return;
            }
            if (vy > 0.5f) {
                // already lifting off this frame - release instead of clamping back down
                onGround = false;
            } else {
                // touchdown / rolling
                y = contactH + PlaneModel.GEAR_H;
                if (vy < 0) vy = 0;
                onGround = true;
                // rolling friction + brakes
                float fr = 0.018f * G * dt + (brake ? 2.8f * dt : 0);
                float sp = speed;
                if (sp > 0.1f) {
                    float f = Math.max(0, sp - fr) / sp;
                    vx *= f; vz *= f;
                }
            }
            if (onGround && speed > ROTATE_SPEED - 3f && elev > 0.05f) {
                pitch += elev * 0.7f * dt;
                // 16 deg: enough attitude for a decisive liftoff just below Vr
                pitch = Math.min(pitch, (float) Math.toRadians(16));
            } else if (onGround) {
                pitch -= pitch * Math.min(1, 4 * dt);
                if (brake) pitch -= pitch * 2;
            }
        } else {
            onGround = false;
        }
        // terrain proximity crash (nose/belly into slope while flying fast)
        if (!onGround && agl() < 0.4f && speed > 25) {
            crash();
            return;
        }

        x += vx * dt;
        y += vy * dt;
        z += vz * dt;

        // keep out of the underground hard (safety net)
        float th2 = terrain.height(x, z);
        if (y < th2 + 0.5f && th2 > 0.5f) {
            y = th2 + 0.5f;
            if (speed() > 30) { crash(); return; }
            vy = Math.max(vy, 0);
        }

        // audio state
        float targetRpm = 700 + throttle * 2000 + speed * 2;
        engineRpmSmooth += (targetRpm - engineRpmSmooth) * Math.min(1, dt * 2.2f);
        rpm = engineRpmSmooth;
        propAngle += rpm / 60f * 2f * (float) Math.PI * dt;
        gLoad = 1;
    }

    private float pitchTrimErr() {
        return (aoa - (float) Math.toRadians(1.7)) * 2f;
    }

    private void speedDecay(float dt, float rate) {
        float s = speed();
        if (s < 0.01f) return;
        float dec = rate * dt;
        float f = Math.max(0, s - dec) / s;
        vx *= f; vz *= f;
    }

    private void crash() {
        crashed = true;
        stalled = false;
        crashTimer = 0;
        rpm = 0;
    }

    public float propAngle() { return propAngle; }

    /** Menu/title-screen helper: advance the prop spinner without running physics. */
    public void spinProp(float dt, float targetRpm) {
        engineRpmSmooth += (targetRpm - engineRpmSmooth) * Math.min(1, dt * 2.2f);
        rpm = engineRpmSmooth;
        propAngle += rpm / 60f * 2f * (float) Math.PI * dt;
        if (propAngle > 1e6f) propAngle -= 1e6f;
    }

    public Mat4 bodyMatrix() {
        computeAxes();
        Mat4 r = Mat4.mul(Mat4.rotateY(yaw), Mat4.mul(Mat4.rotateX(roll), Mat4.rotateZ(pitch)));
        return r;
    }

    private static float clamp(float v, float a, float b) { return v < a ? a : (v > b ? b : v); }
}
