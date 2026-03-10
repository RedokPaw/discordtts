package org.redok.tts;

public class TTSMessageHandlerFactory {

    private final static TTSMessageHandler messageHandler = new TTSMessageHandlerImpl();

    public static TTSMessageHandler getMessageHandler() {
        return messageHandler;
    }

}
