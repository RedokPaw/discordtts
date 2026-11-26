package org.redok.commands;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.redok.preferences.VoicePreferences;
import org.redok.storage.StorageException;
import org.redok.tts.Voice;
import org.redok.tts.VoiceInfo;
import org.redok.tts.VoiceRegistry;
import org.redok.voice.VoiceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/** /voice set|show|reset — голос, которым бот читает сообщения пользователя. Ответы видны только автору. */
public class VoiceCommand implements SlashCommand {

    static final String VOICE_OPTION = "voice";
    static final String PREVIEW = "Теперь я говорю так";
    private static final int MAX_CHOICE_NAME = 100; // лимит Discord на имя варианта автодополнения
    private static final Logger log = LoggerFactory.getLogger(VoiceCommand.class);

    // Только серверы, как /join и /leave. Сам голос общий для всех серверов.
    private static final SlashCommandData commandData = Commands.slash("voice", "Голос, которым я читаю твои сообщения")
            .setContexts(InteractionContextType.GUILD)
            .addSubcommands(
                    new SubcommandData("set", "Выбрать голос")
                            .addOption(OptionType.STRING, VOICE_OPTION, "Голос", true, true),
                    new SubcommandData("show", "Какой у тебя голос сейчас"),
                    new SubcommandData("reset", "Вернуть голос по умолчанию"));

    private final VoicePreferences preferences;
    private final VoiceRegistry voices;
    private final VoiceService voice;

    public VoiceCommand(VoicePreferences preferences, VoiceRegistry voices, VoiceService voice) {
        this.preferences = preferences;
        this.voices = voices;
        this.voice = voice;
    }

    @Override
    public SlashCommandData getSlashCommandData() {
        return commandData;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String userId = event.getUser().getId();
        String answer;
        try {
            answer = switch (String.valueOf(event.getSubcommandName())) {
                case "set" -> set(event, userId);
                case "show" -> show(userId);
                case "reset" -> reset(userId);
                default -> "Не знаю такой подкоманды";
            };
        } catch (StorageException e) {
            log.warn("/voice {} failed for user {}", event.getSubcommandName(), userId, e);
            answer = "Не получилось обратиться к базе, попробуй позже";
        }
        event.reply(answer).setEphemeral(true).queue();
    }

    @Override
    public void onAutocomplete(CommandAutoCompleteInteractionEvent event) {
        if (!VOICE_OPTION.equals(event.getFocusedOption().getName())) {
            event.replyChoices(List.of()).queue();
            return;
        }
        List<Command.Choice> choices = voices.search(event.getFocusedOption().getValue(), VoiceRegistry.MAX_CHOICES)
                .stream()
                .map(info -> new Command.Choice(truncate(voices.label(info)), info.voice().key()))
                .toList();
        event.replyChoices(choices).queue();
    }

    private String set(SlashCommandInteractionEvent event, String userId) {
        String input = event.getOption(VOICE_OPTION, "", OptionMapping::getAsString);
        Optional<VoiceInfo> found = voices.resolve(input);
        if (found.isEmpty()) {
            return "Не знаю голос «" + input + "». Выбери из подсказок в /voice set";
        }
        VoiceInfo info = found.get();
        preferences.set(userId, info.voice());
        if (!voices.isAvailable(info.voice())) {
            return "Запомнил голос " + voices.label(info) + ", но сейчас он недоступен — пока читаю голосом "
                    + defaultLabel();
        }
        preview(event, info.voice());
        return "Теперь твой голос: " + voices.label(info);
    }

    private String show(String userId) {
        Optional<Voice> chosen = preferences.chosen(userId);
        if (chosen.isEmpty()) {
            return "Ты не выбирал голос, читаю голосом по умолчанию: " + defaultLabel();
        }
        Optional<VoiceInfo> info = voices.find(chosen.get());
        String name = info.map(voices::label).orElse(chosen.get().key());
        if (info.isEmpty() || !voices.isAvailable(chosen.get())) {
            return "Твой голос " + name + " сейчас недоступен, читаю голосом по умолчанию: " + defaultLabel();
        }
        return "Твой голос: " + name;
    }

    private String reset(String userId) {
        preferences.reset(userId);
        return "Голос сброшен, читаю голосом по умолчанию: " + defaultLabel();
    }

    /** Если бот сидит в канале автора — пусть сразу скажет фразу новым голосом. */
    private void preview(SlashCommandInteractionEvent event, Voice chosen) {
        Guild guild = event.getGuild();
        Member member = event.getMember();
        GuildVoiceState state = member == null ? null : member.getVoiceState();
        if (guild != null && state != null && state.getChannel() != null && voice.isBotIn(state.getChannel())) {
            voice.speak(guild, PREVIEW, chosen);
        }
    }

    private String defaultLabel() {
        return voices.find(voices.defaultVoice()).map(voices::label).orElse(voices.defaultVoice().key());
    }

    private static String truncate(String name) {
        return name.length() <= MAX_CHOICE_NAME ? name : name.substring(0, MAX_CHOICE_NAME - 1) + "…";
    }
}
