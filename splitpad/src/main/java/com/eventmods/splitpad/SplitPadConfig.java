package com.eventmods.splitpad;

import java.util.List;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Client config (config/splitpad-client.toml). Every value can also be overridden per instance
 * with a JVM argument, e.g. {@code -Dsplitpad.gamepad=2 -Dsplitpad.screen=RIGHT}, so two copies of
 * the game can share one config folder.
 */
public final class SplitPadConfig {
    public enum ScreenPart { FULL, LEFT, RIGHT, TOP, BOTTOM, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue GAMEPAD;
    public static final ForgeConfigSpec.EnumValue<ScreenPart> SCREEN;
    public static final ForgeConfigSpec.IntValue MONITOR;
    public static final ForgeConfigSpec.BooleanValue BORDERLESS;
    public static final ForgeConfigSpec.BooleanValue NO_PAUSE_ON_LOST_FOCUS;
    public static final ForgeConfigSpec.DoubleValue DEADZONE;
    public static final ForgeConfigSpec.DoubleValue LOOK_SPEED_X;
    public static final ForgeConfigSpec.DoubleValue LOOK_SPEED_Y;
    public static final ForgeConfigSpec.BooleanValue INVERT_Y;
    public static final ForgeConfigSpec.DoubleValue CURSOR_SPEED;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> BINDINGS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> MENU_BINDINGS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.push("controller");
        GAMEPAD = b.comment(
                        "Which connected gamepad this game window uses: 1 = first, 2 = second, ...",
                        "0 = no gamepad (keyboard and mouse only). JVM override: -Dsplitpad.gamepad=N")
                .defineInRange("gamepad", 1, 0, 16);
        DEADZONE = b.comment("Stick dead zone (0..1)").defineInRange("deadzone", 0.15, 0.0, 0.9);
        LOOK_SPEED_X = b.comment("Camera turn speed, degrees per second (horizontal)")
                .defineInRange("lookSpeedX", 260.0, 10.0, 2000.0);
        LOOK_SPEED_Y = b.comment("Camera turn speed, degrees per second (vertical)")
                .defineInRange("lookSpeedY", 180.0, 10.0, 2000.0);
        INVERT_Y = b.comment("Invert vertical camera").define("invertY", false);
        CURSOR_SPEED = b.comment("Menu cursor speed, window heights per second")
                .defineInRange("cursorSpeed", 1.0, 0.1, 5.0);
        BINDINGS = b.comment(
                        "In-game buttons: \"BUTTON=action\".",
                        "Buttons: CROSS CIRCLE SQUARE TRIANGLE L1 R1 L2 R2 L3 R3 SHARE OPTIONS PS DPAD_UP DPAD_DOWN DPAD_LEFT DPAD_RIGHT",
                        "(Xbox names also work: A B X Y LB RB LT RT LS RS BACK START GUIDE)",
                        "Action = any key binding id (key.jump, key.attack, key.use, key.inventory, ... or a mod's own,",
                        "see options.txt, e.g. key_key.jump -> key.jump), or one of:",
                        "splitpad:hotbar_next, splitpad:hotbar_prev, splitpad:pause, splitpad:toggle_sneak")
                .defineListAllowEmpty("bindings", List.of(
                        "CROSS=key.jump",
                        "CIRCLE=key.drop",
                        "SQUARE=key.swapOffhand",
                        "TRIANGLE=key.inventory",
                        "R2=key.attack",
                        "L2=key.use",
                        "R1=splitpad:hotbar_next",
                        "L1=splitpad:hotbar_prev",
                        "DPAD_RIGHT=splitpad:hotbar_next",
                        "DPAD_LEFT=splitpad:hotbar_prev",
                        "L3=key.sprint",
                        "R3=splitpad:toggle_sneak",
                        "DPAD_DOWN=key.sneak",
                        "DPAD_UP=key.togglePerspective",
                        "SHARE=key.playerlist",
                        "OPTIONS=splitpad:pause",
                        "PS=key.pickItem"), o -> o instanceof String);
        MENU_BINDINGS = b.comment(
                        "Buttons inside menus/inventories. Left stick moves the cursor, right stick scrolls.",
                        "Actions: left_click, right_click, quick_move (shift-click), back, scroll_up, scroll_down")
                .defineListAllowEmpty("menuBindings", List.of(
                        "CROSS=left_click",
                        "SQUARE=right_click",
                        "TRIANGLE=quick_move",
                        "CIRCLE=back",
                        "OPTIONS=back",
                        "L1=scroll_up",
                        "R1=scroll_down"), o -> o instanceof String);
        b.pop();

        b.push("window");
        SCREEN = b.comment(
                        "Which part of the monitor this game window takes.",
                        "FULL = leave the window alone. Two players: LEFT + RIGHT or TOP + BOTTOM.",
                        "Up to four: TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT. JVM override: -Dsplitpad.screen=RIGHT")
                .defineEnum("screen", ScreenPart.FULL);
        MONITOR = b.comment("Monitor number (0 = primary). JVM override: -Dsplitpad.monitor=N")
                .defineInRange("monitor", 0, 0, 16);
        BORDERLESS = b.comment("Remove the window frame when splitting the screen").define("borderless", true);
        NO_PAUSE_ON_LOST_FOCUS = b.comment(
                        "Do not open the pause menu when the window loses focus (needed so both players can play)")
                .define("noPauseOnLostFocus", true);
        b.pop();

        SPEC = b.build();
    }

    private SplitPadConfig() {}

    public static int gamepad() {
        return intProp("splitpad.gamepad", GAMEPAD.get());
    }

    /** True when this window is set up as part of a split screen (or forced per instance via -D). */
    public static boolean splitActive() {
        return screen() != ScreenPart.FULL || System.getProperty("splitpad.gamepad") != null;
    }

    public static int monitor() {
        return intProp("splitpad.monitor", MONITOR.get());
    }

    public static ScreenPart screen() {
        String s = System.getProperty("splitpad.screen");
        if (s != null) {
            try {
                return ScreenPart.valueOf(s.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e) {
                SplitPad.LOGGER.warn("Unknown -Dsplitpad.screen value '{}'", s);
            }
        }
        return SCREEN.get();
    }

    private static int intProp(String name, int fallback) {
        String s = System.getProperty(name);
        if (s == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            SplitPad.LOGGER.warn("Bad number in -D{}={}", name, s);
            return fallback;
        }
    }
}
