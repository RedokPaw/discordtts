package org.redok;

import org.redok.configuration.BotConfig;
import org.redok.tts.GoogleTranslateTts;
import org.redok.tts.SileroTts;
import org.redok.tts.TtsEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Какие провайдеры TTS бывают и как их создать из конфига. Создаются только перечисленные в
 * TTS_PROVIDERS; новый провайдер — одна строка в {@link #FACTORIES}.
 */
final class TtsProviders {

    private static final Logger log = LoggerFactory.getLogger(TtsProviders.class);
    private static final Map<String, Function<BotConfig, TtsEngine>> FACTORIES = new LinkedHashMap<>();

    static {
        FACTORIES.put(GoogleTranslateTts.ID, TtsProviders::google);
        FACTORIES.put(SileroTts.ID, TtsProviders::silero);
    }

    private TtsProviders() {
    }

    /** Движки из TTS_PROVIDERS по порядку; неизвестное имя или нехватка настроек — {@link IllegalStateException}. */
    static List<TtsEngine> create(BotConfig config) {
        for (String name : config.ttsProviders()) {
            if (!FACTORIES.containsKey(name)) {
                throw new IllegalStateException("unknown TTS provider '" + name + "' in TTS_PROVIDERS, known: "
                        + FACTORIES.keySet());
            }
        }
        if (!config.ttsDefaultVoice().provider().equals(GoogleTranslateTts.ID)) {
            log.warn("default voice {} is not a Google voice: if its provider goes down, nobody will be heard",
                    config.ttsDefaultVoice());
        }
        List<TtsEngine> engines = config.ttsProviders().stream().map(name -> FACTORIES.get(name).apply(config)).toList();
        log.info("TTS providers: {}", engines.stream().map(TtsEngine::id).toList());
        return engines;
    }

    /** Языки для /voice; язык голоса по умолчанию добавляется, даже если его нет в списке. */
    private static TtsEngine google(BotConfig config) {
        Set<String> languages = new LinkedHashSet<>(GoogleTranslateTts.DEFAULT_LANGUAGES);
        if (config.ttsDefaultVoice().provider().equals(GoogleTranslateTts.ID)) {
            languages.add(config.ttsDefaultVoice().id());
        }
        return new GoogleTranslateTts(languages);
    }

    private static TtsEngine silero(BotConfig config) {
        if (config.sileroUrl() == null) {
            throw new IllegalStateException("silero is in TTS_PROVIDERS, but SILERO_URL is not set "
                    + "(for example SILERO_URL=http://silero:8000)");
        }
        return new SileroTts(config.sileroUrl()).start();
    }
}
