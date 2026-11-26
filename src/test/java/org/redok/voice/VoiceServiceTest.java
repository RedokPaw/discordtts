package org.redok.voice;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.SelfUser;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.managers.AudioManager;
import org.junit.jupiter.api.Test;
import org.redok.testutil.FakeTts;
import org.redok.testutil.Stubs;
import org.redok.tts.TtsSynthesizer;
import org.redok.voice.VoiceService.SpeakResult;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoiceServiceTest {

    private final Stubs stubs = new Stubs();
    private final List<Object> sendingHandlers = new ArrayList<>(); // что VoiceService отдавал в AudioManager
    private final FakeTts engine = new FakeTts(10);                 // короткий лимит — чтобы проверить нарезку
    private final AudioChannel channel = stubs.stub(AudioChannel.class, "getId", "42", "getName", "voice");

    @Test
    void speakWithoutJoinIsNotConnected() {
        assertEquals(SpeakResult.NOT_CONNECTED, voice(10, 3).speak(guild("g"), "привет"));
    }

    @Test
    void joinGivesTheSessionToJdaAndOpensTheChannel() {
        voice(10, 3).join(guild("g"), channel);

        assertInstanceOf(GuildVoiceSession.class, sendingHandlers.getLast());
        assertTrue(stubs.called("AudioManager.openAudioConnection[AudioChannelStub]"));
    }

    @Test
    void speakSplitsTheTextAndPlaysChunksInOrder() {
        VoiceService voice = voice(10, 3);
        Guild guild = guild("g");
        voice.join(guild, channel);

        assertEquals(SpeakResult.QUEUED, voice.speak(guild, "раз два три четыре"));

        assertEquals(List.of("раз два", "три четыре"), engine.texts());
        assertEquals(List.of("раз два", "три четыре"), playedTexts(session()));
    }

    @Test
    void speakReadsAtMostMaxChunksOfAMessage() {
        VoiceService voice = voice(10, 2);
        Guild guild = guild("g");
        voice.join(guild, channel);

        voice.speak(guild, "раз два три четыре пять шесть");

        assertEquals(List.of("раз два", "три четыре"), engine.texts());
    }

    @Test
    void blankTextIsNothingToSay() {
        VoiceService voice = voice(10, 3);
        Guild guild = guild("g");
        voice.join(guild, channel);

        assertEquals(SpeakResult.NOTHING_TO_SAY, voice.speak(guild, "  \n "));
        assertTrue(engine.texts().isEmpty());
    }

    @Test
    void aFullQueueRejectsTheWholeMessage() {
        VoiceService voice = voice(3, 3);
        Guild guild = guild("g");
        voice.join(guild, channel);

        assertEquals(SpeakResult.QUEUED, voice.speak(guild, "раз два"));                           // 1 кусок
        assertEquals(SpeakResult.QUEUE_FULL, voice.speak(guild, "раз два три четыре пять шесть")); // 3 куска, мест 2
        assertEquals(SpeakResult.QUEUED, voice.speak(guild, "три четыре"));                        // 1 кусок

        assertEquals(List.of("раз два", "три четыре"), engine.texts(), "отклонённое сообщение не синтезируется");
    }

    @Test
    void leaveDetachesTheSessionAndForgetsTheGuild() {
        VoiceService voice = voice(10, 3);
        Guild guild = guild("g");
        voice.join(guild, channel);

        voice.leave(guild);

        assertNull(sendingHandlers.getLast(), "JDA не должна держать старую сессию с очередью");
        assertTrue(stubs.called("AudioManager.closeAudioConnection"));
        assertEquals(SpeakResult.NOT_CONNECTED, voice.speak(guild, "привет"));
    }

    @Test
    void aDisconnectDropsQueuedSpeechButKeepsTheSession() {
        VoiceService voice = voice(10, 3);
        Guild guild = guild("g");
        voice.join(guild, channel);
        voice.speak(guild, "привет");

        voice.onDisconnected(guild);

        assertFalse(session().canProvide(), "ожидающая речь выброшена");
        assertFalse(stubs.called("closeAudioConnection"), "соединением распоряжается JDA");
        assertEquals(SpeakResult.QUEUED, voice.speak(guild, "привет"), "сессия осталась");
    }

    @Test
    void rejoinDropsTheQueueOfThePreviousSession() {
        VoiceService voice = voice(10, 3);
        Guild guild = guild("g");
        voice.join(guild, channel);
        GuildVoiceSession previous = session();
        voice.speak(guild, "привет");

        voice.join(guild, channel);

        assertFalse(previous.canProvide());
        assertNotSame(previous, session());
    }

    @Test
    void findsTheBotAmongChannelMembers() {
        JDA jda = stubs.stub(JDA.class, "getSelfUser", stubs.stub(SelfUser.class, "getId", "1"));
        AudioChannel withBot = stubs.stub(AudioChannel.class,
                "getJDA", jda, "getMembers", List.of(member("2"), member("1")));
        AudioChannel withoutBot = stubs.stub(AudioChannel.class,
                "getJDA", jda, "getMembers", List.of(member("2")));

        assertTrue(voice(10, 3).isBotIn(withBot));
        assertFalse(voice(10, 3).isBotIn(withoutBot));
    }

    private VoiceService voice(int capacity, int maxChunks) {
        // Синтез прямо в вызывающем потоке, «декодер» отдаёт байты как есть: тест детерминирован
        return new VoiceService(new TtsSynthesizer(engine, encoded -> encoded, Runnable::run, maxChunks), capacity);
    }

    private Guild guild(String id) {
        AudioManager audioManager = stubs.stub(AudioManager.class,
                "setSendingHandler", (Stubs.Answer) args -> sendingHandlers.add(args[0]));
        return stubs.stub(Guild.class, "getId", id, "getAudioManager", audioManager);
    }

    private Member member(String id) {
        return stubs.stub(Member.class, "getId", id);
    }

    private GuildVoiceSession session() {
        return (GuildVoiceSession) sendingHandlers.getLast();
    }

    /** Каждый кусок короче кадра, поэтому кадр = текст куска + тишина. */
    private static List<String> playedTexts(GuildVoiceSession session) {
        List<String> texts = new ArrayList<>();
        while (session.canProvide()) {
            ByteBuffer frame = session.provide20MsAudio();
            byte[] bytes = new byte[frame.remaining()];
            frame.get(bytes);
            int end = 0;
            while (end < bytes.length && bytes[end] != 0) {
                end++;
            }
            texts.add(new String(bytes, 0, end, StandardCharsets.UTF_8));
        }
        return texts;
    }
}
