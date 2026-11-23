package ru.mehanikakomforta.leads;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ru.mehanikakomforta.CrmProperties;
import ru.mehanikakomforta.SecretMatch;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@RestController
public class LeadController {

    private final LeadRepository leads;
    private final LeadProfileRepository profiles;
    private final PriceList prices;
    private final CrmProperties properties;

    public LeadController(
            LeadRepository leads,
            LeadProfileRepository profiles,
            PriceList prices,
            CrmProperties properties
    ) {
        this.leads = leads;
        this.profiles = profiles;
        this.prices = prices;
        this.properties = properties;
    }

    @PostMapping("/api/leads")
    public ResponseEntity<?> create(
            @RequestBody(required = false) LeadRequest request,
            @RequestHeader(value = "X-Lead-Token", required = false) String token
    ) {
        String problem = LeadRules.check(request);
        if (problem != null) {
            return ResponseEntity.badRequest().body(new ApiError(problem));
        }
        LeadSource source = LeadRules.source(request.source());
        if (source == LeadSource.telegram && !SecretMatch.same(properties.leadToken(), token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiError("token"));
        }
        if (source == LeadSource.manual && !AdminGuard.signedIn()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiError("auth"));
        }
        LeadEntity lead = new LeadEntity(
                Instant.now(),
                LeadRules.name(request),
                LeadRules.phone(request),
                LeadRules.objectType(request),
                source
        );
        if (request.telegram() != null) {
            lead.telegram(
                    request.telegram().id(),
                    LeadRules.username(request.telegram()),
                    LeadRules.firstName(request.telegram()),
                    LeadRules.lastName(request.telegram()),
                    LeadRules.bio(request.telegram())
            );
        }
        LeadEntity saved = leads.save(lead);
        return ResponseEntity.status(HttpStatus.CREATED).body(view(saved));
    }

    @GetMapping("/api/leads")
    public ResponseEntity<List<LeadResponse>> list() {
        List<LeadResponse> body = leads.findAllByOrderByCreatedAtDesc().stream()
                .map(this::view)
                .toList();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    @GetMapping("/api/leads/{id}")
    public ResponseEntity<?> one(@PathVariable long id) {
        return leads.findById(id)
                .<ResponseEntity<?>>map(lead -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(detail(lead)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("id")));
    }

    @GetMapping("/api/prices")
    public JsonNode prices() {
        return prices.document();
    }

    @PutMapping("/api/prices")
    public ResponseEntity<?> replacePrices(@RequestBody(required = false) String body) {
        if (body == null || body.isBlank()) {
            return ResponseEntity.badRequest().body(new ApiError("prices"));
        }
        try {
            prices.replace(body);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(new ApiError("prices"));
        } catch (RuntimeException exception) {
            return ResponseEntity.badRequest().body(new ApiError("prices"));
        }
        return ResponseEntity.ok(prices.document());
    }

    @PostMapping(value = "/api/prices", consumes = "multipart/form-data")
    public ResponseEntity<?> uploadPrices(@RequestParam(value = "file", required = false) MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(new ApiError("prices"));
        }
        try {
            prices.replaceSheet(file.getOriginalFilename(), file.getBytes());
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(new ApiError("prices"));
        } catch (Exception exception) {
            return ResponseEntity.badRequest().body(new ApiError("prices"));
        }
        return ResponseEntity.ok(prices.document());
    }

    @PatchMapping("/api/leads/{id}")
    public ResponseEntity<?> edit(@PathVariable long id, @RequestBody(required = false) JsonNode body) {
        var found = leads.findById(id);
        if (found.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("id"));
        }
        if (body == null || body.isNull()) {
            return ResponseEntity.badRequest().body(new ApiError("name"));
        }
        LeadEntity lead = found.get();
        String name = text(body, "name", lead.getName());
        String phone = text(body, "phone", lead.getPhone());
        String objectType = text(body, "objectType", lead.getObjectType());
        String stageRaw = text(body, "stage", lead.getStage().code());
        LeadStage stage = LeadStage.parse(stageRaw);
        if (name.length() < 2 || name.length() > 80) {
            return ResponseEntity.badRequest().body(new ApiError("name"));
        }
        if (phone.length() > 40 || digits(phone) < 10 || digits(phone) > 15) {
            return ResponseEntity.badRequest().body(new ApiError("phone"));
        }
        if (objectType.length() < 2 || objectType.length() > 80) {
            return ResponseEntity.badRequest().body(new ApiError("objectType"));
        }
        if (stage == null) {
            return ResponseEntity.badRequest().body(new ApiError("stage"));
        }
        BigDecimal area = lead.getFloorArea();
        if (body.has("floorArea")) {
            if (body.get("floorArea").isNull()) {
                area = null;
            } else {
                area = body.get("floorArea").decimalValue();
                if (area.signum() <= 0 || area.compareTo(new BigDecimal("50000")) > 0) {
                    return ResponseEntity.badRequest().body(new ApiError("floorArea"));
                }
            }
        }
        String grade = lead.getGrade();
        if (body.has("grade")) {
            grade = body.get("grade").isNull() ? null : body.get("grade").asString();
            if (grade != null && !grade.equals("economy") && !grade.equals("standard") && !grade.equals("premium")) {
                return ResponseEntity.badRequest().body(new ApiError("grade"));
            }
        }
        String condition = lead.getConditionCode();
        if (body.has("condition")) {
            condition = body.get("condition").isNull() ? null : body.get("condition").asString();
            if (condition != null && !condition.equals("new") && !condition.equals("old")) {
                return ResponseEntity.badRequest().body(new ApiError("condition"));
            }
        }
        String packageCode = lead.getPackageCode();
        if (body.has("packageCode")) {
            packageCode = body.get("packageCode").isNull() ? null : body.get("packageCode").asString();
            if (packageCode != null && prices.document().get("packages").get(packageCode) == null) {
                return ResponseEntity.badRequest().body(new ApiError("packageCode"));
            }
        }
        if (body.has("telegram") && !body.get("telegram").isNull()) {
            JsonNode telegram = body.get("telegram");
            String username = telegram.has("username") ? telegram.get("username").asString("") : lead.getTelegramUsername();
            String firstName = telegram.has("firstName") ? telegram.get("firstName").asString("") : lead.getTelegramFirstName();
            String lastName = telegram.has("lastName") ? telegram.get("lastName").asString("") : lead.getTelegramLastName();
            String bio = telegram.has("bio") ? telegram.get("bio").asString("") : lead.getTelegramBio();
            if (username.startsWith("@")) {
                return ResponseEntity.badRequest().body(new ApiError("telegram"));
            }
            Long telegramId = lead.getTelegramId() == null ? 0L : lead.getTelegramId();
            lead.telegram(telegramId, username.trim(), firstName.trim(), lastName.trim(), bio.trim());
        }
        lead.edit(name, phone, objectType, stage, area, grade, condition, packageCode);
        return ResponseEntity.ok(detail(leads.save(lead)));
    }

    private LeadResponse view(LeadEntity lead) {
        return LeadResponse.from(lead, profiles.existsByLeadId(lead.getId()));
    }

    private LeadDetail detail(LeadEntity lead) {
        return new LeadDetail(
                view(lead),
                prices.document().get("teaching").asBoolean(),
                prices.estimate(lead.getFloorArea(), lead.getGrade(), lead.getConditionCode(), lead.getPackageCode())
        );
    }

    private static String text(JsonNode body, String field, String current) {
        if (!body.has(field) || body.get(field).isNull()) {
            return current == null ? "" : current;
        }
        return body.get(field).asString().trim();
    }

    private static int digits(String value) {
        int count = 0;
        for (int i = 0; i < value.length(); i++) {
            if (Character.isDigit(value.charAt(i))) {
                count++;
            }
        }
        return count;
    }

    public record LeadDetail(
            LeadResponse lead,
            boolean teaching,
            PriceList.Estimate estimate
    ) {
    }
}
