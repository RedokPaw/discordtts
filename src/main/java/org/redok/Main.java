package org.redok;

import club.minnced.discord.jdave.interop.JDaveSessionFactory;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.audio.AudioModuleConfig;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.redok.audio.FfmpegDecoder;
import org.redok.commands.JoinChannelCommand;
import org.redok.commands.LeaveChannelCommand;
import org.redok.commands.SlashCommand;
import org.redok.configuration.BotConfig;
import org.redok.listeners.CommandListener;
import org.redok.listeners.TtsChatListener;
import org.redok.listeners.VoiceStateListener;
import org.redok.storage.Database;
import org.redok.tts.GoogleTranslateTts;
import org.redok.tts.TtsSynthesizer;
import org.redok.tts.VoiceRegistry;
import org.redok.voice.VoiceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Точка сборки: все объекты создаются здесь и получают зависимости через конструктор. */
public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws IOException {
        BotConfig config = BotConfig.load();
        // База открывается до подключения к Discord: сломанный файл или чужая схема видны сразу
        Database database = Database.open(config.dbPath());
        Runtime.getRuntime().addShutdownHook(new Thread(database::close, "db-close"));

        // Пул ограничивает, сколько кусков синтезируется одновременно (HTTP + ffmpeg) на весь бот.
        ExecutorService ttsPool = Executors.newFixedThreadPool(
                config.ttsThreads(), Thread.ofPlatform().name("tts-", 1).factory());
        VoiceRegistry voices = new VoiceRegistry(List.of(googleTts(config)), config.ttsDefaultVoice());
        TtsSynthesizer synthesizer = new TtsSynthesizer(voices, new FfmpegDecoder(), ttsPool, config.ttsMaxChunks());
        VoiceService voice = new VoiceService(synthesizer, config.ttsQueueCapacity());
        List<SlashCommand> commands = List.of(
                new JoinChannelCommand(voice),
                new LeaveChannelCommand(voice));

        log.info("Starting bot");
        AudioModuleConfig audioModuleConfig = new AudioModuleConfig()
                //.withAudioSendFactory(new NativeAudioSendFactory())
                .withDaveSessionFactory(new JDaveSessionFactory());
        JDA jda = JDABuilder
                .createDefault(config.token())
                .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                .addEventListeners(
                        new CommandListener(commands),
                        new TtsChatListener(voice, voices),
                        new VoiceStateListener(voice))
                .setAudioModuleConfig(audioModuleConfig)
                .enableCache(CacheFlag.VOICE_STATE)
                .build();
        jda.updateCommands()
                .addCommands(commands.stream().map(SlashCommand::getSlashCommandData).toList())
                .queue();
        log.info("Connecting to Discord");
        HealthServer.start(config.port());
    }

    /** Языки для /voice; язык по умолчанию из старого TTS_LANGUAGE добавляется, даже если его нет в списке. */
    private static GoogleTranslateTts googleTts(BotConfig config) {
        Set<String> languages = new LinkedHashSet<>(GoogleTranslateTts.DEFAULT_LANGUAGES);
        if (config.ttsDefaultVoice().provider().equals(GoogleTranslateTts.ID)) {
            languages.add(config.ttsDefaultVoice().id());
        }
        return new GoogleTranslateTts(languages);
    }
}
