package org.redok.bot;

import club.minnced.discord.jdave.interop.JDaveSessionFactory;
import com.sedmelluq.discord.lavaplayer.jdaudp.NativeAudioSendFactory;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sun.net.httpserver.HttpServer;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.audio.AudioModuleConfig;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.redok.commands.BotCommandHandlerFactory;
import org.redok.commands.SlashCommand;
import org.redok.configuration.BotProperties;
import org.redok.listeners.EventControllerListenerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetSocketAddress;

public class BotRunner {

    private final Logger log = LoggerFactory.getLogger(BotRunner.class);

    private final BotController botController = BotController.create(this);
    private final BotProperties botProperties;
    private JDA jda;

    public BotRunner() {
        try {
            botProperties = BotProperties.getDefaultProperties();
        } catch (FileNotFoundException e) {
            log.error("Cant create instance of BotRunner", e);
            throw new RuntimeException(e);
        }
    }

    public void run() throws IOException {
        log.info("Starting bot");
        AudioModuleConfig audioModuleConfig = new AudioModuleConfig()
                //.withAudioSendFactory(new NativeAudioSendFactory())
                .withDaveSessionFactory(new JDaveSessionFactory());

        jda = JDABuilder
                .createDefault(botProperties.getToken())
                .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                .addEventListeners(EventControllerListenerFactory.getEventControllerListener())
                .setAudioModuleConfig(audioModuleConfig)
                .enableCache(CacheFlag.VOICE_STATE)
                .build();
        jda.updateCommands().addCommands(BotCommandHandlerFactory.getBotCommandHandler()
                        .getCommands()
                        .stream()
                        .map(SlashCommand::getSlashCommandData)
                        .toList())
                .queue();
        log.info("Bot connected to Discord servers");
        startHealthCheckServer(botProperties.getPort());
    }

    private void startHealthCheckServer(int port) throws IOException {
        log.info("Starting health check server");
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/health", exchange -> {
            String response = "{\"status\":\"UP\"}";
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length());
            exchange.getResponseBody().write(response.getBytes());
            exchange.getResponseBody().close();
        });

        server.start();
    }


    public BotController getBotController() {
        return botController;
    }

    public JDA getJda() {
        return jda;
    }
}
