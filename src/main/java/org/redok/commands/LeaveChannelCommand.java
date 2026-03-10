package org.redok.commands;

import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.redok.bot.BotController;
import org.redok.bot.BotFactory;

import java.time.Duration;
import java.util.Objects;

public class LeaveChannelCommand implements SlashCommand {

    private final static SlashCommandData commandData = Commands.slash("leave", "Leave user channel");
    private final BotController botController = BotFactory.getBotRunner().getBotController();

    @Override
    public SlashCommandData getSlashCommandData() {
        return commandData;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        ReplyCallbackAction replyCallbackAction;
        GuildVoiceState voiceState = event.getMember().getVoiceState();
        if (event.getGuild() == null) {
            replyCallbackAction = event.reply("Гильдия не найдена, комманда введена на сервере?");
        } else if (event.getMember().getVoiceState() == null) {
            replyCallbackAction = event.reply("Вы не в голосовом канале");
        } else if (voiceState.getChannel().getMembers()
                .stream()
                .anyMatch(member -> Objects.equals(event.getJDA().getSelfUser().getId(), member.getId()))) {
            botController.leaveVoiceChannel(event.getGuild());
            replyCallbackAction = event.reply("Left the voice channel");
        } else {
            replyCallbackAction = event.reply("Я не в вашем голосовом канале");
        }
        Thread.ofVirtual().start(() -> replyCallbackAction.delay(Duration.ofSeconds(3)).complete().deleteOriginal().queue());
    }
}
