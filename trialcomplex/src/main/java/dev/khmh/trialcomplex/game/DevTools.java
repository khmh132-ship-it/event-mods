package dev.khmh.trialcomplex.game;

import dev.khmh.trialcomplex.TrialComplex;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Инструменты разработки: постройка всей карты, выгрузка блоков для превью. */
public final class DevTools {
    private DevTools() {}

    public static void buildAll(Complex cx) {
        ServerLevel lvl = cx.level();
        List<Room> rooms = cx.rooms();
        for (Room r : rooms) forceLoad(lvl, r, true);
        for (int i = 0; i < rooms.size(); i++) {
            Room r = rooms.get(i);
            r.buildAll(lvl);
            if (i + 1 < rooms.size()) Layout.buildConnector(lvl, r, rooms.get(i + 1), i + 1, rooms.size());
            TrialComplex.LOG.info("Построено: {} ({}) в {}", r.id, r.title, r.origin);
        }
        Room r0 = rooms.get(0);
        Vec3 s = r0.spawn();
        lvl.setDefaultSpawnPos(BlockPos.containing(s), r0.spawnYaw());
        ProgressData d = cx.data();
        d.stage = 0;
        d.started = false;
        d.setDirty();
    }

    private static void forceLoad(ServerLevel lvl, Room r, boolean on) {
        BlockPos a = r.at(-Layout.GAP - 2, 0, -2), b = r.at(r.sx + Layout.GAP + 2, r.sy, r.sz + 2);
        for (int cx = a.getX() >> 4; cx <= b.getX() >> 4; cx++)
            for (int cz = a.getZ() >> 4; cz <= b.getZ() >> 4; cz++)
                lvl.setChunkForced(cx, cz, on);
    }

    /** Запуск с -Dtrialcomplex.autobuild=true: построить, сохранить, выгрузить превью и выключиться. */
    static void autobuild(Complex cx) {
        cx.later(null, 20, () -> {
            buildAll(cx);
            cx.later(null, 100, () -> {
                for (Room r : cx.rooms()) export(cx, r, Path.of("exports"));
                for (Room r : cx.rooms()) forceLoad(cx.level(), r, false);
                cx.server().saveEverything(false, true, true);
                TrialComplex.LOG.info("AUTOBUILD DONE");
                cx.server().halt(false);
            });
        });
    }

    /** Выгрузка блоков комнаты (с запасом вокруг) в простой текстовый формат для рендера превью. */
    public static void export(Complex cx, Room r, Path dir) {
        ServerLevel lvl = cx.level();
        int pad = 3;
        BlockPos a = r.at(-pad, -1, -pad), b = r.at(r.sx + pad - 1, r.sy, r.sz + pad - 1);
        Map<BlockState, Integer> pal = new LinkedHashMap<>();
        StringBuilder sb = new StringBuilder();
        int w = b.getX() - a.getX() + 1, h = b.getY() - a.getY() + 1, d = b.getZ() - a.getZ() + 1;
        for (int y = a.getY(); y <= b.getY(); y++)
            for (int z = a.getZ(); z <= b.getZ(); z++) {
                for (int x = a.getX(); x <= b.getX(); x++) {
                    BlockState s = lvl.getBlockState(new BlockPos(x, y, z));
                    int id = pal.computeIfAbsent(s, k -> pal.size());
                    sb.append(id).append(x == b.getX() ? '\n' : ' ');
                }
            }
        try {
            Files.createDirectories(dir);
            try (Writer wr = Files.newBufferedWriter(dir.resolve(r.id + ".txt"), StandardCharsets.UTF_8)) {
                wr.write(w + " " + h + " " + d + "\n");
                wr.write(pal.size() + "\n");
                for (BlockState s : pal.keySet()) wr.write(s.toString() + "\n");
                wr.write(sb.toString());
            }
        } catch (IOException e) {
            TrialComplex.LOG.error("export", e);
        }
    }
}
