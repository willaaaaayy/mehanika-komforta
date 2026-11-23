package com.mehanika.bot.dialog;

public record InMessage(long chatId, int messageId, long userId, String username, String text) {
}
