package org.redok.commands;

import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.redok.voice.VoiceService;

import java.time.Duration;

public class LeaveChannelCommand implements SlashCommand {

    // Только серверы: по умолчанию команда доступна и в ЛС с ботом, где getMember() == null
    private static final SlashCommandData commandData = Commands.slash("leave", "Leave user channel")
            .setContexts(InteractionContextType.GUILD);
    private final VoiceService voice;

    public LeaveChannelCommand(VoiceService voice) {
        this.voice = voice;
    }

    @Override
    public SlashCommandData getSlashCommandData() {
        return commandData;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        ReplyCallbackAction replyCallbackAction;
        Member member = event.getMember();
        // При CacheFlag.VOICE_STATE getVoiceState() не бывает null, но вне войса getChannel() == null
        GuildVoiceState voiceState = member == null ? null : member.getVoiceState();
        if (event.getGuild() == null || member == null) {
            replyCallbackAction = event.reply("Гильдия не найдена, команда введена на сервере?");
        } else if (voiceState == null || !voiceState.inAudioChannel()) {
            replyCallbackAction = event.reply("Вы не в голосовом канале");
        } else if (voice.isBotIn(voiceState.getChannel())) {
            voice.leave(event.getGuild());
            replyCallbackAction = event.reply("Left the voice channel");
        } else {
            replyCallbackAction = event.reply("Я не в вашем голосовом канале");
        }
        replyCallbackAction
                .delay(Duration.ofSeconds(3))
                .flatMap(InteractionHook::deleteOriginal)
                .queue();
    }
}
