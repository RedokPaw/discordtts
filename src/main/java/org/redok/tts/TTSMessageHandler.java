package org.redok.tts;

import net.dv8tion.jda.api.audio.AudioSendHandler;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public interface TTSMessageHandler {

    void handleMessageAndSpeak(MessageReceivedEvent event);

    AudioSendHandler registerGuild(String guildId);

    void unregisterGuild(String guildId);

}
