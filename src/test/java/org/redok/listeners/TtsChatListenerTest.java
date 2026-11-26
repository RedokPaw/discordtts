package org.redok.listeners;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.SelfUser;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.managers.AudioManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redok.testutil.FakeTts;
import org.redok.testutil.Stubs;
import org.redok.tts.TtsSynthesizer;
import org.redok.voice.VoiceService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Настоящие события JDA поверх заглушек: так проверяется и то, как JDA достаёт участника и гильдию. */
class TtsChatListenerTest {

    private final Stubs stubs = new Stubs();
    private final FakeTts engine = new FakeTts(200);
    private final JDA jda = stubs.stub(JDA.class, "getSelfUser", stubs.stub(SelfUser.class, "getId", "1"));
    private final Member bot = stubs.stub(Member.class, "getId", "1");
    private final AudioChannelUnion voiceChannel = stubs.stub(AudioChannelUnion.class,
            "getId", "42", "getName", "voice", "getJDA", jda, "getMembers", List.of(bot));
    private final Guild guild = stubs.stub(Guild.class, "getId", "g", "getAudioManager", stubs.stub(AudioManager.class));
    private TtsChatListener listener;

    @BeforeEach
    void joinTheVoiceChannel() {
        // очередь на одно место, синтез прямо в вызывающем потоке
        VoiceService voice = new VoiceService(new TtsSynthesizer(engine, encoded -> encoded, Runnable::run, 1), 1);
        voice.join(guild, voiceChannel);
        listener = new TtsChatListener(voice);
    }

    @Test
    void readsAMessageFromTheVoiceChannelChat() {
        receive(memberIn(voiceChannel), "42", "привет");

        assertEquals(List.of("привет"), engine.texts());
    }

    @Test
    void ignoresDirectMessages() {
        receive(null, "42", "привет"); // у сообщений из ЛС и вебхуков getMember() == null

        assertTrue(engine.texts().isEmpty());
    }

    @Test
    void ignoresMembersOutsideVoice() {
        receive(memberIn(null), "42", "привет"); // voice state есть, а канала нет — так JDA ведёт себя на самом деле

        assertTrue(engine.texts().isEmpty());
    }

    @Test
    void ignoresOtherTextChannels() {
        receive(memberIn(voiceChannel), "99", "привет");

        assertTrue(engine.texts().isEmpty());
    }

    @Test
    void ignoresAVoiceChannelWithoutTheBot() {
        AudioChannelUnion otherVoice = stubs.stub(AudioChannelUnion.class,
                "getId", "43", "getJDA", jda, "getMembers", List.of());

        receive(memberIn(otherVoice), "43", "привет");

        assertTrue(engine.texts().isEmpty());
    }

    @Test
    void staysSilentOnAMessageWithoutText() {
        receive(memberIn(voiceChannel), "42", "");

        assertTrue(engine.texts().isEmpty());
        assertFalse(stubs.called("Message.reply"), "картинка без подписи — это не переполненная очередь");
    }

    @Test
    void repliesWhenTheQueueIsFull() {
        receive(memberIn(voiceChannel), "42", "раз");
        receive(memberIn(voiceChannel), "42", "два");

        assertEquals(List.of("раз"), engine.texts());
        assertTrue(stubs.called("Message.reply[Очередь сообщений переполнена"));
    }

    private Member memberIn(AudioChannelUnion channel) {
        return stubs.stub(Member.class, "getVoiceState", stubs.stub(GuildVoiceState.class, "getChannel", channel));
    }

    private void receive(Member author, String channelId, String text) {
        // JDA берёт гильдию из канала сообщения, поэтому канал должен быть и MessageChannel, и GuildChannel
        Object channel = stubs.proxy(new Class<?>[]{MessageChannelUnion.class, GuildChannel.class},
                "getType", ChannelType.VOICE, "getGuild", guild, "getId", channelId);
        Message message = stubs.stub(Message.class,
                "getAuthor", stubs.stub(User.class),
                "getMember", author,
                "getChannel", channel,
                "getChannelId", channelId,
                "getContentDisplay", text);
        listener.onMessageReceived(new MessageReceivedEvent(jda, 0, message));
    }
}
