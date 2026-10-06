package dev.khmh.trialcomplex.rooms;

import dev.khmh.trialcomplex.build.Builder;
import dev.khmh.trialcomplex.build.Theme;
import dev.khmh.trialcomplex.game.Room;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * «Голос приказывает»: выполнять только то, что Голос ПРИКАЗЫВАЕТ. Остальное — ловушки.
 * Уровень 1 — все вместе; 2 — по именам и «Голос просит»; 3 — «Голос НЕ приказывает», меньше времени, «замри».
 */
public class RCommand extends Room {
    enum Act { RED, BLUE, GREEN, YELLOW, SIT, JUMP, LOOK, BUTTON, FREEZE }
    enum Pre { ORDER, NONE, ASK, NOT }
    enum Who { ALL, KHMH, ITACHI }

    record Cmd(Pre pre, Who who, Act act) {}

    private static final Block[] ZONE = {Blocks.RED_CONCRETE, Blocks.BLUE_CONCRETE, Blocks.LIME_CONCRETE, Blocks.YELLOW_CONCRETE};
    // зоны: x0,z0 (5×5)
    private static final int[][] ZONE_AT = {{2, 2}, {12, 2}, {2, 12}, {12, 12}};

    private final int level;
    private final RandomSource rnd = RandomSource.create();
    private final List<Cmd> seq = new ArrayList<>();
    private int idx = -1;
    private boolean listening;
    private long windowEnd;
    private int streakFails;
    // наблюдения за окно
    private final Map<UUID, Set<Act>> did = new HashMap<>();
    private final Map<UUID, Vec3> startPos = new HashMap<>();
    private final Map<UUID, Integer> startZone = new HashMap<>();
    private final Map<UUID, Boolean> wasOnGround = new HashMap<>();

    public RCommand(String id, int level) {
        super(id, "Голос приказывает " + RLaser.roman(level), 19, 9, 19);
        this.level = level;
    }

    private BlockPos button() {
        return at(9, 2, 9);
    }

    @Override
    protected void build(Builder b) {
        Theme.LAB.shell(b, sx, sy, sz);
        for (int i = 0; i < 4; i++) {
            int x0 = ZONE_AT[i][0], z0 = ZONE_AT[i][1];
            b.fill(x0, 0, z0, x0 + 4, 0, z0 + 4, ZONE[i]);
            b.fill(x0 - 1, 0, z0 - 1, x0 + 5, 0, z0 - 1, Blocks.WHITE_CONCRETE);
            b.fill(x0 - 1, 0, z0 + 5, x0 + 5, 0, z0 + 5, Blocks.WHITE_CONCRETE);
            b.fill(x0 - 1, 0, z0, x0 - 1, 0, z0 + 4, Blocks.WHITE_CONCRETE);
            b.fill(x0 + 5, 0, z0, x0 + 5, 0, z0 + 4, Blocks.WHITE_CONCRETE);
        }
        // центральный постамент с кнопкой
        b.set(9, 0, 9, Blocks.SEA_LANTERN);
        b.set(9, 1, 9, Blocks.POLISHED_BLACKSTONE_BRICKS);
        b.set(9, 2, 9, Builder.floorButton(Blocks.POLISHED_BLACKSTONE_BUTTON));
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos p = new BlockPos(9, 1, 9).relative(d);
            b.set(p.getX(), p.getY(), p.getZ(), Builder.stairs(Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, d.getOpposite(), false));
        }
        // правило на стене
        b.sign(9, 4, 1, Direction.SOUTH, DyeColor.YELLOW, "ВЫПОЛНЯЙТЕ", "ТОЛЬКО ТО, ЧТО", "ГОЛОС", "ПРИКАЗЫВАЕТ");
        b.sign(9, 4, sz - 2, Direction.NORTH, DyeColor.YELLOW, "ВЫПОЛНЯЙТЕ", "ТОЛЬКО ТО, ЧТО", "ГОЛОС", "ПРИКАЗЫВАЕТ");
        // «динамики» Голоса
        for (int x : new int[]{3, 15}) {
            b.set(x, sy - 2, 1, Blocks.NOTE_BLOCK);
            b.set(x, sy - 2, sz - 2, Blocks.NOTE_BLOCK);
        }
    }

    private int length() {
        return new int[]{0, 7, 9, 11, 13}[level];
    }

    private int windowTicks() {
        return new int[]{0, 100, 80, 60, 50}[level];
    }

    private void newSequence() {
        seq.clear();
        boolean duo = players().size() >= 2;
        int n = length();
        Act last = null;
        int traps = 0;
        for (int i = 0; i < n; i++) {
            Pre pre;
            boolean trap = i > 0 && rnd.nextFloat() < (level == 1 ? 0.3f : 0.4f);
            if (trap && traps < n / 2) {
                traps++;
                List<Pre> opts = new ArrayList<>(List.of(Pre.NONE));
                if (level >= 2) opts.add(Pre.ASK);
                if (level >= 3) { opts.add(Pre.NOT); opts.add(Pre.NOT); }
                if (level >= 4) opts.add(Pre.ASK);
                pre = opts.get(rnd.nextInt(opts.size()));
            } else pre = Pre.ORDER;
            Who who = Who.ALL;
            if (level >= 2 && duo && rnd.nextFloat() < 0.6f) who = rnd.nextBoolean() ? Who.KHMH : Who.ITACHI;
            Act act;
            do {
                act = Act.values()[rnd.nextInt(8)];
            } while (act == last);
            last = act;
            seq.add(new Cmd(pre, who, act));
        }
        if (level >= 3) seq.add(new Cmd(Pre.ORDER, Who.ALL, Act.FREEZE));
        idx = -1;
    }

    @Override
    protected void onActivate(boolean firstTime) {
        super.onActivate(firstTime);
        setObjective("Выполняйте только то, что Голос ПРИКАЗЫВАЕТ. " + length() + (level >= 3 ? "+1" : "") + " команд без ошибок.");
        say("cmd.start", this::begin);
    }

    private void begin() {
        newSequence();
        later(20, this::next);
    }

    private void next() {
        idx++;
        if (idx >= seq.size()) {
            listening = false;
            win();
            return;
        }
        Cmd c = seq.get(idx);
        String pre = switch (c.pre) {
            case ORDER -> "cmd.pre.order";
            case ASK -> "cmd.pre.ask";
            case NOT -> "cmd.pre.not";
            case NONE -> null;
        };
        if (pre != null) voice().say(pre);
        String who = switch (c.who) { case ALL -> "all"; case KHMH -> "khmh"; case ITACHI -> "itachi"; };
        voice().say("cmd.act." + who + "." + c.act.name().toLowerCase(Locale.ROOT), this::openWindow);
        setObjective("Команда " + (idx + 1) + "/" + seq.size() + ". Только то, что Голос ПРИКАЗЫВАЕТ.");
    }

    private void openWindow() {
        did.clear();
        startPos.clear();
        startZone.clear();
        for (ServerPlayer p : players()) {
            did.put(p.getUUID(), EnumSet.noneOf(Act.class));
            startPos.put(p.getUUID(), p.position());
            startZone.put(p.getUUID(), zoneOf(p));
        }
        listening = true;
        Cmd c = seq.get(idx);
        windowEnd = now() + (c.act == Act.FREEZE ? 120 : windowTicks());
    }

    private int zoneOf(ServerPlayer p) {
        BlockPos l = local(p.blockPosition());
        for (int i = 0; i < 4; i++) {
            int x0 = ZONE_AT[i][0], z0 = ZONE_AT[i][1];
            if (l.getX() >= x0 && l.getX() <= x0 + 4 && l.getZ() >= z0 && l.getZ() <= z0 + 4) return i;
        }
        return -1;
    }

    private boolean targeted(Cmd c, ServerPlayer p) {
        return switch (c.who) {
            case ALL -> true;
            case KHMH -> isHost(p);
            case ITACHI -> !isHost(p);
        };
    }

    @Override
    protected void onTick(long t) {
        // наблюдение за действиями
        for (ServerPlayer p : players()) {
            Set<Act> s = did.get(p.getUUID());
            boolean ground = p.onGround();
            Boolean was = wasOnGround.put(p.getUUID(), ground);
            if (s == null || !listening) continue;
            if (p.isShiftKeyDown()) s.add(Act.SIT);
            if (Boolean.TRUE.equals(was) && !ground && p.getDeltaMovement().y > 0.1) s.add(Act.JUMP);
            if (p.getXRot() < -50) s.add(Act.LOOK);
            Vec3 sp = startPos.get(p.getUUID());
            if (sp != null && sp.distanceTo(p.position()) > 1.5) s.add(Act.FREEZE); // «пошевелился»
        }
        if (listening && t >= windowEnd) {
            listening = false;
            judge();
        }
    }

    @Override
    protected boolean onUse(ServerPlayer p, BlockPos pos) {
        if (pos.equals(button())) {
            Set<Act> s = did.get(p.getUUID());
            if (s != null && listening) s.add(Act.BUTTON);
        }
        return false;
    }

    /** Выполнил ли игрок действие за окно. */
    private boolean performed(ServerPlayer p, Act a) {
        Set<Act> s = did.getOrDefault(p.getUUID(), Set.of());
        if (a.ordinal() <= Act.YELLOW.ordinal()) {
            int z = zoneOf(p);
            Integer z0 = startZone.get(p.getUUID());
            return z == a.ordinal() && (z0 == null || z0 != a.ordinal() || true);
        }
        return s.contains(a);
    }

    private void judge() {
        Cmd c = seq.get(idx);
        ServerPlayer culprit = null;
        boolean ok = true;
        for (ServerPlayer p : players()) {
            if (cx.bypass(p)) continue;
            boolean t = targeted(c, p);
            if (c.act == Act.FREEZE) {
                Set<Act> s = did.getOrDefault(p.getUUID(), Set.of());
                if (!s.isEmpty()) { ok = false; culprit = p; }
                continue;
            }
            boolean zone = c.act.ordinal() <= Act.YELLOW.ordinal();
            boolean doneIt = performed(p, c.act);
            if (c.pre == Pre.ORDER) {
                if (t && !doneIt) { ok = false; culprit = p; }
                // не тебе приказывали — не делай (для зон — не заходи туда, если не стоял)
                if (!t && doneIt && (!zone || startZone.getOrDefault(p.getUUID(), -1) != c.act.ordinal())) { ok = false; culprit = p; }
            } else {
                // ловушка: сделал — ошибка (зона: если зашёл в неё за окно)
                boolean entered = zone ? doneIt && startZone.getOrDefault(p.getUUID(), -1) != c.act.ordinal() : doneIt;
                if (t && entered) { ok = false; culprit = p; }
            }
        }
        if (ok) {
            ding(true);
            progress();
            later(10, this::next);
        } else {
            fail();
            ding(false);
            String grp = c.pre == Pre.ORDER ? (c.act == Act.FREEZE ? "cmd.fail.freeze" : "cmd.fail.slow") : "cmd.fail.trap";
            voice().interrupt(culprit != null ? voice().pickFor(grp, culprit) : voice().pick(grp));
            say("cmd.restart", this::begin);
        }
    }

    @Override
    protected void onReset() {
        listening = false;
        idx = -1;
        seq.clear();
    }

    @Override
    public String debug() {
        return "idx=" + idx + " listening=" + listening + " cmd=" + (idx >= 0 && idx < seq.size() ? seq.get(idx) : "-");
    }
}
