package org.redok.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

public interface SlashCommand {

    SlashCommandData getSlashCommandData();

    void onSlashCommandInteraction(SlashCommandInteractionEvent event);

}
