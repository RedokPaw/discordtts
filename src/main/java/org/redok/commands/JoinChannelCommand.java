package org.redok.commands;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.redok.bot.BotController;
import org.redok.bot.BotFactory;

import java.time.Duration;
import java.util.Objects;

public class JoinChannelCommand implements SlashCommand {

    private final static SlashCommandData commandData = Commands.slash("join", "Join user voice channel");
    private final BotController botController = BotFactory.getBotRunner().getBotController();

    @Override

    public SlashCommandData getSlashCommandData() {
        return commandData;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        GuildVoiceState voiceState = event.getMember().getVoiceState();
        JDA jda = event.getJDA();
        ReplyCallbackAction replyCallbackAction;
        if (voiceState == null || !voiceState.inAudioChannel()) {
            replyCallbackAction = event.reply("Ты не в голосовом канале!");
        } else if (voiceState.getChannel().getMembers()
                .stream()
                .anyMatch(member -> Objects.equals(jda.getSelfUser().getId(), member.getId()))) {
            replyCallbackAction = event.reply("Я уже в баньке!");
        } else {
            botController.joinVoiceChannel(event.getGuild(), voiceState);
            replyCallbackAction = event.reply("Никогда не доверяй фурри");
        }
        Thread.ofVirtual().start(
                () -> replyCallbackAction.delay(Duration.ofSeconds(10)).complete().deleteOriginal().queue());
    }
}
