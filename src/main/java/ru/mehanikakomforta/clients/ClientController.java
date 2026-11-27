package ru.mehanikakomforta.clients;

import org.springframework.http.CacheControl;
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
import ru.mehanikakomforta.leads.ApiError;
import ru.mehanikakomforta.leads.LeadEntity;
import ru.mehanikakomforta.leads.LeadRepository;
import ru.mehanikakomforta.leads.LeadResponse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@RestController
@Transactional
public class ClientController {

    private final ClientRepository clients;
    private final ClientEventRepository events;
    private final LeadRepository leads;
    private final ru.mehanikakomforta.finance.ContractLinks links;
    private final ClientFields cardFields;
    private final ru.mehanikakomforta.finance.FinanceService finances;

    public ClientController(
            ClientRepository clients,
            ClientEventRepository events,
            LeadRepository leads,
            ru.mehanikakomforta.finance.ContractLinks links,
            ClientFields cardFields,
            ru.mehanikakomforta.finance.FinanceService finances
    ) {
        this.clients = clients;
        this.events = events;
        this.leads = leads;
        this.links = links;
        this.cardFields = cardFields;
        this.finances = finances;
    }

    @GetMapping("/api/clients")
    public ResponseEntity<?> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String q
    ) {
        if (!ClientAccess.canReadClients()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError("role"));
        }
        String wanted = status == null || status.isBlank() ? "current" : status;
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        List<ClientView> body = clients.findAllByOrderByCreatedAtDesc().stream()
                .filter(client -> "all".equals(wanted) || wanted.equals(client.getStatus()))
                .filter(client -> type == null || type.isBlank() || type.equals(client.getType()))
                .filter(client -> needle.isEmpty() || client.getName().toLowerCase(Locale.ROOT).contains(needle)
                        || (client.getPhone() != null && client.getPhone().contains(needle)))
                .map(this::view)
                .toList();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    @GetMapping("/api/clients/duplicates")
    public ResponseEntity<?> duplicates(
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String inn,
            @RequestParam(required = false) String email
    ) {
        if (!ClientAccess.canReadClients()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError("role"));
        }
        return ResponseEntity.ok(matches(phone, inn, email).stream().map(this::view).toList());
    }

    @GetMapping("/api/clients/{id}")
    public ResponseEntity<?> one(@PathVariable long id) {
        if (!ClientAccess.canReadClients()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError("role"));
        }
        return clients.findById(id)
                .<ResponseEntity<?>>map(client -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(page(client)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("id")));
    }

    @PatchMapping("/api/clients/{id}")
    public ResponseEntity<?> edit(@PathVariable long id, @RequestBody(required = false) ConvertBody body) {
        if (!ClientAccess.canConvert()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError("role"));
        }
        var found = clients.findById(id);
        if (found.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("id"));
        }
        if (body == null) {
            return ResponseEntity.badRequest().body(new ApiError("name"));
        }
        ClientEntity client = found.get();
        client.fill(body.type(), body.name(), body.phone(), body.email(), body.inn(), body.address(), null);
        client.status(body.status());
        if (body.customFields() != null) {
            cardFields.saveValues(client.getId(), body.customFields());
        }
        events.save(new ClientEventEntity(client.getId(), client.getSourceLeadId(), Instant.now(), ClientAccess.actor(), "edit"));
        return ResponseEntity.ok(page(clients.save(client)));
    }

    @PostMapping("/api/leads/{id}/convert")
    public ResponseEntity<?> convert(@PathVariable long id, @RequestBody(required = false) ConvertBody body) {
        if (!ClientAccess.signedIn()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiError("auth"));
        }
        if (!ClientAccess.canConvert()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError("role"));
        }
        var found = leads.findById(id);
        if (found.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("id"));
        }
        LeadEntity lead = found.get();
        if (lead.getClientId() != null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new Conflict("converted", lead.getClientId()));
        }
        String type = body == null || body.type() == null || body.type().isBlank() ? "person" : body.type();
        if (!type.equals("person") && !type.equals("sole") && !type.equals("company")) {
            return ResponseEntity.badRequest().body(new ApiError("type"));
        }
        String name = body != null && body.name() != null && !body.name().isBlank() ? body.name().trim() : lead.getName();
        String phone = body != null && body.phone() != null ? body.phone().trim() : lead.getPhone();
        String email = body == null ? null : blankToNull(body.email());
        String inn = body == null ? null : blankToNull(body.inn());
        boolean merge = body != null && (Boolean.TRUE.equals(body.merge()) || "merge".equals(body.mode()));
        ClientEntity client;
        if (merge) {
            if (body.clientId() == null) {
                return ResponseEntity.badRequest().body(new ApiError("clientId"));
            }
            var existing = clients.findById(body.clientId());
            if (existing.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("id"));
            }
            client = existing.get();
            client.fill(type, name, phone, email, inn, null, lead.getId());
        } else {
            List<ClientEntity> sameInn = inn == null ? List.of() : matches(null, inn, null);
            if (!sameInn.isEmpty()) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(new Conflict("duplicate", sameInn.get(0).getId()));
            }
            client = new ClientEntity(Instant.now(), type, name, "current");
            client.fill(type, name, phone, email, inn, null, lead.getId());
        }
        if (lead.getTelegramId() != null) {
            client.telegram(lead.getTelegramId(), lead.getTelegramUsername(), lead.getTelegramFirstName(), lead.getTelegramLastName());
        }
        ClientEntity saved = clients.save(client);
        Instant now = Instant.now();
        lead.converted(saved.getId(), now, ClientAccess.actor());
        leads.save(lead);
        events.save(new ClientEventEntity(saved.getId(), lead.getId(), now, ClientAccess.actor(), merge ? "merge" : "create"));
        return ResponseEntity.status(HttpStatus.CREATED).body(new Converted(saved.getId(), saved.getId()));
    }

    @PostMapping("/api/leads/{id}/unconvert")
    public ResponseEntity<?> unconvert(@PathVariable long id) {
        if (!ClientAccess.signedIn()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiError("auth"));
        }
        if (!ClientAccess.canConvert()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError("role"));
        }
        var found = leads.findById(id);
        if (found.isEmpty() || found.get().getClientId() == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("id"));
        }
        LeadEntity lead = found.get();
        Long clientId = lead.getClientId();
        if (links.hasContract(clientId)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError("contracts"));
        }
        lead.clearConversion();
        leads.save(lead);
        clients.findById(clientId).ifPresent(client -> {
            if (lead.getId().equals(client.getSourceLeadId()) && leads.countByClientId(clientId) == 0) {
                client.archive();
                clients.save(client);
            }
        });
        events.save(new ClientEventEntity(clientId, lead.getId(), Instant.now(), ClientAccess.actor(), "unconvert"));
        return ResponseEntity.noContent().build();
    }

    private ClientPage page(ClientEntity client) {
        LeadResponse lead = null;
        if (client.getSourceLeadId() != null) {
            lead = leads.findById(client.getSourceLeadId()).map(LeadResponse::from).orElse(null);
        }
        var money = links.money(client.getId());
        var share = finances.share(client.getId(), links.contractsOf(client.getId()));
        return new ClientPage(
                client.getId(),
                client.getType(),
                client.getName(),
                client.getPhone(),
                client.getEmail(),
                client.getInn(),
                client.getAddress(),
                client.getStatus(),
                client.getSourceLeadId(),
                money.amount(),
                money.paid(),
                money.due(),
                money.overdue(),
                links.cards(client.getId()),
                links.sites(client.getId()),
                events.findByClientIdOrderByAtDesc(client.getId()).stream()
                        .map(event -> new HistoryLine(event.getAt().toString(), historyText(event.getAction())))
                        .toList(),
                lead,
                cardFields.valuesOf(client.getId()),
                share.spent(),
                share.profit(),
                share.payable(),
                share.invested(),
                share.profitability()
        );
    }

    private static String historyText(String action) {
        return switch (action) {
            case "create" -> "Заявка переведена в клиента";
            case "merge" -> "Заявка объединена с клиентом";
            case "edit" -> "Карточка изменена";
            case "unconvert" -> "Клиент возвращён в заявки";
            case "contract" -> "Договор записан в карточку";
            default -> action;
        };
    }

    private ClientView view(ClientEntity client) {
        var money = links.money(client.getId());
        return new ClientView(
                client.getId(),
                client.getType(),
                client.getName(),
                client.getPhone(),
                client.getEmail(),
                client.getInn(),
                client.getStatus(),
                client.getSourceLeadId(),
                money.contracts(),
                money.amount(),
                money.paid(),
                money.due(),
                money.overdue(),
                money.next()
        );
    }

    private List<ClientEntity> matches(String phone, String inn, String email) {
        String phoneTail = tail(phone);
        String innKey = blankToNull(inn);
        String emailKey = email == null || email.isBlank() ? null : email.trim().toLowerCase(Locale.ROOT);
        List<ClientEntity> found = new ArrayList<>();
        for (ClientEntity client : clients.findAllByOrderByCreatedAtDesc()) {
            boolean phoneHit = phoneTail != null && phoneTail.equals(tail(client.getPhone()));
            boolean innHit = innKey != null && innKey.equals(client.getInn());
            boolean emailHit = emailKey != null && client.getEmail() != null
                    && emailKey.equals(client.getEmail().trim().toLowerCase(Locale.ROOT));
            if (phoneHit || innHit || emailHit) {
                found.add(client);
            }
        }
        return found;
    }

    private static String tail(String phone) {
        if (phone == null) {
            return null;
        }
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < phone.length(); i++) {
            if (Character.isDigit(phone.charAt(i))) {
                digits.append(phone.charAt(i));
            }
        }
        if (digits.length() < 10) {
            return digits.length() == 0 ? null : digits.toString();
        }
        return digits.substring(digits.length() - 10);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public record ConvertBody(
            String mode,
            Boolean merge,
            Long clientId,
            String type,
            String name,
            String phone,
            String email,
            String inn,
            String address,
            String status,
            java.util.Map<String, Object> customFields
    ) {
    }

    public record Converted(Long clientId, Long id) {
    }

    public record Conflict(String error, Long clientId) {
    }

    public record ClientView(
            Long id,
            String type,
            String name,
            String phone,
            String email,
            String inn,
            String status,
            Long sourceLeadId,
            Integer contracts,
            java.math.BigDecimal amount,
            java.math.BigDecimal paid,
            java.math.BigDecimal due,
            java.math.BigDecimal overdue,
            ru.mehanikakomforta.finance.ContractLinks.Next nextPayment
    ) {
    }

    public record ClientPage(
            Long id,
            String type,
            String name,
            String phone,
            String email,
            String inn,
            String address,
            String status,
            Long sourceLeadId,
            java.math.BigDecimal amount,
            java.math.BigDecimal paid,
            java.math.BigDecimal due,
            java.math.BigDecimal overdue,
            List<ru.mehanikakomforta.finance.ContractLinks.Card> contracts,
            List<ru.mehanikakomforta.finance.ContractLinks.Site> objects,
            List<HistoryLine> history,
            LeadResponse lead,
            java.util.Map<String, Object> customFields,
            java.math.BigDecimal spent,
            java.math.BigDecimal profit,
            java.math.BigDecimal payable,
            java.math.BigDecimal invested,
            java.math.BigDecimal profitability
    ) {
    }

    public record HistoryLine(String at, String text) {
    }
}
