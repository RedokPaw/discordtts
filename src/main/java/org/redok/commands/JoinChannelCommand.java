package org.redok.commands;

import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.redok.voice.VoiceService;

import java.time.Duration;

public class JoinChannelCommand implements SlashCommand {

    // Только серверы: по умолчанию команда доступна и в ЛС с ботом, где getMember() == null
    private static final SlashCommandData commandData = Commands.slash("join", "Join user voice channel")
            .setContexts(InteractionContextType.GUILD);
    private final VoiceService voice;

    public JoinChannelCommand(VoiceService voice) {
        this.voice = voice;
    }

    @Override
    public SlashCommandData getSlashCommandData() {
        return commandData;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        Member member = event.getMember();
        GuildVoiceState voiceState = member == null ? null : member.getVoiceState();
        ReplyCallbackAction replyCallbackAction;
        if (event.getGuild() == null || member == null) {
            replyCallbackAction = event.reply("Гильдия не найдена, команда введена на сервере?");
        } else if (voiceState == null || !voiceState.inAudioChannel()) {
            replyCallbackAction = event.reply("Ты не в голосовом канале!");
        } else if (voice.isBotIn(voiceState.getChannel())) {
            replyCallbackAction = event.reply("Я уже в баньке!");
        } else {
            voice.join(event.getGuild(), voiceState.getChannel());
            replyCallbackAction = event.reply("Никогда не доверяй фурри");
        }
        replyCallbackAction
                .delay(Duration.ofSeconds(10))
                .flatMap(InteractionHook::deleteOriginal)
                .queue();
    }
}
