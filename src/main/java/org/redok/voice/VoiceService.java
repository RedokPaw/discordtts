package org.redok.voice;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.managers.AudioManager;
import org.redok.tts.TtsSynthesizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Голос бота во всех гильдиях: подключение, отключение и очередь речи. Единственный владелец
 * состояния гильдий, поэтому оно не расходится с JDA.
 */
public class VoiceService {

    /** Что стало с текстом, переданным в {@link #speak}. */
    public enum SpeakResult { QUEUED, QUEUE_FULL, NOT_CONNECTED, NOTHING_TO_SAY }

    private static final Logger log = LoggerFactory.getLogger(VoiceService.class);
    private final TtsSynthesizer synthesizer;
    private final int queueCapacity;
    private final Map<String, GuildVoiceSession> sessions = new ConcurrentHashMap<>();

    public VoiceService(TtsSynthesizer synthesizer, int queueCapacity) {
        this.synthesizer = synthesizer;
        this.queueCapacity = queueCapacity;
    }

    /** Подключает бота к каналу; старая очередь гильдии, если была, отбрасывается. */
    public void join(Guild guild, AudioChannel channel) {
        log.info("joining voice channel {} ({}) in guild {}", channel.getId(), channel.getName(), guild.getId());
        GuildVoiceSession session = new GuildVoiceSession(guild.getId(), queueCapacity);
        GuildVoiceSession previous = sessions.put(guild.getId(), session);
        if (previous != null) {
            previous.clear();
        }
        AudioManager audioManager = guild.getAudioManager();
        audioManager.setSendingHandler(session);
        audioManager.openAudioConnection(channel);
    }

    /** Отключает бота и освобождает очередь гильдии. */
    public void leave(Guild guild) {
        log.info("leaving voice channel in guild: {}", guild.getId());
        AudioManager audioManager = guild.getAudioManager();
        audioManager.setSendingHandler(null); // иначе JDA держит старую сессию с очередью до следующего /join
        audioManager.closeAudioConnection();
        GuildVoiceSession session = sessions.remove(guild.getId());
        if (session != null) {
            session.clear(); // отменяет слоты, память освобождается сразу
        }
    }

    /**
     * Бота отключили не через {@link #leave}, например модератор выкинул его из канала. Ожидающая речь
     * выбрасывается, но сессия остаётся подключённой к JDA: если JDA сама переподключит голос
     * (например, после сбоя связи), бот продолжит говорить без повторного /join.
     */
    public void onDisconnected(Guild guild) {
        GuildVoiceSession session = sessions.get(guild.getId());
        if (session != null) {
            log.info("bot was disconnected from voice in guild {}, dropping queued speech", guild.getId());
            session.clear();
        }
    }

    /** Ставит текст в очередь гильдии. Места резервируются сразу, в порядке прихода сообщений. */
    public SpeakResult speak(Guild guild, String text) {
        SpeakResult result = enqueue(guild, text);
        log.debug("[{}] speak({} chars) -> {}", guild.getId(), text.length(), result);
        return result;
    }

    private SpeakResult enqueue(Guild guild, String text) {
        GuildVoiceSession session = sessions.get(guild.getId());
        if (session == null) {
            return SpeakResult.NOT_CONNECTED;
        }
        List<String> chunks = synthesizer.split(text);
        if (chunks.isEmpty()) {
            return SpeakResult.NOTHING_TO_SAY; // картинка, стикер или эмбед без текста
        }
        List<CompletableFuture<ByteBuffer>> slots = session.reserveSlots(chunks.size());
        if (slots.isEmpty()) {
            return SpeakResult.QUEUE_FULL;
        }
        for (int i = 0; i < chunks.size(); i++) {
            CompletableFuture<ByteBuffer> slot = slots.get(i);
            // подпись для логов: гильдия, слот и номер куска — по ней кусок видно до самого воспроизведения
            String label = "%s/%s %d/%d".formatted(guild.getId(), GuildVoiceSession.slotId(slot), i + 1, chunks.size());
            synthesizer.synthesizeInto(slot, chunks.get(i), label);
        }
        return SpeakResult.QUEUED;
    }

    /** Есть ли бот среди участников голосового канала. */
    public boolean isBotIn(AudioChannel channel) {
        String selfId = channel.getJDA().getSelfUser().getId();
        return channel.getMembers().stream().anyMatch(member -> member.getId().equals(selfId));
    }
}
