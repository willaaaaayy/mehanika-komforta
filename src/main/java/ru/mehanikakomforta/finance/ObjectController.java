package ru.mehanikakomforta.finance;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.mehanikakomforta.clients.ClientAccess;
import ru.mehanikakomforta.clients.ClientEntity;
import ru.mehanikakomforta.clients.ClientRepository;
import ru.mehanikakomforta.leads.ApiError;
import ru.mehanikakomforta.leads.LeadPhotoRepository;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@RestController
@Transactional
class ObjectController {

    private static final Set<String> STATUSES = Set.of("prep", "work", "acceptance", "done", "warranty");

    private final WorkObjectRepository objects;
    private final ContractRepository contracts;
    private final ClientRepository clients;
    private final LeadPhotoRepository photos;
    private final InternalRates rates;
    private final ru.mehanikakomforta.leads.LeadRepository leads;

    ObjectController(
            WorkObjectRepository objects,
            ContractRepository contracts,
            ClientRepository clients,
            LeadPhotoRepository photos,
            InternalRates rates,
            ru.mehanikakomforta.leads.LeadRepository leads
    ) {
        this.objects = objects;
        this.contracts = contracts;
        this.clients = clients;
        this.photos = photos;
        this.rates = rates;
        this.leads = leads;
    }

    @GetMapping("/api/objects")
    ResponseEntity<?> list(@RequestParam(required = false) Long client) {
        if (!ClientAccess.signedIn()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiError("auth"));
        }
        if (!ClientAccess.canReadClients()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError("role"));
        }
        List<Tile> body = new ArrayList<>();
        for (WorkObjectEntity object : objects.findAll()) {
            if (client != null && !client.equals(object.getClientId())) {
                continue;
            }
            body.add(tile(object));
        }
        return ResponseEntity.ok(body);
    }

    @GetMapping("/api/objects/{id}")
    ResponseEntity<?> one(@PathVariable long id) {
        if (!ClientAccess.signedIn()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiError("auth"));
        }
        if (!ClientAccess.canReadClients()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError("role"));
        }
        WorkObjectEntity object = objects.findById(id).orElse(null);
        if (object == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("id"));
        }
        return ResponseEntity.ok(detail(object));
    }

    @PostMapping("/api/objects")
    ResponseEntity<?> create(@RequestBody(required = false) ObjectBody body) {
        if (!ClientAccess.canConvert()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError("role"));
        }
        if (body == null || body.clientId() == null || body.address() == null || body.address().isBlank()) {
            return ResponseEntity.badRequest().body(new ApiError("address"));
        }
        if (clients.findById(body.clientId()).isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("client"));
        }
        String status = body.status() == null || body.status().isBlank() ? "prep" : body.status();
        if (!STATUSES.contains(status)) {
            return ResponseEntity.badRequest().body(new ApiError("status"));
        }
        WorkObjectEntity object = new WorkObjectEntity(body.clientId(), body.address().trim());
        object.change(null, body.type(), area(body.area()), status, null);
        return ResponseEntity.status(HttpStatus.CREATED).body(detail(objects.save(object)));
    }

    @PatchMapping("/api/objects/{id}")
    ResponseEntity<?> change(@PathVariable long id, @RequestBody(required = false) ObjectBody body) {
        if (!ClientAccess.canConvert()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError("role"));
        }
        WorkObjectEntity object = objects.findById(id).orElse(null);
        if (object == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("id"));
        }
        if (body == null) {
            return ResponseEntity.badRequest().body(new ApiError("address"));
        }
        if (body.status() != null && !body.status().isBlank() && !STATUSES.contains(body.status())) {
            return ResponseEntity.badRequest().body(new ApiError("status"));
        }
        object.change(body.address(), body.type(), area(body.area()), body.status(), null);
        return ResponseEntity.ok(detail(objects.save(object)));
    }

    private Tile tile(WorkObjectEntity object) {
        List<ContractEntity> rows = contracts.findByObjectIdAndConfirmedTrueOrderByCreatedAtDesc(object.getId());
        Long leadId = photoLead(rows, object.getClientId());
        String photo = null;
        if (leadId != null) {
            var pictures = photos.findByLeadIdOrderByIdAsc(leadId);
            if (!pictures.isEmpty()) {
                photo = "/api/leads/" + leadId + "/photos/" + pictures.get(0).getId();
            }
        }
        ClientEntity client = clients.findById(object.getClientId()).orElse(null);
        return new Tile(
                object.getId(),
                object.getClientId(),
                client == null ? null : client.getName(),
                object.getAddress(),
                object.getObjectType(),
                object.getArea(),
                object.getStatus(),
                due(rows),
                photo,
                leadId
        );
    }

    private Detail detail(WorkObjectEntity object) {
        List<ContractEntity> rows = contracts.findByObjectIdAndConfirmedTrueOrderByCreatedAtDesc(object.getId());
        List<ContractView> views = new ArrayList<>();
        for (ContractEntity contract : rows) {
            List<Step> steps = new ArrayList<>();
            for (ScheduleEntity step : contract.getSchedule()) {
                steps.add(new Step(
                        step.getId(),
                        step.getTitle(),
                        step.getAmount(),
                        step.getDueOn(),
                        step.getKind(),
                        step.getDueBasis() == null ? null : new Rule(step.getDueBasis(), step.getDueDays())
                ));
            }
            views.add(new ContractView(
                    contract.getId(),
                    contract.getNumber(),
                    contract.getAmount(),
                    new Party(contract.getClientName(), contract.getClientInn()),
                    new Place(contract.getObjectAddress()),
                    steps,
                    List.of()
            ));
        }
        ClientEntity client = clients.findById(object.getClientId()).orElse(null);
        return new Detail(
                object.getId(),
                object.getClientId(),
                client == null ? null : client.getName(),
                object.getAddress(),
                object.getObjectType(),
                object.getArea(),
                object.getStatus(),
                due(rows),
                photoLead(rows, object.getClientId()),
                views,
                costs(object, rows),
                rates.threshold()
        );
    }

    private List<FinanceView.CostLine> costs(WorkObjectEntity object, List<ContractEntity> rows) {
        java.time.LocalDate asOf = objectCreated(rows);
        java.util.LinkedHashSet<String> workIds = new java.util.LinkedHashSet<>();
        for (ContractEntity contract : rows) {
            if (contract.getLeadId() == null) {
                continue;
            }
            leads.findById(contract.getLeadId()).ifPresent(lead -> workIds.addAll(workIds(lead.getWorksJson())));
        }
        ClientEntity client = clients.findById(object.getClientId()).orElse(null);
        if (client != null && client.getSourceLeadId() != null) {
            leads.findById(client.getSourceLeadId()).ifPresent(lead -> workIds.addAll(workIds(lead.getWorksJson())));
        }
        return rates.costs(new ArrayList<>(workIds), asOf);
    }

    private static java.time.LocalDate objectCreated(List<ContractEntity> rows) {
        java.time.LocalDate asOf = null;
        for (ContractEntity contract : rows) {
            if (contract.getSignedOn() == null) {
                continue;
            }
            if (asOf == null || contract.getSignedOn().isBefore(asOf)) {
                asOf = contract.getSignedOn();
            }
        }
        return asOf == null ? java.time.LocalDate.of(2020, 1, 1) : asOf;
    }

    private static List<String> workIds(String worksJson) {
        if (worksJson == null || worksJson.isBlank()) {
            return List.of();
        }
        JsonNode node = tools.jackson.databind.json.JsonMapper.builder().build().readTree(worksJson);
        if (!node.isArray()) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.asString().isBlank()) {
                ids.add(item.asString());
            }
        }
        return ids;
    }

    private Long photoLead(List<ContractEntity> rows, Long clientId) {
        for (ContractEntity contract : rows) {
            if (contract.getLeadId() != null) {
                return contract.getLeadId();
            }
        }
        ClientEntity client = clients.findById(clientId).orElse(null);
        return client == null ? null : client.getSourceLeadId();
    }

    private static BigDecimal due(List<ContractEntity> rows) {
        if (rows.isEmpty()) {
            return null;
        }
        BigDecimal left = BigDecimal.ZERO;
        for (ContractEntity contract : rows) {
            BigDecimal paid = BigDecimal.ZERO;
            for (ScheduleEntity step : contract.getSchedule()) {
                if (step.getPaidOn() != null) {
                    paid = paid.add(step.getPaidAmount() == null ? step.getAmount() : step.getPaidAmount());
                }
            }
            BigDecimal remainder = contract.getAmount().subtract(paid);
            if (remainder.signum() > 0) {
                left = left.add(remainder);
            }
        }
        return left;
    }

    private static BigDecimal area(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String text = node.asString().trim().replace(',', '.');
        if (text.isEmpty()) {
            return null;
        }
        return new BigDecimal(text);
    }

    record ObjectBody(Long clientId, String address, String type, JsonNode area, String status) {
    }

    record Tile(
            Long id,
            Long clientId,
            String clientName,
            String address,
            String type,
            BigDecimal area,
            String status,
            BigDecimal due,
            String photo,
            Long leadId
    ) {
    }

    record Detail(
            Long id,
            Long clientId,
            String clientName,
            String address,
            String type,
            BigDecimal area,
            String status,
            BigDecimal due,
            Long leadId,
            List<ContractView> contracts,
            List<FinanceView.CostLine> costs,
            BigDecimal threshold
    ) {
    }

    record ContractView(
            Long id,
            String number,
            BigDecimal amount,
            Party client,
            Place object,
            List<Step> schedule,
            List<Object> addenda
    ) {
    }

    record Party(String name, String inn) {
    }

    record Place(String address) {
    }

    record Step(Long id, String title, BigDecimal amount, java.time.LocalDate due, String kind, Rule dueRule) {
    }

    record Rule(String basis, Integer businessDays) {
    }
}
