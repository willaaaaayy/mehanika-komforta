package com.mehanika.bot.lead;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.mehanika.bot.BotSettings;
import com.mehanika.bot.Jsons;

@Component
public class LeadStore {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSxxx");

    private final Path path;
    private final Object lock = new Object();

    @Autowired
    public LeadStore(BotSettings settings) {
        this(Path.of(settings.leadsPath()));
    }

    public LeadStore(Path path) {
        this.path = path;
    }

    public String append(Map<String, Object> lead) {
        String createdAt = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS).format(STAMP);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("created_at", createdAt);
        row.putAll(lead);
        try {
            String line = Jsons.MAPPER.writeValueAsString(row);
            synchronized (lock) {
                if (path.getParent() != null) {
                    Files.createDirectories(path.getParent());
                }
                Files.writeString(path, line + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
            return createdAt;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
