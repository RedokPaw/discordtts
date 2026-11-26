package org.redok.audio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Декодирует через внешний процесс ffmpeg: закодированный звук идёт в stdin, PCM читается из stdout. */
public class FfmpegDecoder implements AudioDecoder {

    private static final Logger log = LoggerFactory.getLogger(FfmpegDecoder.class);
    private static final List<String> FFMPEG = List.of(
            "ffmpeg",
            "-loglevel", "error",
            "-threads", "1",
            "-i", "pipe:0",
            "-f", "s16be",
            "-ar", "48000",
            "-ac", "2",
            "pipe:1");
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    private final ProcessBuilder processBuilder;
    private final Duration timeout;

    public FfmpegDecoder() {
        this(FFMPEG, DEFAULT_TIMEOUT);
    }

    /** Команда и таймаут меняются только в тестах: например, {@code cat} вместо ffmpeg. */
    FfmpegDecoder(List<String> command, Duration timeout) {
        // stderr никто не читает — не даём ему забить пайп
        this.processBuilder = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD);
        this.timeout = timeout;
    }

    @Override
    public byte[] toPcm(byte[] encoded) throws IOException, InterruptedException {
        Process process = processBuilder.start();
        try {
            // Сторож: если процесс завис, через timeout он будет убит, а readAllBytes ниже получит EOF.
            CompletableFuture.delayedExecutor(timeout.toMillis(), TimeUnit.MILLISECONDS).execute(() -> {
                if (process.isAlive()) {
                    log.warn("{} is still running after {} ms, killing it", processBuilder.command().getFirst(),
                            timeout.toMillis());
                    process.destroyForcibly();
                }
            });
            // stdin пишем параллельно с чтением stdout, иначе обе стороны упрутся в заполненный пайп.
            Thread.ofVirtual().start(() -> {
                try (OutputStream stdin = process.getOutputStream()) {
                    stdin.write(encoded);
                } catch (IOException ignored) {
                    // процесс закрыл stdin раньше времени — причину покажет код выхода
                }
            });
            byte[] pcm;
            try (InputStream stdout = process.getInputStream()) {
                pcm = stdout.readAllBytes();
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException(processBuilder.command().getFirst() + " exited with code " + exitCode);
            }
            return pcm;
        } finally {
            process.destroyForcibly(); // если процесс уже завершился, ничего не делает
        }
    }
}
