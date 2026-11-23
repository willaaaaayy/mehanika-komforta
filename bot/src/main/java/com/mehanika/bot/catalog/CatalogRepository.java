package com.mehanika.bot.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.mehanika.bot.BotSettings;

@Component
public class CatalogRepository {
    private static final Logger log = LoggerFactory.getLogger(CatalogRepository.class);
    private final Path file;
    private final Object lock = new Object();
    private PriceCatalog cached;
    private long mtime = Long.MIN_VALUE;

    public CatalogRepository(BotSettings settings) {
        this(settings.catalogPath());
    }

    public CatalogRepository(String location) {
        if (location.startsWith("classpath:")) {
            this.file = null;
            this.cached = loadClasspath(location.substring("classpath:".length()));
        } else {
            String path = location.startsWith("file:") ? location.substring("file:".length()) : location;
            this.file = Path.of(path);
            this.cached = read(this.file);
            this.mtime = mtimeOf(this.file);
        }
    }

    public PriceCatalog current() {
        if (file == null) {
            return cached;
        }
        long observed = mtimeOf(file);
        if (observed == mtime && cached != null) {
            return cached;
        }
        synchronized (lock) {
            observed = mtimeOf(file);
            if (observed == mtime && cached != null) {
                return cached;
            }
            try {
                PriceCatalog loaded = read(file);
                cached = loaded;
                mtime = observed;
                return loaded;
            } catch (RuntimeException ex) {
                if (cached == null) {
                    throw ex;
                }
                log.warn("prices.json reload failed ({})", ex.getClass().getSimpleName());
                return cached;
            }
        }
    }

    public PriceCatalog forClient(String clientId) {
        return current().forClient(clientId);
    }

    private static PriceCatalog read(Path path) {
        try {
            return PriceCatalog.load(Files.readString(path));
        } catch (IOException ex) {
            throw new IllegalStateException("prices.json unreadable");
        }
    }

    private static PriceCatalog loadClasspath(String name) {
        String resource = name.startsWith("/") ? name.substring(1) : name;
        try (InputStream input = CatalogRepository.class.getClassLoader().getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("prices.json missing");
            }
            return PriceCatalog.load(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new IllegalStateException("prices.json unreadable");
        }
    }

    private static long mtimeOf(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ex) {
            return Long.MIN_VALUE;
        }
    }
}
