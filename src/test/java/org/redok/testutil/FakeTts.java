package org.redok.testutil;

import org.redok.tts.TtsEngine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Движок TTS для тестов: «звук» — это байты самого текста, все запросы записываются. */
public final class FakeTts implements TtsEngine {

    private final int maxTextLength;
    private final List<String> texts = new CopyOnWriteArrayList<>();
    private volatile IOException failure;

    public FakeTts(int maxTextLength) {
        this.maxTextLength = maxTextLength;
    }

    /** Все следующие вызовы synthesize бросят это исключение. */
    public void failWith(IOException failure) {
        this.failure = failure;
    }

    /** Тексты, которые просили озвучить, в порядке запросов. */
    public List<String> texts() {
        return texts;
    }

    @Override
    public int maxTextLength() {
        return maxTextLength;
    }

    @Override
    public byte[] synthesize(String text) throws IOException {
        texts.add(text);
        if (failure != null) {
            throw failure;
        }
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
