package org.redok.listeners;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.SelfUser;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.managers.AudioManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redok.testutil.FakeTts;
import org.redok.testutil.Stubs;
import org.redok.tts.TtsSynthesizer;
import org.redok.voice.GuildVoiceSession;
import org.redok.voice.VoiceService;
import org.redok.voice.VoiceService.SpeakResult;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoiceStateListenerTest {

    private final Stubs stubs = new Stubs();
    private final AtomicReference<GuildVoiceSession> session = new AtomicReference<>();
    private final JDA jda = stubs.stub(JDA.class, "getSelfUser", stubs.stub(SelfUser.class, "getId", "1"));
    private final Guild guild = stubs.stub(Guild.class, "getId", "g", "getAudioManager", stubs.stub(AudioManager.class,
            "setSendingHandler", (Stubs.Answer) args -> {
                session.set((GuildVoiceSession) args[0]);
                return null;
            }));
    private final Member bot = member("1", null);
    private VoiceService voice;
    private VoiceStateListener listener;

    @BeforeEach
    void setUp() {
        voice = new VoiceService(new TtsSynthesizer(new FakeTts(200), encoded -> encoded, Runnable::run, 3), 10);
        listener = new VoiceStateListener(voice);
    }

    @Test
    void leavesWhenTheLastPersonLeaves() {
        AudioChannelUnion channel = channel("42", bot);
        voice.join(guild, channel);

        listener.onGuildVoiceUpdate(new GuildVoiceUpdateEvent(jda, 0, member("2", null), channel));

        assertTrue(stubs.called("AudioManager.closeAudioConnection"));
        assertEquals(SpeakResult.NOT_CONNECTED, voice.speak(guild, "привет"));
    }

    @Test
    void leavesAnEmptyStageChannelToo() {
        // настоящий JDA бросает IllegalStateException из asVoiceChannel() для канала типа STAGE
        AudioChannelUnion stage = stubs.stub(AudioChannelUnion.class, "getId", "77", "getJDA", jda,
                "getMembers", List.of(bot),
                "asVoiceChannel", new IllegalStateException("Cannot convert channel of type STAGE to VoiceChannel"));
        voice.join(guild, stage);

        listener.onGuildVoiceUpdate(new GuildVoiceUpdateEvent(jda, 0, member("2", null), stage));

        assertTrue(stubs.called("AudioManager.closeAudioConnection"));
    }

    @Test
    void staysWhileSomeoneIsStillThere() {
        AudioChannelUnion channel = channel("42", bot, member("3", null));
        voice.join(guild, channel);

        listener.onGuildVoiceUpdate(new GuildVoiceUpdateEvent(jda, 0, member("2", null), channel));

        assertFalse(stubs.called("closeAudioConnection"));
    }

    @Test
    void dropsQueuedSpeechWhenTheBotIsDisconnected() {
        AudioChannelUnion channel = channel("42");
        voice.join(guild, channel);
        voice.speak(guild, "привет");

        // бот ушёл из канала и никуда не пришёл: так выглядит кик модератором
        listener.onGuildVoiceUpdate(new GuildVoiceUpdateEvent(jda, 0, bot, channel));

        assertFalse(session.get().canProvide(), "ожидающая речь выброшена");
        assertFalse(stubs.called("closeAudioConnection"), "соединением распоряжается JDA");
        assertEquals(SpeakResult.QUEUED, voice.speak(guild, "привет"), "сессия осталась на случай переподключения");
    }

    @Test
    void keepsTheQueueWhenTheBotIsMoved() {
        AudioChannelUnion from = channel("42");
        AudioChannelUnion to = channel("43");
        voice.join(guild, from);
        voice.speak(guild, "привет");

        listener.onGuildVoiceUpdate(new GuildVoiceUpdateEvent(jda, 0, member("1", to), from));

        assertTrue(session.get().canProvide());
    }

    private AudioChannelUnion channel(String id, Member... members) {
        return stubs.stub(AudioChannelUnion.class, "getId", id, "getJDA", jda, "getMembers", List.of(members));
    }

    /** Участник и канал, в котором он оказался после события (null — вышел из войса). */
    private Member member(String id, AudioChannelUnion currentChannel) {
        return stubs.stub(Member.class, "getId", id, "getGuild", guild,
                "getVoiceState", stubs.stub(GuildVoiceState.class, "getChannel", currentChannel));
    }
}
