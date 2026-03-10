package org.redok.utils;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UrlReplacer {

    // Паттерн для поиска URL вида http(s)://(www.)example.com/...
    private static final Pattern URL_PATTERN = Pattern.compile(
            "https?://(?:www\\.)?([\\w.-]+)(?:/[^\\s]*)?"
    );

    public static String replaceUrlsWithDomains(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        Matcher matcher = URL_PATTERN.matcher(text);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String url = matcher.group(0);
            String replacement = extractRootDomain(url);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }

        matcher.appendTail(result);
        return result.toString();
    }

    private static String extractRootDomain(String url) {
        try {
            String host = new URI(url).getHost();
            if (host == null) {
                return url;
            }
            if (host.startsWith("www.")) {
                host = host.substring(4);
            }
            // Берём вторую часть с конца: "sub.example.com" -> ["sub", "example", "com"]
            String[] parts = host.split("\\.");
            return parts.length >= 2
                    ? parts[parts.length - 2]  // предпоследний элемент = имя домена
                    : parts[0];

        } catch (Exception e) {
            return url;
        }
    }
}