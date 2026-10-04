package com.eventmods.splitpad;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraftforge.fml.loading.FMLPaths;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWGamepadState;
import org.lwjgl.system.MemoryUtil;

/** Polls one GLFW gamepad (PlayStation, Xbox, ...). Must only be used on the render thread. */
public final class Gamepad {
    public enum Button {
        CROSS(GLFW.GLFW_GAMEPAD_BUTTON_A, "A"),
        CIRCLE(GLFW.GLFW_GAMEPAD_BUTTON_B, "B"),
        SQUARE(GLFW.GLFW_GAMEPAD_BUTTON_X, "X"),
        TRIANGLE(GLFW.GLFW_GAMEPAD_BUTTON_Y, "Y"),
        L1(GLFW.GLFW_GAMEPAD_BUTTON_LEFT_BUMPER, "LB"),
        R1(GLFW.GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER, "RB"),
        SHARE(GLFW.GLFW_GAMEPAD_BUTTON_BACK, "BACK"),
        OPTIONS(GLFW.GLFW_GAMEPAD_BUTTON_START, "START"),
        PS(GLFW.GLFW_GAMEPAD_BUTTON_GUIDE, "GUIDE"),
        L3(GLFW.GLFW_GAMEPAD_BUTTON_LEFT_THUMB, "LS"),
        R3(GLFW.GLFW_GAMEPAD_BUTTON_RIGHT_THUMB, "RS"),
        DPAD_UP(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_UP, null),
        DPAD_RIGHT(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_RIGHT, null),
        DPAD_DOWN(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_DOWN, null),
        DPAD_LEFT(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_LEFT, null),
        L2(-1, "LT"),
        R2(-1, "RT");

        final int glfw;
        final String xboxName;

        Button(int glfw, String xboxName) {
            this.glfw = glfw;
            this.xboxName = xboxName;
        }

        public static Button parse(String s) {
            String n = s.trim().toUpperCase(Locale.ROOT);
            for (Button b : values()) {
                if (b.name().equals(n) || n.equals(b.xboxName)) {
                    return b;
                }
            }
            return null;
        }
    }

    private static final float TRIGGER_THRESHOLD = 0.3f;

    private final GLFWGamepadState state = GLFWGamepadState.create();
    private final boolean[] down = new boolean[Button.values().length];
    private final float[] axes = new float[6];
    private int jid = -1;
    private String name = "";
    private long lastScan;
    private boolean mappingsLoaded;

    /** Re-reads the controller state. Returns false if the configured gamepad is not connected. */
    public boolean poll() {
        if (!mappingsLoaded) {
            mappingsLoaded = true;
            loadExtraMappings();
            logJoysticks();
        }
        int wanted = SplitPadConfig.gamepad();
        long now = System.currentTimeMillis();
        if (wanted <= 0) {
            jid = -1;
        } else if (jid < 0 || !GLFW.glfwJoystickIsGamepad(jid) || now - lastScan > 2000) {
            lastScan = now;
            int found = findGamepad(wanted);
            if (found != jid) {
                jid = found;
                name = found >= 0 ? String.valueOf(GLFW.glfwGetGamepadName(found)) : "";
                SplitPad.LOGGER.info("SplitPad: gamepad #{} -> {}", wanted, found >= 0 ? "joystick " + found + " (" + name + ")" : "not connected");
            }
        }
        if (jid < 0 || !GLFW.glfwGetGamepadState(jid, state)) {
            java.util.Arrays.fill(down, false);
            java.util.Arrays.fill(axes, 0f);
            return false;
        }
        for (int i = 0; i < axes.length; i++) {
            axes[i] = state.axes(i);
        }
        for (Button b : Button.values()) {
            boolean pressed;
            if (b == Button.L2) {
                pressed = trigger(GLFW.GLFW_GAMEPAD_AXIS_LEFT_TRIGGER) > TRIGGER_THRESHOLD;
            } else if (b == Button.R2) {
                pressed = trigger(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER) > TRIGGER_THRESHOLD;
            } else {
                pressed = state.buttons(b.glfw) == GLFW.GLFW_PRESS;
            }
            down[b.ordinal()] = pressed;
        }
        return true;
    }

    public boolean connected() {
        return jid >= 0;
    }

    public String name() {
        return name;
    }

    public boolean isDown(Button b) {
        return down[b.ordinal()];
    }

    /** Trigger value 0..1 (GLFW reports -1 at rest). */
    public float trigger(int axis) {
        return (axes[axis] + 1f) * 0.5f;
    }

    /** Left (or right) stick with a radial dead zone, rescaled so the result is 0..1 in length. */
    public float[] stick(boolean right, double deadzone, double exponent) {
        float x = axes[right ? GLFW.GLFW_GAMEPAD_AXIS_RIGHT_X : GLFW.GLFW_GAMEPAD_AXIS_LEFT_X];
        float y = axes[right ? GLFW.GLFW_GAMEPAD_AXIS_RIGHT_Y : GLFW.GLFW_GAMEPAD_AXIS_LEFT_Y];
        double len = Math.sqrt(x * x + y * y);
        if (len <= deadzone) {
            return new float[] {0f, 0f};
        }
        double scaled = Math.min(1.0, (len - deadzone) / (1.0 - deadzone));
        double k = Math.pow(scaled, exponent) / len;
        return new float[] {(float) (x * k), (float) (y * k)};
    }

    public static List<String> connectedGamepads() {
        List<String> out = new ArrayList<>();
        for (int j = GLFW.GLFW_JOYSTICK_1; j <= GLFW.GLFW_JOYSTICK_LAST; j++) {
            if (GLFW.glfwJoystickIsGamepad(j)) {
                out.add(String.valueOf(GLFW.glfwGetGamepadName(j)));
            }
        }
        return out;
    }

    private static int findGamepad(int number) {
        int n = 0;
        for (int j = GLFW.GLFW_JOYSTICK_1; j <= GLFW.GLFW_JOYSTICK_LAST; j++) {
            if (GLFW.glfwJoystickIsGamepad(j) && ++n == number) {
                return j;
            }
        }
        return -1;
    }

    private static void logJoysticks() {
        for (int j = GLFW.GLFW_JOYSTICK_1; j <= GLFW.GLFW_JOYSTICK_LAST; j++) {
            if (GLFW.glfwJoystickPresent(j)) {
                SplitPad.LOGGER.info("SplitPad: joystick {} '{}' guid={} gamepad={}", j,
                        GLFW.glfwGetJoystickName(j), GLFW.glfwGetJoystickGUID(j), GLFW.glfwJoystickIsGamepad(j));
            }
        }
    }

    /** Optional SDL mappings file for controllers GLFW does not know: config/splitpad/gamecontrollerdb.txt */
    private static void loadExtraMappings() {
        Path file = FMLPaths.CONFIGDIR.get().resolve("splitpad").resolve("gamecontrollerdb.txt");
        if (!Files.isRegularFile(file)) {
            return;
        }
        ByteBuffer buf = null;
        try {
            byte[] bytes = Files.readAllBytes(file);
            buf = MemoryUtil.memAlloc(bytes.length + 1);
            buf.put(bytes).put((byte) 0).flip();
            boolean ok = GLFW.glfwUpdateGamepadMappings(buf);
            SplitPad.LOGGER.info("SplitPad: loaded {} ({})", file, ok ? "ok" : "rejected");
        } catch (Exception e) {
            SplitPad.LOGGER.warn("SplitPad: could not read {}", file, e);
        } finally {
            if (buf != null) {
                MemoryUtil.memFree(buf);
            }
        }
    }
}
