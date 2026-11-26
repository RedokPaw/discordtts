package org.redok.tts;

import java.util.Objects;

/**
 * Ключ голоса: провайдер и id голоса у этого провайдера, в строке — {@code provider:id}, например {@code google:ru}.
 * Эта строка хранится в базе и уходит в значение автодополнения, поэтому id голоса менять нельзя.
 */
public record Voice(String provider, String id) {

    public Voice {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(id, "id");
        if (provider.isBlank() || id.isBlank() || provider.contains(":")) {
            throw new IllegalArgumentException("bad voice: provider '" + provider + "', id '" + id + "'");
        }
    }

    /** Разбирает {@code provider:id}; бросает {@link IllegalArgumentException}, если строка не такого вида. */
    public static Voice parse(String key) {
        int colon = key == null ? -1 : key.indexOf(':');
        if (colon < 0) {
            throw new IllegalArgumentException("voice must look like provider:id, got '" + key + "'");
        }
        return new Voice(key.substring(0, colon).strip(), key.substring(colon + 1).strip());
    }

    public String key() {
        return provider + ":" + id;
    }

    @Override
    public String toString() {
        return key();
    }
}
