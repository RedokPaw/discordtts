package org.redok.audio;

import java.io.IOException;

/** Декодирует звук в формат, который ждёт JDA: PCM 48 кГц, стерео, 16 бит, big-endian. */
public interface AudioDecoder {

    byte[] toPcm(byte[] encoded) throws IOException, InterruptedException;
}
