package com.eventmods.splitpad;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;

/** Moves and resizes the game window to its part of the monitor. */
public final class WindowLayout {
    private WindowLayout() {}

    public static void apply(Minecraft mc) {
        SplitPadConfig.ScreenPart part = SplitPadConfig.screen();
        if (part == SplitPadConfig.ScreenPart.FULL) {
            return;
        }
        Window window = mc.getWindow();
        long handle = window.getWindow();
        if (window.isFullscreen()) {
            window.toggleFullScreen();
            mc.options.fullscreen().set(false);
        }
        GLFW.glfwRestoreWindow(handle);

        long monitor = GLFW.glfwGetPrimaryMonitor();
        PointerBuffer monitors = GLFW.glfwGetMonitors();
        int wantedMonitor = SplitPadConfig.monitor();
        if (monitors != null && wantedMonitor < monitors.limit()) {
            monitor = monitors.get(wantedMonitor);
        }
        if (monitor == 0L) {
            SplitPad.LOGGER.warn("SplitPad: no monitor found, window not moved");
            return;
        }
        int[] mx = new int[1], my = new int[1], mw = new int[1], mh = new int[1];
        GLFW.glfwGetMonitorWorkarea(monitor, mx, my, mw, mh);

        int x = mx[0], y = my[0], w = mw[0], h = mh[0];
        int halfW = w / 2, halfH = h / 2;
        switch (part) {
            case LEFT -> w = halfW;
            case RIGHT -> { x += halfW; w -= halfW; }
            case TOP -> h = halfH;
            case BOTTOM -> { y += halfH; h -= halfH; }
            case TOP_LEFT -> { w = halfW; h = halfH; }
            case TOP_RIGHT -> { x += halfW; w -= halfW; h = halfH; }
            case BOTTOM_LEFT -> { y += halfH; w = halfW; h -= halfH; }
            case BOTTOM_RIGHT -> { x += halfW; y += halfH; w -= halfW; h -= halfH; }
            default -> { }
        }

        boolean borderless = SplitPadConfig.BORDERLESS.get();
        GLFW.glfwSetWindowAttrib(handle, GLFW.GLFW_DECORATED, borderless ? GLFW.GLFW_FALSE : GLFW.GLFW_TRUE);
        if (!borderless) {
            int[] l = new int[1], t = new int[1], r = new int[1], b = new int[1];
            GLFW.glfwGetWindowFrameSize(handle, l, t, r, b);
            x += l[0];
            y += t[0];
            w -= l[0] + r[0];
            h -= t[0] + b[0];
        }
        GLFW.glfwSetWindowPos(handle, x, y);
        GLFW.glfwSetWindowSize(handle, Math.max(w, 320), Math.max(h, 240));
        SplitPad.LOGGER.info("SplitPad: window -> {} at {},{} size {}x{}", part, x, y, w, h);
    }
}
