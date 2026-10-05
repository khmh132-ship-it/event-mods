package dev.khmh.trialcomplex.game;

import dev.khmh.trialcomplex.build.Builder;
import dev.khmh.trialcomplex.build.Palette;
import dev.khmh.trialcomplex.voice.Voice;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Испытание. Геометрия строится в локальных координатах (см. {@link Builder}).
 * Вход — западная стена (x=0), выход — восточная (x=sx-1). Проёмы 3×4, по центру entryZ/exitZ.
 */
public abstract class Room {
    public final String id;
    public final String title;
    public final int sx, sy, sz;
    protected int entryZ, exitZ;

    protected Complex cx;
    protected BlockPos origin;
    protected Gate entry, exit;

    int epoch;
    boolean activated, solved, introDone;
    long activatedAt, lastProgress;
    private String objective = "";

    protected Room(String id, String title, int sx, int sy, int sz) {
        this.id = id;
        this.title = title;
        this.sx = sx;
        this.sy = sy;
        this.sz = sz;
        this.entryZ = sz / 2;
        this.exitZ = sz / 2;
    }

    void place(Complex cx, BlockPos origin) {
        this.cx = cx;
        this.origin = origin;
        this.entry = new Gate(at(0, 1, entryZ - 1), at(0, 4, entryZ + 1), doorPalette());
        this.exit = new Gate(at(sx - 1, 1, exitZ - 1), at(sx - 1, 4, exitZ + 1), doorPalette());
        if (sy > 7) {
            entry.withIndicator(at(0, 5, entryZ));
            exit.withIndicator(at(sx - 1, 5, exitZ));
        }
        init();
    }

    /** Вызывается после того, как комнате назначено место в мире (до любой постройки). */
    protected void init() {}

    // ---------- координаты ----------

    public BlockPos at(int x, int y, int z) {
        return origin.offset(x, y, z);
    }

    public Vec3 v(double x, double y, double z) {
        return new Vec3(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
    }

    public BlockPos local(BlockPos global) {
        return global.subtract(origin);
    }

    public BlockPos origin() {
        return origin;
    }

    /** Внутренность комнаты (за плоскостью входной двери). */
    public AABB interior() {
        return new AABB(v(1, 1, 1), v(sx - 1, sy - 1, sz - 1));
    }

    public boolean inside(Player p) {
        return interior().contains(p.position());
    }

    public Vec3 spawn() {
        return v(2.5, 1, entryZ + 0.5);
    }

    public float spawnYaw() {
        return -90f;
    }

    /** Ниже этой высоты — «упал в пустоту». */
    public double voidY() {
        return origin.getY() - 6;
    }

    public Vec3 entryDoorGlobal() {
        return v(0, 1, entryZ);
    }

    public Vec3 exitDoorGlobal() {
        return v(sx - 1, 1, exitZ);
    }

    // ---------- постройка ----------

    protected Palette doorPalette() {
        return Palette.of(Blocks.IRON_BLOCK);
    }

    /** Полная постройка комнаты в исходном состоянии. Должна быть детерминированной. */
    public final void buildAll(ServerLevel lvl) {
        Builder b = new Builder(lvl, origin, id.hashCode() * 31L + 7);
        build(b);
        if (sy > 7) {
            if (hasEntryDoor()) dev.khmh.trialcomplex.build.Styles.doorFrame(b, 0, entryZ, sy);
            dev.khmh.trialcomplex.build.Styles.doorFrame(b, sx - 1, exitZ, sy);
        }
        if (hasEntryDoor()) entry.setInstant(lvl, true);
        exit.setInstant(lvl, false);
    }

    protected abstract void build(Builder b);

    protected boolean hasEntryDoor() {
        return true;
    }

    public boolean hasEntry() {
        return hasEntryDoor();
    }

    // ---------- жизненный цикл ----------

    /** Комната «включилась» — игрок вошёл внутрь. firstTime=false после сброса. */
    protected void onActivate(boolean firstTime) {}

    protected void onTick(long t) {}

    /** ПКМ по блоку внутри комнаты. true — отменить ванильное действие. */
    protected boolean onUse(ServerPlayer p, BlockPos pos) {
        return false;
    }

    /** Игрок встал на новый блок пола (floor — блок под ногами). */
    protected void onStep(ServerPlayer p, BlockPos floor) {}

    /** Сбросить внутреннее состояние логики (геометрия уже перестроена). */
    protected void onReset() {}

    /** Вызывается при /puzzle skip до засчитывания. */
    protected void onSkip() {}

    /** Можно ли закрыть входную дверь (по умолчанию — когда все внутри). */
    protected boolean lockEntryWhenAllInside() {
        return true;
    }

    // ---------- помощники для наследников ----------

    protected Voice voice() {
        return cx.voice();
    }

    protected ServerLevel level() {
        return cx.level();
    }

    protected long now() {
        return cx.tick();
    }

    protected void later(int ticks, Runnable r) {
        cx.later(this, ticks, r);
    }

    protected void progress() {
        lastProgress = cx.tick();
    }

    protected void fail() {
        cx.data().inc(cx.data().fails, id);
        progress();
    }

    protected void solve() {
        cx.solved(this);
    }

    public boolean isActive() {
        return activated && !solved;
    }

    public boolean isSolved() {
        return solved;
    }

    public String objective() {
        return objective;
    }

    protected void setObjective(String text) {
        if (!text.equals(objective)) {
            objective = text;
            cx.hudDirty();
        }
    }

    public int hintCount() {
        return 3;
    }

    /** Отладочная строка для /puzzle dev peek. */
    public String debug() {
        return "";
    }

    /** Считается ли комната испытанием в HUD (вступление и концовка — нет). */
    public boolean countsAsTrial() {
        return true;
    }
}
