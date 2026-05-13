package org.redok.tts;

import net.dv8tion.jda.api.audio.AudioSendHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;


//TODO: все таки хранить не InputStream, а массив байтов
public class AudioSendHandlerImpl implements AudioSendHandler {

    private static final int QUEUE_CAPACITY = 10;
    private static final int FRAME_SIZE = 3840;
    private final BlockingQueue<InputStream> ttsQueue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
    private InputStream pcmStream;
    private final byte[] buffer = new byte[FRAME_SIZE];
    private boolean hasChunk = false;
    private ByteBuffer chunk = ByteBuffer.allocate(FRAME_SIZE);

    private final Logger logger = LoggerFactory.getLogger(AudioSendHandlerImpl.class);

    public boolean queueTtsStream(InputStream inputStream) {
        return ttsQueue.offer(inputStream);
    }

    public boolean isQueueFull() {
        return ttsQueue.size() >= QUEUE_CAPACITY;
    }

    @Override
    public boolean canProvide() {
        if (hasChunk) {
            return true;
        }
        if (pcmStream == null) {
            pcmStream = ttsQueue.poll();
        }
        if (pcmStream == null) {
            return false;
        }

        try {
            int bytesRead = pcmStream.readNBytes(buffer, 0, FRAME_SIZE);
            if (bytesRead == 0) {
                pcmStream.close();
                pcmStream = ttsQueue.poll();
                return false;
            }
            chunk.clear();
            chunk.put(buffer, 0, bytesRead);
            if (bytesRead < FRAME_SIZE) {
                chunk.put(new byte[FRAME_SIZE - bytesRead]); // тишина
            }
            chunk.flip();
            hasChunk = true;
            return true;
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            return false;
        }
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