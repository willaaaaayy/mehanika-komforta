package com.mehanika.bot.lead;

import java.nio.file.Path;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.mehanika.bot.BotSettings;
import com.mehanika.bot.catalog.CatalogRepository;

@Component
public class CrmReplay implements ApplicationRunner {
    private final CrmForwarder forwarder;
    private final BotSettings settings;
    private final CatalogRepository catalogs;

    public CrmReplay(CrmForwarder forwarder, BotSettings settings, CatalogRepository catalogs) {
        this.forwarder = forwarder;
        this.settings = settings;
        this.catalogs = catalogs;
    }

    @Override
    public void run(ApplicationArguments args) {
        forwarder.replay(Path.of(settings.leadsPath()), key -> {
            if (key == null || key.isBlank()) {
                return key;
            }
            String label = catalogs.current().objects().get(key);
            return label == null || label.isBlank() ? key : label;
        });
    }
}
