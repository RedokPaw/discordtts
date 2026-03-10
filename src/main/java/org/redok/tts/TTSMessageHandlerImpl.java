package org.redok.tts;

import net.dv8tion.jda.api.audio.AudioSendHandler;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.redok.utils.UrlReplacer;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

//TODO: Отделить гуглттс, сделать аудиопровайдера кастомайзбл, конвертирование тоже увести в другой класс
public class TTSMessageHandlerImpl implements TTSMessageHandler {

    private final String language = "ru";
    private final Map<String, AudioSendHandlerImpl> handlerPerGuild = new HashMap<>();
    private final ProcessBuilder pb = new ProcessBuilder(
            "ffmpeg",
            "-loglevel", "error",
            "-threads", "1",
            "-i", "pipe:0",
            "-f", "s16be",
            "-ar", "48000",
            "-ac", "2",
            "pipe:1"
    );

    @Override
    public void handleMessageAndSpeak(MessageReceivedEvent event) {
        String text = UrlReplacer.replaceUrlsWithDomains(event.getMessage().getContentDisplay());
        InputStream mp3Stream = getAudioFromGoogleTTS(text);
        InputStream pcmStream = convertAudioToPCM(mp3Stream);
        handlerPerGuild.get(event.getGuild().getId()).queueTtsStream(pcmStream);
    }

    @Override
    public AudioSendHandler registerGuild(String guildId) {
        AudioSendHandlerImpl audioSendHandler = new AudioSendHandlerImpl();
        handlerPerGuild.put(guildId, audioSendHandler);
        return audioSendHandler;
    }

    @Override
    public void unregisterGuild(String guildId) {
        handlerPerGuild.remove(guildId);
    }

    public InputStream getAudioFromGoogleTTS(String text) {
        String encodedText = URLEncoder.encode(text, StandardCharsets.UTF_8);
        String url = String.format(
                "http://translate.google.com/translate_tts?ie=UTF-8&total=1&idx=0&client=tw-ob&q=%s&tl=%s",
                encodedText,
                language
        );

        HttpResponse<InputStream> response;
        HttpClient httpClient = HttpClient.newBuilder().build();
        HttpRequest request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(url))
                .build();
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return response.body();
    }

    public InputStream convertAudioToPCM(InputStream audioStream) {
        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Thread.ofVirtual().start(() -> {
            try (OutputStream ffmpegIn = process.getOutputStream()) {
                audioStream.transferTo(ffmpegIn);
            } catch (IOException ignored) {
            }
        });
        return new BufferedInputStream(process.getInputStream());
    }
}





