package dev.khmh.trialcomplex.rooms;

import dev.khmh.trialcomplex.build.Builder;
import dev.khmh.trialcomplex.build.Styles;
import dev.khmh.trialcomplex.game.Room;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;

/** Конец пробной версии: шлюз, за которым будут следующие испытания. */
public class R99DemoEnd extends Room {
    public R99DemoEnd() {
        super("r99", "Конец пробной версии", 11, 7, 11);
    }

    @Override
    protected void build(Builder b) {
        Styles.lab(b, sx, sy, sz, Blocks.SEA_LANTERN.defaultBlockState(), Blocks.PURPLE_TERRACOTTA);
        b.fill(sx - 1, 1, exitZ - 1, sx - 1, 4, exitZ + 1, Blocks.BARRIER);
        b.sign(5, 2, 1, Direction.SOUTH, DyeColor.ORANGE, "СТРОИТСЯ", "", "ДАЛЬШЕ — В", "СЛЕДУЮЩЕЙ ВЕРСИИ");
        b.set(5, 1, 5, Blocks.CAKE);
        b.set(5, 0, 5, Blocks.GOLD_BLOCK);
    }

    @Override
    protected void onActivate(boolean firstTime) {
        if (firstTime) voice().say("demo.end");
        setObjective("Пробная версия пройдена. /puzzle status — ваша статистика.");
    }

    @Override
    public int hintCount() {
        return 0;
    }

    @Override
    public boolean countsAsTrial() {
        return false;
    }
}
