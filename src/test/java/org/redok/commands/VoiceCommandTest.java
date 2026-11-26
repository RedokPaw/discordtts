package org.redok.commands;

import gnu.trove.map.hash.TLongObjectHashMap;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.SelfUser;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.AutoCompleteQuery;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.CommandAutoCompleteInteraction;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.managers.AudioManager;
import net.dv8tion.jda.api.requests.restaction.interactions.AutoCompleteCallbackAction;
import net.dv8tion.jda.api.utils.data.DataObject;
import org.junit.jupiter.api.Test;
import org.redok.listeners.CommandListener;
import org.redok.preferences.VoicePreferences;
import org.redok.testutil.FakeTts;
import org.redok.testutil.MemoryVoiceStore;
import org.redok.testutil.Stubs;
import org.redok.tts.TtsSynthesizer;
import org.redok.tts.Voice;
import org.redok.tts.VoiceRegistry;
import org.redok.voice.VoiceService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoiceCommandTest {

    private final Stubs stubs = new Stubs();
    private final JDA jda = stubs.stub(JDA.class, "getSelfUser", stubs.stub(SelfUser.class, "getId", "1"));
    private final Member bot = stubs.stub(Member.class, "getId", "1");
    private final Guild guild = stubs.stub(Guild.class, "getId", "g", "getAudioManager", stubs.stub(AudioManager.class));
    private final FakeTts engine = new FakeTts(200); // fake:a «Альфа» (по умолчанию), fake:b «Бета»
    private final VoiceRegistry voices = engine.registry();
    private final MemoryVoiceStore store = new MemoryVoiceStore();
    private final VoicePreferences preferences = new VoicePreferences(store, voices);
    private final VoiceService voice =
            new VoiceService(new TtsSynthesizer(voices, encoded -> encoded, Runnable::run, 3), 10);
    private final VoiceCommand command = new VoiceCommand(preferences, voices, voice);
    private final List<Command.Choice> choices = new ArrayList<>();

    @Test
    void isAGuildCommandWithAnAutocompletedVoice() {
        assertEquals(Set.of(InteractionContextType.GUILD), command.getSlashCommandData().getContexts());
        SubcommandData set = command.getSlashCommandData().getSubcommands().stream()
                .filter(sub -> sub.getName().equals("set")).findFirst().orElseThrow();
        assertTrue(set.getOptions().getFirst().isAutoComplete());
        assertTrue(set.getOptions().getFirst().isRequired());
    }

    @Test
    void setSavesTheVoiceAndRepliesOnlyToTheAuthor() {
        run("set", "fake:b", memberIn(null));

        assertEquals(Optional.of(FakeTts.OTHER_VOICE), preferences.chosen("7"));
        assertReplied("Теперь твой голос: Бета (Fake)");
        assertTrue(stubs.called("setEphemeral[true]"));
    }

    @Test
    void setAcceptsAVoiceTypedByName() {
        run("set", "бета", memberIn(null)); // вписали руками, а не выбрали из подсказки

        assertEquals(Optional.of(FakeTts.OTHER_VOICE), preferences.chosen("7"));
    }

    @Test
    void setRejectsAnUnknownVoice() {
        run("set", "zzz", memberIn(null));

        assertEquals(Optional.empty(), preferences.chosen("7"));
        assertReplied("Не знаю голос «zzz»");
    }

    @Test
    void setSaysAPhraseWithTheNewVoiceWhenTheBotIsInTheChannel() {
        AudioChannelUnion channel = channel(bot);
        voice.join(guild, channel);

        run("set", "fake:b", memberIn(channel));

        assertEquals(List.of(VoiceCommand.PREVIEW), engine.texts());
        assertEquals(List.of(FakeTts.OTHER_VOICE), engine.voicesUsed());
    }

    @Test
    void setStaysSilentWhenTheBotIsNotInTheAuthorsChannel() {
        run("set", "fake:b", memberIn(channel()));

        assertTrue(engine.texts().isEmpty());
    }

    @Test
    void setRemembersAnUnavailableVoiceButSaysSo() {
        engine.setAvailable(false);

        run("set", "fake:b", memberIn(null));

        assertEquals(Optional.of(FakeTts.OTHER_VOICE), preferences.chosen("7"));
        assertReplied("сейчас он недоступен");
    }

    @Test
    void showWithoutAChoiceNamesTheDefaultVoice() {
        run("show", null, memberIn(null));

        assertReplied("Ты не выбирал голос, читаю голосом по умолчанию: Альфа (Fake)");
    }

    @Test
    void showNamesTheChosenVoice() {
        preferences.set("7", FakeTts.OTHER_VOICE);

        run("show", null, memberIn(null));

        assertReplied("Твой голос: Бета (Fake)");
    }

    @Test
    void showExplainsAVoiceThatIsGone() {
        store.save("7", new Voice("silero", "xenia")); // провайдера больше нет в конфиге

        run("show", null, memberIn(null));

        assertReplied("Твой голос silero:xenia сейчас недоступен, читаю голосом по умолчанию: Альфа (Fake)");
    }

    @Test
    void resetForgetsTheChoice() {
        preferences.set("7", FakeTts.OTHER_VOICE);

        run("reset", null, memberIn(null));

        assertEquals(Optional.empty(), store.find("7"));
        assertReplied("Голос сброшен");
    }

    @Test
    void aDatabaseErrorIsReportedToTheUser() {
        store.breakDown();

        run("set", "fake:b", memberIn(null));

        assertReplied("Не получилось обратиться к базе");
    }

    @Test
    void autocompleteSuggestsMatchingVoices() {
        command.onAutocomplete(autocomplete("voice", "бе"));

        assertEquals(List.of(new Command.Choice("Бета (Fake)", "fake:b")), choices);
    }

    @Test
    void theListenerRoutesAutocompleteByCommandName() {
        CommandListener listener = new CommandListener(List.of(command));

        listener.onCommandAutoCompleteInteraction(autocomplete("voice", ""));
        assertEquals(2, choices.size());

        choices.clear();
        listener.onCommandAutoCompleteInteraction(autocomplete("nope", ""));
        assertEquals(List.of(), choices, "незнакомой команде — пустой список, а не тишина");
    }

    private AudioChannelUnion channel(Member... members) {
        return stubs.stub(AudioChannelUnion.class,
                "getId", "42", "getName", "voice", "getJDA", jda, "getMembers", List.of(members));
    }

    private Member memberIn(AudioChannelUnion channel) {
        return stubs.stub(Member.class, "getId", "7", "getVoiceState",
                stubs.stub(GuildVoiceState.class, "getChannel", channel, "inAudioChannel", channel != null));
    }

    private void run(String subcommand, String voiceOption, Member member) {
        List<OptionMapping> options = voiceOption == null ? List.of() : List.of(option(voiceOption));
        SlashCommandInteraction interaction = stubs.stub(SlashCommandInteraction.class,
                "getName", "voice", "getSubcommandName", subcommand, "getOptions", options,
                "getUser", stubs.stub(User.class, "getId", "7"), "getMember", member, "getGuild", guild);
        command.onSlashCommandInteraction(new SlashCommandInteractionEvent(jda, 0, interaction));
    }

    private CommandAutoCompleteInteractionEvent autocomplete(String commandName, String typed) {
        CommandAutoCompleteInteraction interaction = stubs.stub(CommandAutoCompleteInteraction.class,
                "getName", commandName,
                "getFocusedOption", new AutoCompleteQuery(option(typed)),
                "replyChoices", (Stubs.Answer) args -> {
                    @SuppressWarnings("unchecked")
                    Collection<Command.Choice> given = (Collection<Command.Choice>) args[0];
                    choices.addAll(given);
                    return stubs.stub(AutoCompleteCallbackAction.class);
                });
        return new CommandAutoCompleteInteractionEvent(jda, 0, interaction);
    }

    private OptionMapping option(String value) {
        DataObject data = DataObject.empty()
                .put("name", VoiceCommand.VOICE_OPTION)
                .put("type", OptionType.STRING.getKey())
                .put("value", value);
        // JDA приводит api к внутреннему JDAImpl; строковой опции он не нужен, поэтому null
        return new OptionMapping(data, new TLongObjectHashMap<>(), null, null);
    }

    private void assertReplied(String fragment) {
        assertTrue(stubs.called(fragment), "нет ответа «" + fragment + "»: " + stubs.calls());
    }
}
