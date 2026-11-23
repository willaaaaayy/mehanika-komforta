package com.mehanika.bot.lead;

public interface AccountLookup {
    TelegramProfile load(long userId, String username, String firstName, String lastName);
}
