package dev.khmh.trialcomplex.rooms;

import dev.khmh.trialcomplex.build.Builder;
import dev.khmh.trialcomplex.build.Styles;
import dev.khmh.trialcomplex.game.Room;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** 0. Пробуждение: капсулы, знакомство с Голосом, «первое испытание» — нажать кнопку. */
public class R00Wake extends Room {
    private static final int[][] PODS = {{3, 4}, {3, 8}};
    private static final BlockState LAMP_OFF = Blocks.GRAY_CONCRETE.defaultBlockState();
    private static final BlockState LAMP_ON = Blocks.SEA_LANTERN.defaultBlockState();

    private int phase; // 0 ждём, 1 вступление, 2 кнопка, 3 нажато
    private long buttonSince;
    private boolean idleSaid, earlySaid;

    public R00Wake() {
        super("r00", "Пробуждение", 15, 8, 13);
    }

    @Override
    protected boolean hasEntryDoor() {
        return false;
    }

    private BlockPos button() {
        return at(10, 2, entryZ);
    }

    @Override
    protected void build(Builder b) {
        Styles.lab(b, sx, sy, sz, LAMP_OFF, Blocks.CYAN_TERRACOTTA);
        // капсулы
        for (int[] pod : PODS) {
            int x = pod[0], z = pod[1];
            b.set(x, 0, z, Blocks.CRYING_OBSIDIAN);
            for (int y = 1; y <= 2; y++) {
                b.set(x - 1, y, z, Blocks.LIGHT_BLUE_STAINED_GLASS);
                b.set(x + 1, y, z, Blocks.LIGHT_BLUE_STAINED_GLASS);
                b.set(x, y, z - 1, Blocks.LIGHT_BLUE_STAINED_GLASS);
                b.set(x, y, z + 1, Blocks.LIGHT_BLUE_STAINED_GLASS);
                for (int dx : new int[]{-1, 1})
                    for (int dz : new int[]{-1, 1}) b.set(x + dx, y, z + dz, Blocks.IRON_BLOCK);
            }
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++) {
                    b.set(x + dx, 0, z + dz, dx == 0 && dz == 0 ? Blocks.CRYING_OBSIDIAN : Blocks.POLISHED_BLACKSTONE);
                    b.set(x + dx, 3, z + dz, Builder.slab(Blocks.SMOOTH_QUARTZ_SLAB, false));
                }
            b.set(x, 3, z, Blocks.IRON_BLOCK);
            b.set(x, 4, z, Blocks.CHAIN);
            b.set(x, 5, z, Blocks.CHAIN);
            b.set(x, 6, z, Blocks.CHAIN);
            b.sign(x - 2, 2, z, Direction.WEST, DyeColor.LIGHT_BLUE, "КАПСУЛА", x == 3 && z == 4 ? "№1" : "№2", "", "");
        }
        // постамент с кнопкой
        b.set(10, 1, entryZ, Blocks.POLISHED_BLACKSTONE_BRICKS);
        b.set(10, 2, entryZ, Builder.floorButton(Blocks.POLISHED_BLACKSTONE_BUTTON));
        for (int dz = -1; dz <= 1; dz += 2) b.set(10, 1, entryZ + dz, Builder.stairs(Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS,
                dz < 0 ? Direction.SOUTH : Direction.NORTH, false));
        b.sign(9, 1, entryZ, Direction.WEST, DyeColor.YELLOW, "", "НАЖМИ", "", "");
        // пульты у северной стены
        for (int x = 6; x <= 12; x += 3) {
            b.set(x, 1, 1, Builder.stairs(Blocks.SMOOTH_QUARTZ_STAIRS, Direction.NORTH, false));
            b.set(x + 1, 1, 1, Builder.stairs(Blocks.SMOOTH_QUARTZ_STAIRS, Direction.NORTH, false));
            b.set(x, 2, 0, Blocks.BLACK_STAINED_GLASS);
            b.set(x + 1, 2, 0, Blocks.BLACK_STAINED_GLASS);
            b.set(x, 3, 0, Blocks.CYAN_STAINED_GLASS);
            b.set(x + 1, 3, 0, Blocks.BLACK_STAINED_GLASS);
        }
        // вентиляция у южной стены
        for (int x = 6; x <= 12; x += 3) {
            b.set(x, 5, sz - 1, Blocks.IRON_BARS);
        }
        b.sign(sx - 2, 5, exitZ, Direction.WEST, DyeColor.RED, "", "ВЫХОД", "", "");
    }

    private List<BlockPos> lamps() {
        List<BlockPos> out = new ArrayList<>();
        for (int x = 2; x < sx - 2; x += 4)
            for (int z = 2; z < sz - 2; z += 4) {
                out.add(at(x, sy - 1, z));
                out.add(at(x + 1, sy - 1, z));
                out.add(at(x, sy - 1, z + 1));
                out.add(at(x + 1, sy - 1, z + 1));
            }
        return out;
    }

    /** Поставить игрока в капсулу (до начала). */
    public void placeInPod(ServerPlayer p) {
        int[] pod = cx.roles().isHost(p) ? PODS[0] : PODS[1];
        cx.teleport(p, v(pod[0] + 0.5, 1, pod[1] + 0.5), -90f, 0f);
    }

    private void lightsOn(boolean animated) {
        List<BlockPos> ls = lamps();
        // включаем группами по 4 (одна панель), с запада на восток
        for (int i = 0; i < ls.size(); i += 4) {
            final int from = i;
            Runnable r = () -> {
                for (int k = from; k < from + 4 && k < ls.size(); k++) level().setBlock(ls.get(k), LAMP_ON, Block.UPDATE_CLIENTS);
                cx.sound(ls.get(from), SoundEvents.BEACON_POWER_SELECT, 0.8f, 1.6f);
            };
            if (animated) later(6 + i * 2, r);
            else r.run();
        }
        if (animated) later(6, () -> cx.sound(at(7, 3, 6), SoundEvents.BEACON_ACTIVATE, 1.5f, 0.8f));
    }

    private void openPods() {
        for (int[] pod : PODS) {
            int x = pod[0], z = pod[1];
            for (int y = 1; y <= 2; y++)
                for (int dx = -1; dx <= 1; dx++)
                    for (int dz = -1; dz <= 1; dz++)
                        if (dx != 0 || dz != 0) level().setBlock(at(x + dx, y, z + dz), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            cx.sound(at(pod[0], 2, pod[1]), SoundEvents.GLASS_BREAK, 1f, 0.7f);
            cx.sound(at(pod[0], 2, pod[1]), SoundEvents.PISTON_CONTRACT, 1f, 1.2f);
            cx.particles(ParticleTypes.CLOUD, Vec3.atCenterOf(at(pod[0], 1, pod[1])), 30, 0.6, 0.02);
        }
    }

    @Override
    protected void onActivate(boolean firstTime) {
        phase = 1;
        if (firstTime) {
            voice().say("r00.intro.1", () -> lightsOn(true));
            voice().say("r00.intro.2");
            voice().say("r00.intro.3");
            voice().say("r00.intro.4");
            voice().say("r00.intro.5");
            voice().say("r00.intro.6");
            voice().say("r00.intro.7", this::openPods);
        } else {
            lightsOn(false);
            openPods();
        }
        voice().say("r00.button", () -> {
            phase = 2;
            buttonSince = now();
            setObjective("Нажмите кнопку. Да, это испытание.");
        });
    }

    @Override
    protected boolean onUse(ServerPlayer p, BlockPos pos) {
        if (!pos.equals(button())) return false;
        if (phase < 2) {
            if (!earlySaid) {
                earlySaid = true;
                voice().say("r00.early");
            }
            return true;
        }
        if (phase == 2) {
            phase = 3;
            setObjective("");
            voice().interrupt(voice().pickFor("r00.pressed", p), this::solve);
        }
        return false;
    }

    @Override
    protected void onTick(long t) {
        if (phase == 2 && !idleSaid && t - buttonSince > 20 * 25) {
            idleSaid = true;
            voice().say("r00.idle");
        }
    }

    @Override
    protected void onReset() {
        phase = 0;
        idleSaid = false;
        earlySaid = false;
    }

    @Override
    protected void onSkip() {
        lightsOn(false);
        openPods();
    }

    @Override
    public boolean countsAsTrial() {
        return false;
    }
}
