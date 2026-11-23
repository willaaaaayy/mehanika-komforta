package com.mehanika.bot;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class BotSettings {
    private final String token;
    private final String adminChatId;
    private final String catalogPath;
    private final String leadsPath;

    public BotSettings(Environment env) {
        this.token = env.getProperty("BOT_TOKEN", "").trim();
        this.adminChatId = env.getProperty("ADMIN_CHAT_ID", "").trim();
        this.catalogPath = env.getProperty("bot.catalog-path", "file:./prices.json");
        this.leadsPath = env.getProperty("bot.leads-path", "data/leads.jsonl");
    }

    public String token() {
        return token;
    }

    public String catalogPath() {
        return catalogPath;
    }

    public String leadsPath() {
        return leadsPath;
    }

    public Long adminChatId() {
        if (adminChatId.isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(adminChatId);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
