package org.redok.tts;

import java.io.IOException;
import java.util.List;

/** Синтез речи: текст → закодированный звук (например, MP3), который понимает {@link org.redok.audio.AudioDecoder}. */
public interface TtsEngine {

    /** Id провайдера в ключе голоса: {@code google} в {@code google:ru}. Не меняется, хранится в базе. */
    String id();

    /** Имя провайдера для людей: «Google». */
    String displayName();

    /** Голоса провайдера; у каждого {@link Voice#provider()} равен {@link #id()}. */
    List<VoiceInfo> voices();

    /** Может ли движок синтезировать прямо сейчас. Голос недоступного движка заменяется голосом по умолчанию. */
    default boolean isAvailable() {
        return true;
    }

    /** Сколько символов текста движок принимает за один вызов {@link #synthesize}. */
    int maxTextLength();

    /** @param voiceId {@link Voice#id()} одного из {@link #voices()} */
    byte[] synthesize(String text, String voiceId) throws IOException, InterruptedException;
}
