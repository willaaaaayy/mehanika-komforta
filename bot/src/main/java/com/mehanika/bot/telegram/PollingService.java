package com.mehanika.bot.telegram;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.mehanika.bot.BotSettings;
import tools.jackson.databind.JsonNode;

@Component
@ConditionalOnProperty(name = "bot.polling", havingValue = "true", matchIfMissing = true)
public class PollingService implements org.springframework.context.SmartLifecycle {
    private static final Logger log = LoggerFactory.getLogger(PollingService.class);

    private final BotSettings settings;
    private final UpdateRouter router;
    private volatile boolean running;
    private Thread thread;
    private TelegramClient client;

    public PollingService(BotSettings settings, UpdateRouter router) {
        this.settings = settings;
        this.router = router;
    }

    @Override
    public void start() {
        if (settings.token().isBlank()) {
            log.error("В .env нет BOT_TOKEN. Скопируйте .env.example в .env и вставьте токен.");
            System.exit(1);
        }
        client = new TelegramClient(settings.token());
        running = true;
        thread = new Thread(this::loop, "telegram-poll");
        thread.setDaemon(false);
        thread.start();
    }

    @Override
    public void stop() {
        running = false;
        if (client != null) {
            client.close();
        }
        if (thread != null) {
            thread.interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void loop() {
        try {
            String username = client.username();
            log.info("telegram getMe ok, bot @{}", username);
            client.deleteWebhook();
        } catch (RuntimeException ex) {
            log.error("telegram startup failed ({})", ex.getClass().getSimpleName());
            System.exit(1);
        }
        long offset = 0;
        boolean announced = false;
        while (running) {
            try {
                List<JsonNode> batch = client.getUpdates(offset);
                if (!announced) {
                    log.info("long polling started");
                    announced = true;
                }
                for (JsonNode update : batch) {
                    offset = update.path("update_id").asLong() + 1;
                    try {
                        client.perform(router.handle(update));
                    } catch (RuntimeException ex) {
                        log.warn("update failed ({})", ex.getClass().getSimpleName());
                    }
                }
            } catch (TelegramClient.Conflict ex) {
                log.warn("telegram getUpdates conflict, another poller is running");
                pause();
            } catch (RuntimeException ex) {
                if (!running) {
                    break;
                }
                log.warn("telegram getUpdates failed ({})", ex.getClass().getSimpleName());
                pause();
            }
        }
    }

    private void pause() {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            running = false;
        }
    }
}
