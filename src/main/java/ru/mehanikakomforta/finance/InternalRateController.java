package ru.mehanikakomforta.finance;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ru.mehanikakomforta.clients.ClientAccess;
import ru.mehanikakomforta.leads.ApiError;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@RestController
class InternalRateController {

    private static final ZoneId SARATOV = ZoneId.of("Europe/Saratov");
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final InternalRates rates;

    InternalRateController(InternalRates rates) {
        this.rates = rates;
    }

    @GetMapping("/api/internal-rates")
    ResponseEntity<?> list(@RequestParam(required = false) String grade) {
        if (!ClientAccess.signedIn()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiError("auth"));
        }
        if (!ClientAccess.canReadClients()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError("role"));
        }
        return ResponseEntity.ok(rates.catalog(grade));
    }

    @PutMapping("/api/internal-rates")
    ResponseEntity<?> save(@RequestBody(required = false) RateBody body) {
        if (!ClientAccess.canConvert()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError("role"));
        }
        if (body == null || body.workId() == null || body.workId().isBlank()) {
            return ResponseEntity.badRequest().body(new ApiError("workId"));
        }
        LocalDate from = date(body.validFrom());
        if (from == null) {
            return ResponseEntity.badRequest().body(new ApiError("validFrom"));
        }
        return ResponseEntity.ok(rates.save(
                body.workId().trim(),
                body.title(),
                body.unit(),
                money(body.labor()),
                money(body.material()),
                from
        ));
    }

    @PostMapping(value = "/api/internal-rates/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<?> upload(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(required = false) String preview,
            @RequestParam(required = false) String rows
    ) throws java.io.IOException {
        if (!ClientAccess.canConvert()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError("role"));
        }
        boolean checking = preview == null || !"false".equals(preview);
        if (checking) {
            if (file == null || file.isEmpty()) {
                return ResponseEntity.badRequest().body(new ApiError("file"));
            }
            return ResponseEntity.ok(rates.preview(file.getBytes()));
        }
        List<InternalRates.RateView> accepted = readRows(rows);
        if (accepted.isEmpty()) {
            return ResponseEntity.badRequest().body(new ApiError("rows"));
        }
        for (InternalRates.RateView row : accepted) {
            LocalDate from = date(row.validFrom());
            if (row.workId() == null || row.workId().isBlank() || from == null) {
                return ResponseEntity.badRequest().body(new ApiError("rows"));
            }
            rates.save(row.workId(), row.title(), row.unit(), row.labor(), row.material(), from);
        }
        return ResponseEntity.ok(rates.catalog("standard"));
    }

    private static List<InternalRates.RateView> readRows(String rows) {
        if (rows == null || rows.isBlank()) {
            return List.of();
        }
        JsonNode node = MAPPER.readTree(rows);
        if (!node.isArray()) {
            return List.of();
        }
        List<InternalRates.RateView> accepted = new ArrayList<>();
        for (JsonNode item : node) {
            accepted.add(new InternalRates.RateView(
                    text(item, "workId"),
                    item.path("title").asString(""),
                    item.path("unit").asString(""),
                    money(item.get("labor")),
                    money(item.get("material")),
                    money(item.get("clientPrice")),
                    money(item.get("cost")),
                    money(item.get("margin")),
                    item.path("version").isNumber() ? item.path("version").asInt() : null,
                    text(item, "validFrom"),
                    null,
                    item.path("teaching").asBoolean(false)
            ));
        }
        return accepted;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asString();
        return text == null || text.isBlank() ? null : text;
    }

    private static LocalDate date(String value) {
        if (value == null || value.isBlank()) {
            return LocalDate.now(SARATOV);
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (Exception exception) {
            return null;
        }
    }

    private static BigDecimal money(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String text = node.asString().trim().replace(" ", "").replace(",", ".");
        if (text.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static BigDecimal money(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.trim().replace(" ", "").replace(",", "."));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    record RateBody(String workId, String title, String unit, String labor, String material, String validFrom, String grade) {
    }
}
