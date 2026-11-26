package org.redok.commands;

import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.List;

public interface SlashCommand {

    SlashCommandData getSlashCommandData();

    void onSlashCommandInteraction(SlashCommandInteractionEvent event);

    /** Подсказки для опции с автодополнением. По умолчанию подсказок нет. */
    default void onAutocomplete(CommandAutoCompleteInteractionEvent event) {
        event.replyChoices(List.of()).queue();
    }
}
