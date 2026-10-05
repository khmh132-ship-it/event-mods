package com.echohorror.horror;

import com.echohorror.Config;
import com.echohorror.story.StoryData;
import com.echohorror.story.StoryManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Locale;

/** Ask the dark a question at night and, sometimes, it answers. Only you see the answer. */
public final class ChatReplies {
    private record Rule(String[] keys, String[] answers) {}

    private static final Rule[] RULES = {
            new Rule(new String[]{"кто здесь", "кто тут", "кто там", "здесь кто-то", "тут кто-то"}, new String[]{"я", "мы", "ты", "все здесь", "только ты. пока"}),
            new Rule(new String[]{"ау"}, new String[]{"ау", "ау ау", "ауууу", "я тут, иди на голос"}),
            new Rule(new String[]{"помогите", "помоги", "спасите", "help"}, new String[]{"помогите", "помоги мне", "мне тоже страшно", "никто не придёт"}),
            new Rule(new String[]{"привет", "здравствуй", "хай"}, new String[]{"привет", "привет. мы знакомы?", "здравствуй. давно не виделись"}),
            new Rule(new String[]{"где ты", "ты где", "где вы"}, new String[]{"сзади", "под тобой", "рядом", "там же, где ты"}),
            new Rule(new String[]{"страшно", "мне страшно", "жутко"}, new String[]{"мне тоже", "не бойся. это недолго", "страшно — это хорошо. значит, живой"}),
            new Rule(new String[]{"эхо"}, new String[]{"эхо", "эхо...", "да?"}),
            new Rule(new String[]{"что это", "что это было"}, new String[]{"это я", "ничего. иди дальше", "ты знаешь"}),
    };

    private ChatReplies() {}

    /** Returns true if the dark decided to answer (so the usual echo stays quiet). */
    public static boolean maybeReply(ServerPlayer p, String raw) {
        if (raw.startsWith("/") || !Config.FAKE_CHAT.get()) return false;
        StoryData d = StoryData.get(p.server);
        int ch = StoryManager.effectiveChapter(d);
        if (ch < StoryManager.CH_RELAY || ch == StoryManager.CH_SILENCE) return false;
        if (!HorrorUtil.isNight(p.serverLevel()) && !HorrorUtil.isUnderground(p)) return false;
        String t = raw.toLowerCase(Locale.ROOT).replaceAll("[^а-яёa-z\\- ]", " ").replaceAll("\\s+", " ").trim();
        for (Rule r : RULES) {
            for (String k : r.keys()) {
                if (!(t.equals(k) || t.startsWith(k + " ") || t.endsWith(" " + k) || t.contains(" " + k + " "))) continue;
                if (p.getRandom().nextFloat() > 0.45f) return false;
                String answer = r.answers()[p.getRandom().nextInt(r.answers().length)];
                // who answers: you, a friend who is somewhere else, or nobody at all
                List<ServerPlayer> others = HorrorUtil.others(p);
                float roll = p.getRandom().nextFloat();
                String who = roll < 0.5f || others.isEmpty() ? p.getGameProfile().getName()
                        : others.get(p.getRandom().nextInt(others.size())).getGameProfile().getName();
                boolean whisper = roll > 0.85f;
                Scheduler.schedule(50 + p.getRandom().nextInt(90), () -> {
                    if (p.hasDisconnected()) return;
                    if (whisper) p.sendSystemMessage(Component.translatable("commands.message.display.incoming", who,
                            Component.literal(answer)).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
                    else p.sendSystemMessage(Component.translatable("chat.type.text", who, Component.literal(answer)));
                    Sanity.add(p, -2);
                });
                return true;
            }
        }
        return false;
    }
}
