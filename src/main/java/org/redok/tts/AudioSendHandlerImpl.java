package org.redok.tts;

import net.dv8tion.jda.api.audio.AudioSendHandler;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class AudioSendHandlerImpl implements AudioSendHandler {
    private static final int FRAME_SIZE = 3840;
    private final Queue<InputStream> ttsQueue = new ConcurrentLinkedQueue<>();
    private InputStream pcmStream;
    private final byte[] buffer = new byte[FRAME_SIZE];

    private ByteBuffer nextChunk = null;

    public void queueTtsStream(InputStream inputStream) {
        ttsQueue.add(inputStream);
    }

    @Override
    public boolean canProvide() {
        if (nextChunk != null) {
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
                pcmStream = null;
                return false;
            }
            ByteBuffer chunk = ByteBuffer.allocate(FRAME_SIZE);
            chunk.put(buffer, 0, FRAME_SIZE);
            chunk.flip();
            nextChunk = chunk;
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public ByteBuffer provide20MsAudio() {
        ByteBuffer chunk = nextChunk;
        nextChunk = null;
        return chunk;
    }

    @Override
    public boolean isOpus() {
        return false;
    }
}