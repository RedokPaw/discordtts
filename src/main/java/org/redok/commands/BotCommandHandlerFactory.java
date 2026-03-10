package org.redok.commands;

public class BotCommandHandlerFactory {
    private static final BotCommandHandler botCommandHandler = new BotCommandHandler();

    public static BotCommandHandler getBotCommandHandler() {
        return botCommandHandler;
    }

}
