package com.mehanika.bot.lead;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.mehanika.bot.BotSettings;
import com.mehanika.bot.Jsons;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

@Component
public class CrmForwarder implements CrmLeadSink {
    private static final Logger log = LoggerFactory.getLogger(CrmForwarder.class);

    private final String url;
    private final String token;
    private final Path marker;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final Object lock = new Object();

    @Autowired
    public CrmForwarder(BotSettings settings) {
        this(settings.crmUrl(), settings.leadToken(), markerNear(settings.leadsPath()));
    }

    public CrmForwarder(String url, String token, Path marker) {
        this.url = url == null ? "" : url.trim();
        this.token = token == null ? "" : token.trim();
        this.marker = marker;
    }

    @Override
    public CrmLeadSink.Result forward(String name, String phone, String objectType) {
        if (url.isBlank() || token.isBlank()) {
            return CrmLeadSink.Result.UNAVAILABLE;
        }
        String cleanName = name == null ? "" : name.strip();
        String cleanPhone = phone == null ? "" : phone.strip();
        String cleanObject = objectType == null ? "" : objectType.strip();
        if (cleanName.length() < 2 || cleanPhone.isBlank() || cleanObject.length() < 2) {
            return CrmLeadSink.Result.REJECTED;
        }
        ObjectNode body = Jsons.MAPPER.createObjectNode();
        body.put("name", cleanName);
        body.put("phone", cleanPhone);
        body.put("objectType", cleanObject);
        body.put("source", "telegram");
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(3))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("X-Lead-Token", token)
                    .POST(HttpRequest.BodyPublishers.ofString(Jsons.MAPPER.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                log.info("crm lead forwarded");
                return CrmLeadSink.Result.SENT;
            }
            if (status >= 400 && status < 500) {
                log.warn("crm forward rejected (http {})", status);
                return CrmLeadSink.Result.REJECTED;
            }
            log.warn("crm forward unavailable (http {})", status);
            return CrmLeadSink.Result.UNAVAILABLE;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("crm forward unavailable ({})", ex.getClass().getSimpleName());
            return CrmLeadSink.Result.UNAVAILABLE;
        } catch (Exception ex) {
            log.warn("crm forward unavailable ({})", ex.getClass().getSimpleName());
            return CrmLeadSink.Result.UNAVAILABLE;
        }
    }

    @Override
    public void remember(String createdAt) {
        if (createdAt == null || createdAt.isBlank() || marker == null) {
            return;
        }
        synchronized (lock) {
            try {
                if (marker.getParent() != null) {
                    Files.createDirectories(marker.getParent());
                }
                Files.writeString(marker, createdAt + "\n", StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException ex) {
                log.warn("crm forward marker failed ({})", ex.getClass().getSimpleName());
            }
        }
    }

    public void replay(Path leadsFile, Function<String, String> objectLabel) {
        if (url.isBlank() || token.isBlank() || leadsFile == null || !Files.isRegularFile(leadsFile)) {
            return;
        }
        Set<String> sent = readMarker();
        try {
            for (String line : Files.readAllLines(leadsFile, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                JsonNode row;
                try {
                    row = Jsons.MAPPER.readTree(line);
                } catch (RuntimeException ex) {
                    continue;
                }
                String createdAt = row.path("created_at").asText("");
                if (createdAt.isBlank() || sent.contains(createdAt)) {
                    continue;
                }
                String key = row.path("object_type").asText("");
                String label = objectLabel == null ? key : objectLabel.apply(key);
                CrmLeadSink.Result result = forward(row.path("name").asText(""), row.path("phone").asText(""), label == null ? key : label);
                if (result == CrmLeadSink.Result.UNAVAILABLE) {
                    return;
                }
                remember(createdAt);
                sent.add(createdAt);
            }
        } catch (IOException ex) {
            log.warn("crm replay failed ({})", ex.getClass().getSimpleName());
        }
    }

    private Set<String> readMarker() {
        Set<String> sent = new HashSet<>();
        if (marker == null || !Files.isRegularFile(marker)) {
            return sent;
        }
        try {
            for (String line : Files.readAllLines(marker, StandardCharsets.UTF_8)) {
                if (!line.isBlank()) {
                    sent.add(line.strip());
                }
            }
        } catch (IOException ex) {
            log.warn("crm forward marker failed ({})", ex.getClass().getSimpleName());
        }
        return sent;
    }

    private static Path markerNear(String leadsPath) {
        Path leads = Path.of(leadsPath == null || leadsPath.isBlank() ? "data/leads.jsonl" : leadsPath);
        Path parent = leads.getParent();
        Path name = Path.of(leads.getFileName() + ".crm");
        return parent == null ? name : parent.resolve(name);
    }
}
