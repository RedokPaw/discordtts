package org.redok.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class BotCommandHandler {

    private final Logger log = LoggerFactory.getLogger(BotCommandHandler.class);
    private final HashMap<String, SlashCommand> commands = new HashMap<>();

    public BotCommandHandler() {
        log.debug("Registering commands");
        SlashCommand joinCommand = new JoinChannelCommand();
        commands.put(joinCommand.getSlashCommandData().getName(), joinCommand);
        SlashCommand leaveCommand = new LeaveChannelCommand();
        commands.put(leaveCommand.getSlashCommandData().getName(), leaveCommand);
    }

    public void doCommand(SlashCommandInteractionEvent event) {
        SlashCommand slashCommand = commands.get(event.getName());
        if (slashCommand != null) {
            slashCommand.onSlashCommandInteraction(event);
        } else {
            Thread.ofVirtual().start(() ->
                    event.reply("This slash command does not exist")
                            .delay(Duration.ofSeconds(10)).complete().deleteOriginal().queue());
        }
    }

    public List<SlashCommand> getCommands() {
        return new ArrayList<>(commands.values());
    }
}
