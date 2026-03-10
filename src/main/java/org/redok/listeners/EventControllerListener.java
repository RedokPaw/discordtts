package org.redok.listeners;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.redok.bot.BotController;
import org.redok.bot.BotFactory;
import org.redok.commands.BotCommandHandler;
import org.redok.commands.BotCommandHandlerFactory;
import org.redok.tts.TTSMessageHandler;
import org.redok.tts.TTSMessageHandlerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

public class EventControllerListener extends ListenerAdapter {

    private final Logger log = LoggerFactory.getLogger(EventControllerListener.class);
    private final BotCommandHandler botCommandHandler = BotCommandHandlerFactory.getBotCommandHandler();
    private final TTSMessageHandler messageHandler = TTSMessageHandlerFactory.getMessageHandler();
    private final BotController botController = BotFactory.getBotRunner().getBotController();

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        botCommandHandler.doCommand(event);
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) {
            return;
        }
        GuildVoiceState memberVoiceState = event.getMember().getVoiceState();
        if (memberVoiceState == null) {
            return;
        }
        if (!Objects.equals(event.getMessage().getChannelId(), memberVoiceState.getChannel().getId())) {
            return;
        }
        if (memberVoiceState.getChannel().getMembers()
                .stream()
                .noneMatch(member -> member.getId().equals(event.getJDA().getSelfUser().getId()))) {
            return;
        }

        messageHandler.handleMessageAndSpeak(event);
    }

    @Override
    public void onGuildVoiceUpdate(@NotNull GuildVoiceUpdateEvent event) {
        Guild guild = event.getGuild();
        AudioChannelUnion audioChannelUnion = event.getChannelLeft();
        if (audioChannelUnion == null) {
            return;
        }
        List<Member> voiceChannelMembers = audioChannelUnion.asVoiceChannel().getMembers();
        if (voiceChannelMembers.size() == 1 &&
                voiceChannelMembers.getFirst().getId().equals(event.getJDA().getSelfUser().getId())) {
            log.info("leaving empty voice channel: {} ", audioChannelUnion.asVoiceChannel().getId());
            botController.leaveVoiceChannel(guild);
        }
    }
}
