package skytrainer;

import org.lwjgl.opengl.GL20;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import org.lwjgl.BufferUtils;

/** Thin shader program wrapper. */
public final class Shader {
    private final int program;

    public Shader(String vsSrc, String fsSrc) {
        int vs = compile(GL20.GL_VERTEX_SHADER, vsSrc);
        int fs = compile(GL20.GL_FRAGMENT_SHADER, fsSrc);
        program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vs);
        GL20.glAttachShader(program, fs);
        GL20.glLinkProgram(program);
        IntBuffer ok = BufferUtils.createIntBuffer(1);
        GL20.glGetProgramiv(program, GL20.GL_LINK_STATUS, ok);
        if (ok.get(0) == 0) {
            String log = GL20.glGetProgramInfoLog(program);
            throw new IllegalStateException("Shader link failed:\n" + log);
        }
        GL20.glDetachShader(program, vs);
        GL20.glDetachShader(program, fs);
        GL20.glDeleteShader(vs);
        GL20.glDeleteShader(fs);
    }

    private static int compile(int type, String src) {
        int sh = GL20.glCreateShader(type);
        GL20.glShaderSource(sh, src);
        GL20.glCompileShader(sh);
        IntBuffer ok = BufferUtils.createIntBuffer(1);
        GL20.glGetShaderiv(sh, GL20.GL_COMPILE_STATUS, ok);
        if (ok.get(0) == 0) {
            String log = GL20.glGetShaderInfoLog(sh);
            throw new IllegalStateException("Shader compile failed (" + (type == GL20.GL_VERTEX_SHADER ? "vertex" : "fragment") + "):\n" + log + "\n---\n" + src);
        }
        return sh;
    }

    public void use() { GL20.glUseProgram(program); }

    /** GL program id, for raw glUniform calls on hot paths. */
    public int id() { return program; }

    public int loc(String name) { return GL20.glGetUniformLocation(program, name); }

    public void setMat4(int loc, FloatBuffer mat) {
        if (loc >= 0) GL20.glUniformMatrix4fv(loc, false, mat);
    }

    public void setFloat(int loc, float v) { if (loc >= 0) GL20.glUniform1f(loc, v); }

    public void setInt(int loc, int v) { if (loc >= 0) GL20.glUniform1i(loc, v); }

    public void setVec2(int loc, float x, float y) { if (loc >= 0) GL20.glUniform2f(loc, x, y); }

    public void setVec3(int loc, float x, float y, float z) { if (loc >= 0) GL20.glUniform3f(loc, x, y, z); }

    public void setVec4(int loc, float x, float y, float z, float w) { if (loc >= 0) GL20.glUniform4f(loc, x, y, z, w); }

    // =====================================================================
    // GUI pipeline: pos3/uv2/col3, texture * color * tint, distance fog off.
    // =====================================================================
    public static final String GUI_VERTEX =
            "#version 150\n" +
            "in vec3 aPos;\n" +
            "in vec2 aUV;\n" +
            "in vec3 aColor;\n" +
            "uniform mat4 uProj;\n" +
            "uniform mat4 uView;\n" +
            "out vec2 vUV;\n" +
            "out vec3 vColor;\n" +
            "void main() {\n" +
            "    gl_Position = uProj * (uView * vec4(aPos, 1.0));\n" +
            "    vUV = aUV;\n" +
            "    vColor = aColor;\n" +
            "}\n";

    public static final String GUI_FRAGMENT =
            "#version 150\n" +
            "uniform sampler2D uTex;\n" +
            "uniform vec4 uTint;\n" +
            "in vec2 vUV;\n" +
            "in vec3 vColor;\n" +
            "out vec4 fragColor;\n" +
            "void main() {\n" +
            "    vec4 tex = texture(uTex, vUV);\n" +
            "    vec4 c = tex * vec4(vColor, 1.0) * uTint;\n" +
            "    if (c.a < 0.02) discard;\n" +
            "    fragColor = c;\n" +
            "}\n";

    // =====================================================================
    // World pipeline: pos3/normal3/uv2/col3, sun + hemispheric ambient,
    // distance fog, optional wave displacement for water.
    // =====================================================================
    public static final String WORLD_VERTEX =
            "#version 150\n" +
            "in vec3 aPos;\n" +
            "in vec3 aNormal;\n" +
            "in vec2 aUV;\n" +
            "in vec3 aColor;\n" +
            "uniform mat4 uProj;\n" +
            "uniform mat4 uView;\n" +
            "uniform mat4 uModel;\n" +
            "uniform float uTime;\n" +
            "uniform float uWave;\n" +       // 1 = animate as water surface
            "out vec3 vNormal;\n" +
            "out vec2 vUV;\n" +
            "out vec3 vColor;\n" +
            "out float vDist;\n" +
            "out vec3 vWorld;\n" +
            "void main() {\n" +
            "    vec4 world = uModel * vec4(aPos, 1.0);\n" +
            "    if (uWave > 0.5) {\n" +
            "        world.y += sin(uTime * 1.1 + world.x * 0.045) * 0.35\n" +
            "                 + sin(uTime * 1.7 + world.z * 0.06) * 0.28;\n" +
            "    }\n" +
            "    vec4 viewPos = uView * world;\n" +
            "    vDist = length(viewPos.xyz);\n" +
            "    gl_Position = uProj * viewPos;\n" +
            "    vNormal = normalize(mat3(uModel) * aNormal);\n" +
            "    vUV = aUV;\n" +
            "    vColor = aColor;\n" +
            "    vWorld = world.xyz;\n" +
            "}\n";

    public static final String WORLD_FRAGMENT =
            "#version 150\n" +
            "uniform sampler2D uTex;\n" +
            "uniform vec3 uSunDir;\n" +      // pointing toward the sun
            "uniform vec3 uSunColor;\n" +
            "uniform vec3 uAmbTop;\n" +
            "uniform vec3 uAmbBot;\n" +
            "uniform vec3 uFogColor;\n" +
            "uniform float uFogStart;\n" +
            "uniform float uFogEnd;\n" +
            "uniform vec4 uTint;\n" +
            "uniform float uUnlit;\n" +      // 1 = skip lighting (sky/prop disc/gauges)
            "uniform float uSpecular;\n" +   // water sun glint strength
            "uniform vec3 uCamPos;\n" +
            "in vec3 vNormal;\n" +
            "in vec2 vUV;\n" +
            "in vec3 vColor;\n" +
            "in float vDist;\n" +
            "in vec3 vWorld;\n" +
            "out vec4 fragColor;\n" +
            "void main() {\n" +
            "    vec4 tex = texture(uTex, vUV);\n" +
            "    vec3 base = tex.rgb * vColor;\n" +
            "    vec3 lit;\n" +
            "    if (uUnlit > 0.5) {\n" +
            "        lit = base;\n" +
            "    } else {\n" +
            "        vec3 n = normalize(vNormal);\n" +
            "        // soft (wrapped) terminator: slopes whose normal sits near\n" +
            "        // perpendicular to the sun otherwise flicker per-vertex between\n" +
            "        // warm sunlit and blue-ambient shading — dark mottling on far hills\n" +
            "        float ndl = smoothstep(-0.08, 0.30, dot(n, uSunDir));\n" +
            "        vec3 amb = mix(uAmbBot, uAmbTop, n.y * 0.5 + 0.5);\n" +
            "        lit = base * (amb + uSunColor * ndl);\n" +
            "        // soft shoulder above 0.75: distant micro-triangles that straddle\n" +
            "        // the old hard clip at 1.0 popped to pure white (255) next to their\n" +
            "        // 240-ish neighbors - the \"see-through pinholes\" on far mountains.\n" +
            "        vec3 over = max(lit - vec3(0.75), vec3(0.0));\n" +
            "        lit = lit - over + over / (1.0 + over * 2.4);\n" +
            "        if (uSpecular > 0.001) {\n" +
            "            vec3 vdir = normalize(uCamPos - vWorld);\n" +
            "            vec3 h = normalize(vdir + uSunDir);\n" +
            "            float spec = pow(max(dot(n, h), 0.0), 90.0) * uSpecular;\n" +
            "            lit += uSunColor * spec;\n" +
            "        }\n" +
            "    }\n" +
            "    vec4 c = vec4(lit, tex.a * uTint.a);\n" +
            "    if (c.a < 0.02) discard;\n" +
            "    float fog = clamp((vDist - uFogStart) / max(uFogEnd - uFogStart, 0.001), 0.0, 1.0);\n" +
            "    fog = fog * fog * (3.0 - 2.0 * fog);\n" +
            "    c.rgb = mix(c.rgb, uFogColor, fog * uTint.a);\n" +
            "    c.rgb *= uTint.rgb;\n" +
            "    fragColor = c;\n" +
            "}\n";

    // =====================================================================
    // Sky pipeline: fullscreen triangle; computes ray from camera basis.
    // =====================================================================
    public static final String SKY_VERTEX =
            "#version 150\n" +
            "in vec2 aPos;\n" +
            "out vec2 vNdc;\n" +
            "void main() {\n" +
            "    vNdc = aPos;\n" +
            "    gl_Position = vec4(aPos, 0.9999, 1.0);\n" +
            "}\n";

    public static final String SKY_FRAGMENT =
            "#version 150\n" +
            "uniform vec3 uCamFwd, uCamRight, uCamUp;\n" +
            "uniform vec2 uTanHalfFov;\n" +
            "uniform vec3 uSunDir;\n" +
            "uniform vec3 uZenith;\n" +
            "uniform vec3 uHorizon;\n" +
            "uniform vec3 uSunTint;\n" +
            "uniform float uNight;\n" +      // 0 day .. 1 night (stars)
            "uniform float uAspect;\n" +
            "in vec2 vNdc;\n" +
            "out vec4 fragColor;\n" +
            "\n" +
            "float hash(vec3 p) {\n" +
            "    p = fract(p * 0.3183099 + vec3(0.1, 0.2, 0.3));\n" +
            "    p *= 17.0;\n" +
            "    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));\n" +
            "}\n" +
            "\n" +
            "void main() {\n" +
            "    vec3 ray = normalize(uCamFwd + uCamRight * vNdc.x * uTanHalfFov.x\n" +
            "                              + uCamUp   * vNdc.y * uTanHalfFov.y);\n" +
            "    float t = clamp(ray.y * 2.0 + 0.18, 0.0, 1.0);\n" +
            "    t = t * t * (3.0 - 2.0 * t);\n" +
            "    vec3 col = mix(uHorizon, uZenith, t);\n" +
            "    // sun disc + glow\n" +
            "    float sd = max(dot(ray, uSunDir), 0.0);\n" +
            "    col += uSunTint * (pow(sd, 900.0) * 1.4 + pow(sd, 18.0) * 0.22);\n" +
            "    // stars (night only), dim near horizon\n" +
            "    if (uNight > 0.01 && ray.y > 0.02) {\n" +
            "        vec3 cell = floor(ray * 220.0);\n" +
            "        float h = hash(cell);\n" +
            "        float star = step(0.9985, h) * uNight;\n" +
            "        star *= 0.5 + 0.5 * sin(h * 90.0);\n" +
            "        col += vec3(star);\n" +
            "    }\n" +
            "    fragColor = vec4(col, 1.0);\n" +
            "}\n";
}
