package org.redok.audio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Работу с процессом проверяем без ffmpeg: вместо него cat, sh и sleep, поэтому только Linux и macOS. */
@EnabledOnOs({OS.LINUX, OS.MAC})
class FfmpegDecoderTest {

    @Test
    void passesDataThroughTheProcessWithoutADeadlock() throws Exception {
        byte[] input = new byte[1 << 20]; // 1 МиБ — заметно больше буфера пайпа (64 КиБ)
        new Random(42).nextBytes(input);

        assertArrayEquals(input, new FfmpegDecoder(List.of("cat"), Duration.ofSeconds(10)).toPcm(input));
    }

    @Test
    void failsOnANonZeroExitCode() {
        FfmpegDecoder decoder = new FfmpegDecoder(List.of("sh", "-c", "cat > /dev/null; exit 3"), Duration.ofSeconds(10));

        IOException e = assertThrows(IOException.class, () -> decoder.toPcm(new byte[]{1, 2, 3}));
        assertTrue(e.getMessage().endsWith("exited with code 3"), e.getMessage());
    }

    @Test
    void theWatchdogKillsAHungProcess() {
        FfmpegDecoder decoder = new FfmpegDecoder(List.of("sleep", "30"), Duration.ofMillis(500));
        long start = System.nanoTime();

        assertThrows(IOException.class, () -> decoder.toPcm(new byte[0]));
        assertTrue(Duration.ofNanos(System.nanoTime() - start).toMillis() < 3000, "процесс не убит вовремя");
    }
}
