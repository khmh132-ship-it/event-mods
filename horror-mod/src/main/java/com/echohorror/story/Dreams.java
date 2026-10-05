package com.echohorror.story;

import com.echohorror.horror.HorrorUtil;
import com.echohorror.horror.Sanity;
import com.echohorror.horror.Scheduler;
import com.echohorror.network.Fx;
import com.echohorror.network.Net;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/** What you see when you finally fall asleep. One dream per stage of the story, in order. */
public final class Dreams {
    private record Dream(int minChapter, String text) {}

    private static final Dream[] DREAMS = {
            new Dream(StoryManager.CH_SIGNAL, "Тебе снится эфир.\nЖенский голос считает. На каждом числе в лесу гаснет одно окно.\nНа твоём числе гаснет твоё."),
            new Dream(StoryManager.CH_RELAY, "Тебе снится Лисицын.\nОн стоит у окна ретранслятора и шевелит губами в такт твоему дыханию.\nОн не моргает. Он учится."),
            new Dream(StoryManager.CH_VILLAGE, "Тебе снится девочка со шкатулкой.\n«Не говори с ними, — шепчет она твоим голосом. — Они запоминают».\nШкатулка играет. Всё вокруг замолкает и слушает."),
            new Dream(StoryManager.CH_VILLAGE, "Тебе снится колокол.\nОн звонит без звука, и от этой тишины у тебя идёт кровь из ушей.\nКто-то внизу пытается повторить звон. У него не получается."),
            new Dream(StoryManager.CH_DEPTHS, "Тебе снится колодец.\nТы спускаешься по лестнице, бесконечно.\nСнизу кто-то поднимается тебе навстречу. Так же медленно. Шаг в шаг."),
            new Dream(StoryManager.CH_OBJECT, "Тебе снится Воронов.\n«Мы его не нашли, — говорит он. — Мы его разбудили. Это разные вещи».\nУ него твоё лицо."),
            new Dream(StoryManager.CH_OBJECT, "Тебе снится, что ты проснулся.\nУ кровати стоишь ты и смотришь, как ты спишь.\nПотом ложишься на твоё место."),
            new Dream(StoryManager.CH_BELFRY, "Тебе снятся все, кого ты встретил.\nОни стоят кругом и считают.\nТы — следующее число."),
    };

    private Dreams() {}

    /** Shows the next unseen dream, if the story has reached it. Returns true if one was shown. */
    public static boolean tryDream(ServerPlayer p, StoryData d) {
        CompoundTag tag = Sanity.data(p);
        int next = tag.getInt("dream");
        int ch = StoryManager.effectiveChapter(d);
        String text;
        if (d.flag("ending_echo")) {
            if (p.getRandom().nextFloat() > 0.3f) return false;
            text = "Тебе больше ничего не снится.\nТебе только слышится.";
        } else {
            while (next < DREAMS.length && DREAMS[next].minChapter() < ch - 1) next++; // that part of the story is long gone
            if (next >= DREAMS.length || DREAMS[next].minChapter() > ch) return false;
            text = DREAMS[next].text();
            tag.putInt("dream", next + 1);
        }
        Net.fx(p, Fx.DREAM, 260, 0, text);
        HorrorUtil.playAt(p, "scare.hum", 0.35f, 0.6f);
        Scheduler.schedule(250, () -> {
            if (p.hasDisconnected()) return;
            Net.fx(p, Fx.SUBTITLE, 60, 0, "Ты проснулся. Наверное.");
            Sanity.add(p, -3);
        });
        return true;
    }
}
