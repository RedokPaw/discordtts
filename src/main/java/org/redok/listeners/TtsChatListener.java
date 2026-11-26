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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;

/**
 * Читает вслух текстовый чат голосового канала, в котором сидят и автор, и бот.
 * Сообщения, начинающиеся с {@value #SKIP_PREFIX}, не читаются: так можно написать в чат молча.
 */
public class TtsChatListener extends ListenerAdapter {

    static final String SKIP_PREFIX = "!";
    private static final Logger log = LoggerFactory.getLogger(TtsChatListener.class);
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
            log.debug("message {} ignored: author {} is not in a voice channel", event.getMessageId(), member.getId());
            return;
        }
        if (!Objects.equals(event.getMessage().getChannelId(), voiceChannel.getId())) {
            log.debug("message {} ignored: written in channel {}, not in the chat of author's voice channel {}",
                    event.getMessageId(), event.getMessage().getChannelId(), voiceChannel.getId());
            return;
        }
        if (!voice.isBotIn(voiceChannel)) {
            log.debug("message {} ignored: the bot is not in voice channel {}", event.getMessageId(), voiceChannel.getId());
            return;
        }

        String content = event.getMessage().getContentDisplay();
        if (content.stripLeading().startsWith(SKIP_PREFIX)) {
            log.debug("message {} ignored: starts with '{}'", event.getMessageId(), SKIP_PREFIX);
            return;
        }
        String text = UrlReplacer.replaceUrlsWithDomains(content);
        log.debug("message {} accepted: {} chars ({} after replacing URLs)", event.getMessageId(), content.length(),
                text.length());
        if (voice.speak(event.getGuild(), text) == SpeakResult.QUEUE_FULL) {
            event.getMessage().reply("Очередь сообщений переполнена, не так быстро!")
                    .delay(Duration.ofSeconds(10))
                    .flatMap(Message::delete)
                    .queue();
        }
    }
}
