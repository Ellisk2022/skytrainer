package skytrainer;

import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Set;

/** Keyboard and mouse state collected via GLFW callbacks. */
public final class Input {
    public final Set<Integer> down = new HashSet<>();
    public final Set<Integer> pressed = new HashSet<>();
    public final Set<Integer> mouseDown = new HashSet<>();
    public final Set<Integer> mousePressed = new HashSet<>();
    public float mouseDX, mouseDY;   // accumulated since last frame (already scale-corrected)
    public float scroll;             // accumulated wheel steps since last frame
    public float mouseX, mouseY;     // window coords
    public float scale = 1f;         // framebuffer / window scale for HiDPI
    public boolean rawMouseSupported;
    public boolean cursorLocked;

    public void endFrame() {
        pressed.clear();
        mousePressed.clear();
        mouseDX = 0;
        mouseDY = 0;
        scroll = 0;
    }

    public boolean key(int k) { return down.contains(k); }

    public boolean keyPressed(int k) { return pressed.contains(k); }

    public boolean mousePressed(int b) { return mousePressed.contains(b); }

    public boolean mouseDown(int b) { return mouseDown.contains(b); }

    public void onKey(int key, int action) {
        if (key < 0) return;
        if (action == GLFW.GLFW_PRESS) {
            down.add(key);
            pressed.add(key);
        } else if (action == GLFW.GLFW_RELEASE) {
            down.remove(key);
        }
    }

    public void onMouseButton(int button, int action) {
        if (action == GLFW.GLFW_PRESS) {
            mouseDown.add(button);
            mousePressed.add(button);
        } else if (action == GLFW.GLFW_RELEASE) {
            mouseDown.remove(button);
        }
    }

    public void onMousePos(double x, double y) {
        float nx = (float) x * scale, ny = (float) y * scale;
        if (cursorLocked) {
            mouseDX += nx - mouseX;
            mouseDY += ny - mouseY;
        }
        mouseX = nx;
        mouseY = ny;
    }

    public void onScroll(double yoff) { scroll += (float) yoff; }

    public void lockCursor(long window) {
        GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
        if (rawMouseSupported) GLFW.glfwSetInputMode(window, GLFW.GLFW_RAW_MOUSE_MOTION, GLFW.GLFW_TRUE);
        cursorLocked = true;
    }

    public void unlockCursor(long window) {
        GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        cursorLocked = false;
    }
}
