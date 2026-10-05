package com.echohorror.horror;

import com.echohorror.Config;
import com.echohorror.entity.CrawlerEntity;
import com.echohorror.network.Fx;
import com.echohorror.network.Net;
import com.echohorror.story.Achievements;
import com.echohorror.story.StoryData;
import com.echohorror.story.StoryManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Down there, they hear you talk (Simple Voice Chat). Whispering is safe. */
public final class VoiceNoise {
    private static final Map<UUID, long[]> TALK = new HashMap<>(); // {window start (s), half-seconds of speech}

    private VoiceNoise() {}

    /** Server thread; called at most twice a second per speaking player. */
    public static void onVoice(ServerPlayer p) {
        if (p.hasDisconnected() || !p.isAlive() || !HorrorUtil.isSurvivalLike(p)) return;
        StoryData d = StoryData.get(p.server);
        if (StoryManager.effectiveChapter(d) < StoryManager.CH_DEPTHS) return;
        Vec3 pos = p.position();
        ServerLevel level = p.serverLevel();
        boolean deep = d.in("depths", pos) || d.in("bunker", pos);
        boolean echo = level == p.server.overworld() && StoryManager.echoNight(d, level) && HorrorUtil.isNight(level);
        if (!deep && !echo) return;
        NoiseSystem.noise(level, pos, deep ? 24 : 16, p);

        CompoundTag tag = Sanity.data(p);
        if (!tag.getBoolean("voiceHint")) {
            tag.putBoolean("voiceHint", true);
            Net.fx(p, Fx.SUBTITLE, 80, 0, "Тише. Здесь слышат голоса. Шёпотом — можно.");
        }

        long sec = level.getGameTime() / 20;
        long[] t = TALK.computeIfAbsent(p.getUUID(), k -> new long[]{sec, 0});
        if (sec - t[0] > 60) {
            t[0] = sec;
            t[1] = 0;
        }
        if (++t[1] < 30) return; // ~15 seconds of talking within a minute
        t[0] = sec;
        t[1] = 0;
        if (!Config.HOSTILE_SPAWNS.get() || !level.getEntitiesOfClass(CrawlerEntity.class, p.getBoundingBox().inflate(32)).isEmpty()) return;
        if (HorrorDirector.force(p, "crawlers")) {
            Net.fx(p, Fx.SUBTITLE, 70, 0, "Тебя услышали.");
            Achievements.award(p, "heard");
        }
    }

    public static void clear() {
        TALK.clear();
    }
}
