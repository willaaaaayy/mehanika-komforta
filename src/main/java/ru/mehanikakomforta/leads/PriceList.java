package ru.mehanikakomforta.leads;

import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

@Service
public class PriceList {

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final Path botFile = Path.of("/opt/mehanika-bot/prices.json");
    private JsonNode document;

    public PriceList() {
        try {
            if (Files.isRegularFile(botFile)) {
                try {
                    JsonNode loaded = mapper.readTree(Files.readString(botFile));
                    if (loaded.get("works") != null && loaded.get("grades") != null && loaded.get("packages") != null) {
                        this.document = loaded;
                        return;
                    }
                } catch (Exception ignored) {
                    // A torn file must not stop the service. The classpath copy stays until the next whole write.
                }
            }
            try (InputStream input = PriceList.class.getResourceAsStream("/policies/prices.json")) {
                if (input == null) {
                    throw new IllegalStateException("prices missing");
                }
                this.document = mapper.readTree(input);
            }
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    public synchronized JsonNode document() {
        return document;
    }

    public synchronized void replace(String json) {
        JsonNode next = mapper.readTree(json);
        if (!(next instanceof tools.jackson.databind.node.ObjectNode catalog)
                || next.get("works") == null
                || next.get("grades") == null
                || next.get("packages") == null) {
            throw new IllegalArgumentException("prices");
        }
        if (!catalog.has("teaching")) {
            catalog.put("teaching", document.get("teaching") != null && document.get("teaching").asBoolean());
        }
        store(catalog);
    }

    public synchronized void replaceSheet(String filename, byte[] bytes) {
        JsonNode next = mapper.readTree(mapper.writeValueAsString(document));
        PriceSheet.apply(next, filename, bytes);
        store(next);
    }

    private void store(JsonNode next) {
        if (next.get("teaching") == null || next.get("works") == null) {
            throw new IllegalArgumentException("prices");
        }
        String body = mapper.writeValueAsString(next);
        JsonNode written = mapper.readTree(body);
        if (written.get("teaching") == null || written.get("works") == null || written.get("grades") == null || written.get("packages") == null) {
            throw new IllegalArgumentException("prices");
        }
        try {
            if (Files.isRegularFile(botFile)) {
                Files.writeString(
                        botFile,
                        body,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.TRUNCATE_EXISTING
                );
            }
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
        this.document = written;
    }

    public synchronized Estimate estimate(BigDecimal floor, String grade, String condition, String packageCode) {
        if (floor == null || grade == null || document.get("grades").get(grade) == null) {
            return null;
        }
        String pack = packageCode == null || packageCode.isBlank() ? "paint" : packageCode;
        JsonNode bundle = document.get("packages").get(pack);
        if (bundle == null) {
            return null;
        }
        BigDecimal height = document.get("defaultHeight").decimalValue();
        BigDecimal walls = floor.multiply(document.get("wallFactor").decimalValue());
        long labor = 0;
        long material = 0;
        List<String> lines = new ArrayList<>();
        for (JsonNode id : bundle.get("works")) {
            JsonNode work = document.get("works").get(id.asString());
            BigDecimal qty = switch (work.get("area").asString()) {
                case "walls" -> walls;
                case "ceiling" -> floor;
                default -> floor;
            };
            qty = qty.setScale(1, RoundingMode.HALF_UP);
            long sum = qty.multiply(BigDecimal.valueOf(work.get("prices").get(grade).asLong()))
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValue();
            BigDecimal share = work.get("laborShare").decimalValue();
            long workLabor = BigDecimal.valueOf(sum).multiply(share).setScale(0, RoundingMode.HALF_UP).longValue();
            labor += workLabor;
            material += sum - workLabor;
            lines.add(work.get("title").asString());
        }
        long middle = labor + material;
        String spreadKey = "finish|" + ("old".equals(condition) ? "old" : "new");
        BigDecimal spread = document.get("spread").get(spreadKey).decimalValue();
        long low = BigDecimal.valueOf(middle).multiply(BigDecimal.ONE.subtract(spread)).setScale(0, RoundingMode.HALF_UP).longValue();
        long high = BigDecimal.valueOf(middle).multiply(BigDecimal.ONE.add(spread)).setScale(0, RoundingMode.HALF_UP).longValue();
        boolean belowMin = middle < document.get("minOrder").asLong();
        return new Estimate(
                document.get("teaching").asBoolean(),
                document.get("note").asString(),
                bundle.get("title").asString(),
                lines,
                labor,
                material,
                middle,
                low,
                high,
                document.get("warrantyYears") == null ? 2 : document.get("warrantyYears").asInt(),
                belowMin
        );
    }

    public record Estimate(
            boolean teaching,
            String note,
            String packageTitle,
            List<String> lines,
            long labor,
            long material,
            long middle,
            long low,
            long high,
            int warrantyYears,
            boolean belowMin
    ) {
    }
}
