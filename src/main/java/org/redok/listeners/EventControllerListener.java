package org.redok.listeners;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.redok.bot.BotController;
import org.redok.commands.BotCommandHandler;
import org.redok.tts.TTSMessageHandler;
import org.redok.utils.UrlReplacer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

public class EventControllerListener extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(EventControllerListener.class);
    private final BotCommandHandler botCommandHandler;
    private final TTSMessageHandler messageHandler;
    private final BotController botController;

    public EventControllerListener(BotCommandHandler botCommandHandler, TTSMessageHandler messageHandler,
                                   BotController botController) {
        this.botCommandHandler = botCommandHandler;
        this.messageHandler = messageHandler;
        this.botController = botController;
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        botCommandHandler.doCommand(event);
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
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
        if (!Objects.equals(event.getMessage().getChannelId(), voiceChannel.getId())) {
            return;
        }
        if (voiceChannel.getMembers()
                .stream()
                .noneMatch(m -> m.getId().equals(event.getJDA().getSelfUser().getId()))) {
            return;
        }

        String text = UrlReplacer.replaceUrlsWithDomains(event.getMessage().getContentDisplay());
        if (text.isBlank()) {
            return; // картинка, стикер или эмбед без текста: произносить нечего, Google ответил бы 400
        }
        boolean queued = messageHandler.handleMessageAndSpeak(text, event.getGuild().getId());
        if (!queued) {
            event.getMessage().reply("Очередь сообщений переполнена, не так быстро!")
                    .delay(Duration.ofSeconds(10))
                    .flatMap(Message::delete)
                    .queue();
        }
    }

    @Override
    public void onGuildVoiceUpdate(@NotNull GuildVoiceUpdateEvent event) {
        Guild guild = event.getGuild();
        AudioChannelUnion audioChannelUnion = event.getChannelLeft();
        if (audioChannelUnion == null) {
            return;
        }
        // getMembers() есть у любого AudioChannel, а asVoiceChannel() бросал IllegalStateException на Stage-каналах
        List<Member> voiceChannelMembers = audioChannelUnion.getMembers();
        if (voiceChannelMembers.size() == 1 &&
                voiceChannelMembers.getFirst().getId().equals(event.getJDA().getSelfUser().getId())) {
            log.info("leaving empty voice channel: {} ", audioChannelUnion.getId());
            botController.leaveVoiceChannel(guild);
        }
    }
}
