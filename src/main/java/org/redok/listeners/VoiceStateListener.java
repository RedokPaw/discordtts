package org.redok.listeners;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.redok.voice.VoiceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/** Уходит из опустевшего канала и прибирает очередь, если бота отключили из канала. */
public class VoiceStateListener extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(VoiceStateListener.class);
    private final VoiceService voice;

    public VoiceStateListener(VoiceService voice) {
        this.voice = voice;
    }

    @Override
    public void onGuildVoiceUpdate(@NotNull GuildVoiceUpdateEvent event) {
        AudioChannelUnion channelLeft = event.getChannelLeft();
        if (channelLeft == null) {
            return;
        }
        if (event.getMember().getId().equals(event.getJDA().getSelfUser().getId())) {
            if (event.getChannelJoined() == null) {
                // Бота отключили. Если это был наш собственный leave, сессии уже нет и вызов ничего не сделает.
                voice.onDisconnected(event.getGuild());
            }
            return;
        }
        // getMembers() есть у любого AudioChannel, а asVoiceChannel() бросал IllegalStateException на Stage-каналах
        List<Member> voiceChannelMembers = channelLeft.getMembers();
        if (voiceChannelMembers.size() == 1 && voice.isBotIn(channelLeft)) {
            log.info("leaving empty voice channel: {} ", channelLeft.getId());
            voice.leave(event.getGuild());
        }
    }
}
