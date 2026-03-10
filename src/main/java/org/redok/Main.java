package org.redok;

import org.redok.bot.BotFactory;
import org.redok.bot.BotRunner;

import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {
        BotRunner botRunner = BotFactory.getBotRunner();
        botRunner.run();
    }
}