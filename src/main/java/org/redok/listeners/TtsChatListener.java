package org.redok.listeners;

import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.redok.utils.UrlReplacer;
import org.redok.voice.VoiceService;
import org.redok.voice.VoiceService.SpeakResult;

import java.time.Duration;
import java.util.Objects;

/** Читает вслух текстовый чат голосового канала, в котором сидят и автор, и бот. */
public class TtsChatListener extends ListenerAdapter {

    private final VoiceService voice;

    public TtsChatListener(VoiceService voice) {
        this.voice = voice;
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        // getMember() == null для личных сообщений и вебхуков
        Member member = event.getMember();
        if (event.getAuthor().isBot() || member == null) {
            return;
        }
        // При CacheFlag.VOICE_STATE getVoiceState() не бывает null, но вне войса getChannel() == null
        GuildVoiceState memberVoiceState = member.getVoiceState();
        AudioChannelUnion voiceChannel = memberVoiceState == null ? null : memberVoiceState.getChannel();
        if (voiceChannel == null) {
            return;
        }
        if (!Objects.equals(event.getMessage().getChannelId(), voiceChannel.getId()) || !voice.isBotIn(voiceChannel)) {
            return;
        }

        String text = UrlReplacer.replaceUrlsWithDomains(event.getMessage().getContentDisplay());
        if (voice.speak(event.getGuild(), text) == SpeakResult.QUEUE_FULL) {
            event.getMessage().reply("Очередь сообщений переполнена, не так быстро!")
                    .delay(Duration.ofSeconds(10))
                    .flatMap(Message::delete)
                    .queue();
        }
    }
}
