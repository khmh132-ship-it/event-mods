package dev.khmh.trialcomplex.build;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/** Взвешенный набор блоков для «живых» поверхностей (чтобы стены не были однотонными). */
public final class Palette {
    private final List<BlockState> states = new ArrayList<>();
    private final List<Integer> weights = new ArrayList<>();
    private int total;

    private Palette() {}

    /** Аргументы: Block/BlockState, вес, Block/BlockState, вес, ... (вес можно опустить у последнего — 1). */
    public static Palette of(Object... args) {
        Palette p = new Palette();
        for (int i = 0; i < args.length; i++) {
            BlockState s = args[i] instanceof Block b ? b.defaultBlockState() : (BlockState) args[i];
            int w = 1;
            if (i + 1 < args.length && args[i + 1] instanceof Integer n) {
                w = n;
                i++;
            }
            p.states.add(s);
            p.weights.add(w);
            p.total += w;
        }
        return p;
    }

    public BlockState pick(RandomSource rnd) {
        if (states.size() == 1) return states.get(0);
        int r = rnd.nextInt(total);
        for (int i = 0; i < states.size(); i++) {
            r -= weights.get(i);
            if (r < 0) return states.get(i);
        }
        return states.get(0);
    }
}
