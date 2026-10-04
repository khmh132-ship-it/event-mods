package com.eventmods.splitpad;

import com.eventmods.splitpad.Gamepad.Button;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

/** Turns gamepad input into Minecraft input for this game window. */
public final class ClientHandler {
    private static final String HOTBAR_NEXT = "splitpad:hotbar_next";
    private static final String HOTBAR_PREV = "splitpad:hotbar_prev";
    private static final String PAUSE = "splitpad:pause";
    private static final String TOGGLE_SNEAK = "splitpad:toggle_sneak";

    private final Minecraft mc = Minecraft.getInstance();
    private final Gamepad pad = new Gamepad();
    private final boolean[] prevDown = new boolean[Button.values().length];

    private boolean initialised;
    private List<? extends String> parsedFrom;
    private List<? extends String> parsedMenuFrom;
    private final Map<Button, String> bindings = new EnumMap<>(Button.class);
    private final Map<Button, String> menuBindings = new EnumMap<>(Button.class);
    private final Map<String, KeyMapping> keyByName = new HashMap<>();
    /** Key mappings we currently hold down, so we can release them when the button is let go. */
    private final Map<KeyMapping, Boolean> heldByUs = new HashMap<>();
    private boolean toggledSneak;

    private boolean virtualGrab;
    private Screen lastScreen;
    private double cursorX = -1, cursorY = -1;
    private double scrollAccumulator;
    private long lastFrameNanos;
    private long foundAt;

    // ------------------------------------------------------------------ ticks

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        if (!initialised) {
            initialised = true;
            if (SplitPadConfig.splitActive() && SplitPadConfig.NO_PAUSE_ON_LOST_FOCUS.get()) {
                mc.options.pauseOnLostFocus = false;
            }
            WindowLayout.apply(mc);
        }

        boolean wasConnected = pad.connected();
        if (!pad.poll()) {
            releaseAllHeld();
            virtualGrab = false;
            return;
        }
        if (!wasConnected) {
            foundAt = System.currentTimeMillis();
        }
        refreshBindings();
        updateMouseGrab();

        if (mc.screen == null) {
            if (mc.player != null && mc.getOverlay() == null) {
                handleGameButtons();
            }
        } else {
            releaseAllHeld();
            handleMenuButtons(mc.screen);
        }
        for (Button b : Button.values()) {
            prevDown[b.ordinal()] = pad.isDown(b);
        }
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        long now = System.nanoTime();
        double dt = lastFrameNanos == 0 ? 0 : Math.min((now - lastFrameNanos) / 1.0e9, 0.1);
        lastFrameNanos = now;
        if (!pad.connected() || !pad.poll()) {
            return;
        }
        double dz = SplitPadConfig.DEADZONE.get();

        Screen screen = mc.screen;
        if (screen == null) {
            lastScreen = null;
            LocalPlayer player = mc.player;
            if (player != null && !mc.isPaused() && mc.getOverlay() == null) {
                float[] look = pad.stick(true, dz, 2.0);
                double yaw = look[0] * SplitPadConfig.LOOK_SPEED_X.get() * dt;
                double pitch = look[1] * SplitPadConfig.LOOK_SPEED_Y.get() * dt * (SplitPadConfig.INVERT_Y.get() ? -1 : 1);
                if (yaw != 0 || pitch != 0) {
                    // Entity.turn multiplies by 0.15 (mouse units -> degrees)
                    player.turn(yaw / 0.15, pitch / 0.15);
                }
            }
            return;
        }
        moveCursor(screen, dt, dz);
    }

    // ------------------------------------------------------------------ movement

    @SubscribeEvent
    public void onMovementInput(MovementInputUpdateEvent event) {
        if (!pad.connected() || mc.screen != null) {
            return;
        }
        float[] move = pad.stick(false, SplitPadConfig.DEADZONE.get(), 1.0);
        if (move[0] == 0 && move[1] == 0) {
            return;
        }
        Input input = event.getInput();
        float forward = -move[1];
        float strafe = -move[0];
        if (event.getEntity() instanceof LocalPlayer lp && lp.isMovingSlowly()) {
            float slow = Mth.clamp(0.3f + EnchantmentHelper.getSneakingSpeedBonus(event.getEntity()), 0f, 1f);
            forward *= slow;
            strafe *= slow;
        }
        if (input.forwardImpulse == 0) {
            input.forwardImpulse = forward;
            input.up = forward > 0.1f;
            input.down = forward < -0.1f;
        }
        if (input.leftImpulse == 0) {
            input.leftImpulse = strafe;
            input.left = strafe > 0.1f;
            input.right = strafe < -0.1f;
        }
    }

    // ------------------------------------------------------------------ in-game buttons

    private void handleGameButtons() {
        Map<KeyMapping, Boolean> wanted = new HashMap<>();
        for (Map.Entry<Button, String> e : bindings.entrySet()) {
            Button b = e.getKey();
            String action = e.getValue();
            boolean down = pad.isDown(b);
            boolean pressed = down && !prevDown[b.ordinal()];
            switch (action) {
                case HOTBAR_NEXT -> { if (pressed) scrollHotbar(1); }
                case HOTBAR_PREV -> { if (pressed) scrollHotbar(-1); }
                case PAUSE -> { if (pressed) mc.pauseGame(false); }
                case TOGGLE_SNEAK -> { if (pressed) toggledSneak = !toggledSneak; }
                default -> {
                    KeyMapping km = key(action);
                    if (km == null) {
                        continue;
                    }
                    if (pressed) {
                        km.clickCount++;
                    }
                    wanted.merge(km, down, Boolean::logicalOr);
                }
            }
        }
        if (toggledSneak) {
            wanted.put(mc.options.keyShift, true);
        }
        for (Map.Entry<KeyMapping, Boolean> e : wanted.entrySet()) {
            KeyMapping km = e.getKey();
            if (e.getValue()) {
                km.setDown(true);
                heldByUs.put(km, true);
            } else if (heldByUs.remove(km) != null) {
                km.setDown(false);
            }
        }
        heldByUs.keySet().removeIf(km -> {
            if (!wanted.containsKey(km)) {
                km.setDown(false);
                return true;
            }
            return false;
        });
    }

    private void scrollHotbar(int dir) {
        if (mc.player == null) {
            return;
        }
        if (mc.player.isSpectator()) {
            mc.gui.getSpectatorGui().onMouseScrolled(-dir);
            return;
        }
        var inv = mc.player.getInventory();
        inv.selected = Math.floorMod(inv.selected + dir, 9);
    }

    private void releaseAllHeld() {
        for (KeyMapping km : heldByUs.keySet()) {
            km.setDown(false);
        }
        heldByUs.clear();
    }

    /**
     * Vanilla only keeps mining while the mouse is "grabbed", which never happens in a window without
     * focus. Pretend it is grabbed while a gamepad drives an unfocused window, and grab for real as
     * soon as the window gets focus.
     */
    private void updateMouseGrab() {
        MouseHandler mouse = mc.mouseHandler;
        if (mc.screen != null) {
            virtualGrab = false;
            return;
        }
        if (mc.isWindowActive()) {
            if (virtualGrab) {
                virtualGrab = false;
                mouse.mouseGrabbed = false;
                mouse.grabMouse();
            }
        } else if (!mouse.isMouseGrabbed()) {
            mouse.mouseGrabbed = true;
            virtualGrab = true;
        }
    }

    // ------------------------------------------------------------------ menus

    private void moveCursor(Screen screen, double dt, double dz) {
        MouseHandler mouse = mc.mouseHandler;
        long handle = mc.getWindow().getWindow();
        double w = mc.getWindow().getScreenWidth();
        double h = mc.getWindow().getScreenHeight();
        if (screen != lastScreen) {
            if (lastScreen == null || cursorX < 0) {
                cursorX = w / 2;
                cursorY = h / 2;
            }
            lastScreen = screen;
        }

        float[] move = pad.stick(false, dz, 1.6);
        double speed = SplitPadConfig.CURSOR_SPEED.get() * h * dt;
        boolean moved = move[0] != 0 || move[1] != 0;
        cursorX = Mth.clamp(cursorX + move[0] * speed, 0, w - 1);
        cursorY = Mth.clamp(cursorY + move[1] * speed, 0, h - 1);
        // In a focused window the real mouse wins until the stick is touched again.
        boolean outOfSync = mouse.xpos() != cursorX || mouse.ypos() != cursorY;
        if (moved || (outOfSync && !mc.isWindowActive())) {
            mouse.onMove(handle, cursorX, cursorY);
        }

        float[] scroll = pad.stick(true, dz, 1.0);
        scrollAccumulator += -scroll[1] * dt * 10.0;
        if (Math.abs(scrollAccumulator) >= 1.0) {
            double step = Math.signum(scrollAccumulator);
            scrollAccumulator -= step;
            mouse.onScroll(handle, 0, step);
        }
    }

    private void handleMenuButtons(Screen screen) {
        MouseHandler mouse = mc.mouseHandler;
        long handle = mc.getWindow().getWindow();
        for (Map.Entry<Button, String> e : menuBindings.entrySet()) {
            Button b = e.getKey();
            boolean down = pad.isDown(b);
            boolean was = prevDown[b.ordinal()];
            if (down == was) {
                continue;
            }
            if (mc.screen != screen) {
                return; // a previous action switched screens; wait for the next tick
            }
            if (cursorX >= 0 && (mouse.xpos() != cursorX || mouse.ypos() != cursorY) && !mc.isWindowActive()) {
                mouse.onMove(handle, cursorX, cursorY);
            }
            switch (e.getValue()) {
                case "left_click" -> mouse.onPress(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT, down ? GLFW.GLFW_PRESS : GLFW.GLFW_RELEASE, 0);
                case "right_click" -> mouse.onPress(handle, GLFW.GLFW_MOUSE_BUTTON_RIGHT, down ? GLFW.GLFW_PRESS : GLFW.GLFW_RELEASE, 0);
                case "quick_move" -> {
                    if (down && screen instanceof AbstractContainerScreen<?> container) {
                        Slot slot = container.getSlotUnderMouse();
                        if (slot != null && slot.hasItem()) {
                            container.slotClicked(slot, slot.index, 0, ClickType.QUICK_MOVE);
                        }
                    }
                }
                case "back" -> mc.keyboardHandler.keyPress(handle, GLFW.GLFW_KEY_ESCAPE,
                        GLFW.glfwGetKeyScancode(GLFW.GLFW_KEY_ESCAPE), down ? GLFW.GLFW_PRESS : GLFW.GLFW_RELEASE, 0);
                case "scroll_up" -> { if (down) mouse.onScroll(handle, 0, 1); }
                case "scroll_down" -> { if (down) mouse.onScroll(handle, 0, -1); }
                default -> { }
            }
        }
    }

    @SubscribeEvent
    public void onScreenRender(ScreenEvent.Render.Post event) {
        if (!pad.connected() || cursorX < 0) {
            return;
        }
        // Only draw our cursor while the gamepad drives this window's pointer.
        if (mc.isWindowActive() && (mc.mouseHandler.xpos() != cursorX || mc.mouseHandler.ypos() != cursorY)) {
            return;
        }
        var window = mc.getWindow();
        int x = (int) (cursorX * window.getGuiScaledWidth() / window.getScreenWidth());
        int y = (int) (cursorY * window.getGuiScaledHeight() / window.getScreenHeight());
        GuiGraphics g = event.getGuiGraphics();
        g.pose().pushPose();
        g.pose().translate(0, 0, 500);
        // simple arrow pointer: dark outline, white body
        for (int i = 0; i < 9; i++) {
            g.fill(x - 1, y + i - 1, x + i / 2 + 2, y + i + 1, 0xFF000000);
        }
        for (int i = 0; i < 8; i++) {
            g.fill(x, y + i, x + i / 2 + 1, y + i + 1, 0xFFFFFFFF);
        }
        g.pose().popPose();
    }

    // ------------------------------------------------------------------ HUD

    @SubscribeEvent
    public void onRenderGui(RenderGuiEvent.Post event) {
        int wanted = SplitPadConfig.gamepad();
        if (wanted <= 0 || mc.options.hideGui) {
            return;
        }
        Component text;
        int color;
        if (!pad.connected() && !SplitPadConfig.splitActive()) {
            return; // plain single-player without a gamepad: stay silent
        } else if (!pad.connected()) {
            text = Component.translatable("splitpad.hud.no_gamepad", wanted, Gamepad.connectedGamepads().size());
            color = 0xFF5555;
        } else if (System.currentTimeMillis() - foundAt < 6000) {
            text = Component.translatable("splitpad.hud.gamepad", wanted, pad.name());
            color = 0x55FF55;
        } else {
            return;
        }
        event.getGuiGraphics().drawString(mc.font, text, 4, 4, color, true);
    }

    // ------------------------------------------------------------------ config

    private void refreshBindings() {
        List<? extends String> game = SplitPadConfig.BINDINGS.get();
        List<? extends String> menu = SplitPadConfig.MENU_BINDINGS.get();
        if (game != parsedFrom) {
            parsedFrom = game;
            parse(game, bindings);
            keyByName.clear();
        }
        if (menu != parsedMenuFrom) {
            parsedMenuFrom = menu;
            parse(menu, menuBindings);
        }
    }

    private static void parse(List<? extends String> lines, Map<Button, String> into) {
        into.clear();
        for (String line : lines) {
            int eq = line.indexOf('=');
            Button b = eq > 0 ? Button.parse(line.substring(0, eq)) : null;
            if (b == null) {
                SplitPad.LOGGER.warn("SplitPad: bad binding '{}'", line);
                continue;
            }
            into.put(b, line.substring(eq + 1).trim());
        }
    }

    private KeyMapping key(String name) {
        KeyMapping km = keyByName.get(name);
        if (km == null && !keyByName.containsKey(name)) {
            for (KeyMapping k : mc.options.keyMappings) {
                if (k.getName().equals(name)) {
                    km = k;
                    break;
                }
            }
            if (km == null) {
                SplitPad.LOGGER.warn("SplitPad: unknown key binding '{}'", name);
            }
            keyByName.put(name, km);
        }
        return km;
    }
}
