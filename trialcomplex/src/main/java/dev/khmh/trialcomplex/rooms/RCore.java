package dev.khmh.trialcomplex.rooms;

import dev.khmh.trialcomplex.build.Builder;
import dev.khmh.trialcomplex.build.Palette;
import dev.khmh.trialcomplex.build.PixelText;
import dev.khmh.trialcomplex.build.Theme;
import dev.khmh.trialcomplex.game.Layout;
import dev.khmh.trialcomplex.game.ProgressData;
import dev.khmh.trialcomplex.game.Room;
import dev.khmh.trialcomplex.parts.Keypad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Финал. Ядро Голоса. 1) код из цифр в шлюзах; 2) две кнопки по разные стороны ядра — одновременно;
 * 3) стоять на плитах, пока Голос уговаривает не отключать. Потом — титры и статистика.
 */
public class RCore extends Room {
    private static final BlockState OFF = Blocks.BLACK_CONCRETE.defaultBlockState();
    private static final int C = 12; // центр
    private final Keypad keypad = new Keypad(new BlockPos(9, 5, 1), Direction.SOUTH);
    private int phase; // 0 код, 1 синхрон, 2 плиты, 3 отключение, 4 конец
    private String entered = "";
    private boolean locked;
    private long pressN = -100, pressS = -100, holdStart = -1;
    private int pleaIdx;

    public RCore() {
        super("core", "Ядро", 25, 14, 25);
    }

    @Override
    public boolean countsAsTrial() {
        return false;
    }

    private BlockPos btn(int side) {
        return side == 0 ? at(C, 2, C - 4) : at(C, 2, C + 4);
    }

    private BlockPos pad(int k) {
        return k == 0 ? at(C - 5, 0, C) : at(C + 5, 0, C);
    }

    @Override
    protected void build(Builder b) {
        Theme.CORE.shell(b, sx, sy, sz);
        // ядро
        b.fill(C - 1, 1, C - 1, C + 1, sy - 3, C + 1, Blocks.CRYING_OBSIDIAN);
        b.set(C, sy - 2, C, Blocks.SHROOMLIGHT);
        for (int y = 2; y < sy - 3; y += 3) b.fill(C - 1, y, C - 1, C + 1, y, C + 1, Blocks.SHROOMLIGHT);
        // щит
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++)
                if (Math.max(Math.abs(dx), Math.abs(dz)) == 3) b.fill(C + dx, 1, C + dz, C + dx, 6, C + dz, Blocks.RED_STAINED_GLASS);
        // кнопки синхрона (снаружи щита, на столбиках)
        for (int side = 0; side < 2; side++) {
            BlockPos l = local(btn(side));
            b.set(l.getX(), 1, l.getZ(), Blocks.POLISHED_BLACKSTONE_BRICKS);
            b.set(l.getX(), 2, l.getZ(), Builder.floorButton(Blocks.CRIMSON_BUTTON));
        }
        // плиты отключения
        for (int k = 0; k < 2; k++) {
            BlockPos l = local(pad(k));
            b.fill(l.getX() - 1, 0, l.getZ() - 1, l.getX() + 1, 0, l.getZ() + 1, Blocks.GOLD_BLOCK);
        }
        // табло и клавиатура
        b.fill(1, 8, 0, 17, 14 - 2, 0, Blocks.POLISHED_BLACKSTONE_BRICKS);
        b.fill(2, 8, 0, 16, 12, 0, OFF);
        PixelText.draw(b.level, b.pos(2, 12, 0), Direction.EAST, "____", Blocks.GRAY_CONCRETE.defaultBlockState(), OFF);
        keypad.build(b);
        b.sign(16, 4, 1, Direction.SOUTH, DyeColor.RED, "ПАРОЛЬ ЯДРА:", "ЦИФРЫ, ЧТО", "ВЫ ВИДЕЛИ", "В ШЛЮЗАХ");
        // трубы к ядру
        for (int z = 1; z < C - 3; z++) b.set(C, sy - 2, z, Builder.axis(Blocks.POLISHED_BASALT, Direction.Axis.Z));
        for (int z = C + 4; z < sz - 1; z++) b.set(C, sy - 2, z, Builder.axis(Blocks.POLISHED_BASALT, Direction.Axis.Z));
    }

    private void redraw(BlockState on) {
        PixelText.draw(level(), at(2, 12, 0), Direction.EAST, (entered + "____").substring(0, 4), on, OFF);
    }

    @Override
    protected void onActivate(boolean firstTime) {
        super.onActivate(firstTime);
        phase = 0;
        setObjective("Введите пароль ядра: цифры, которые вы видели в шлюзах, по порядку.");
    }

    @Override
    protected boolean onUse(ServerPlayer p, BlockPos pos) {
        if (phase == 0 && !locked) {
            String k = keypad.keyAt(local(pos));
            if (k == null) return false;
            cx.sound(pos, SoundEvents.NOTE_BLOCK_HAT.value(), 0.8f, 1.5f);
            if (k.equals("C")) entered = "";
            else if (k.equals("OK")) {
                if (entered.equals(Layout.finalCode())) {
                    locked = true;
                    redraw(Blocks.VERDANT_FROGLIGHT.defaultBlockState());
                    shield(false);
                    phase = 1;
                    voice().interrupt("core.shield");
                    setObjective("Нажмите две кнопки по разные стороны ядра ОДНОВРЕМЕННО.");
                } else {
                    fail();
                    locked = true;
                    redraw(Blocks.REDSTONE_BLOCK.defaultBlockState());
                    sayNow("core.wrongcode", p);
                    later(25, () -> { locked = false; entered = ""; redraw(Blocks.OCHRE_FROGLIGHT.defaultBlockState()); });
                }
                return false;
            } else if (entered.length() < 4) entered += k;
            redraw(Blocks.OCHRE_FROGLIGHT.defaultBlockState());
            progress();
            return false;
        }
        if (phase == 1) {
            if (pos.equals(btn(0))) pressN = now();
            else if (pos.equals(btn(1))) pressS = now();
            else return false;
            boolean solo = players().size() < 2;
            if (solo || Math.abs(pressN - pressS) <= 10) {
                phase = 2;
                progress();
                voice().interrupt("core.sync", () -> setObjective("Встаньте ОБА на золотые плиты и стойте. Что бы он ни говорил."));
            } else later(12, () -> { if (phase == 1 && Math.abs(pressN - pressS) > 10) sayNow("core.desync", null); });
        }
        return false;
    }

    private void shield(boolean on) {
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++)
                if (Math.max(Math.abs(dx), Math.abs(dz)) == 3)
                    for (int y = 1; y <= 6; y++) set(at(C + dx, y, C + dz), (on ? Blocks.RED_STAINED_GLASS : Blocks.AIR).defaultBlockState());
        cx.sound(at(C, 3, C), on ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_DEACTIVATE, 2f, 0.6f);
    }

    @Override
    protected void onTick(long t) {
        // ядро живое: кольца частиц и гул (на последнем этапе — быстрее и выше)
        if (phase <= 2) {
            Vec3 core = Vec3.atCenterOf(at(C, 4, C));
            if (t % 2 == 0) {
                double a = t * (phase == 2 ? 0.3 : 0.12);
                for (int i = 0; i < 3; i++) {
                    // пока стоит щит (до y=6), частицы над ним: сквозь цветное стекло они не рисуются
                    double lo = phase == 0 ? 3 : -3, hi = 8;
                    double ang = a + i * 2.0944, h = lo + ((t + i * 20) % 60) / 60.0 * (hi - lo);
                    level().sendParticles(ParticleTypes.END_ROD, core.x + Math.cos(ang) * 2.2, core.y + h, core.z + Math.sin(ang) * 2.2, 2, 0.05, 0.05, 0.05, 0);
                }
            }
            if (t % (phase == 2 ? 30 : 80) == 0) cx.sound(at(C, 4, C), SoundEvents.BEACON_AMBIENT, 2f, phase == 2 ? 1.5f : 0.6f);
        }
        if (phase != 2) return;
        int need = Math.min(2, players().size()), on = 0;
        for (int k = 0; k < 2; k++)
            for (ServerPlayer p : players()) {
                BlockPos f = p.blockPosition().below();
                BlockPos c = pad(k);
                if (Math.abs(f.getX() - c.getX()) <= 1 && Math.abs(f.getZ() - c.getZ()) <= 1 && f.getY() == c.getY()) { on++; break; }
            }
        if (on < need) {
            if (holdStart >= 0) { holdStart = -1; sayNow("core.stepoff", null); }
            return;
        }
        if (holdStart < 0) { holdStart = t; pleaIdx = 0; }
        long held = t - holdStart;
        // мольбы Голоса, пока держат плиты (ровно 30 секунд)
        if (held % 120 == 0 && pleaIdx < 5) { voice().interrupt("core.plea." + (++pleaIdx)); }
        if (t % 10 == 0) cx.particles(ParticleTypes.ELECTRIC_SPARK, Vec3.atCenterOf(at(C, 4, C)), 20, 1.2, 0.2);
        if (held >= 600) shutdown();
    }

    private void shutdown() {
        phase = 3;
        setObjective("");
        voice().interrupt("core.dying");
        // гаснет свет
        for (int i = 0; i < 12; i++) {
            final int k = i;
            later(10 + i * 6, () -> {
                for (int x = 1; x < sx - 1; x++) for (int z = 1; z < sz - 1; z++) {
                    BlockPos p = at(x, sy - 1, z);
                    if ((x * 7 + z * 13) % 12 == k && level().getBlockState(p).is(Blocks.SHROOMLIGHT)) set(p, Blocks.NETHER_BRICKS.defaultBlockState());
                }
                cx.sound(at(C, 4, C), SoundEvents.REDSTONE_TORCH_BURNOUT, 1f, 0.5f + k * 0.05f);
            });
        }
        voice().say(null, () -> {
            ProgressData d = cx.data();
            long mins = (level().getGameTime() - d.startedAt) / 20 / 60;
            cx.title("КОМПЛЕКС ПРОЙДЕН", mins + " мин · подсказок " + d.sum(d.hints) + " · ошибок " + d.sum(d.fails));
            for (ServerPlayer p : players()) {
                p.sendSystemMessage(Component.literal("§6§lИСПЫТАТЕЛЬНЫЙ КОМПЛЕКС ПРОЙДЕН"));
                p.sendSystemMessage(Component.literal("§eВремя: §f" + mins + " мин"));
                p.sendSystemMessage(Component.literal("§eПодсказок: §f" + d.sum(d.hints) + "§e, пропусков: §f" + d.sum(d.skips) + "§e, ошибок: §f" + d.sum(d.fails)));
                p.sendSystemMessage(Component.literal("§eПадений: §fКхмх — " + d.falls.getInt("khmh") + ", Итачи — " + d.falls.getInt("itachi")));
                p.sendSystemMessage(Component.literal("§7Спасибо за игру. Голос вас (не) любит."));
            }
            // салют
            for (int i = 0; i < 8; i++) {
                final int k = i;
                later(i * 12, () -> {
                    Vec3 at = Vec3.atCenterOf(at(3 + (k * 7) % (sx - 6), sy - 4, 3 + (k * 11) % (sz - 6)));
                    level().sendParticles(ParticleTypes.FIREWORK, at.x, at.y, at.z, 60, 0.2, 0.2, 0.2, 0.25);
                    cx.sound(BlockPos.containing(at), k % 2 == 0 ? SoundEvents.FIREWORK_ROCKET_LARGE_BLAST : SoundEvents.FIREWORK_ROCKET_TWINKLE, 2f, 1f);
                });
            }
            later(200, () -> voice().interrupt("core.reboot", () -> { phase = 4; solve(); }));
        });
    }

    @Override
    protected void onReset() {
        phase = 0;
        entered = "";
        locked = false;
        holdStart = -1;
    }

    @Override
    public String debug() {
        return "phase=" + phase + " code=" + Layout.finalCode();
    }
}
