package ru.mehanikakomforta.finance;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ru.mehanikakomforta.CrmProperties;
import ru.mehanikakomforta.clients.ClientAccess;
import ru.mehanikakomforta.leads.ApiError;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Set;

@RestController
class ExpenseController {

    private static final Set<String> KINDS = Set.of("object", "overhead", "personal", "credit");
    private static final Set<String> OBJECT_CATEGORIES = Set.of("materials", "works", "labor", "transport", "rent");
    private static final Set<String> OVERHEAD_CATEGORIES = Set.of("office", "ads", "software", "taxes", "salary_office", "other");

    private final ExpenseRepository expenses;
    private final CrmProperties properties;

    ExpenseController(ExpenseRepository expenses, CrmProperties properties) {
        this.expenses = expenses;
        this.properties = properties;
    }

    @PostMapping(value = "/api/expenses", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<?> create(
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String amount,
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String object,
            @RequestParam(required = false) String supplier,
            @RequestParam(required = false) String principal,
            @RequestParam(required = false) String interest,
            @RequestParam(required = false) String owed,
            @RequestParam(required = false) String confirmed,
            @RequestParam(value = "file", required = false) MultipartFile file
    ) throws IOException {
        if (!ClientAccess.canConvert()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError("role"));
        }
        String problem = problem(date, kind, category, object);
        if (problem != null) {
            return ResponseEntity.badRequest().body(new ApiError(problem));
        }
        ExpenseEntity expense = new ExpenseEntity(LocalDate.parse(date), kind, category, ClientAccess.actor());
        fill(expense, date, amount, kind, category, object, supplier, principal, interest, owed, confirmed);
        ExpenseEntity saved = expenses.save(expense);
        store(saved, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(view(saved));
    }

    @PatchMapping(value = "/api/expenses/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<?> change(
            @PathVariable long id,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String amount,
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String object,
            @RequestParam(required = false) String supplier,
            @RequestParam(required = false) String principal,
            @RequestParam(required = false) String interest,
            @RequestParam(required = false) String owed,
            @RequestParam(required = false) String confirmed,
            @RequestParam(value = "file", required = false) MultipartFile file
    ) throws IOException {
        if (!ClientAccess.canConvert()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError("role"));
        }
        ExpenseEntity expense = expenses.findById(id).orElse(null);
        if (expense == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("id"));
        }
        String problem = problem(date, kind, category, object);
        if (problem != null) {
            return ResponseEntity.badRequest().body(new ApiError(problem));
        }
        fill(expense, date, amount, kind, category, object, supplier, principal, interest, owed, confirmed);
        expenses.save(expense);
        store(expense, file);
        return ResponseEntity.ok(view(expense));
    }

    private static void fill(ExpenseEntity expense, String date, String amount, String kind, String category, String object, String supplier,
                             String principal, String interest, String owed, String confirmed) {
        expense.fill(
                LocalDate.parse(date),
                money(amount),
                kind,
                category,
                blank(object),
                blank(supplier),
                money(principal),
                money(interest),
                money(owed),
                "true".equals(confirmed)
        );
    }

    private void store(ExpenseEntity expense, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return;
        }
        String name = file.getOriginalFilename() == null ? "receipt" : Path.of(file.getOriginalFilename()).getFileName().toString();
        Path root = Path.of(properties.contractDir());
        Path parent = root.getParent() == null ? root : root.getParent();
        Path dir = parent.resolve("expense-receipts");
        Files.createDirectories(dir);
        Files.write(dir.resolve(String.valueOf(expense.getId())), file.getBytes());
        expense.file(name);
        expenses.save(expense);
    }

    private static String problem(String date, String kind, String category, String object) {
        if (date == null || date.isBlank()) {
            return "date";
        }
        try {
            LocalDate.parse(date);
        } catch (Exception exception) {
            return "date";
        }
        if (kind == null || !KINDS.contains(kind)) {
            return "kind";
        }
        if ("object".equals(kind) && (object == null || object.isBlank())) {
            return "object";
        }
        if ("object".equals(kind) && !OBJECT_CATEGORIES.contains(category)) {
            return "category";
        }
        if ("overhead".equals(kind) && !OVERHEAD_CATEGORIES.contains(category)) {
            return "category";
        }
        if ("personal".equals(kind) && !"other".equals(category)) {
            return "category";
        }
        if ("credit".equals(kind) && !"interest".equals(category)) {
            return "category";
        }
        return null;
    }

    private static FinanceView.ExpenseRow view(ExpenseEntity expense) {
        return new FinanceView.ExpenseRow(
                expense.getId(),
                expense.getSpentOn(),
                expense.getAmount(),
                expense.getKind(),
                expense.getCategory(),
                expense.getObjectName(),
                expense.getSupplier(),
                expense.getPrincipal(),
                expense.getInterest(),
                expense.getOwed(),
                expense.isConfirmed()
        );
    }

    private static BigDecimal money(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return new BigDecimal(value.trim().replace(',', '.'));
    }

    private static String blank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
