package dev.khmh.trialcomplex.parts;

import dev.khmh.trialcomplex.game.Complex;
import dev.khmh.trialcomplex.game.Gate;
import dev.khmh.trialcomplex.game.Room;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Две половины комнаты с дверями: закрываются, когда в каждой по игроку; если кто-то
 * выпал/перезашёл и оказался снаружи — открываются снова (прогресс комнаты не сбрасывается).
 */
public final class Split {
    private final AABB a, b;
    private final Gate gateA, gateB;
    private boolean closed;
    private int stray;

    public Split(AABB a, AABB b, Gate gateA, Gate gateB) {
        this.a = a;
        this.b = b;
        this.gateA = gateA;
        this.gateB = gateB;
    }

    public ServerPlayer in(Complex cx, int side) {
        AABB box = side == 0 ? a : b;
        for (ServerPlayer p : cx.players()) if (box.contains(p.position())) return p;
        return null;
    }

    public int sideOf(ServerPlayer p) {
        if (a.contains(p.position())) return 0;
        if (b.contains(p.position())) return 1;
        return -1;
    }

    public boolean closed() {
        return closed;
    }

    /** Вызывать каждый тик. Возвращает true в момент разделения (двери закрылись). */
    public boolean tick(Complex cx, Room owner) {
        List<ServerPlayer> ps = cx.players();
        if (!closed) {
            ServerPlayer pa = in(cx, 0), pb = in(cx, 1);
            boolean solo = ps.size() == 1 && (pa != null || pb != null);
            if ((pa != null && pb != null && pa != pb) || solo) {
                for (ServerPlayer p : ps) if (p.getBoundingBox().intersects(gateA.box()) || p.getBoundingBox().intersects(gateB.box())) return false;
                gateA.close(cx, owner);
                gateB.close(cx, owner);
                closed = true;
                stray = 0;
                return true;
            }
            return false;
        }
        boolean someoneOut = ps.size() >= 2 && (in(cx, 0) == null || in(cx, 1) == null);
        if (someoneOut && ++stray > 40) {
            stray = 0;
            closed = false;
            gateA.open(cx, owner);
            gateB.open(cx, owner);
        } else if (!someoneOut) stray = 0;
        return false;
    }

    public void open(Complex cx, Room owner) {
        gateA.open(cx, owner);
        gateB.open(cx, owner);
    }

    public void reset() {
        closed = false;
        stray = 0;
    }
}
