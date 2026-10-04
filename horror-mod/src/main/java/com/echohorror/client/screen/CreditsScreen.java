package com.echohorror.client.screen;

import com.echohorror.story.Notes;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/** Epilogue + scrolling credits. */
public class CreditsScreen extends Screen {
    private final List<Object> lines = new ArrayList<>();
    private float scroll;

    public CreditsScreen() {
        super(Component.literal("Тишина"));
    }

    @Override
    protected void init() {
        lines.clear();
        lines.add(">ЭХО");
        lines.add(">Объект «Колокол»");
        lines.add("");
        lines.add("");
        Notes.Note ending = Notes.get("ending");
        for (String p : ending.text().split("\n")) {
            if (p.isEmpty()) lines.add("");
            else lines.addAll(font.split(Component.literal(p), Math.min(280, width - 40)));
        }
        String[] tail = {"", "", "", "#В ролях", "", "Сергей Лисицын — радист ретранслятора Р-7", "Таня — голос за дверью",
                "Маша, 7 лет — та, что больше не разговаривает", "о. Никодим — звонарь", "Пётр Кузьмич — сосед",
                "Группа «Шахтёр» — шестеро. Или семеро", "Зоя Козлова — Немая", "А. С. Воронов — руководитель объекта",
                "Отголосок — все вы", "", "", "#Звук", "", "Всё, что вы слышали, было собрано из шума,", "синтезатора речи и ваших собственных голосов.",
                "", "", "#Спасибо, что дослушали", "", "", "", "", "", "", "", "Сколько вас было в начале?", "", "", "", "", "", "",
                "(Esc — закрыть)"};
        for (String s : tail) lines.add(s);
        scroll = -height;
    }

    @Override
    public void tick() {
        scroll += 0.55f;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0xFF000000);
        float y = -(scroll + pt * 0.55f);
        for (Object o : lines) {
            int iy = (int) y;
            if (iy > -20 && iy < height + 20) {
                if (o instanceof FormattedCharSequence f) {
                    g.drawString(font, f, width / 2 - font.width(f) / 2, iy, 0xBBBBBB, false);
                } else {
                    String s = (String) o;
                    if (s.startsWith(">")) {
                        g.pose().pushPose();
                        g.pose().translate(width / 2f, iy, 0);
                        g.pose().scale(3, 3, 1);
                        g.drawCenteredString(font, s.substring(1), 0, 0, s.equals(">ЭХО") ? 0xD0D0D0 : 0x8A1A1A);
                        g.pose().popPose();
                        y += 22;
                    } else if (s.startsWith("#")) {
                        g.drawCenteredString(font, s.substring(1), width / 2, iy, 0x9A2020);
                    } else if (!s.isEmpty()) {
                        g.drawCenteredString(font, s, width / 2, iy, 0x909090);
                    }
                }
            }
            y += 12;
        }
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
