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
import java.util.Locale;
import java.util.Map;

@RestController
@Transactional
class ContractController {

    private final ContractRepository contracts;
    private final ScheduleRepository schedule;
    private final CrmProperties properties;

    ContractController(ContractRepository contracts, ScheduleRepository schedule, CrmProperties properties) {
        this.contracts = contracts;
        this.schedule = schedule;
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
                .filter(item -> client == null || client.isBlank() || item.getClientName().equals(client))
                .filter(item -> object == null || object.isBlank() || object.equals(item.getObjectAddress()))
                .toList();
        Map<String, List<ContractEntity>> grouped = new LinkedHashMap<>();
        for (ContractEntity row : rows) {
            grouped.computeIfAbsent(row.getClientName(), key -> new ArrayList<>()).add(row);
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
                                contract.getClientName(),
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
                items.add(new FinanceView.Item(contract.getId(), contract.getNumber(), contract.getObjectAddress(), steps));
            }
            BigDecimal due = amount.subtract(paid);
            receivable = receivable.add(due.max(BigDecimal.ZERO));
            clients.add(new FinanceView.ClientRow(
                    entry.getKey(),
                    entry.getKey(),
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
        List<FinanceView.Week> weekRows = weeks.entrySet().stream()
                .map(item -> new FinanceView.Week(item.getKey(), item.getValue()))
                .toList();
        return new FinanceView(
                new FinanceView.Totals(receivable, null, null, received, null, null),
                clients,
                attention,
                weekRows
        );
    }

    @PostMapping(value = "/api/contracts", consumes = "multipart/form-data")
    ResponseEntity<?> upload(@RequestParam(value = "file", required = false) MultipartFile file) throws IOException {
        if (file == null || file.isEmpty() || file.getOriginalFilename() == null
                || !file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".docx")) {
            return ResponseEntity.badRequest().body(new ApiError("contract"));
        }
        ContractText.Read read = ContractText.read(file.getBytes());
        if (read == null) {
            return ResponseEntity.badRequest().body(new ApiError("contract"));
        }
        if (contracts.findByNumber(read.number()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError("duplicate"));
        }
        ContractEntity contract = new ContractEntity(Instant.now(), read.number(), file.getOriginalFilename());
        contract.fill(read.number(), read.client(), read.inn(), read.objectAddress(), read.amount(), false);
        contract.replaceSchedule(rows(contract, read.schedule()));
        ContractEntity saved = contracts.save(contract);
        Files.createDirectories(Path.of(properties.contractDir()));
        Files.write(Path.of(properties.contractDir(), String.valueOf(saved.getId())), file.getBytes());
        return ResponseEntity.status(HttpStatus.CREATED).body(draft(saved));
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
        return ResponseEntity.status(HttpStatus.CREATED).body(draft(contracts.save(contract)));
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
        return ResponseEntity.ok(draft(contracts.save(contract)));
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
        return ResponseEntity.ok(draft(found.get()));
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

    private static void apply(ContractEntity contract, ConfirmBody body, boolean confirmed) {
        contract.fill(
                body.number().trim(),
                body.client().name().trim(),
                body.client().inn() == null || body.client().inn().isBlank() ? null : body.client().inn().trim(),
                body.object() == null || body.object().address() == null || body.object().address().isBlank()
                        ? null
                        : body.object().address().trim(),
                body.amount(),
                confirmed
        );
        List<ScheduleEntity> rows = new ArrayList<>();
        List<ConfirmBody.Row> source = body.schedule() == null ? List.of() : body.schedule();
        for (int i = 0; i < source.size(); i++) {
            ConfirmBody.Row row = source.get(i);
            if (row.amount() == null) {
                continue;
            }
            String kind = row.kind() == null || row.kind().isBlank() ? "stage" : row.kind();
            rows.add(new ScheduleEntity(contract, row.title() == null || row.title().isBlank() ? "Платёж" : row.title().trim(), row.amount(), row.due(), kind, i));
        }
        contract.replaceSchedule(rows);
    }

    private static List<ScheduleEntity> rows(ContractEntity contract, List<ContractText.Row> source) {
        List<ScheduleEntity> rows = new ArrayList<>();
        for (int i = 0; i < source.size(); i++) {
            ContractText.Row row = source.get(i);
            rows.add(new ScheduleEntity(contract, row.title(), row.amount(), null, row.kind(), i));
        }
        return rows;
    }

    private static Draft draft(ContractEntity contract) {
        return new Draft(
                contract.getId(),
                contract.getNumber(),
                contract.getAmount(),
                new Draft.Party(contract.getClientName(), contract.getClientInn()),
                new Draft.Place(contract.getObjectAddress()),
                contract.getSchedule().stream()
                        .map(row -> new Draft.Step(row.getId(), row.getTitle(), row.getAmount(), row.getDueOn(), row.getKind()))
                        .toList()
        );
    }

    record ConfirmBody(String number, BigDecimal amount, Party client, Place object, List<Row> schedule) {
        record Party(String name, String inn) {
        }

        record Place(String address) {
        }

        record Row(String title, BigDecimal amount, LocalDate due, String kind) {
        }
    }

    record PayBody(Long scheduleId, BigDecimal amount, LocalDate date, String method) {
    }

    record Draft(Long id, String number, BigDecimal amount, Party client, Place object, List<Step> schedule) {
        record Party(String name, String inn) {
        }

        record Place(String address) {
        }

        record Step(Long id, String title, BigDecimal amount, LocalDate due, String kind) {
        }
    }
}
