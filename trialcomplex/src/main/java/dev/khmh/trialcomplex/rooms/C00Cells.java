package dev.khmh.trialcomplex.rooms;

import dev.khmh.trialcomplex.build.Builder;
import dev.khmh.trialcomplex.build.Palette;
import dev.khmh.trialcomplex.build.Theme;
import dev.khmh.trialcomplex.game.Gate;
import dev.khmh.trialcomplex.game.Room;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Вступление. Двое просыпаются в камерах друг напротив друга. Рычаг в камере открывает СОСЕДНЮЮ.
 * Потом — встать вдвоём на две золотые плиты в зале.
 */
public class C00Cells extends Room {
    private static final BlockState OFF = Blocks.GRAY_CONCRETE.defaultBlockState();
    private static final int CX = 7; // центр камер по x
    private static final int[] CELL_Z = {2, 12}; // центр камеры A (север) и B (юг)
    private static final int[][] PLATES = {{13, 5}, {13, 9}};

    private Gate barsA, barsB;
    private int phase; // 0 ждём, 1 вступление, 2 рычаги, 3 плиты, 4 готово
    private boolean aOpen, bOpen, idleSaid;
    private long phaseSince;

    public C00Cells() {
        super("c00", "Камеры", 17, 9, 15);
    }

    @Override
    protected boolean hasEntryDoor() {
        return false;
    }

    @Override
    public boolean countsAsTrial() {
        return false;
    }

    @Override
    protected void init() {
        BlockState bars = Blocks.IRON_BARS.defaultBlockState().setValue(IronBarsBlock.EAST, true).setValue(IronBarsBlock.WEST, true);
        barsA = new Gate(at(CX - 1, 1, 4), at(CX + 1, 3, 4), Palette.of(bars));
        barsB = new Gate(at(CX - 1, 1, 10), at(CX + 1, 3, 10), Palette.of(bars));
    }

    private BlockPos lever(int cell) {
        return cell == 0 ? at(CX, 2, 1) : at(CX, 2, 13);
    }

    @Override
    protected void build(Builder b) {
        Theme.LAB.shell(b, sx, sy, sz);
        // потолочные лампы выключены до вступления
        for (BlockPos p : lamps()) b.level.setBlock(p, OFF, 2);
        // камеры
        for (int c = 0; c < 2; c++) {
            int z0 = c == 0 ? 1 : 10, z1 = c == 0 ? 4 : 13;
            for (int z = z0; z <= z1; z++)
                for (int y = 1; y <= 4; y++) {
                    b.set(CX - 2, y, z, Blocks.POLISHED_DEEPSLATE);
                    b.set(CX + 2, y, z, Blocks.POLISHED_DEEPSLATE);
                }
            b.fill(CX - 2, 4, z0, CX + 2, 4, z1, Blocks.POLISHED_DEEPSLATE);
            b.fill(CX - 1, 0, z0, CX + 1, 0, z1, Blocks.DEEPSLATE_TILES);
            int cz = CELL_Z[c];
            b.set(CX, 0, cz, Blocks.CRYING_OBSIDIAN);
            // нары
            b.set(CX - 1, 1, cz + (c == 0 ? -1 : 1), Builder.slab(Blocks.SPRUCE_SLAB, false));
            b.set(CX + 1, 3, cz + (c == 0 ? -1 : 1), Blocks.CHAIN);
            // рычаг на задней стене
            BlockPos lv = local(lever(c));
            b.set(lv.getX(), lv.getY(), lv.getZ(), Builder.lever(c == 0 ? Direction.SOUTH : Direction.NORTH, AttachFace.WALL, false));
            b.sign(CX + 2 - 1, 3, c == 0 ? 1 : 13, c == 0 ? Direction.SOUTH : Direction.NORTH, DyeColor.LIGHT_BLUE,
                    "КАМЕРА", c == 0 ? "№1" : "№2", "", "");
        }
        // плиты в зале
        for (int[] pl : PLATES) {
            b.set(pl[0], 0, pl[1], Blocks.GOLD_BLOCK);
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++)
                    if (dx != 0 || dz != 0) b.set(pl[0] + dx, 0, pl[1] + dz, Blocks.YELLOW_CONCRETE);
        }
        b.sign(sx - 2, 3, 7, Direction.WEST, DyeColor.YELLOW, "", "ВСТАНЬТЕ", "НА ЗОЛОТО", "ОБА");
        // пульты и мониторы у восточной стены
        for (int z : new int[]{2, 3, 11, 12}) {
            b.set(sx - 2, 1, z, Builder.stairs(Blocks.SMOOTH_QUARTZ_STAIRS, Direction.EAST, false));
            b.set(sx - 1, 2, z, Blocks.BLACK_STAINED_GLASS);
            b.set(sx - 1, 3, z, z % 2 == 0 ? Blocks.CYAN_STAINED_GLASS : Blocks.BLACK_STAINED_GLASS);
        }
        barsA.setInstant(b.level, false);
        barsB.setInstant(b.level, false);
    }

    private List<BlockPos> lamps() {
        List<BlockPos> out = new ArrayList<>();
        for (int x = 2; x < sx - 2; x += 4)
            for (int z = 2; z < sz - 2; z += 4)
                for (int dx = 0; dx <= 1; dx++)
                    for (int dz = 0; dz <= 1; dz++) out.add(at(x + dx, sy - 1, z + dz));
        return out;
    }

    @Override
    public void placeBeforeStart(ServerPlayer p) {
        int cz = isHost(p) ? CELL_Z[0] : CELL_Z[1];
        cx.teleport(p, v(CX + 0.5, 1, cz + 0.5), isHost(p) ? 0f : 180f, 0f);
    }

    @Override
    public Vec3 checkpoint(ServerPlayer p) {
        if (phase <= 2) {
            int cz = isHost(p) ? CELL_Z[0] : CELL_Z[1];
            return v(CX + 0.5, 1, cz + 0.5);
        }
        return v(10.5, 1, 7.5);
    }

    private void lightsOn(boolean animated) {
        List<BlockPos> ls = lamps();
        for (int i = 0; i < ls.size(); i += 4) {
            final int from = i;
            Runnable r = () -> {
                for (int k = from; k < from + 4 && k < ls.size(); k++) set(ls.get(k), Blocks.SEA_LANTERN.defaultBlockState());
                cx.sound(ls.get(from), SoundEvents.BEACON_POWER_SELECT, 0.7f, 1.5f);
            };
            if (animated) later(4 + i * 2, r);
            else r.run();
        }
    }

    @Override
    protected void onActivate(boolean firstTime) {
        phase = 1;
        if (firstTime) {
            say("c00.intro.1", () -> lightsOn(true));
            for (int i = 2; i <= 6; i++) say("c00.intro." + i);
        } else lightsOn(false);
        say("c00.levers", () -> {
            phase = 2;
            phaseSince = now();
            setObjective("Выберитесь из камер. Рычаг открывает НЕ вашу камеру.");
        });
    }

    @Override
    protected boolean onUse(ServerPlayer p, BlockPos pos) {
        if (phase < 2) {
            if (pos.equals(lever(0)) || pos.equals(lever(1))) {
                sayNow("c00.early", null);
                return true;
            }
            return false;
        }
        if (phase != 2) return false;
        boolean solo = players().size() < 2;
        if (pos.equals(lever(0)) && !bOpen) {
            bOpen = true;
            barsB.open(cx, this);
            if (solo) { aOpen = true; barsA.open(cx, this); }
            puff(1);
            sayNow(aOpen ? "c00.both" : "c00.oneopen", p);
        } else if (pos.equals(lever(1)) && !aOpen) {
            aOpen = true;
            barsA.open(cx, this);
            if (solo) { bOpen = true; barsB.open(cx, this); }
            puff(0);
            sayNow(bOpen ? "c00.both" : "c00.oneopen", p);
        }
        if (aOpen && bOpen) {
            phase = 3;
            phaseSince = now();
            setObjective("Встаньте вдвоём на золотые плиты.");
        }
        return false;
    }

    private void puff(int cell) {
        cx.particles(ParticleTypes.CLOUD, Vec3.atCenterOf(at(CX, 2, cell == 0 ? 4 : 10)), 20, 0.8, 0.02);
    }

    @Override
    protected void onTick(long t) {
        if (phase == 2 && !idleSaid && t - phaseSince > 20 * 30) {
            idleSaid = true;
            say("c00.idle");
        }
        if (phase == 3) {
            int need = Math.min(2, players().size());
            int on = 0;
            for (int[] pl : PLATES) {
                for (ServerPlayer p : players())
                    if (p.onGround() && p.blockPosition().below().equals(at(pl[0], 0, pl[1]))) { on++; break; }
            }
            if (on >= need && need > 0) {
                phase = 4;
                win();
            }
        }
    }

    @Override
    protected void onReset() {
        phase = 0;
        aOpen = bOpen = idleSaid = false;
    }

    @Override
    protected void onSkip() {
        lightsOn(false);
        barsA.setInstant(level(), true);
        barsB.setInstant(level(), true);
    }

    @Override
    public String debug() {
        return "phase=" + phase;
    }
}
