package ru.mehanikakomforta.finance;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ru.mehanikakomforta.CrmProperties;
import ru.mehanikakomforta.leads.ApiError;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@Transactional
class ContractController {

    private final ContractRepository contracts;
    private final ScheduleRepository schedule;
    private final ContractLinks links;
    private final FinanceService finances;
    private final InternalRates rates;
    private final ru.mehanikakomforta.leads.LeadRepository leads;
    private final CrmProperties properties;

    ContractController(
            ContractRepository contracts,
            ScheduleRepository schedule,
            ContractLinks links,
            FinanceService finances,
            InternalRates rates,
            ru.mehanikakomforta.leads.LeadRepository leads,
            CrmProperties properties
    ) {
        this.contracts = contracts;
        this.schedule = schedule;
        this.links = links;
        this.finances = finances;
        this.rates = rates;
        this.leads = leads;
        this.properties = properties;
    }

    @GetMapping("/api/finance")
    FinanceView finance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String client,
            @RequestParam(required = false) String object,
            @RequestParam(required = false) String mode
    ) {
        List<ContractEntity> rows = contracts.findByConfirmedTrueOrderByCreatedAtDesc().stream()
                .filter(item -> sameClient(item, client))
                .filter(item -> object == null || object.isBlank() || object.equals(item.getObjectAddress()))
                .toList();
        Map<String, List<ContractEntity>> grouped = new LinkedHashMap<>();
        for (ContractEntity row : rows) {
            String key = row.getClientId() == null ? row.getClientName() : String.valueOf(row.getClientId());
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(row);
        }
        List<FinanceView.ClientRow> clients = new ArrayList<>();
        BigDecimal receivable = BigDecimal.ZERO;
        BigDecimal received = BigDecimal.ZERO;
        List<FinanceView.Attention> attention = new ArrayList<>();
        Map<String, BigDecimal> weeks = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();
        for (var entry : grouped.entrySet()) {
            BigDecimal amount = BigDecimal.ZERO;
            BigDecimal paid = BigDecimal.ZERO;
            BigDecimal overdue = BigDecimal.ZERO;
            FinanceView.NextPayment next = null;
            List<FinanceView.Item> items = new ArrayList<>();
            for (ContractEntity contract : entry.getValue()) {
                amount = amount.add(contract.getAmount());
                List<FinanceView.Step> steps = new ArrayList<>();
                for (ScheduleEntity step : contract.getSchedule()) {
                    boolean isPaid = step.getPaidOn() != null;
                    boolean late = !isPaid && step.getDueOn() != null && step.getDueOn().isBefore(today);
                    if (isPaid && inPeriod(step.getPaidOn(), from, to)) {
                        paid = paid.add(step.getPaidAmount() == null ? step.getAmount() : step.getPaidAmount());
                        received = received.add(step.getPaidAmount() == null ? step.getAmount() : step.getPaidAmount());
                        String week = step.getPaidOn().get(WeekFields.ISO.weekBasedYear()) + "-W"
                                + step.getPaidOn().get(WeekFields.ISO.weekOfWeekBasedYear());
                        weeks.merge(week, step.getPaidAmount() == null ? step.getAmount() : step.getPaidAmount(), BigDecimal::add);
                    }
                    if (late) {
                        overdue = overdue.add(step.getAmount());
                        attention.add(new FinanceView.Attention(
                                contract.getClientId() == null ? contract.getClientName() : String.valueOf(contract.getClientId()),
                                step.getTitle(),
                                step.getAmount(),
                                (int) (today.toEpochDay() - step.getDueOn().toEpochDay())
                        ));
                    }
                    if (!isPaid && next == null) {
                        next = new FinanceView.NextPayment(step.getAmount(), step.getDueOn());
                    }
                    steps.add(new FinanceView.Step(
                            step.getId(),
                            step.getTitle(),
                            step.getAmount(),
                            step.getDueOn(),
                            step.getKind(),
                            isPaid ? "paid" : late ? "overdue" : "planned",
                            late ? (int) (today.toEpochDay() - step.getDueOn().toEpochDay()) : null
                    ));
                }
                items.add(new FinanceView.Item(
                        contract.getId(),
                        contract.getNumber(),
                        contract.getObjectAddress(),
                        steps,
                        workLines(contract)
                ));
            }
            BigDecimal due = amount.subtract(paid);
            receivable = receivable.add(due.max(BigDecimal.ZERO));
            ContractEntity first = entry.getValue().get(0);
            String shownId = first.getClientId() == null ? first.getClientName() : String.valueOf(first.getClientId());
            clients.add(new FinanceView.ClientRow(
                    shownId,
                    first.getClientName(),
                    entry.getValue().size(),
                    amount,
                    null,
                    paid,
                    due.max(BigDecimal.ZERO),
                    overdue.signum() == 0 ? BigDecimal.ZERO : overdue,
                    next,
                    items
            ));
        }
        FinanceService.Result money = finances.assess(rows, from, to, mode, client, object);
        Map<String, FinanceView.Week> weekRows = new LinkedHashMap<>();
        for (var item : weeks.entrySet()) {
            weekRows.put(item.getKey(), new FinanceView.Week(item.getKey(), item.getValue(), money.outs().get(item.getKey())));
        }
        for (var item : money.outs().entrySet()) {
            weekRows.putIfAbsent(item.getKey(), new FinanceView.Week(item.getKey(), null, item.getValue()));
        }
        return new FinanceView(
                new FinanceView.Totals(receivable, money.payable(), money.spent(), received, money.profit(), money.invested(), money.profitability()),
                clients,
                attention,
                new ArrayList<>(weekRows.values()),
                money.expenses(),
                money.months(),
                money.cascade(),
                money.capital(),
                money.profitabilityMonths()
        );
    }

    @PostMapping(value = "/api/contracts", consumes = "multipart/form-data")
    ResponseEntity<?> upload(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(required = false) Long clientId,
            @RequestParam(required = false) Long leadId,
            @RequestParam(required = false) Long addendumOf
    ) throws IOException {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(new ApiError("contract"));
        }
        byte[] bytes = file.getBytes();
        if (!isPdf(bytes) && !isDocx(bytes)) {
            return ResponseEntity.badRequest().body(new ApiError("contract"));
        }
        ContractText.Read read = ContractText.read(bytes);
        if (read == null) {
            return ResponseEntity.badRequest().body(new ApiError("contract"));
        }
        if (addendumOf != null || read.addendum()) {
            ContractEntity base = addendumOf == null
                    ? contracts.findByNumber(read.number()).orElse(null)
                    : contracts.findById(addendumOf).orElse(null);
            if (base == null || !base.isConfirmed()) {
                return ResponseEntity.badRequest().body(new ApiError("contract"));
            }
            Files.createDirectories(Path.of(properties.contractDir()));
            Files.write(Path.of(properties.contractDir(), base.getId() + "-addendum"), bytes);
            return ResponseEntity.ok(draft(base, read, true));
        }
        if (contracts.findByNumber(read.number()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError("duplicate"));
        }
        String original = file.getOriginalFilename() == null ? "dogovor" : file.getOriginalFilename();
        ContractEntity contract = new ContractEntity(Instant.now(), read.number(), original);
        contract.fill(read.number(), read.client(), read.inn(), read.objectAddress(), read.amount(), false);
        contract.details(null, read.subject(), read.vat(), read.kpp(), read.ogrn(), read.clientAddress(), read.signatory());
        contract.linked(clientId, null, leadId);
        contract.replaceSchedule(rows(contract, read.schedule()));
        ContractEntity saved = contracts.save(contract);
        Files.createDirectories(Path.of(properties.contractDir()));
        Files.write(Path.of(properties.contractDir(), String.valueOf(saved.getId())), bytes);
        return ResponseEntity.status(HttpStatus.CREATED).body(draft(saved, read, false));
    }

    @PostMapping(value = "/api/contracts", consumes = "application/json")
    ResponseEntity<?> create(@RequestBody(required = false) ConfirmBody body) {
        String problem = problem(body);
        if (problem != null) {
            return ResponseEntity.badRequest().body(new ApiError(problem));
        }
        if (contracts.findByNumber(body.number().trim()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError("duplicate"));
        }
        ContractEntity contract = new ContractEntity(Instant.now(), body.number().trim(), "вручную");
        apply(contract, body, true);
        links.place(contracts.save(contract), body.clientId(), body.leadId());
        return ResponseEntity.status(HttpStatus.CREATED).body(draft(contract, null, false));
    }

    @PatchMapping("/api/contracts/{id}")
    ResponseEntity<?> confirm(@PathVariable long id, @RequestBody(required = false) ConfirmBody body) {
        var found = contracts.findById(id);
        if (found.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("id"));
        }
        String problem = problem(body);
        if (problem != null) {
            return ResponseEntity.badRequest().body(new ApiError(problem));
        }
        var other = contracts.findByNumber(body.number().trim());
        if (other.isPresent() && !other.get().getId().equals(id)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError("duplicate"));
        }
        ContractEntity contract = found.get();
        apply(contract, body, true);
        Long clientId = body.clientId() != null ? body.clientId() : contract.getClientId();
        Long leadId = body.leadId() != null ? body.leadId() : contract.getLeadId();
        links.place(contracts.save(contract), clientId, leadId);
        return ResponseEntity.ok(draft(contract, null, false));
    }

    @PostMapping("/api/contracts/{id}/payments")
    ResponseEntity<?> pay(@PathVariable long id, @RequestBody(required = false) PayBody body) {
        var found = contracts.findById(id);
        if (found.isEmpty() || !found.get().isConfirmed()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("id"));
        }
        if (body == null || body.scheduleId() == null || body.amount() == null || body.date() == null) {
            return ResponseEntity.badRequest().body(new ApiError("payment"));
        }
        var step = schedule.findById(body.scheduleId());
        if (step.isEmpty() || step.get().getContract() == null || !step.get().getContract().getId().equals(id)) {
            return ResponseEntity.badRequest().body(new ApiError("payment"));
        }
        step.get().pay(body.amount(), body.date(), body.method());
        schedule.save(step.get());
        return ResponseEntity.ok(draft(found.get(), null, false));
    }

    private static boolean inPeriod(LocalDate date, LocalDate from, LocalDate to) {
        if (date == null) {
            return false;
        }
        if (from != null && date.isBefore(from)) {
            return false;
        }
        return to == null || !date.isAfter(to);
    }

    private static String problem(ConfirmBody body) {
        if (body == null || body.number() == null || body.number().isBlank()) {
            return "number";
        }
        if (body.amount() == null || body.amount().signum() < 0) {
            return "amount";
        }
        if (body.client() == null || body.client().name() == null || body.client().name().isBlank()) {
            return "client";
        }
        return null;
    }

    private List<FinanceView.WorkLine> workLines(ContractEntity contract) {
        if (contract.getLeadId() == null) {
            return List.of();
        }
        java.time.LocalDate asOf = contract.getSignedOn() == null ? java.time.LocalDate.of(2020, 1, 1) : contract.getSignedOn();
        return leads.findById(contract.getLeadId())
                .map(lead -> rates.lines(lead.getWorksJson(), lead.getGrade(), asOf))
                .orElse(List.of());
    }

    private static boolean sameClient(ContractEntity item, String client) {
        if (client == null || client.isBlank()) {
            return true;
        }
        if (client.equals(item.getClientName())) {
            return true;
        }
        return item.getClientId() != null && client.equals(String.valueOf(item.getClientId()));
    }

    private static boolean isPdf(byte[] bytes) {
        return bytes.length > 4 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F';
    }

    private static boolean isDocx(byte[] bytes) {
        return bytes.length > 4 && bytes[0] == 'P' && bytes[1] == 'K';
    }

    private static void apply(ContractEntity contract, ConfirmBody body, boolean confirmed) {
        contract.fill(
                body.number().trim(),
                body.client().name().trim(),
                blank(body.client().inn()),
                body.object() == null ? null : blank(body.object().address()),
                body.amount(),
                confirmed
        );
        contract.details(
                date(body.date()),
                blank(body.subject()),
                body.vat(),
                blank(body.client().kpp()),
                blank(body.client().ogrn()),
                blank(body.client().address()),
                blank(body.client().signatory())
        );
        List<ScheduleEntity> rows = new ArrayList<>();
        List<ConfirmBody.Row> source = body.schedule() == null ? List.of() : body.schedule();
        for (int i = 0; i < source.size(); i++) {
            ConfirmBody.Row row = source.get(i);
            if (row.amount() == null) {
                continue;
            }
            String kind = row.kind() == null || row.kind().isBlank() ? "stage" : row.kind();
            ScheduleEntity step = new ScheduleEntity(
                    contract,
                    row.title() == null || row.title().isBlank() ? "Платёж" : row.title().trim(),
                    row.amount(),
                    row.due(),
                    kind,
                    i
            );
            if (row.due() == null && row.dueRule() != null && "act".equals(row.dueRule().basis())) {
                step.dueRule("act", row.dueRule().businessDays());
            }
            rows.add(step);
        }
        contract.replaceSchedule(rows);
    }

    private static List<ScheduleEntity> rows(ContractEntity contract, List<ContractText.Row> source) {
        List<ScheduleEntity> rows = new ArrayList<>();
        for (int i = 0; i < source.size(); i++) {
            ContractText.Row row = source.get(i);
            ScheduleEntity step = new ScheduleEntity(contract, row.title(), row.amount(), null, row.kind(), i);
            if (row.dueBasis() != null) {
                step.dueRule(row.dueBasis(), row.dueDays());
            }
            rows.add(step);
        }
        return rows;
    }

    private static Draft draft(ContractEntity contract, ContractText.Read read, boolean compare) {
        List<Draft.Step> steps = new ArrayList<>();
        if (compare && read != null) {
            for (ContractText.Row row : read.schedule()) {
                steps.add(new Draft.Step(null, row.title(), row.amount(), null, row.kind(), rule(row.dueBasis(), row.dueDays())));
            }
        } else {
            for (ScheduleEntity row : contract.getSchedule()) {
                steps.add(new Draft.Step(
                        row.getId(),
                        row.getTitle(),
                        row.getAmount(),
                        row.getDueOn(),
                        row.getKind(),
                        rule(row.getDueBasis(), row.getDueDays())
                ));
            }
        }
        Draft.Party party = compare && read != null
                ? new Draft.Party(read.client(), read.inn(), read.kpp(), read.ogrn(), read.clientAddress(), read.signatory())
                : new Draft.Party(contract.getClientName(), contract.getClientInn(), contract.getClientKpp(), contract.getClientOgrn(), contract.getClientAddress(), contract.getSignatory());
        String address = compare && read != null ? read.objectAddress() : contract.getObjectAddress();
        return new Draft(
                contract.getId(),
                compare && read != null ? read.number() : contract.getNumber(),
                read == null ? null : read.date(),
                compare && read != null ? read.subject() : contract.getSubject(),
                compare && read != null ? read.amount() : contract.getAmount(),
                compare && read != null ? read.vat() : contract.getVat(),
                false,
                read == null ? null : read.paragraphs(),
                contract.isConfirmed() ? contract.getClientId() : null,
                party,
                new Draft.Place(address),
                steps,
                compare ? new Draft.Compare(
                        new Draft.MoneyChange(contract.getAmount(), read == null ? contract.getAmount() : read.amount()),
                        new Draft.TextChange(scheduleText(contract), scheduleText(read))
                ) : null,
                confidence(read),
                source(read)
        );
    }

    private static Draft.Rule rule(String basis, Integer days) {
        return basis == null ? null : new Draft.Rule(basis, days);
    }

    private static String scheduleText(ContractEntity contract) {
        StringBuilder text = new StringBuilder();
        for (ScheduleEntity step : contract.getSchedule()) {
            if (!text.isEmpty()) {
                text.append("; ");
            }
            text.append(step.getTitle()).append(' ');
            text.append(step.getDueOn() != null ? step.getDueOn() : actText(step.getDueBasis(), step.getDueDays()));
        }
        return text.toString();
    }

    private static String scheduleText(ContractText.Read read) {
        if (read == null) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (ContractText.Row row : read.schedule()) {
            if (!text.isEmpty()) {
                text.append("; ");
            }
            text.append(row.title()).append(' ').append(actText(row.dueBasis(), row.dueDays()));
        }
        return text.toString();
    }

    private static String actText(String basis, Integer days) {
        if (!"act".equals(basis)) {
            return "—";
        }
        return days == null ? "по акту" : "по акту, " + days + " банк. дн.";
    }

    private static Map<String, String> confidence(ContractText.Read read) {
        if (read == null) {
            return null;
        }
        Map<String, String> marks = new LinkedHashMap<>();
        marks.put("number", "high");
        marks.put("date", "none");
        marks.put("subject", read.subject() == null ? "none" : "high");
        marks.put("amount", "high");
        marks.put("vat", read.vat() == null ? "none" : "high");
        marks.put("client.name", "high");
        marks.put("client.inn", read.inn() == null ? "none" : "high");
        marks.put("client.kpp", read.kpp() == null ? "none" : "high");
        marks.put("client.ogrn", read.ogrn() == null ? "none" : "high");
        marks.put("client.address", read.clientAddress() == null ? "none" : "high");
        marks.put("client.signatory", read.signatory() == null ? "none" : "check");
        marks.put("object.address", read.objectAddress() == null ? "none" : "high");
        for (int i = 0; i < read.schedule().size(); i++) {
            marks.put("schedule." + i + ".amount", "high");
        }
        return marks;
    }

    private static Map<String, Draft.Source> source(ContractText.Read read) {
        if (read == null) {
            return null;
        }
        Map<String, Draft.Source> marks = new LinkedHashMap<>();
        mark(marks, read.paragraphs(), "number", read.number());
        mark(marks, read.paragraphs(), "subject", read.subject() == null ? null : read.subject().substring(0, Math.min(24, read.subject().length())));
        markAmount(marks, read);
        mark(marks, read.paragraphs(), "client.name", read.client());
        mark(marks, read.paragraphs(), "client.inn", read.inn());
        mark(marks, read.paragraphs(), "object.address", read.objectAddress() == null ? null : "на объекте");
        return marks;
    }

    private static void markAmount(Map<String, Draft.Source> marks, ContractText.Read read) {
        if (read.amount() == null || read.paragraphs() == null) {
            return;
        }
        String digits = read.amount().stripTrailingZeros().toPlainString();
        for (int i = 0; i < read.paragraphs().size(); i++) {
            String folded = read.paragraphs().get(i).replace(" ", "").replace("\u00A0", "");
            if (folded.contains(digits)) {
                marks.put("amount", new Draft.Source(i, null));
                return;
            }
        }
    }

    private static void mark(Map<String, Draft.Source> marks, List<String> paragraphs, String key, String snippet) {
        if (snippet == null || paragraphs == null) {
            return;
        }
        for (int i = 0; i < paragraphs.size(); i++) {
            if (paragraphs.get(i).contains(snippet)) {
                marks.put(key, new Draft.Source(i, null));
                return;
            }
        }
    }

    private static LocalDate date(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (Exception exception) {
            return null;
        }
    }

    private static String blank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    record ConfirmBody(
            String number,
            BigDecimal amount,
            String date,
            String subject,
            BigDecimal vat,
            Long clientId,
            Long leadId,
            Party client,
            Place object,
            List<Row> schedule
    ) {
        record Party(String name, String inn, String kpp, String ogrn, String address, String signatory) {
        }

        record Place(String address) {
        }

        record Row(String title, BigDecimal amount, LocalDate due, String kind, DueRule dueRule) {
        }

        record DueRule(String basis, Integer businessDays) {
        }
    }

    record PayBody(Long scheduleId, BigDecimal amount, LocalDate date, String method) {
    }

    record Draft(
            Long id,
            String number,
            String date,
            String subject,
            BigDecimal amount,
            BigDecimal vat,
            boolean scan,
            List<String> paragraphs,
            Long clientId,
            Party client,
            Place object,
            List<Step> schedule,
            Compare compare,
            Map<String, String> confidence,
            Map<String, Source> source
    ) {
        record Party(String name, String inn, String kpp, String ogrn, String address, String signatory) {
        }

        record Place(String address) {
        }

        record Step(Long id, String title, BigDecimal amount, LocalDate due, String kind, Rule dueRule) {
        }

        record Rule(String basis, Integer businessDays) {
        }

        record Compare(MoneyChange amount, TextChange schedule) {
        }

        record MoneyChange(BigDecimal was, BigDecimal now) {
        }

        record TextChange(String was, String now) {
        }

        record Source(Integer paragraph, Integer page) {
        }
    }
}
