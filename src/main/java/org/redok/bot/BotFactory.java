package org.redok.bot;

public class BotFactory {
    private static BotRunner botRunner;
    public static BotRunner getBotRunner() {
        if (botRunner == null) {
            botRunner = new BotRunner();
        }
        return botRunner;
    }
}
