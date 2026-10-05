package dev.khmh.trialcomplex.voice;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.khmh.trialcomplex.TrialComplex;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Индекс реплик Голоса: id → текст субтитра и длительность.
 * Генерируется скриптом tools/voice/build_voice.py вместе с .ogg и sounds.json.
 */
public final class VoiceLines {
    public record Line(String id, String text, int ms) {}

    private static final Map<String, Line> LINES = new LinkedHashMap<>();
    private static final Map<String, List<String>> GROUPS = new HashMap<>();

    private VoiceLines() {}

    public static synchronized void load() {
        if (!LINES.isEmpty()) return;
        try (InputStream in = TrialComplex.class.getResourceAsStream("/assets/trialcomplex/voice_lines.json")) {
            if (in == null) {
                TrialComplex.LOG.warn("voice_lines.json не найден — Голос будет молчать");
                return;
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var e : root.entrySet()) {
                JsonObject o = e.getValue().getAsJsonObject();
                LINES.put(e.getKey(), new Line(e.getKey(), o.get("text").getAsString(), o.get("ms").getAsInt()));
            }
            // группа = id без последнего числового сегмента: "fall.khmh.3" → "fall.khmh"
            for (String id : LINES.keySet()) {
                int dot = id.lastIndexOf('.');
                if (dot > 0 && id.substring(dot + 1).chars().allMatch(Character::isDigit)) {
                    GROUPS.computeIfAbsent(id.substring(0, dot), k -> new ArrayList<>()).add(id);
                }
            }
            TrialComplex.LOG.info("Загружено реплик Голоса: {}", LINES.size());
        } catch (Exception ex) {
            TrialComplex.LOG.error("Не удалось загрузить реплики Голоса", ex);
        }
    }

    public static Line get(String id) {
        load();
        return LINES.get(id);
    }

    public static List<String> group(String prefix) {
        load();
        return GROUPS.getOrDefault(prefix, List.of());
    }

    public static boolean exists(String id) {
        return get(id) != null;
    }
}
