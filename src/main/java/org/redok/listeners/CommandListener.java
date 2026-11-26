package org.redok.listeners;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import org.jetbrains.annotations.NotNull;
import org.redok.commands.SlashCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Передаёт слэш-команду обработчику с тем же именем. */
public class CommandListener extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(CommandListener.class);
    private final Map<String, SlashCommand> commands = new HashMap<>();

    public CommandListener(List<SlashCommand> commandList) {
        log.debug("Registering commands");
        for (SlashCommand command : commandList) {
            commands.put(command.getSlashCommandData().getName(), command);
        }
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        SlashCommand slashCommand = commands.get(event.getName());
        if (slashCommand != null) {
            slashCommand.onSlashCommandInteraction(event);
        } else {
            event.reply("This slash command does not exist")
                    .delay(Duration.ofSeconds(10))
                    .flatMap(InteractionHook::deleteOriginal)
                    .queue();
        }
    }
}
