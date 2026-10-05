package dev.khmh.trialcomplex.rooms;

import dev.khmh.trialcomplex.build.Builder;
import dev.khmh.trialcomplex.build.Palette;
import dev.khmh.trialcomplex.build.Theme;
import dev.khmh.trialcomplex.game.Room;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Рыцари и лжецы. Залы с дверями; на каждой — утверждение. Рыцарь всегда говорит правду, лжец всегда лжёт.
 * Кнопка у двери — выбор. Неверно — пол зала проваливается. Задачи подобраны tools/levels/knights_gen.py.
 */
public class RKnights extends Room {
    record Hall(String[] said, int exit) {}

    static final Hall[][] LEVELS = {
            {new Hall(new String[]{"СРЕДИ НАС РОВНО 2 ЛЖЕЦА", "ДВЕРЬ 1 — РЫЦАРЬ", "ВЫХОД НЕ ЗА ДВЕРЬЮ 2"}, 1),
                    new Hall(new String[]{"ДВЕРИ 1 И 2 — РАЗНОГО ТИПА", "ВЫХОД НЕ ЗА ДВЕРЬЮ 1", "СРЕДИ НАС РОВНО 3 ЛЖЕЦА"}, 0)},
            {new Hall(new String[]{"ДВЕРЬ 4 — РЫЦАРЬ", "ВЫХОД ЗА ДВЕРЬЮ ЛЖЕЦА", "ВЫХОД ЗА ДВЕРЬЮ 3", "ДВЕРИ 2 И 3 — РАЗНОГО ТИПА"}, 2),
                    new Hall(new String[]{"СРЕДИ НАС НЕТ ЛЖЕЦОВ", "СРЕДИ НАС РОВНО 1 ЛЖЕЦ", "МЫ ВСЕ ЧЕТВЕРО ЛЖЕЦЫ", "ВЫХОД ЗА ДВЕРЬЮ 4"}, 3)},
            {new Hall(new String[]{"МЫ ВСЕ ЧЕТВЕРО ЛЖЕЦЫ", "ВЫХОД ЗА ДВЕРЬЮ 4", "ДВЕРЬ 4 — РЫЦАРЬ", "ДВЕРИ 1 И 2 — РАЗНОГО ТИПА"}, 3),
                    new Hall(new String[]{"ДВЕРЬ 2 — РЫЦАРЬ", "ДВЕРЬ 4 — РЫЦАРЬ", "ВЫХОД ЗА ДВЕРЬЮ 1", "ВЫХОД ЗА ДВЕРЬЮ ЛЖЕЦА"}, 2)},
    };

    private final Hall[] halls;
    private int hall;
    private boolean busy, done;

    public RKnights(String id, int level) {
        super(id, "Рыцари и лжецы " + RLaser.roman(level), 11 * LEVELS[level - 1].length + 4, 8, 13);
        this.halls = LEVELS[level - 1];
    }

    private int h0(int k) {
        return 1 + 11 * k;
    }

    private int doorZ(int k, int i) {
        int n = halls[k].said.length;
        return n == 3 ? 3 + 3 * i : 2 + 3 * i;
    }

    private BlockPos button(int k, int i) {
        return at(h0(k) + 8, 2, doorZ(k, i) - 1);
    }

    @Override
    protected void build(Builder b) {
        Theme.LOGIC.shell(b, sx, sy, sz);
        for (int k = 0; k < halls.length; k++) {
            int wx = h0(k) + 9;
            b.fill(wx, 1, 1, wx, sy - 2, sz - 2, Palette.of(Blocks.STONE_BRICKS, 3, Blocks.MOSSY_STONE_BRICKS, 1));
            for (int i = 0; i < halls[k].said.length; i++) {
                int z = doorZ(k, i);
                b.set(wx, 1, z, Blocks.IRON_BLOCK);
                b.set(wx, 2, z, Blocks.IRON_BLOCK);
                b.set(wx, 3, z, Blocks.CHISELED_STONE_BRICKS);
                b.set(h0(k) + 8, 2, z - 1, Builder.wallButton(Blocks.STONE_BUTTON, Direction.WEST));
                List<String> lines = wrap("ДВЕРЬ " + (i + 1) + ": " + halls[k].said[i], 14);
                String[] top = new String[4], bot = new String[4];
                for (int j = 0; j < 4; j++) { top[j] = j < lines.size() ? lines.get(j) : ""; bot[j] = j + 4 < lines.size() ? lines.get(j + 4) : ""; }
                b.sign(h0(k) + 8, 4, z, Direction.WEST, DyeColor.WHITE, top);
                if (lines.size() > 4) b.sign(h0(k) + 8, 3, z, Direction.WEST, DyeColor.WHITE, bot);
            }
            // правила на северной стене зала
            b.sign(h0(k) + 2, 3, 1, Direction.SOUTH, DyeColor.YELLOW, "РЫЦАРЬ ВСЕГДА", "ГОВОРИТ ПРАВДУ.", "ЛЖЕЦ ВСЕГДА", "ЛЖЁТ.");
            b.sign(h0(k) + 5, 3, 1, Direction.SOUTH, DyeColor.YELLOW, "КАЖДАЯ ДВЕРЬ —", "РЫЦАРЬ ИЛИ ЛЖЕЦ.", "ВЫХОД — РОВНО", "ОДИН.");
            b.set(h0(k) + 1, 1, 11, Blocks.LECTERN);
            b.set(h0(k) + 7, 1, 11, Blocks.CHISELED_BOOKSHELF);
        }
    }

    static List<String> wrap(String s, int w) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : s.split(" ")) {
            if (line.length() > 0 && line.length() + 1 + word.length() > w) { out.add(line.toString()); line.setLength(0); }
            if (line.length() > 0) line.append(' ');
            line.append(word);
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }

    @Override
    public Vec3 checkpoint(ServerPlayer p) {
        return v(h0(hall) + 1.5, 1, sz / 2 + 0.5);
    }

    @Override
    protected boolean onFall(ServerPlayer p) {
        cx.teleport(p, checkpoint(p), -90f, 0f);
        return true;
    }

    @Override
    protected void onActivate(boolean firstTime) {
        super.onActivate(firstTime);
        hall = 0;
        setObjective("Прочитайте двери. Ровно одна ведёт дальше. Нажмите кнопку у неё.");
    }

    @Override
    protected boolean onUse(ServerPlayer p, BlockPos pos) {
        if (busy || done) return false;
        for (int i = 0; i < halls[hall].said.length; i++) {
            if (!pos.equals(button(hall, i))) continue;
            int wx = h0(hall) + 9, z = doorZ(hall, i);
            if (i == halls[hall].exit) {
                set(at(wx, 1, z), Blocks.AIR.defaultBlockState());
                set(at(wx, 2, z), Blocks.AIR.defaultBlockState());
                cx.sound(at(wx, 1, z), SoundEvents.IRON_DOOR_OPEN, 1f, 0.8f);
                progress();
                hall++;
                if (hall >= halls.length) { done = true; win(); }
                else { ding(true); voice().interrupt(voice().pick("knights.next")); }
            } else {
                busy = true;
                fail();
                voice().interrupt(voice().pickFor("knights.wrong", p));
                int k = hall;
                cx.sound(at(h0(k) + 4, 1, 6), SoundEvents.PISTON_CONTRACT, 1.5f, 0.5f);
                for (int x = h0(k); x <= h0(k) + 8; x++) for (int zz = 1; zz <= sz - 2; zz++) set(at(x, 0, zz), Blocks.AIR.defaultBlockState());
                later(14, () -> {
                    Builder b = new Builder(level(), origin, id.hashCode());
                    for (int x = h0(k); x <= h0(k) + 8; x++) for (int zz = 1; zz <= sz - 2; zz++) b.set(x, 0, zz, Theme.LOGIC.floor());
                    busy = false;
                });
            }
            return false;
        }
        return false;
    }

    @Override
    protected void onReset() {
        hall = 0;
        busy = done = false;
    }

    @Override
    public String debug() {
        return "hall=" + hall + " answer=" + (hall < halls.length ? halls[hall].exit : -1);
    }
}
