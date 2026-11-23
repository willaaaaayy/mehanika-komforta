package com.mehanika.bot.dialog;

public record InCallback(long chatId, int messageId, long userId, String username, String callbackId, String data) {
}
