package org.redok.tts;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Справочник голосов: какие провайдеры есть, какие у них голоса и какой голос по умолчанию. Собирается
 * один раз при старте и не меняется, поэтому потокобезопасен без блокировок. Ничего не решает за
 * пользователя: какой голос взять для человека, решает {@link org.redok.preferences.VoicePreferences}.
 */
public class VoiceRegistry {

    /** Discord показывает в автодополнении не больше 25 вариантов. */
    public static final int MAX_CHOICES = 25;

    private final Map<String, TtsEngine> engines = new LinkedHashMap<>();
    private final Voice defaultVoice;

    /** Бросает {@link IllegalArgumentException} при двух движках с одним id или неизвестном голосе по умолчанию. */
    public VoiceRegistry(List<TtsEngine> engineList, Voice defaultVoice) {
        for (TtsEngine engine : engineList) {
            if (engines.putIfAbsent(engine.id(), engine) != null) {
                throw new IllegalArgumentException("two TTS engines with id '" + engine.id() + "'");
            }
        }
        if (find(defaultVoice).isEmpty()) {
            throw new IllegalArgumentException("default voice " + defaultVoice + " is not one of the voices: "
                    + all().stream().map(info -> info.voice().key()).toList());
        }
        this.defaultVoice = defaultVoice;
    }

    public Voice defaultVoice() {
        return defaultVoice;
    }

    /** Есть ли такой голос в каталоге; неизвестный голос нельзя сохранить. */
    public Optional<VoiceInfo> find(Voice voice) {
        TtsEngine engine = engines.get(voice.provider());
        if (engine == null) {
            return Optional.empty();
        }
        return engine.voices().stream().filter(info -> info.voice().equals(voice)).findFirst();
    }

    /** Может ли голос звучать прямо сейчас: он есть в каталоге и его движок доступен. */
    public boolean isAvailable(Voice voice) {
        return find(voice).isPresent() && engines.get(voice.provider()).isAvailable();
    }

    /** Движок голоса; бросает {@link IllegalArgumentException}, если провайдера нет. */
    public TtsEngine engine(Voice voice) {
        TtsEngine engine = engines.get(voice.provider());
        if (engine == null) {
            throw new IllegalArgumentException("no TTS engine for voice " + voice);
        }
        return engine;
    }

    public List<VoiceInfo> all() {
        return engines.values().stream().flatMap(engine -> engine.voices().stream()).toList();
    }

    /** Имя голоса вместе с провайдером: «Русский (Google)». */
    public String label(VoiceInfo info) {
        return info.name() + " (" + engines.get(info.voice().provider()).displayName() + ")";
    }

    /**
     * Голос по тексту, который ввёл человек: ключ {@code google:ru} или точное имя либо id («Русский», «ru»),
     * если оно однозначно. Нужен, когда в /voice вписали текст руками, а не выбрали из подсказки.
     */
    public Optional<VoiceInfo> resolve(String input) {
        String query = input.strip();
        try {
            Optional<VoiceInfo> byKey = find(Voice.parse(query));
            if (byKey.isPresent()) {
                return byKey;
            }
        } catch (IllegalArgumentException notAKey) {
            // не provider:id — ищем по имени
        }
        List<VoiceInfo> exact = all().stream()
                .filter(info -> info.name().equalsIgnoreCase(query) || info.voice().id().equalsIgnoreCase(query)
                        || label(info).equalsIgnoreCase(query))
                .toList();
        return exact.size() == 1 ? Optional.of(exact.getFirst()) : Optional.empty();
    }

    /**
     * Подсказки для автодополнения: сначала голоса, у которых имя или id начинаются с запроса, потом те,
     * где запрос встречается в имени, ключе или имени провайдера. Регистр не важен, не больше limit штук.
     */
    public List<VoiceInfo> search(String query, int limit) {
        String q = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        List<VoiceInfo> starts = new ArrayList<>();
        List<VoiceInfo> contains = new ArrayList<>();
        for (VoiceInfo info : all()) {
            String name = info.name().toLowerCase(Locale.ROOT);
            String id = info.voice().id().toLowerCase(Locale.ROOT);
            if (name.startsWith(q) || id.startsWith(q)) {
                starts.add(info);
            } else if (label(info).toLowerCase(Locale.ROOT).contains(q) || info.voice().key().toLowerCase(Locale.ROOT).contains(q)) {
                contains.add(info);
            }
        }
        starts.addAll(contains);
        return List.copyOf(starts.subList(0, Math.min(limit, starts.size())));
    }
}
