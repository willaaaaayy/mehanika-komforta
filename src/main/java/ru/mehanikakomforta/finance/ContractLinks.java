package ru.mehanikakomforta.finance;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.mehanikakomforta.CrmProperties;
import ru.mehanikakomforta.clients.ClientEntity;
import ru.mehanikakomforta.clients.ClientEventEntity;
import ru.mehanikakomforta.clients.ClientEventRepository;
import ru.mehanikakomforta.clients.ClientFields;
import ru.mehanikakomforta.clients.ClientRepository;
import ru.mehanikakomforta.leads.LeadEntity;
import ru.mehanikakomforta.leads.LeadRepository;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
@Order(1)
public class ContractLinks implements ApplicationRunner {

    private final ContractRepository contracts;
    private final WorkObjectRepository objects;
    private final ClientRepository clients;
    private final ClientEventRepository events;
    private final LeadRepository leads;
    private final CrmProperties properties;
    private final ClientFields cardFields;

    public ContractLinks(
            ContractRepository contracts,
            WorkObjectRepository objects,
            ClientRepository clients,
            ClientEventRepository events,
            LeadRepository leads,
            CrmProperties properties,
            ClientFields cardFields
    ) {
        this.contracts = contracts;
        this.objects = objects;
        this.clients = clients;
        this.events = events;
        this.leads = leads;
        this.properties = properties;
        this.cardFields = cardFields;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        attachMissing();
    }

    @Transactional
    public void attachMissing() {
        for (ContractEntity contract : contracts.findByConfirmedTrueOrderByCreatedAtDesc()) {
            if (contract.getClientId() == null) {
                rememberRules(contract);
                place(contract, null, contract.getLeadId());
            }
        }
    }

    public boolean hasContract(Long clientId) {
        return clientId != null && contracts.countByClientIdAndConfirmedTrue(clientId) > 0;
    }

    public Money money(Long clientId) {
        return moneyOf(contracts.findByClientIdAndConfirmedTrueOrderByCreatedAtDesc(clientId));
    }

    public List<ContractEntity> contractsOf(Long clientId) {
        return contracts.findByClientIdAndConfirmedTrueOrderByCreatedAtDesc(clientId);
    }

    public List<Card> cards(Long clientId) {
        List<Card> cards = new ArrayList<>();
        for (ContractEntity contract : contracts.findByClientIdAndConfirmedTrueOrderByCreatedAtDesc(clientId)) {
            cards.add(card(contract));
        }
        return cards;
    }

    public List<Site> sites(Long clientId) {
        List<Site> sites = new ArrayList<>();
        for (WorkObjectEntity object : objects.findByClientIdOrderById(clientId)) {
            sites.add(new Site(object.getId(), object.getAddress(), object.getStatus(), object.getObjectType(), object.getArea()));
        }
        return sites;
    }

    @Transactional
    public Long place(ContractEntity contract, Long clientId, Long leadId) {
        if (!contract.isConfirmed()) {
            return contract.getClientId();
        }
        ClientEntity client = clientId == null ? null : clients.findById(clientId).orElse(null);
        LeadEntity lead = leadId == null ? null : leads.findById(leadId).orElse(null);
        if (client == null && lead != null && lead.getClientId() != null) {
            client = clients.findById(lead.getClientId()).orElse(null);
        }
        if (client == null && contract.getClientInn() != null) {
            for (ClientEntity existing : clients.findAllByOrderByCreatedAtDesc()) {
                if (contract.getClientInn().equals(existing.getInn())) {
                    client = existing;
                    break;
                }
            }
        }
        boolean created = false;
        if (client == null) {
            String type = typeOf(contract.getClientName(), contract.getClientInn());
            client = new ClientEntity(Instant.now(), type, shorten(contract.getClientName()), "current");
            client.fill(type, contract.getClientName(), null, null, contract.getClientInn(), contract.getClientAddress(), null);
            client = clients.save(client);
            created = true;
        } else {
            client.remember(contract.getClientInn(), contract.getClientAddress());
            clients.save(client);
        }
        Long objectId = objectFor(client.getId(), contract.getObjectAddress());
        contract.linked(client.getId(), objectId, lead == null ? null : lead.getId());
        contracts.save(contract);
        if (lead != null && lead.getClientId() == null) {
            lead.converted(client.getId(), Instant.now(), "director");
            leads.save(lead);
        }
        if (created) {
            events.save(new ClientEventEntity(client.getId(), lead == null ? null : lead.getId(), Instant.now(), "director", "contract"));
        }
        java.util.Map<String, String> copied = new java.util.LinkedHashMap<>();
        copied.put("client.kpp", contract.getClientKpp());
        copied.put("client.ogrn", contract.getClientOgrn());
        copied.put("client.signatory", contract.getSignatory());
        copied.put("object.address", contract.getObjectAddress());
        cardFields.rememberContract(client.getId(), copied);
        return client.getId();
    }

    private void rememberRules(ContractEntity contract) {
        Path file = Path.of(properties.contractDir(), String.valueOf(contract.getId()));
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            ContractText.Read read = ContractText.read(Files.readAllBytes(file));
            if (read == null) {
                return;
            }
            for (ScheduleEntity step : contract.getSchedule()) {
                if (step.getDueOn() != null || step.getDueBasis() != null) {
                    continue;
                }
                for (ContractText.Row row : read.schedule()) {
                    if (row.kind().equals(step.getKind()) && row.dueBasis() != null) {
                        step.dueRule(row.dueBasis(), row.dueDays());
                    }
                }
            }
        } catch (Exception ignored) {
            // A stored file that cannot be reread keeps the schedule already saved.
        }
    }

    private Long objectFor(Long clientId, String address) {
        if (address == null || address.isBlank()) {
            return null;
        }
        String wanted = address.trim();
        for (WorkObjectEntity object : objects.findByClientIdOrderById(clientId)) {
            if (object.getAddress().equalsIgnoreCase(wanted)) {
                return object.getId();
            }
        }
        return objects.save(new WorkObjectEntity(clientId, shorten(wanted, 500))).getId();
    }

    private static Money moneyOf(List<ContractEntity> rows) {
        BigDecimal amount = BigDecimal.ZERO;
        BigDecimal paid = BigDecimal.ZERO;
        BigDecimal overdue = BigDecimal.ZERO;
        Next next = null;
        LocalDate today = LocalDate.now();
        for (ContractEntity contract : rows) {
            amount = amount.add(contract.getAmount());
            for (ScheduleEntity step : contract.getSchedule()) {
                boolean isPaid = step.getPaidOn() != null;
                BigDecimal stepPaid = isPaid ? (step.getPaidAmount() == null ? step.getAmount() : step.getPaidAmount()) : BigDecimal.ZERO;
                paid = paid.add(stepPaid);
                if (!isPaid && step.getDueOn() != null && step.getDueOn().isBefore(today)) {
                    overdue = overdue.add(step.getAmount());
                }
                if (!isPaid && next == null) {
                    next = new Next(step.getAmount(), step.getDueOn());
                }
            }
        }
        BigDecimal due = amount.subtract(paid);
        if (due.signum() < 0) {
            due = BigDecimal.ZERO;
        }
        return new Money(rows.size(), amount, paid, due, overdue.signum() == 0 ? BigDecimal.ZERO : overdue, next);
    }

    private static Card card(ContractEntity contract) {
        List<Step> steps = new ArrayList<>();
        for (ScheduleEntity step : contract.getSchedule()) {
            Rule rule = step.getDueBasis() == null ? null : new Rule(step.getDueBasis(), step.getDueDays());
            steps.add(new Step(step.getId(), step.getTitle(), step.getAmount(), step.getDueOn(), step.getKind(), rule));
        }
        return new Card(
                contract.getId(),
                contract.getNumber(),
                contract.getAmount(),
                new Party(contract.getClientName(), contract.getClientInn()),
                new Place(contract.getObjectAddress()),
                steps
        );
    }

    private static String typeOf(String name, String inn) {
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (lower.contains("индивидуальн") || lower.startsWith("ип ") || (inn != null && inn.length() == 12)) {
            return "sole";
        }
        if (lower.contains("общество") || lower.contains("ооо") || (inn != null && inn.length() == 10)) {
            return "company";
        }
        return "person";
    }

    private static String shorten(String value) {
        return shorten(value, 200);
    }

    private static String shorten(String value, int limit) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= limit ? trimmed : trimmed.substring(0, limit);
    }

    public record Money(int contracts, BigDecimal amount, BigDecimal paid, BigDecimal due, BigDecimal overdue, Next next) {
    }

    public record Next(BigDecimal amount, LocalDate date) {
    }

    public record Card(Long id, String number, BigDecimal amount, Party client, Place object, List<Step> schedule) {
    }

    public record Party(String name, String inn) {
    }

    public record Place(String address) {
    }

    public record Step(Long id, String title, BigDecimal amount, LocalDate due, String kind, Rule dueRule) {
    }

    public record Rule(String basis, Integer businessDays) {
    }

    public record Site(Long id, String address, String status, String objectType, java.math.BigDecimal area) {
    }
}
