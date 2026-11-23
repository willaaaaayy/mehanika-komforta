package ru.mehanikakomforta.leads;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import ru.mehanikakomforta.CrmProperties;
import ru.mehanikakomforta.SecretMatch;

import java.time.Instant;
import java.util.List;

@RestController
public class LeadController {

    private final LeadRepository leads;
    private final LeadProfileRepository profiles;
    private final CrmProperties properties;

    public LeadController(LeadRepository leads, LeadProfileRepository profiles, CrmProperties properties) {
        this.leads = leads;
        this.profiles = profiles;
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

    @PatchMapping("/api/leads/{id}")
    public ResponseEntity<?> stage(@PathVariable long id, @RequestBody(required = false) StageRequest request) {
        LeadStage stage = request == null ? null : LeadStage.parse(request.stage());
        if (stage == null) {
            return ResponseEntity.badRequest().body(new ApiError("stage"));
        }
        var found = leads.findById(id);
        if (found.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("id"));
        }
        LeadEntity lead = found.get();
        lead.changeStage(stage, Instant.now());
        return ResponseEntity.ok(view(leads.save(lead)));
    }

    private LeadResponse view(LeadEntity lead) {
        return LeadResponse.from(lead, profiles.existsByLeadId(lead.getId()));
    }
}
