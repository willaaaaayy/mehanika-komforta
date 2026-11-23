package com.mehanika.bot.telegram;

public sealed interface Out {
    record Send(long chatId, String html, Keyboard keyboard, boolean bestEffort) implements Out {
    }

    record Markup(long chatId, int messageId, Keyboard keyboard) implements Out {
    }

    record Toast(String callbackId, String text) implements Out {
    }
}
