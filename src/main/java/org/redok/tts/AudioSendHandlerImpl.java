package org.redok.tts;

import net.dv8tion.jda.api.audio.AudioSendHandler;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class AudioSendHandlerImpl implements AudioSendHandler {
    private static final int FRAME_SIZE = 3840;
    private final BlockingQueue<InputStream> ttsQueue = new LinkedBlockingQueue<>();
    private InputStream pcmStream;
    private final byte[] buffer = new byte[FRAME_SIZE];

    private ByteBuffer nextChunk = null;
    private ByteBuffer tempChunk = ByteBuffer.allocate(FRAME_SIZE);

    public boolean queueTtsStream(InputStream inputStream) {
        return ttsQueue.offer(inputStream);
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
            tempChunk.clear();
            tempChunk.put(buffer, 0, bytesRead);
            tempChunk.flip();
            nextChunk = tempChunk;
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