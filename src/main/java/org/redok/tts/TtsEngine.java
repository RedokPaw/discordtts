package org.redok.tts;

import java.io.IOException;

/** Синтез речи: текст → закодированный звук (например, MP3), который понимает {@link org.redok.audio.AudioDecoder}. */
public interface TtsEngine {

    /** Сколько символов текста движок принимает за один вызов {@link #synthesize}. */
    int maxTextLength();

    byte[] synthesize(String text) throws IOException, InterruptedException;
}
