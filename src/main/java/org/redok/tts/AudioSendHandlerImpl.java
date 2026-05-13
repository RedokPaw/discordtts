package org.redok.tts;

import net.dv8tion.jda.api.audio.AudioSendHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;


public class AudioSendHandlerImpl implements AudioSendHandler {

    private static final int QUEUE_CAPACITY = 10;
    private static final int FRAME_SIZE = 3840;
    private final BlockingQueue<ByteBuffer> ttsQueue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
    private ByteBuffer ttsBuffer;
    private ByteBuffer chunk = ByteBuffer.allocate(FRAME_SIZE);
    private boolean hasChunk = false;

    private final Logger log = LoggerFactory.getLogger(AudioSendHandlerImpl.class);

    public boolean queueTtsStream(InputStream inputStream) {
        byte[] payload;
        try (inputStream) {
            payload = inputStream.readAllBytes();
        } catch (IOException e) {
            log.error("something bad happened while reading tts audio: {}", e.getMessage());
            throw new RuntimeException(e);
        }
        int remainder = payload.length % FRAME_SIZE;
        if (remainder != 0) {
            payload = Arrays.copyOf(payload, payload.length + (FRAME_SIZE - remainder));
        }
        return ttsQueue.offer(ByteBuffer.wrap(payload));
    }

    public boolean isQueueFull() {
        return ttsQueue.size() >= QUEUE_CAPACITY;
    }

    @Override
    public boolean canProvide() {
        if (hasChunk) {
            return true;
        }
        if (ttsBuffer == null) {
            ttsBuffer = ttsQueue.poll();
            if (ttsBuffer == null) {
                return false;
            }
        }

        int bytesLeft = Math.min(ttsBuffer.remaining(), FRAME_SIZE);
        if (bytesLeft == 0) {
            ttsBuffer = null;
            return false;
        }

        chunk.clear();
        ttsBuffer.limit(ttsBuffer.position() + bytesLeft);
        chunk.put(ttsBuffer);
        ttsBuffer.limit(ttsBuffer.capacity());

        chunk.flip();
        hasChunk = true;
        return true;
    }

    @Override
    public ByteBuffer provide20MsAudio() {
        hasChunk = false;
        return chunk;
    }

    @Override
    public boolean isOpus() {
        return false;
    }
}