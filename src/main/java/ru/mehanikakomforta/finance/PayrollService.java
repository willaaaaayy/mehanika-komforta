package ru.mehanikakomforta.finance;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mehanikakomforta.clients.ClientAccess;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PayrollService {

    private static final ZoneId SARATOV = ZoneId.of("Europe/Saratov");

    private final EmployeeRepository employees;
    private final WorkLogRepository logs;
    private final SalaryMonthRepository salaries;
    private final PayrollPaymentRepository payments;
    private final ExpenseRepository expenses;
    private final InternalRates rates;

    public PayrollService(
            EmployeeRepository employees,
            WorkLogRepository logs,
            SalaryMonthRepository salaries,
            PayrollPaymentRepository payments,
            ExpenseRepository expenses,
            InternalRates rates
    ) {
        this.employees = employees;
        this.logs = logs;
        this.salaries = salaries;
        this.payments = payments;
        this.expenses = expenses;
        this.rates = rates;
    }

    public List<EmployeeView> people() {
        List<EmployeeView> body = new ArrayList<>();
        for (EmployeeEntity employee : employees.findAllByOrderByNameAsc()) {
            body.add(view(employee));
        }
        return body;
    }

    @Transactional
    public EmployeeView saveEmployee(String name, String payType, BigDecimal rate) {
        return view(employees.save(new EmployeeEntity(name, payType, rate)));
    }

    @Transactional
    public EmployeeView updateEmployee(long id, String name, String payType, BigDecimal rate) {
        EmployeeEntity employee = employees.findById(id).orElse(null);
        if (employee == null) {
            return null;
        }
        employee.change(name, payType, rate);
        return view(employees.save(employee));
    }

    public List<LogView> logs(LocalDate from, LocalDate to) {
        List<LogView> body = new ArrayList<>();
        for (WorkLogEntity log : logs.findAllByOrderByWorkedOnDesc()) {
            if (!inPeriod(log.getWorkedOn(), from, to)) {
                continue;
            }
            EmployeeEntity employee = employees.findById(log.getEmployeeId()).orElse(null);
            body.add(logView(log, employee));
        }
        return body;
    }

    @Transactional
    public LogView saveLog(long employeeId, LocalDate date, String objectName, String workId, BigDecimal quantity) {
        EmployeeEntity employee = employees.findById(employeeId).orElse(null);
        if (employee == null || date == null) {
            return null;
        }
        Priced priced = price(employee, date, workId, quantity, null);
        if (priced == null) {
            return null;
        }
        WorkLogEntity log = logs.save(new WorkLogEntity(employeeId, date, blank(objectName), blank(workId), quantity, priced.rate(), priced.amount()));
        attach(log, employee, priced.amount(), date, objectName, "labor");
        return logView(logs.save(log), employee);
    }

    @Transactional
    public LogView updateLog(long id, long employeeId, LocalDate date, String objectName, String workId, BigDecimal quantity) {
        WorkLogEntity log = logs.findById(id).orElse(null);
        EmployeeEntity employee = employees.findById(employeeId).orElse(null);
        if (log == null || employee == null || date == null) {
            return null;
        }
        Priced priced = price(employee, date, workId, quantity, log.getRate());
        if (priced == null) {
            return null;
        }
        log.revise(date, blank(objectName), blank(workId), quantity, priced.rate(), priced.amount());
        attach(log, employee, priced.amount(), date, objectName, "labor");
        return logView(logs.save(log), employee);
    }

    @Transactional
    public Sheet sheet(LocalDate from, LocalDate to) {
        ensureSalaries(from, to);
        List<Row> rows = new ArrayList<>();
        for (EmployeeEntity employee : employees.findAllByOrderByNameAsc()) {
            BigDecimal accrued = accrued(employee, from, to);
            BigDecimal advance = null;
            BigDecimal paid = null;
            for (PayrollPaymentEntity payment : payments.findByEmployeeIdOrderByPaidOnAsc(employee.getId())) {
                if (!inPeriod(payment.getPaidOn(), from, to)) {
                    continue;
                }
                if (payment.isAdvance()) {
                    advance = add(advance, payment.getAmount());
                } else {
                    paid = add(paid, payment.getAmount());
                }
            }
            if (accrued == null && advance == null && paid == null) {
                continue;
            }
            BigDecimal due = accrued == null ? null : accrued.subtract(nz(advance)).subtract(nz(paid));
            rows.add(new Row(employee.getId(), employee.getName(), accrued, advance, paid, due));
        }
        return new Sheet(rows);
    }

    @Transactional
    public Row pay(long employeeId, BigDecimal amount, LocalDate date) {
        EmployeeEntity employee = employees.findById(employeeId).orElse(null);
        if (employee == null || amount == null || amount.signum() <= 0 || date == null) {
            return null;
        }
        List<ExpenseEntity> open = openExpenses(employee);
        BigDecimal owedNow = BigDecimal.ZERO;
        for (ExpenseEntity expense : open) {
            if (expense.getOwed() != null) {
                owedNow = owedNow.add(expense.getOwed());
            }
        }
        if (amount.compareTo(owedNow) > 0) {
            return null;
        }
        BigDecimal left = amount;
        for (ExpenseEntity expense : open) {
            BigDecimal owed = expense.getOwed() == null ? BigDecimal.ZERO : expense.getOwed();
            if (owed.signum() <= 0) {
                continue;
            }
            BigDecimal take = owed.min(left);
            expense.cost(expense.getAmount(), owed.subtract(take));
            expenses.save(expense);
            left = left.subtract(take);
            if (left.signum() <= 0) {
                break;
            }
        }
        if (left.signum() > 0) {
            return null;
        }
        boolean stillOwed = open.stream().anyMatch(expense -> expense.getOwed() != null && expense.getOwed().signum() > 0);
        payments.save(new PayrollPaymentEntity(employeeId, date, amount, stillOwed));
        return sheet(date.withDayOfMonth(1), date).rows().stream()
                .filter(row -> employeeId == row.id())
                .findFirst()
                .orElse(new Row(employeeId, employee.getName(), null, stillOwed ? amount : null, stillOwed ? null : amount, null));
    }

    public List<LaborLine> labor(String address) {
        if (address == null || address.isBlank()) {
            return List.of();
        }
        Map<String, BigDecimal> sums = new LinkedHashMap<>();
        for (WorkLogEntity log : logs.findByObjectName(address)) {
            if (log.getAmount() == null) {
                continue;
            }
            EmployeeEntity employee = employees.findById(log.getEmployeeId()).orElse(null);
            if (employee == null || "office".equals(employee.getPayType())) {
                continue;
            }
            sums.merge(employee.getName(), log.getAmount(), BigDecimal::add);
        }
        List<LaborLine> lines = new ArrayList<>();
        sums.forEach((name, amount) -> lines.add(new LaborLine(name, amount)));
        return lines;
    }

    private void ensureSalaries(LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(SARATOV);
        LocalDate start = (from == null ? today : from).withDayOfMonth(1);
        LocalDate end = to == null ? today : to;
        for (EmployeeEntity employee : employees.findAll()) {
            if (!"office".equals(employee.getPayType()) || employee.getRate() == null) {
                continue;
            }
            LocalDate month = start;
            while (!month.isAfter(end)) {
                if (!month.isAfter(today)) {
                    ensureMonth(employee, month);
                }
                month = month.plusMonths(1);
            }
        }
    }

    private void ensureMonth(EmployeeEntity employee, LocalDate month) {
        if (salaries.findByEmployeeIdAndMonthStart(employee.getId(), month).isPresent()) {
            return;
        }
        SalaryMonthEntity row = salaries.save(new SalaryMonthEntity(employee.getId(), month, employee.getRate()));
        ExpenseEntity expense = expenses.save(expense(month, "overhead", "salary_office", null, employee.getName(), employee.getRate()));
        row.expense(expense.getId());
        salaries.save(row);
    }

    private Priced price(EmployeeEntity employee, LocalDate date, String workId, BigDecimal quantity, BigDecimal storedRate) {
        if ("office".equals(employee.getPayType())) {
            return new Priced(employee.getRate(), null);
        }
        if (quantity == null) {
            return null;
        }
        if ("daily".equals(employee.getPayType())) {
            BigDecimal rate = storedRate != null ? storedRate : employee.getRate();
            if (rate == null) {
                return null;
            }
            return new Priced(rate, quantity.multiply(rate).setScale(2, RoundingMode.HALF_UP));
        }
        BigDecimal labor = rates.laborAt(workId, date);
        if (labor == null) {
            return null;
        }
        return new Priced(labor, quantity.multiply(labor).setScale(2, RoundingMode.HALF_UP));
    }

    private void attach(WorkLogEntity log, EmployeeEntity employee, BigDecimal amount, LocalDate date, String objectName, String category) {
        if ("office".equals(employee.getPayType()) || amount == null) {
            if (log.getExpenseId() != null) {
                expenses.findById(log.getExpenseId()).ifPresent(expense -> {
                    expense.cost(null, null);
                    expenses.save(expense);
                });
            }
            return;
        }
        String site = blank(objectName);
        if (log.getExpenseId() == null) {
            ExpenseEntity expense = expenses.save(expense(date, "object", category, site, employee.getName(), amount));
            log.expense(expense.getId());
            return;
        }
        expenses.findById(log.getExpenseId()).ifPresent(expense -> {
            BigDecimal previous = expense.getAmount() == null ? BigDecimal.ZERO : expense.getAmount();
            BigDecimal owed = expense.getOwed() == null ? BigDecimal.ZERO : expense.getOwed();
            BigDecimal settled = previous.subtract(owed);
            BigDecimal left = amount.subtract(settled);
            expense.fill(date, amount, "object", category, site, employee.getName(), null, null, left.signum() < 0 ? BigDecimal.ZERO : left, true);
            expenses.save(expense);
        });
    }

    private ExpenseEntity expense(LocalDate date, String kind, String category, String objectName, String employee, BigDecimal amount) {
        ExpenseEntity expense = new ExpenseEntity(date, kind, category, ClientAccess.actor());
        expense.fill(date, amount, kind, category, objectName, employee, null, null, amount, true);
        return expense;
    }

    private BigDecimal accrued(EmployeeEntity employee, LocalDate from, LocalDate to) {
        BigDecimal total = null;
        if ("office".equals(employee.getPayType())) {
            for (SalaryMonthEntity month : salaries.findByEmployeeId(employee.getId())) {
                if (inPeriod(month.getMonthStart(), from == null ? null : from.withDayOfMonth(1), to)) {
                    total = add(total, month.getAmount());
                }
            }
            return total;
        }
        for (WorkLogEntity log : logs.findByEmployeeId(employee.getId())) {
            if (inPeriod(log.getWorkedOn(), from, to)) {
                total = add(total, log.getAmount());
            }
        }
        return total;
    }

    private List<ExpenseEntity> openExpenses(EmployeeEntity employee) {
        List<ExpenseEntity> open = new ArrayList<>();
        for (WorkLogEntity log : logs.findByEmployeeId(employee.getId())) {
            if (log.getExpenseId() != null) {
                expenses.findById(log.getExpenseId()).ifPresent(open::add);
            }
        }
        for (SalaryMonthEntity month : salaries.findByEmployeeId(employee.getId())) {
            if (month.getExpenseId() != null) {
                expenses.findById(month.getExpenseId()).ifPresent(open::add);
            }
        }
        open.sort(Comparator.comparing(ExpenseEntity::getSpentOn));
        return open;
    }

    private static EmployeeView view(EmployeeEntity employee) {
        return new EmployeeView(employee.getId(), employee.getName(), employee.getPayType(), employee.getRate());
    }

    private static LogView logView(WorkLogEntity log, EmployeeEntity employee) {
        return new LogView(
                log.getId(),
                log.getEmployeeId(),
                employee == null ? null : employee.getName(),
                log.getWorkedOn().toString(),
                log.getObjectName(),
                log.getWorkId(),
                log.getQuantity()
        );
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

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String blank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public record EmployeeView(Long id, String name, String payType, BigDecimal rate) {
    }

    public record LogView(Long id, Long employeeId, String employee, String date, String object, String workId, BigDecimal quantity) {
    }

    public record Row(Long id, String employee, BigDecimal accrued, BigDecimal advance, BigDecimal paid, BigDecimal due) {
    }

    public record Sheet(List<Row> rows) {
    }

    public record LaborLine(String employee, BigDecimal amount) {
    }

    private record Priced(BigDecimal rate, BigDecimal amount) {
    }
}
