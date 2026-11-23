package com.mehanika.bot;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class BotSettings {
    private final String token;
    private final String adminChatId;
    private final String catalogPath;
    private final String leadsPath;
    private final String crmUrl;
    private final String leadToken;

    public BotSettings(Environment env) {
        this.token = env.getProperty("BOT_TOKEN", "").trim();
        this.adminChatId = env.getProperty("ADMIN_CHAT_ID", "").trim();
        this.catalogPath = env.getProperty("bot.catalog-path", "file:./prices.json");
        this.leadsPath = env.getProperty("bot.leads-path", "data/leads.jsonl");
        this.crmUrl = env.getProperty("CRM_URL", "").trim();
        this.leadToken = env.getProperty("LEAD_TOKEN", "").trim();
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

    public String crmUrl() {
        return crmUrl;
    }

    public String leadToken() {
        return leadToken;
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
