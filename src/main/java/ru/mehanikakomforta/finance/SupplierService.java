package ru.mehanikakomforta.finance;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mehanikakomforta.CrmProperties;
import ru.mehanikakomforta.clients.ClientAccess;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class SupplierService {

    private final SupplierRepository suppliers;
    private final SupplierDocumentRepository documents;
    private final SupplierLineRepository lines;
    private final SupplierPaymentRepository payments;
    private final ExpenseRepository expenses;
    private final CrmProperties properties;

    public SupplierService(
            SupplierRepository suppliers,
            SupplierDocumentRepository documents,
            SupplierLineRepository lines,
            SupplierPaymentRepository payments,
            ExpenseRepository expenses,
            CrmProperties properties
    ) {
        this.suppliers = suppliers;
        this.documents = documents;
        this.lines = lines;
        this.payments = payments;
        this.expenses = expenses;
        this.properties = properties;
    }

    public List<Card> list() {
        List<Card> body = new ArrayList<>();
        for (SupplierEntity supplier : suppliers.findAllByOrderByNameAsc()) {
            body.add(card(supplier, false));
        }
        return body;
    }

    public Card one(long id) {
        return suppliers.findById(id).map(supplier -> card(supplier, true)).orElse(null);
    }

    @Transactional
    public Card save(String name, String inn, String phone) {
        return card(suppliers.save(new SupplierEntity(name, inn, phone)), true);
    }

    @Transactional
    public Card update(long id, String name, String inn, String phone, String address, String contract) {
        SupplierEntity supplier = suppliers.findById(id).orElse(null);
        if (supplier == null) {
            return null;
        }
        supplier.change(name, inn, phone, address, contract);
        return card(suppliers.save(supplier), true);
    }

    public List<LooseLine> unassigned() {
        List<LooseLine> body = new ArrayList<>();
        for (SupplierLineEntity line : lines.findAll()) {
            if (line.getObjectName() != null) {
                continue;
            }
            SupplierDocumentEntity document = documents.findById(line.getDocumentId()).orElse(null);
            if (document == null || document.isCancelled()) {
                continue;
            }
            SupplierEntity supplier = suppliers.findById(document.getSupplierId()).orElse(null);
            body.add(new LooseLine(line.getId(), line.getTitle(), line.getAmount(), supplier == null ? null : supplier.getName()));
        }
        return body;
    }

    @Transactional
    public Card addDocument(long supplierId, String kind, String number, LocalDate date, List<IncomingLine> incoming, String fileName, byte[] file) throws java.io.IOException {
        SupplierEntity supplier = suppliers.findById(supplierId).orElse(null);
        if (supplier == null || date == null || incoming == null || incoming.stream().noneMatch(item -> item.amount() != null && item.amount().signum() > 0)) {
            return null;
        }
        SupplierDocumentEntity document = documents.save(new SupplierDocumentEntity(supplierId, kind, number, date));
        if (file != null && file.length > 0) {
            Path root = Path.of(properties.contractDir());
            Path parent = root.getParent() == null ? root : root.getParent();
            Path dir = parent.resolve("supplier-docs");
            Files.createDirectories(dir);
            Files.write(dir.resolve(String.valueOf(document.getId())), file);
            document.file(fileName == null ? "document" : fileName);
            documents.save(document);
        }
        for (IncomingLine item : incoming) {
            if (item.amount() == null || item.amount().signum() <= 0) {
                continue;
            }
            String title = item.title() == null || item.title().isBlank() ? "Позиция" : item.title().trim();
            SupplierLineEntity line = lines.save(new SupplierLineEntity(document.getId(), title, item.amount(), blank(item.object())));
            ExpenseEntity expense = expense(supplier, document, line);
            line.expense(expense.getId());
            lines.save(line);
        }
        if (lines.findByDocumentIdOrderByIdAsc(document.getId()).isEmpty()) {
            return null;
        }
        settle(supplier);
        return card(supplier, true);
    }

    @Transactional
    public boolean assign(long lineId, String objectName) {
        SupplierLineEntity line = lines.findById(lineId).orElse(null);
        if (line == null || objectName == null || objectName.isBlank()) {
            return false;
        }
        SupplierDocumentEntity document = documents.findById(line.getDocumentId()).orElse(null);
        if (document == null || document.isCancelled()) {
            return false;
        }
        line.object(objectName.trim());
        lines.save(line);
        suppliers.findById(document.getSupplierId()).ifPresent(this::settle);
        return true;
    }

    @Transactional
    public Card pay(long supplierId, BigDecimal amount, LocalDate date) {
        SupplierEntity supplier = suppliers.findById(supplierId).orElse(null);
        if (supplier == null || amount == null || amount.signum() <= 0 || date == null) {
            return null;
        }
        Totals totals = totals(supplier);
        if (totals.debt() == null || amount.compareTo(totals.debt()) > 0) {
            return null;
        }
        payments.save(new SupplierPaymentEntity(supplierId, date, amount));
        settle(supplier);
        return card(supplier, true);
    }

    @Transactional
    public Card cancel(long supplierId, long documentId, String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        SupplierDocumentEntity document = documents.findById(documentId).orElse(null);
        if (document == null || !document.getSupplierId().equals(supplierId) || document.isCancelled()) {
            return null;
        }
        document.cancel(reason.trim());
        documents.save(document);
        SupplierEntity supplier = suppliers.findById(supplierId).orElse(null);
        if (supplier == null) {
            return null;
        }
        settle(supplier);
        return card(supplier, true);
    }

    public List<MaterialLine> materials(String address) {
        if (address == null || address.isBlank()) {
            return List.of();
        }
        List<MaterialLine> body = new ArrayList<>();
        for (SupplierLineEntity line : lines.findByObjectName(address)) {
            SupplierDocumentEntity document = documents.findById(line.getDocumentId()).orElse(null);
            if (document == null || document.isCancelled()) {
                continue;
            }
            SupplierEntity supplier = suppliers.findById(document.getSupplierId()).orElse(null);
            body.add(new MaterialLine(line.getTitle(), line.getAmount(), supplier == null ? null : supplier.getName()));
        }
        return body;
    }

    public Statement statement(long supplierId, LocalDate from, LocalDate to) {
        SupplierEntity supplier = suppliers.findById(supplierId).orElse(null);
        if (supplier == null) {
            return null;
        }
        List<String> rows = new ArrayList<>();
        BigDecimal purchased = null;
        BigDecimal paid = null;
        for (SupplierDocumentEntity document : documents.findBySupplierIdOrderByIssuedOnAscIdAsc(supplierId)) {
            if (document.isCancelled() || !inPeriod(document.getIssuedOn(), from, to)) {
                continue;
            }
            for (SupplierLineEntity line : lines.findByDocumentIdOrderByIdAsc(document.getId())) {
                purchased = add(purchased, line.getAmount());
                rows.add(document.getIssuedOn() + " " + (line.getTitle() == null ? "Позиция" : line.getTitle()) + " " + line.getAmount().toPlainString());
            }
        }
        for (SupplierPaymentEntity payment : payments.findBySupplierId(supplierId)) {
            if (!inPeriod(payment.getPaidOn(), from, to)) {
                continue;
            }
            paid = add(paid, payment.getAmount());
            rows.add(payment.getPaidOn() + " Оплата " + payment.getAmount().toPlainString());
        }
        BigDecimal debt = purchased == null ? null : purchased.subtract(paid == null ? BigDecimal.ZERO : paid);
        return new Statement(supplier.getName(), purchased, paid, debt, rows);
    }

    private ExpenseEntity expense(SupplierEntity supplier, SupplierDocumentEntity document, SupplierLineEntity line) {
        ExpenseEntity expense = new ExpenseEntity(document.getIssuedOn(), "object", "materials", ClientAccess.actor());
        BigDecimal amount = line.getObjectName() == null ? null : line.getAmount();
        expense.fill(document.getIssuedOn(), amount, "object", "materials", line.getObjectName(), supplier.getName(), null, null, line.getAmount(), true);
        return expenses.save(expense);
    }

    private void settle(SupplierEntity supplier) {
        BigDecimal left = paid(supplier) == null ? BigDecimal.ZERO : paid(supplier);
        for (Open line : openLines(supplier)) {
            BigDecimal owed;
            BigDecimal cost;
            if (line.document().isCancelled()) {
                owed = null;
                cost = null;
            } else {
                BigDecimal cover = line.line().getAmount().min(left);
                left = left.subtract(cover);
                owed = line.line().getAmount().subtract(cover);
                cost = line.line().getObjectName() == null ? null : line.line().getAmount();
            }
            if (line.line().getExpenseId() == null) {
                continue;
            }
            BigDecimal remaining = owed;
            expenses.findById(line.line().getExpenseId()).ifPresent(expense -> {
                expense.fill(line.document().getIssuedOn(), cost, "object", "materials", line.line().getObjectName(), supplier.getName(), null, null, remaining, true);
                expenses.save(expense);
            });
        }
    }

    private Card card(SupplierEntity supplier, boolean withDocuments) {
        Totals totals = totals(supplier);
        List<DocumentView> docs = withDocuments ? documents(supplier) : List.of();
        return new Card(
                supplier.getId(),
                supplier.getName(),
                supplier.getInn(),
                supplier.getPhone(),
                supplier.getAddress(),
                supplier.getContractName(),
                totals.purchased(),
                totals.paid(),
                totals.debt(),
                totals.nextDue(),
                totals.overdue(),
                docs
        );
    }

    private List<DocumentView> documents(SupplierEntity supplier) {
        List<DocumentView> body = new ArrayList<>();
        for (SupplierDocumentEntity document : documents.findBySupplierIdOrderByIssuedOnAscIdAsc(supplier.getId())) {
            List<LineView> items = new ArrayList<>();
            for (SupplierLineEntity line : lines.findByDocumentIdOrderByIdAsc(document.getId())) {
                items.add(new LineView(line.getId(), line.getTitle(), line.getAmount(), line.getObjectName()));
            }
            body.add(new DocumentView(document.getId(), document.getKind(), document.getNumber(), document.getIssuedOn().toString(), document.isCancelled(), items));
        }
        return body;
    }

    private Totals totals(SupplierEntity supplier) {
        BigDecimal purchased = null;
        BigDecimal overdue = null;
        LocalDate next = null;
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Europe/Saratov"));
        BigDecimal left = paid(supplier) == null ? BigDecimal.ZERO : paid(supplier);
        for (Open line : openLines(supplier)) {
            if (line.document().isCancelled()) {
                continue;
            }
            purchased = add(purchased, line.line().getAmount());
            BigDecimal cover = line.line().getAmount().min(left);
            left = left.subtract(cover);
            BigDecimal owed = line.line().getAmount().subtract(cover);
            if (owed.signum() > 0) {
                if (next == null || line.document().getIssuedOn().isBefore(next)) {
                    next = line.document().getIssuedOn();
                }
                if (line.document().getIssuedOn().isBefore(today)) {
                    overdue = add(overdue, owed);
                }
            }
        }
        BigDecimal paid = paid(supplier);
        BigDecimal debt = purchased == null ? null : purchased.subtract(paid == null ? BigDecimal.ZERO : paid);
        return new Totals(purchased, paid == null || paid.signum() == 0 ? null : paid, debt, next == null ? null : next.toString(), overdue);
    }

    private BigDecimal paid(SupplierEntity supplier) {
        BigDecimal total = null;
        for (SupplierPaymentEntity payment : payments.findBySupplierId(supplier.getId())) {
            total = add(total, payment.getAmount());
        }
        return total;
    }

    private List<Open> openLines(SupplierEntity supplier) {
        List<Open> body = new ArrayList<>();
        for (SupplierDocumentEntity document : documents.findBySupplierIdOrderByIssuedOnAscIdAsc(supplier.getId())) {
            for (SupplierLineEntity line : lines.findByDocumentIdOrderByIdAsc(document.getId())) {
                body.add(new Open(document, line));
            }
        }
        body.sort(Comparator.comparing((Open item) -> item.document().getIssuedOn()).thenComparing(item -> item.line().getId()));
        return body;
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

    private static BigDecimal add(BigDecimal left, BigDecimal right) {
        if (right == null) {
            return left;
        }
        return left == null ? right : left.add(right);
    }

    private static String blank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public record IncomingLine(String title, BigDecimal amount, String object) {
    }

    public record Card(
            Long id,
            String name,
            String inn,
            String phone,
            String address,
            String contract,
            BigDecimal purchased,
            BigDecimal paid,
            BigDecimal debt,
            String nextDue,
            BigDecimal overdue,
            List<DocumentView> documents
    ) {
    }

    public record DocumentView(Long id, String kind, String number, String date, boolean cancelled, List<LineView> lines) {
    }

    public record LineView(Long id, String title, BigDecimal amount, String object) {
    }

    public record LooseLine(Long id, String title, BigDecimal amount, String supplier) {
    }

    public record MaterialLine(String title, BigDecimal amount, String supplier) {
    }

    public record Statement(String name, BigDecimal purchased, BigDecimal paid, BigDecimal debt, List<String> rows) {
    }

    private record Totals(BigDecimal purchased, BigDecimal paid, BigDecimal debt, String nextDue, BigDecimal overdue) {
    }

    private record Open(SupplierDocumentEntity document, SupplierLineEntity line) {
    }
}
