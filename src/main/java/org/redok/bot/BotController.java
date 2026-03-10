package org.redok.bot;

import net.dv8tion.jda.api.audio.AudioSendHandler;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.managers.AudioManager;
import org.jetbrains.annotations.NotNull;
import org.redok.tts.TTSMessageHandler;
import org.redok.tts.TTSMessageHandlerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BotController {

    private final Logger log = LoggerFactory.getLogger(BotController.class);
    private final BotRunner botRunner;
    private final TTSMessageHandler ttsMessageHandler = TTSMessageHandlerFactory.getMessageHandler();

    private BotController(BotRunner botRunner) {
        this.botRunner = botRunner;
    }

    public static BotController create(BotRunner botRunner) {
        return new BotController(botRunner);
    }

    public void joinVoiceChannel(@NotNull Guild guild, @NotNull GuildVoiceState guildVoiceState) {
        log.info("joining voice channel with id: {},  guild: {}", guildVoiceState.getChannel().asVoiceChannel().getId(),
                guildVoiceState.getChannel().getName());
        AudioSendHandler audioSendHandler = ttsMessageHandler.registerGuild(guild.getId());
        AudioManager audioManager = guild.getAudioManager();
        audioManager.setSendingHandler(audioSendHandler);
        audioManager.openAudioConnection(guildVoiceState.getChannel());
    }

    public void leaveVoiceChannel(@NotNull Guild guild) {
        log.info("leaving voice channel in guild: {}", guild.getId());
        ttsMessageHandler.unregisterGuild(guild.getId());
        AudioManager audioManager = guild.getAudioManager();
        audioManager.closeAudioConnection();
    }
}
