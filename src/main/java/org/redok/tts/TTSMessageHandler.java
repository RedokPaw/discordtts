package org.redok.tts;

import net.dv8tion.jda.api.audio.AudioSendHandler;

public interface TTSMessageHandler {

    void handleMessageAndSpeak(String text, String guildId);

    AudioSendHandler registerGuild(String guildId);

    void unregisterGuild(String guildId);

}
