package org.redok.voice;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuildVoiceSessionTest {

    private static final int FRAME = 3840;

    @Test
    void playsSlotsInArrivalOrderEvenIfALaterSlotIsReadyFirst() {
        GuildVoiceSession session = new GuildVoiceSession(10);
        CompletableFuture<ByteBuffer> first = session.reserveSlots(1).getFirst();
        CompletableFuture<ByteBuffer> second = session.reserveSlots(1).getFirst();

        second.complete(filled(FRAME + 100, 2));
        assertFalse(session.canProvide(), "голова ещё не готова — играть нечего, хотя второй слот готов");

        first.complete(filled(2 * FRAME, 1));
        assertEquals(List.of("1x3840", "1x3840", "2x3840", "2x100+0x3740"), drain(session),
                "сначала первый слот, потом второй; хвост последнего кадра — тишина");
    }

    @Test
    void skipsAFailedHeadAndPlaysTheNextSlot() {
        GuildVoiceSession session = new GuildVoiceSession(10);
        List<CompletableFuture<ByteBuffer>> slots = session.reserveSlots(2);
        slots.get(1).complete(filled(FRAME, 3));
        slots.get(0).completeExceptionally(new IOException("Google TTS answered HTTP 400"));

        assertEquals(List.of("3x3840"), drain(session));
    }

    @Test
    void aStuckSlotTimesOutAndTheQueueMovesOn() throws InterruptedException {
        GuildVoiceSession session = new GuildVoiceSession(10, Duration.ofMillis(100));
        List<CompletableFuture<ByteBuffer>> slots = session.reserveSlots(2);
        slots.get(1).complete(filled(FRAME, 4)); // первый слот так и не заполнится

        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (!session.canProvide() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(List.of("4x3840"), drain(session));
        assertTrue(slots.get(0).isCompletedExceptionally());
    }

    @Test
    void reservesAllChunksOfAMessageOrNone() {
        GuildVoiceSession session = new GuildVoiceSession(10);

        assertEquals(7, session.reserveSlots(7).size());
        assertTrue(session.reserveSlots(4).isEmpty(), "свободно 3 места: не занимается ни одно");
        assertEquals(3, session.reserveSlots(3).size());
        assertTrue(session.reserveSlots(1).isEmpty());
    }

    @Test
    void clearCancelsPendingSlotsAndDropsLateResults() {
        GuildVoiceSession session = new GuildVoiceSession(10);
        List<CompletableFuture<ByteBuffer>> slots = session.reserveSlots(3);

        session.clear();

        assertTrue(slots.stream().allMatch(CompletableFuture::isCancelled));
        assertFalse(slots.getFirst().complete(filled(FRAME, 9)), "опоздавший результат синтеза отбрасывается");
        assertFalse(session.canProvide());
        assertEquals(10, session.reserveSlots(10).size(), "вся очередь снова свободна");
    }

    @Test
    void releasesThePlayedBufferImmediately() throws ReflectiveOperationException {
        GuildVoiceSession session = new GuildVoiceSession(10);
        session.reserveSlots(1).getFirst().complete(filled(FRAME, 5));

        drain(session);

        Field buffer = GuildVoiceSession.class.getDeclaredField("ttsBuffer");
        buffer.setAccessible(true);
        assertNull(buffer.get(session), "доигранный PCM не должен ждать в памяти следующего сообщения");
    }

    private static ByteBuffer filled(int size, int value) {
        byte[] bytes = new byte[size];
        Arrays.fill(bytes, (byte) value);
        return ByteBuffer.wrap(bytes);
    }

    /** Читает кадры как аудиопоток JDA и описывает каждый сериями одинаковых байтов: "2x100+0x3740". */
    private static List<String> drain(GuildVoiceSession session) {
        List<String> frames = new ArrayList<>();
        while (session.canProvide()) {
            ByteBuffer frame = session.provide20MsAudio();
            StringBuilder description = new StringBuilder();
            int i = frame.position();
            while (i < frame.limit()) {
                byte value = frame.get(i);
                int j = i;
                while (j < frame.limit() && frame.get(j) == value) {
                    j++;
                }
                if (!description.isEmpty()) {
                    description.append('+');
                }
                description.append(value).append('x').append(j - i);
                i = j;
            }
            frames.add(description.toString());
            frame.position(frame.limit()); // JDA «потребляет» кадр
        }
        return frames;
    }
}
