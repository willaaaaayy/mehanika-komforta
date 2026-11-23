package com.mehanika.bot.lead;

public record TelegramProfile(
        long id,
        String username,
        String firstName,
        String lastName,
        String bio,
        LeadPhoto photo
) {
}
