package dev.khmh.trialcomplex.rooms;

import dev.khmh.trialcomplex.build.Builder;
import dev.khmh.trialcomplex.build.Palette;
import dev.khmh.trialcomplex.game.Room;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Невидимый мост над пустотой (ночь). Тропа из невидимых блоков; видит её только тот,
 * кто НЕ на мосту, и только пока кто-то другой идёт. Уровень 2 — мигающие участки.
 * Уровень 3 — две дорожки, оба идут одновременно и ведут друг друга.
 */
public class RBridge extends Room {
    private static final int BX0 = 8, BX1 = 25;   // мост по x
    private static final BlockState SEEN = Blocks.SEA_LANTERN.defaultBlockState();
    private static final BlockState BLINK_ON = Blocks.VERDANT_FROGLIGHT.defaultBlockState();
    private static final BlockState BLINK_OFF = Blocks.MAGMA_BLOCK.defaultBlockState();

    private final int level;
    private final List<List<BlockPos>> lanes = new ArrayList<>();      // локальные клетки тропы
    private final Set<BlockPos> blink = new HashSet<>();               // локальные мигающие клетки
    private final int[][] laneZ;                                        // [дорожка] = {z0, z1}
    private boolean revealed, blinkOn = true;
    private final Set<UUID> arrived = new HashSet<>();
    private final Map<UUID, Set<Integer>> showing = new HashMap<>();
    private boolean doneSaid;

    public RBridge(String id, int level) {
        super(id, "Невидимый мост " + RLaser.roman(level), 34, 12, 21);
        this.level = level;
        this.laneZ = level == 3 ? new int[][]{{2, 8}, {12, 18}} : new int[][]{{3, 17}};
    }

    @Override
    protected void init() {
        lanes.clear();
        blink.clear();
        RandomSource r = RandomSource.create(id.hashCode() * 17L + level);
        for (int[] lz : laneZ) lanes.add(path(r, lz[0], lz[1]));
        if (level == 2 || level == 4) {
            List<BlockPos> p = lanes.get(0);
            for (int i = 6; i < p.size() - 4; i += level == 4 ? 4 : 7) {
                blink.add(p.get(i));
                blink.add(p.get(i + 1));
            }
        }
    }

    /** Случайная тропа слева направо без соприкосновений с самой собой. */
    private static List<BlockPos> path(RandomSource r, int z0, int z1) {
        for (int attempt = 0; attempt < 200; attempt++) {
            List<BlockPos> out = new ArrayList<>();
            int x = BX0, z = (z0 + z1) / 2;
            out.add(new BlockPos(x, 0, z));
            boolean ok = true;
            while (x < BX1 && ok) {
                // вбок 1–4 клетки с шансом, потом вперёд 1–2
                if (r.nextFloat() < 0.65f) {
                    int dir = r.nextBoolean() ? 1 : -1;
                    int len = 1 + r.nextInt(4);
                    for (int i = 0; i < len; i++) {
                        int nz = z + dir;
                        if (nz < z0 || nz > z1) break;
                        z = nz;
                        out.add(new BlockPos(x, 0, z));
                    }
                }
                int fw = 1 + r.nextInt(2);
                for (int i = 0; i < fw && x < BX1; i++) {
                    x++;
                    out.add(new BlockPos(x, 0, z));
                }
            }
            // проверка: никакие две непоследовательные клетки не соседствуют
            for (int i = 0; i < out.size() && ok; i++)
                for (int j = i + 2; j < out.size(); j++)
                    if (out.get(i).distManhattan(out.get(j)) <= 1) { ok = false; break; }
            if (ok && out.size() >= 24) return out;
        }
        List<BlockPos> straight = new ArrayList<>();
        for (int x = BX0; x <= BX1; x++) straight.add(new BlockPos(x, 0, (z0 + z1) / 2));
        return straight;
    }

    private BlockState railX() {
        return Blocks.GLASS_PANE.defaultBlockState().setValue(IronBarsBlock.NORTH, true).setValue(IronBarsBlock.SOUTH, true);
    }

    @Override
    protected void build(Builder b) {
        b.clear(0, -8, 0, sx - 1, sy - 1, sz - 1);
        Palette top = Palette.of(Blocks.STONE_BRICKS, 5, Blocks.MOSSY_STONE_BRICKS, 1, Blocks.CRACKED_STONE_BRICKS, 1);
        // платформы старта и финиша (парящие острова с «корнями»)
        for (int[] pr : new int[][]{{1, BX0 - 1}, {BX1 + 1, sx - 2}}) {
            {
                int za = 1, zb = sz - 2;
                b.fill(pr[0], 0, za, pr[1], 0, zb, top);
                b.fill(pr[0], -1, za, pr[1], -1, zb, Blocks.STONE_BRICKS);
                for (int d = 2; d <= 6; d++)
                    b.fill(pr[0] + d / 2, -d, za + d / 2, pr[1] - d / 2, -d, zb - d / 2,
                            Palette.of(Blocks.STONE, 3, Blocks.ANDESITE, 2, Blocks.TUFF, 1));
                // фонари
                for (int z : new int[]{za, zb}) {
                    b.set(pr[0], 1, z, Blocks.STONE_BRICK_WALL);
                    b.set(pr[0], 2, z, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
                    b.set(pr[1], 1, z, Blocks.STONE_BRICK_WALL);
                    b.set(pr[1], 2, z, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
                }
                // кромка у моста подсвечена
                int edge = pr[0] == 1 ? pr[1] : pr[0];
                for (int[] lz : laneZ) for (int z = lz[0]; z <= lz[1]; z++) b.set(edge, 0, z, Blocks.POLISHED_DEEPSLATE);
            }
        }
        // стены с дверями (вход/выход)
        for (int x : new int[]{0, sx - 1}) {
            b.fill(x, 0, entryZ - 4, x, 7, entryZ + 4, Palette.of(Blocks.DEEPSLATE_TILES, 3, Blocks.POLISHED_DEEPSLATE, 1));
            b.fill(x, 8, entryZ - 3, x, 8, entryZ + 3, Blocks.POLISHED_DEEPSLATE);
        }
        // смотровая вышка на старте (северная часть платформы), лестница с юга
        int tz = 4;
        b.fill(2, 1, tz - 2, 5, 5, tz + 1, Palette.of(Blocks.POLISHED_ANDESITE, 3, Blocks.STONE_BRICKS, 1));
        b.fill(1, 6, 1, 7, 6, 7, Blocks.SPRUCE_PLANKS);
        for (int y = 1; y <= 6; y++) b.set(3, y, tz + 2, Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.SOUTH));
        b.set(3, 6, tz + 2, Blocks.AIR);
        for (int z = 1; z <= 7; z++) b.set(7, 7, z, railX());
        b.set(1, 7, 1, Blocks.LANTERN); b.set(1, 7, 7, Blocks.LANTERN);
        b.sign(6, 7, 1, Direction.SOUTH, DyeColor.LIGHT_BLUE, "СМОТРОВАЯ", "ПЛОЩАДКА", "", "");
        // тропы — невидимые блоки
        for (List<BlockPos> lane : lanes)
            for (BlockPos p : lane) b.set(p.getX(), p.getY(), p.getZ(), Blocks.BARRIER);
        b.sign(BX0 - 1, 1, laneZ[0][0] - 1, Direction.EAST, DyeColor.WHITE, "МОСТ ЕСТЬ.", "ПРОСТО ЕГО", "НЕ ВИДНО.", "");
    }

    @Override
    public Vec3 spawn() {
        return v(3.5, 1, entryZ + 0.5);
    }

    private int laneOf(ServerPlayer p) {
        BlockPos l = local(p.blockPosition());
        if (l.getX() < BX0 || l.getX() > BX1) return -1;
        for (int i = 0; i < laneZ.length; i++) if (l.getZ() >= laneZ[i][0] - 1 && l.getZ() <= laneZ[i][1] + 1) return i;
        return -1;
    }

    @Override
    public Vec3 checkpoint(ServerPlayer p) {
        if (arrived.contains(p.getUUID())) return v(BX1 + 3.5, 1, sz / 2 + 0.5);
        return v(BX0 - 2.5, 1, sz / 2 + 0.5);
    }

    @Override
    protected boolean onFall(ServerPlayer p) {
        Vec3 c = checkpoint(p);
        cx.teleport(p, c, -90f, 0f);
        voice().interrupt(voice().pickFor("bridge.fall", p));
        fail();
        return true;
    }

    @Override
    protected void onActivate(boolean firstTime) {
        super.onActivate(firstTime);
        setObjective(level == 3 ? "Каждый идёт по своей дорожке. Тропу видит только напарник."
                : "Один идёт по мосту, второй смотрит и подсказывает. Тропу видно, только пока кто-то на мосту.");
    }

    @Override
    protected void onTick(long t) {
        if ((level == 2 || level == 4) && !revealed && t % (level == 4 ? 40 : 50) == 0) {
            blinkOn = !blinkOn;
            for (BlockPos l : blink) setL(l.getX(), l.getY(), l.getZ(), (blinkOn ? Blocks.BARRIER : Blocks.AIR).defaultBlockState());
        }
        if (!revealed && t % 5 == 0) updateViews();
        // прибытие
        for (ServerPlayer p : players()) {
            BlockPos l = local(p.blockPosition());
            if (p.onGround() && l.getX() > BX1 && l.getX() < sx - 1 && arrived.add(p.getUUID())) onArrive(p);
        }
    }

    private void updateViews() {
        int nLanes = lanes.size();
        boolean[] occupied = new boolean[nLanes];
        for (ServerPlayer p : players()) {
            int ln = laneOf(p);
            if (ln >= 0) occupied[ln] = true;
        }
        for (ServerPlayer p : players()) {
            Set<Integer> now = new HashSet<>();
            int mine = laneOf(p);
            for (int i = 0; i < nLanes; i++) if (occupied[i] && mine != i) now.add(i);
            Set<Integer> before = showing.getOrDefault(p.getUUID(), Set.of());
            for (int i : before) if (!now.contains(i)) for (BlockPos l : lanes.get(i)) p.connection.send(new ClientboundBlockUpdatePacket(level(), at(l.getX(), l.getY(), l.getZ())));
            for (int i : now)
                for (BlockPos l : lanes.get(i)) {
                    BlockState s = blink.contains(l) ? (blinkOn ? BLINK_ON : BLINK_OFF) : SEEN;
                    cx.fakeBlock(p, at(l.getX(), l.getY(), l.getZ()), s);
                }
            showing.put(p.getUUID(), now);
        }
    }

    private void onArrive(ServerPlayer p) {
        progress();
        int need = players().size();
        if (level == 3) {
            if (arrived.size() >= need) { reveal(); win(); }
            else voice().interrupt(voice().pickFor("bridge.arrived", p));
            return;
        }
        if (!revealed) {
            reveal();
            voice().interrupt(voice().pickFor("bridge.arrived", p));
            say("bridge.reveal");
            setObjective("Тропа открыта. Переходите все.");
        }
        if (arrived.size() >= need && !doneSaid) {
            doneSaid = true;
            win();
        }
    }

    private void reveal() {
        revealed = true;
        for (List<BlockPos> lane : lanes)
            for (BlockPos l : lane) setL(l.getX(), l.getY(), l.getZ(), Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState());
        showing.clear();
    }

    @Override
    protected void onReset() {
        revealed = false;
        blinkOn = true;
        arrived.clear();
        showing.clear();
        doneSaid = false;
    }

    @Override
    protected void onSkip() {
        reveal();
    }

    @Override
    public String debug() {
        StringBuilder sb = new StringBuilder("revealed=" + revealed + " blinkOn=" + blinkOn + " arrived=" + arrived.size() + " blink=");
        for (BlockPos p : blink) sb.append(p.getX()).append(',').append(p.getZ()).append(';');
        for (int i = 0; i < lanes.size(); i++) {
            sb.append(" lane").append(i).append('=');
            for (BlockPos p : lanes.get(i)) sb.append(p.getX()).append(',').append(p.getZ()).append(';');
        }
        return sb.toString();
    }
}
