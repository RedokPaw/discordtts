package org.redok.commands;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.SelfUser;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.managers.AudioManager;
import org.junit.jupiter.api.Test;
import org.redok.listeners.CommandListener;
import org.redok.testutil.FakeTts;
import org.redok.testutil.Stubs;
import org.redok.tts.TtsSynthesizer;
import org.redok.voice.VoiceService;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlashCommandsTest {

    private final Stubs stubs = new Stubs();
    private final JDA jda = stubs.stub(JDA.class, "getSelfUser", stubs.stub(SelfUser.class, "getId", "1"));
    private final Member bot = stubs.stub(Member.class, "getId", "1");
    private final Guild guild = stubs.stub(Guild.class, "getId", "g", "getAudioManager", stubs.stub(AudioManager.class));
    private final VoiceService voice =
            new VoiceService(new TtsSynthesizer(new FakeTts(200).registry(), encoded -> encoded, Runnable::run, 3), 10);
    private final SlashCommand join = new JoinChannelCommand(voice);
    private final SlashCommand leave = new LeaveChannelCommand(voice);

    @Test
    void commandsAreRegisteredForGuildsOnly() {
        // по умолчанию Discord показывает команды ещё и в ЛС с ботом, где getMember() == null
        assertEquals(Set.of(InteractionContextType.GUILD), join.getSlashCommandData().getContexts());
        assertEquals(Set.of(InteractionContextType.GUILD), leave.getSlashCommandData().getContexts());
    }

    @Test
    void joinInDirectMessages() {
        run(join, null);

        assertReplied("Гильдия не найдена");
    }

    @Test
    void joinOutsideVoice() {
        run(join, memberIn(null));

        assertReplied("Ты не в голосовом канале");
    }

    @Test
    void joinWhenTheBotIsAlreadyThere() {
        run(join, memberIn(channel("42", bot)));

        assertReplied("Я уже в баньке");
        assertFalse(stubs.called("openAudioConnection"));
    }

    @Test
    void joinConnectsTheBot() {
        run(join, memberIn(channel("42")));

        assertTrue(stubs.called("AudioManager.openAudioConnection"));
        assertReplied("Никогда не доверяй фурри");
    }

    @Test
    void joinFromAStageChannel() {
        AudioChannelUnion stage = stubs.stub(AudioChannelUnion.class, "getId", "77", "getName", "stage", "getJDA", jda,
                "getMembers", List.of(),
                "asVoiceChannel", new IllegalStateException("Cannot convert channel of type STAGE to VoiceChannel"));

        run(join, memberIn(stage));

        assertTrue(stubs.called("AudioManager.openAudioConnection"));
    }

    @Test
    void leaveInDirectMessages() {
        run(leave, null);

        assertReplied("Гильдия не найдена");
    }

    @Test
    void leaveOutsideVoice() {
        run(leave, memberIn(null));

        assertReplied("Вы не в голосовом канале");
    }

    @Test
    void leaveFromTheBotsChannel() {
        run(leave, memberIn(channel("42", bot)));

        assertTrue(stubs.called("AudioManager.closeAudioConnection"));
        assertReplied("Left the voice channel");
    }

    @Test
    void leaveFromAnotherChannel() {
        run(leave, memberIn(channel("43")));

        assertFalse(stubs.called("closeAudioConnection"));
        assertReplied("Я не в вашем голосовом канале");
    }

    @Test
    void commandListenerRoutesByName() {
        AtomicInteger handled = new AtomicInteger();
        SlashCommand ping = new SlashCommand() {
            private final SlashCommandData data = Commands.slash("ping", "ping");

            @Override
            public SlashCommandData getSlashCommandData() {
                return data;
            }

            @Override
            public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
                handled.incrementAndGet();
            }
        };
        CommandListener listener = new CommandListener(List.of(ping));

        listener.onSlashCommandInteraction(event("ping", null));
        listener.onSlashCommandInteraction(event("nope", null));

        assertEquals(1, handled.get());
        assertReplied("This slash command does not exist");
    }

    private AudioChannelUnion channel(String id, Member... members) {
        return stubs.stub(AudioChannelUnion.class,
                "getId", id, "getName", "voice", "getJDA", jda, "getMembers", List.of(members));
    }

    private Member memberIn(AudioChannelUnion channel) {
        return stubs.stub(Member.class, "getId", "2", "getVoiceState",
                stubs.stub(GuildVoiceState.class, "getChannel", channel, "inAudioChannel", channel != null));
    }

    private void run(SlashCommand command, Member member) {
        command.onSlashCommandInteraction(event(command.getSlashCommandData().getName(), member));
    }

    /** Взаимодействие из гильдии, если есть участник, иначе — из ЛС (ни участника, ни гильдии). */
    private SlashCommandInteractionEvent event(String name, Member member) {
        SlashCommandInteraction interaction = stubs.stub(SlashCommandInteraction.class,
                "getName", name, "getMember", member, "getGuild", member == null ? null : guild);
        return new SlashCommandInteractionEvent(jda, 0, interaction);
    }

    private void assertReplied(String fragment) {
        assertTrue(stubs.called(fragment), "нет ответа «" + fragment + "»");
    }
}
