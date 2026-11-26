package org.redok.commands;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.redok.bot.BotController;
import org.redok.bot.BotFactory;

import java.time.Duration;
import java.util.Objects;

public class JoinChannelCommand implements SlashCommand {

    // Только серверы: по умолчанию команда доступна и в ЛС с ботом, где getMember() == null
    private final static SlashCommandData commandData = Commands.slash("join", "Join user voice channel")
            .setContexts(InteractionContextType.GUILD);
    private final BotController botController = BotFactory.getBotRunner().getBotController();

    @Override

    public SlashCommandData getSlashCommandData() {
        return commandData;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        Member member = event.getMember();
        GuildVoiceState voiceState = member == null ? null : member.getVoiceState();
        JDA jda = event.getJDA();
        ReplyCallbackAction replyCallbackAction;
        if (event.getGuild() == null || member == null) {
            replyCallbackAction = event.reply("Гильдия не найдена, команда введена на сервере?");
        } else if (voiceState == null || !voiceState.inAudioChannel()) {
            replyCallbackAction = event.reply("Ты не в голосовом канале!");
        } else if (voiceState.getChannel().getMembers()
                .stream()
                .anyMatch(m -> Objects.equals(jda.getSelfUser().getId(), m.getId()))) {
            replyCallbackAction = event.reply("Я уже в баньке!");
        } else {
            botController.joinVoiceChannel(event.getGuild(), voiceState);
            replyCallbackAction = event.reply("Никогда не доверяй фурри");
        }
        replyCallbackAction
                .delay(Duration.ofSeconds(10))
                .flatMap(InteractionHook::deleteOriginal)
                .queue();
    }
}
