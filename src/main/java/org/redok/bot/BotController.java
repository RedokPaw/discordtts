package org.redok.bot;

import net.dv8tion.jda.api.audio.AudioSendHandler;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.managers.AudioManager;
import org.jetbrains.annotations.NotNull;
import org.redok.tts.TTSMessageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BotController {

    private static final Logger log = LoggerFactory.getLogger(BotController.class);
    private final TTSMessageHandler ttsMessageHandler;

    public BotController(TTSMessageHandler ttsMessageHandler) {
        this.ttsMessageHandler = ttsMessageHandler;
    }

    public void joinVoiceChannel(@NotNull Guild guild, @NotNull GuildVoiceState guildVoiceState) {
        // asVoiceChannel() бросал IllegalStateException на Stage-каналах, а id и имя есть у любого канала
        log.info("joining voice channel {} ({}) in guild {}", guildVoiceState.getChannel().getId(),
                guildVoiceState.getChannel().getName(), guild.getId());
        AudioSendHandler audioSendHandler = ttsMessageHandler.registerGuild(guild.getId());
        AudioManager audioManager = guild.getAudioManager();
        audioManager.setSendingHandler(audioSendHandler);
        audioManager.openAudioConnection(guildVoiceState.getChannel());
    }

    public void leaveVoiceChannel(@NotNull Guild guild) {
        log.info("leaving voice channel in guild: {}", guild.getId());
        AudioManager audioManager = guild.getAudioManager();
        audioManager.setSendingHandler(null); // иначе JDA держит старый хендлер с очередью до следующего /join
        audioManager.closeAudioConnection();
        ttsMessageHandler.unregisterGuild(guild.getId()); // отменяет слоты, память освобождается сразу
    }
}
