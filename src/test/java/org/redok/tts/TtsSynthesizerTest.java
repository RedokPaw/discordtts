package org.redok.tts;

import org.junit.jupiter.api.Test;
import org.redok.audio.AudioDecoder;
import org.redok.testutil.FakeTts;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TtsSynthesizerTest {

    private static final String WORDS =
            "раз два три четыре пять шесть семь восемь девять десять ".repeat(8).strip(); // 447 символов

    @Test
    void splitsByWordsWithoutLosingText() {
        List<String> chunks = TtsSynthesizer.splitByWords(WORDS, 200);

        assertEquals(List.of(197, 197, 51), chunks.stream().map(String::length).toList());
        assertEquals(WORDS, String.join(" ", chunks));
    }

    @Test
    void cutsAWordLongerThanTheLimit() {
        List<String> chunks = TtsSynthesizer.splitByWords("x".repeat(450), 200);

        assertEquals(List.of(200, 200, 50), chunks.stream().map(String::length).toList());
    }

    @Test
    void blankTextHasNoChunksAndShortTextHasOne() {
        assertEquals(List.of(), TtsSynthesizer.splitByWords("  \n ", 200));
        assertEquals(List.of("привет"), TtsSynthesizer.splitByWords("привет", 200));
    }

    @Test
    void splitUsesTheEngineLimitAndMaxChunks() {
        TtsSynthesizer synthesizer = new TtsSynthesizer(new FakeTts(10), encoded -> encoded, Runnable::run, 2);

        assertEquals(List.of("раз два", "три четыре"), synthesizer.split("раз два три четыре пять шесть"));
    }

    @Test
    void synthesizeIntoCompletesTheSlotWithDecodedAudio() {
        AudioDecoder upperCase = encoded -> new String(encoded, UTF_8).toUpperCase().getBytes(UTF_8);
        TtsSynthesizer synthesizer = new TtsSynthesizer(new FakeTts(200), upperCase, Runnable::run, 3);
        CompletableFuture<ByteBuffer> slot = new CompletableFuture<>();

        synthesizer.synthesizeInto(slot, "привет", "g");

        assertEquals("ПРИВЕТ", UTF_8.decode(slot.join()).toString());
    }

    @Test
    void synthesizeIntoPutsTheErrorIntoTheSlot() {
        FakeTts engine = new FakeTts(200);
        engine.failWith(new IOException("Google TTS answered HTTP 400"));
        TtsSynthesizer synthesizer = new TtsSynthesizer(engine, encoded -> encoded, Runnable::run, 3);
        CompletableFuture<ByteBuffer> slot = new CompletableFuture<>();

        synthesizer.synthesizeInto(slot, "привет", "g");

        ExecutionException e = assertThrows(ExecutionException.class, slot::get);
        assertInstanceOf(IOException.class, e.getCause());
    }

    @Test
    void synthesizeIntoSkipsACancelledSlot() {
        FakeTts engine = new FakeTts(200);
        TtsSynthesizer synthesizer = new TtsSynthesizer(engine, encoded -> encoded, Runnable::run, 3);
        CompletableFuture<ByteBuffer> slot = new CompletableFuture<>();
        slot.cancel(false);

        synthesizer.synthesizeInto(slot, "привет", "g");

        assertTrue(engine.texts().isEmpty(), "незачем ходить в Google ради слота, который никто не ждёт");
    }
}
