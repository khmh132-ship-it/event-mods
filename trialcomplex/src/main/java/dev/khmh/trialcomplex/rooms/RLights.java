package dev.khmh.trialcomplex.rooms;

import dev.khmh.trialcomplex.build.Builder;
import dev.khmh.trialcomplex.build.Theme;
import dev.khmh.trialcomplex.game.Room;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** «Свет»: шагнул на плиту — она и соседи (крестом) переключаются. Зажгите все. Между плитами — дорожки. */
public class RLights extends Room {
    private static final BlockState ON = Blocks.OCHRE_FROGLIGHT.defaultBlockState();
    private static final BlockState OFF = Blocks.BLACK_CONCRETE.defaultBlockState();
    private static final int FX = 4, FZ = 3;

    private final int n, presses;
    private final boolean diag;
    private boolean[][] lit, start;
    private boolean done;
    private final Map<UUID, Integer> onTile = new HashMap<>();

    public RLights(String id, int level) {
        super(id, "Свет " + RLaser.roman(level), (level == 4 ? 4 : level + 2) * 3 + 1 + 8, 9, (level == 4 ? 4 : level + 2) * 3 + 1 + 6);
        this.n = level == 4 ? 4 : level + 2;
        this.diag = level == 4;
        this.presses = level == 1 ? 3 : level == 2 ? 5 : level == 3 ? 8 : 6;
    }

    private BlockPos tileCorner(int i, int j) {
        return at(FX + 1 + 3 * i, 0, FZ + 1 + 3 * j);
    }

    private BlockPos resetButton() {
        return at(2, 2, 1);
    }

    @Override
    protected void build(Builder b) {
        Theme.MECH.shell(b, sx, sy, sz);
        int size = 3 * n + 1;
        b.fill(FX, 0, FZ, FX + size - 1, 0, FZ + size - 1, Blocks.POLISHED_ANDESITE);
        for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) paint(b, i, j, true);
        b.set(2, 2, 0, Blocks.REDSTONE_BLOCK);
        b.set(2, 2, 1, Builder.wallButton(Blocks.STONE_BUTTON, Direction.SOUTH));
        b.sign(2, 3, 1, Direction.SOUTH, DyeColor.ORANGE, "", "СБРОС", "ПОЛЯ", "");
        b.sign(sx - 3, 3, 1, Direction.SOUTH, DyeColor.WHITE, "ШАГ НА ПЛИТУ", "ПЕРЕКЛЮЧАЕТ ЕЁ", "И СОСЕДЕЙ", diag ? "ПО ДИАГОНАЛИ" : "КРЕСТОМ");
        // трубы под потолком
        for (int x = 1; x < sx - 1; x++) b.set(x, sy - 2, 1, Builder.axis(Blocks.POLISHED_BASALT, Direction.Axis.X));
        for (int x = 1; x < sx - 1; x += 5) b.set(x, sy - 2, 1, Blocks.WAXED_COPPER_BLOCK);
    }

    private void paint(Builder b, int i, int j, boolean on) {
        BlockPos c = local(tileCorner(i, j));
        for (int dx = 0; dx < 2; dx++) for (int dz = 0; dz < 2; dz++) b.set(c.getX() + dx, 0, c.getZ() + dz, on ? ON : OFF);
    }

    private void draw(int i, int j) {
        BlockPos c = tileCorner(i, j);
        for (int dx = 0; dx < 2; dx++) for (int dz = 0; dz < 2; dz++) set(c.offset(dx, 0, dz), lit[i][j] ? ON : OFF);
    }

    private void scramble() {
        RandomSource r = RandomSource.create();
        do {
            lit = new boolean[n][n];
            for (boolean[] row : lit) java.util.Arrays.fill(row, true);
            for (int k = 0; k < presses; k++) toggle(r.nextInt(n), r.nextInt(n), false);
        } while (allLit());
        start = new boolean[n][n];
        for (int i = 0; i < n; i++) start[i] = lit[i].clone();
        for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) draw(i, j);
    }

    private void toggle(int i, int j, boolean draw) {
        int[][] d = diag ? new int[][]{{0, 0}, {1, 1}, {-1, 1}, {1, -1}, {-1, -1}} : new int[][]{{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] o : d) {
            int a = i + o[0], c = j + o[1];
            if (a < 0 || c < 0 || a >= n || c >= n) continue;
            lit[a][c] = !lit[a][c];
            if (draw) draw(a, c);
        }
    }

    private boolean allLit() {
        for (boolean[] row : lit) for (boolean v : row) if (!v) return false;
        return true;
    }

    @Override
    protected void onActivate(boolean firstTime) {
        super.onActivate(firstTime);
        scramble();
        setObjective(diag ? "Зажгите все плиты. Теперь соседи — ПО ДИАГОНАЛИ." : "Зажгите все плиты. Шаг на плиту переключает её и соседей. Ходите по дорожкам.");
    }

    @Override
    protected void onStep(ServerPlayer p, BlockPos floor) {
        if (done || lit == null) return;
        BlockPos l = local(floor);
        int rx = l.getX() - FX - 1, rz = l.getZ() - FZ - 1;
        int tile = -1;
        if (l.getY() == 0 && rx >= 0 && rz >= 0 && rx % 3 < 2 && rz % 3 < 2 && rx / 3 < n && rz / 3 < n) tile = (rx / 3) * 100 + rz / 3;
        Integer prev = onTile.put(p.getUUID(), tile);
        if (tile < 0 || (prev != null && prev == tile)) return;
        toggle(tile / 100, tile % 100, true);
        cx.sound(floor, SoundEvents.STONE_BUTTON_CLICK_ON, 0.8f, 1.2f);
        progress();
        if (allLit()) {
            done = true;
            win();
        }
    }

    @Override
    protected boolean onUse(ServerPlayer p, BlockPos pos) {
        if (pos.equals(resetButton()) && !done && start != null) {
            for (int i = 0; i < n; i++) lit[i] = start[i].clone();
            for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) draw(i, j);
            onTile.clear();
            sayNow("lights.reset", p);
        }
        return false;
    }

    @Override
    protected void onReset() {
        done = false;
        lit = null;
        onTile.clear();
    }

    @Override
    public String debug() {
        if (lit == null) return "-";
        StringBuilder sb = new StringBuilder("off=");
        for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) if (!lit[i][j]) sb.append(i).append(',').append(j).append(';');
        return sb.toString();
    }
}
