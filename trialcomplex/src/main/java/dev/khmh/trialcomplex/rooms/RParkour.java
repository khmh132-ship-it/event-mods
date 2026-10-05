package dev.khmh.trialcomplex.rooms;

import dev.khmh.trialcomplex.build.Builder;
import dev.khmh.trialcomplex.build.Palette;
import dev.khmh.trialcomplex.game.Room;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Кооп-паркур над пустотой: две дорожки. Мост (строчная буква) на одной дорожке появляется,
 * пока напарник стоит на золотой плите (заглавная) на другой. 'x' — осыпается. Проверено tools/levels/parkour_check.py.
 */
public class RParkour extends Room {
    static final String[][] LEVELS = {
            {"###A####bb###  ##C####dd####", "#aa####B##  ###cc###D##  ###"},
            {"##A###xxx##bb###  ##C##xx##dd###", "#aa##x##B###  ##cc###xxx#D#  ###"},
            {"##A##bb###C##xx##dd##E###  ##ff#####", "#aa###B##cc###D##ee##xx##F###  #####"},
    };
    private static final int X0 = 4;
    private static final int[] LANE_Z = {2, 8}; // дорожка: z..z+2
    private static final BlockState BRIDGE_ON = Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();

    private final String[] lanes;
    private final int len;
    private final Map<UUID, Vec3> cp = new HashMap<>();
    private final Map<BlockPos, Long> crumbling = new HashMap<>();
    private boolean done;
    private boolean[] padOn = new boolean[8];

    public RParkour(String id, int level) {
        super(id, "Мосты на двоих " + RLaser.roman(level), LEVELS[level - 1][0].length() + 9, 9, 13);
        this.lanes = LEVELS[level - 1];
        this.len = lanes[0].length();
    }

    private char c(int lane, int i) {
        return lanes[lane].charAt(i);
    }

    @Override
    protected void build(Builder b) {
        b.clear(0, -6, 0, sx - 1, sy - 1, sz - 1);
        Palette stone = Palette.of(Blocks.POLISHED_ANDESITE, 3, Blocks.ANDESITE, 1, Blocks.STONE, 1);
        // старт и финиш — широкие площадки
        b.fill(1, 0, 1, X0 - 1, 0, sz - 2, stone);
        b.fill(X0 + len, 0, 1, sx - 2, 0, sz - 2, stone);
        for (int lane = 0; lane < 2; lane++)
            for (int i = 0; i < len; i++) {
                char ch = c(lane, i);
                BlockState s = switch (ch) {
                    case '#', 'S' -> stone.pick(b.rnd);
                    case 'x' -> Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
                    case ' ' -> null;
                    default -> Character.isUpperCase(ch) ? Blocks.GOLD_BLOCK.defaultBlockState() : null;
                };
                for (int dz = 0; dz < 3; dz++) {
                    if (s != null) b.set(X0 + i, 0, LANE_Z[lane] + dz, s);
                    else b.set(X0 + i, 0, LANE_Z[lane] + dz, Blocks.AIR);
                }
                // буквы мостов подписаны на столбиках у края
                if (Character.isLowerCase(ch) && (i == 0 || c(lane, i - 1) != ch)) {
                    b.set(X0 + i, 1, LANE_Z[lane] - 1, Blocks.STONE_BRICK_WALL);
                    b.set(X0 + i, 2, LANE_Z[lane] - 1, Blocks.SOUL_LANTERN);
                }
                if (Character.isUpperCase(ch) && ch != 'S') b.set(X0 + i, 1, LANE_Z[lane] + (lane == 0 ? 3 : -1), Blocks.LANTERN);
            }
        // стены у дверей
        for (int x : new int[]{0, sx - 1}) b.fill(x, 0, 1, x, 6, sz - 2, Palette.of(Blocks.DEEPSLATE_TILES, 3, Blocks.POLISHED_DEEPSLATE, 1));
        b.sign(2, 1, 1, Direction.SOUTH, DyeColor.WHITE, "СТОИШЬ НА", "ЗОЛОТЕ — У", "НАПАРНИКА", "ПОЯВЛЯЕТСЯ МОСТ");
    }

    @Override
    public Vec3 checkpoint(ServerPlayer p) {
        return cp.getOrDefault(p.getUUID(), spawn());
    }

    @Override
    protected boolean onFall(ServerPlayer p) {
        cx.teleport(p, checkpoint(p), -90f, 0f);
        voice().interrupt(voice().pickFor("parkour.fall", p));
        return true;
    }

    @Override
    protected void onActivate(boolean firstTime) {
        super.onActivate(firstTime);
        setObjective("Каждый на свою дорожку. Стойте на золоте — у напарника появится мост.");
    }

    private int laneOf(BlockPos local) {
        for (int k = 0; k < 2; k++) if (local.getZ() >= LANE_Z[k] && local.getZ() <= LANE_Z[k] + 2) return k;
        return -1;
    }

    @Override
    protected void onTick(long t) {
        // какие плиты заняты
        boolean[] now = new boolean[8];
        for (ServerPlayer p : players()) {
            if (!p.onGround()) continue;
            BlockPos l = local(p.blockPosition().below());
            int lane = laneOf(l), i = l.getX() - X0;
            if (lane < 0 || i < 0 || i >= len || l.getY() != 0) continue;
            char ch = c(lane, i);
            if (Character.isUpperCase(ch) && ch != 'S') {
                now[ch - 'A'] = true;
                cp.put(p.getUUID(), v(X0 + i + 0.5, 1, LANE_Z[lane] + 1.5));
            }
            if (ch == 'x') crumbling.putIfAbsent(at(X0 + i, 0, LANE_Z[lane] + (l.getZ() - LANE_Z[lane])), t);
        }
        for (int k = 0; k < 8; k++)
            if (now[k] != padOn[k]) {
                padOn[k] = now[k];
                char low = (char) ('a' + k);
                for (int lane = 0; lane < 2; lane++)
                    for (int i = 0; i < len; i++)
                        if (c(lane, i) == low) for (int dz = 0; dz < 3; dz++) set(at(X0 + i, 0, LANE_Z[lane] + dz), now[k] ? BRIDGE_ON : Blocks.AIR.defaultBlockState());
                cx.sound(at(X0 + len / 2, 1, 6), now[k] ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_DEACTIVATE, 0.6f, 1.5f);
            }
        // осыпание: через 15 тиков пропадает, через 80 возвращается
        crumbling.entrySet().removeIf(e -> {
            long age = t - e.getValue();
            if (age == 15) { set(e.getKey(), Blocks.AIR.defaultBlockState()); cx.sound(e.getKey(), SoundEvents.GRAVEL_BREAK, 1f, 0.8f); }
            if (age >= 80) { set(e.getKey(), Blocks.CRACKED_STONE_BRICKS.defaultBlockState()); return true; }
            return false;
        });
        // финиш
        if (!done && t % 10 == 0) {
            int there = 0;
            for (ServerPlayer p : players()) if (local(p.blockPosition()).getX() >= X0 + len) there++;
            if (there > 0 && there >= players().size()) { done = true; win(); }
        }
    }

    @Override
    protected void onReset() {
        cp.clear();
        crumbling.clear();
        padOn = new boolean[8];
        done = false;
    }

    @Override
    public String debug() {
        return "pads=" + Arrays.toString(padOn);
    }
}
