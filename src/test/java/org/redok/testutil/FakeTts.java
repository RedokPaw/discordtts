package org.redok.testutil;

import org.redok.tts.TtsEngine;
import org.redok.tts.Voice;
import org.redok.tts.VoiceInfo;
import org.redok.tts.VoiceRegistry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Движок TTS для тестов: «звук» — это байты самого текста, все запросы записываются. Голоса: fake:a и fake:b. */
public final class FakeTts implements TtsEngine {

    public static final Voice VOICE = new Voice("fake", "a");
    public static final Voice OTHER_VOICE = new Voice("fake", "b");

    private final String id;
    private final int maxTextLength;
    private final List<String> texts = new CopyOnWriteArrayList<>();
    private final List<Voice> voicesUsed = new CopyOnWriteArrayList<>();
    private volatile IOException failure;
    private volatile boolean available = true;

    public FakeTts(int maxTextLength) {
        this("fake", maxTextLength);
    }

    public FakeTts(String id, int maxTextLength) {
        this.id = id;
        this.maxTextLength = maxTextLength;
    }

    /** Реестр из одного этого движка с голосом по умолчанию fake:a. */
    public VoiceRegistry registry() {
        return new VoiceRegistry(List.of(this), VOICE);
    }

    /** Все следующие вызовы synthesize бросят это исключение. */
    public void failWith(IOException failure) {
        this.failure = failure;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    /** Тексты, которые просили озвучить, в порядке запросов. */
    public List<String> texts() {
        return texts;
    }

    /** Голоса запросов, в том же порядке, что и {@link #texts()}. */
    public List<Voice> voicesUsed() {
        return voicesUsed;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String displayName() {
        return "Fake";
    }

    @Override
    public List<VoiceInfo> voices() {
        return List.of(new VoiceInfo(new Voice(id, "a"), "Альфа"), new VoiceInfo(new Voice(id, "b"), "Бета"));
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public int maxTextLength() {
        return maxTextLength;
    }

    @Override
    public byte[] synthesize(String text, String voiceId) throws IOException {
        texts.add(text);
        voicesUsed.add(new Voice(id, voiceId));
        if (failure != null) {
            throw failure;
        }
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
