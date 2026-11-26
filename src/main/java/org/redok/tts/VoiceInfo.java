package org.redok.tts;

/** Голос для людей: ключ и имя, которое показывается в /voice. Имя можно менять, в базу оно не попадает. */
public record VoiceInfo(Voice voice, String name) {
}
