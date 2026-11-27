package ru.mehanikakomforta.finance;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class FinanceService {

    private static final Set<String> FINISHED = Set.of("done", "warranty", "сдан", "гарантия");

    private final ExpenseRepository expenses;
    private final WorkObjectRepository objects;

    public FinanceService(ExpenseRepository expenses, WorkObjectRepository objects) {
        this.expenses = expenses;
        this.objects = objects;
    }

    public Result assess(List<ContractEntity> contracts, LocalDate from, LocalDate to, String mode, String client, String object) {
        boolean cash = mode == null || mode.isBlank() || "cash".equals(mode);
        List<ExpenseEntity> rows = expenses.findAllByOrderBySpentOnDesc().stream()
                .filter(item -> inPeriod(item.getSpentOn(), from, to))
                .filter(item -> matches(item, contracts, client, object))
                .toList();
        BigDecimal direct = null;
        BigDecimal overhead = null;
        BigDecimal interest = null;
        BigDecimal personal = null;
        BigDecimal payable = null;
        Map<String, BigDecimal> outs = new LinkedHashMap<>();
        Map<String, BigDecimal> monthCosts = new LinkedHashMap<>();
        List<FinanceView.ProfitLine> directLines = new ArrayList<>();
        List<FinanceView.ProfitLine> overheadLines = new ArrayList<>();
        List<FinanceView.ProfitLine> interestLines = new ArrayList<>();
        List<FinanceView.ExpenseRow> listed = new ArrayList<>();
        for (ExpenseEntity expense : rows) {
            BigDecimal cost = costOf(expense);
            if ("personal".equals(expense.getKind())) {
                personal = add(personal, expense.getAmount());
            } else if (cost != null) {
                if ("overhead".equals(expense.getKind())) {
                    overhead = add(overhead, cost);
                    overheadLines.add(line(expense, cost));
                } else if ("credit".equals(expense.getKind())) {
                    interest = add(interest, cost);
                    interestLines.add(line(expense, cost));
                } else {
                    direct = add(direct, cost);
                    directLines.add(line(expense, cost));
                }
                String week = week(expense.getSpentOn());
                outs.merge(week, cost, BigDecimal::add);
                monthCosts.merge(expense.getSpentOn().toString().substring(0, 7), cost, BigDecimal::add);
            }
            if (expense.getOwed() != null) {
                payable = add(payable, expense.getOwed());
            }
            listed.add(new FinanceView.ExpenseRow(
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
            ));
        }
        BigDecimal revenue = cash ? revenue(contracts, from, to) : null;
        BigDecimal spent = sum(direct, overhead, interest);
        BigDecimal profit = null;
        if (cash && (revenue != null || spent != null)) {
            profit = nz(revenue).subtract(nz(spent));
        } else if (!cash && revenue != null) {
            profit = revenue.subtract(nz(spent));
        }
        Map<String, BigDecimal> monthRevenue = cash ? monthlyRevenue(contracts, from, to) : Map.of();
        List<FinanceView.Month> months = cash ? months(monthRevenue, monthCosts) : List.of();
        List<FinanceView.ProfitRow> cascade = cascade(revenue, direct, directLines, overhead, overheadLines, interest, interestLines, personal, profit);
        Capital capital = capital(contracts, from, to, client, object, profit, months);
        return new Result(spent, profit, payable, capital.invested(), capital.profitability(), outs, months, cascade, listed, capital.rows(), capital.months());
    }

    public Share share(Long clientId, List<ContractEntity> contracts) {
        Result result = assess(contracts, null, null, "cash", String.valueOf(clientId), null);
        return new Share(result.spent(), result.profit(), result.payable(), result.invested(), result.profitability());
    }

    private static BigDecimal costOf(ExpenseEntity expense) {
        if ("personal".equals(expense.getKind())) {
            return null;
        }
        if ("credit".equals(expense.getKind())) {
            return expense.getInterest();
        }
        return expense.getAmount();
    }

    private boolean matches(ExpenseEntity expense, List<ContractEntity> contracts, String client, String object) {
        if (object != null && !object.isBlank() && !object.equals(expense.getObjectName())) {
            return false;
        }
        if (client == null || client.isBlank()) {
            return true;
        }
        if (!"object".equals(expense.getKind())) {
            return false;
        }
        for (ContractEntity contract : contracts) {
            if (expense.getObjectName() != null && expense.getObjectName().equals(contract.getObjectAddress())) {
                return true;
            }
        }
        for (WorkObjectEntity site : objects.findAll()) {
            if (String.valueOf(site.getClientId()).equals(client) && site.getAddress() != null && site.getAddress().equals(expense.getObjectName())) {
                return true;
            }
        }
        return false;
    }

    private static BigDecimal revenue(List<ContractEntity> contracts, LocalDate from, LocalDate to) {
        BigDecimal total = null;
        for (ContractEntity contract : contracts) {
            for (ScheduleEntity step : contract.getSchedule()) {
                if (step.getPaidOn() != null && inPeriod(step.getPaidOn(), from, to)) {
                    total = add(total, step.getPaidAmount() == null ? step.getAmount() : step.getPaidAmount());
                }
            }
        }
        return total;
    }

    private static Map<String, BigDecimal> monthlyRevenue(List<ContractEntity> contracts, LocalDate from, LocalDate to) {
        Map<String, BigDecimal> months = new LinkedHashMap<>();
        for (ContractEntity contract : contracts) {
            for (ScheduleEntity step : contract.getSchedule()) {
                if (step.getPaidOn() != null && inPeriod(step.getPaidOn(), from, to)) {
                    months.merge(step.getPaidOn().toString().substring(0, 7),
                            step.getPaidAmount() == null ? step.getAmount() : step.getPaidAmount(), BigDecimal::add);
                }
            }
        }
        return months;
    }

    private static List<FinanceView.Month> months(Map<String, BigDecimal> revenue, Map<String, BigDecimal> costs) {
        List<String> keys = new ArrayList<>();
        keys.addAll(revenue.keySet());
        costs.keySet().stream().filter(key -> !keys.contains(key)).forEach(keys::add);
        keys.sort(Comparator.naturalOrder());
        List<FinanceView.Month> months = new ArrayList<>();
        for (String key : keys) {
            BigDecimal in = revenue.get(key);
            BigDecimal out = costs.get(key);
            if (in == null && out == null) {
                continue;
            }
            months.add(new FinanceView.Month(key, nz(in).subtract(nz(out))));
        }
        return months;
    }

    private static List<FinanceView.ProfitRow> cascade(
            BigDecimal revenue,
            BigDecimal direct,
            List<FinanceView.ProfitLine> directLines,
            BigDecimal overhead,
            List<FinanceView.ProfitLine> overheadLines,
            BigDecimal interest,
            List<FinanceView.ProfitLine> interestLines,
            BigDecimal personal,
            BigDecimal profit
    ) {
        List<FinanceView.ProfitRow> rows = new ArrayList<>();
        addRow(rows, "Выручка", revenue, List.of());
        addRow(rows, "Прямые расходы", direct, directLines);
        addRow(rows, "Общие расходы", overhead, overheadLines);
        addRow(rows, "Проценты", interest, interestLines);
        addRow(rows, "Личные", personal, List.of());
        addRow(rows, "Прибыль", profit, List.of());
        return rows;
    }

    private static void addRow(List<FinanceView.ProfitRow> rows, String title, BigDecimal amount, List<FinanceView.ProfitLine> lines) {
        if (amount == null) {
            return;
        }
        rows.add(new FinanceView.ProfitRow(title, amount, null, lines));
    }

    private static FinanceView.ProfitLine line(ExpenseEntity expense, BigDecimal amount) {
        String title = expense.getCategory();
        if (expense.getObjectName() != null && !expense.getObjectName().isBlank()) {
            title = title + " · " + expense.getObjectName();
        }
        return new FinanceView.ProfitLine(title, amount);
    }

    static String week(LocalDate date) {
        return date.get(WeekFields.ISO.weekBasedYear()) + "-W" + date.get(WeekFields.ISO.weekOfWeekBasedYear());
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

    private static BigDecimal sum(BigDecimal direct, BigDecimal overhead, BigDecimal interest) {
        if (direct == null && overhead == null && interest == null) {
            return null;
        }
        return nz(direct).add(nz(overhead)).add(nz(interest));
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private Capital capital(List<ContractEntity> contracts, LocalDate from, LocalDate to, String client, String objectFilter, BigDecimal profit, List<FinanceView.Month> profitMonths) {
        List<ExpenseEntity> all = expenses.findAllByOrderBySpentOnDesc();
        BigDecimal invested = null;
        BigDecimal average = null;
        List<FinanceView.CapitalRow> rows = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (WorkObjectEntity site : objects.findAll()) {
            if (objectFilter != null && !objectFilter.isBlank() && !objectFilter.equals(site.getAddress())) {
                continue;
            }
            if (!inScope(site, contracts, client)) {
                continue;
            }
            List<Movement> movements = movements(site, contracts, all);
            if (movements.isEmpty()) {
                continue;
            }
            movements.sort(Comparator.comparing(Movement::date));
            BigDecimal balance = BigDecimal.ZERO;
            BigDecimal peak = null;
            LocalDate first = null;
            LocalDate returned = null;
            boolean opened = false;
            for (Movement movement : movements) {
                if (first == null) {
                    first = movement.date();
                }
                balance = balance.add(movement.delta());
                if (balance.signum() > 0) {
                    opened = true;
                    returned = null;
                    if (peak == null || balance.compareTo(peak) > 0) {
                        peak = balance;
                    }
                } else if (opened) {
                    returned = movement.date();
                }
            }
            boolean unfinished = site.getStatus() == null || !FINISHED.contains(site.getStatus());
            BigDecimal current = unfinished && balance.signum() > 0 ? balance : null;
            if (current != null) {
                invested = add(invested, current);
                rows.add(new FinanceView.CapitalRow(
                        site.getId(),
                        site.getAddress(),
                        current,
                        peak,
                        first == null ? null : (int) ChronoUnit.DAYS.between(first, today),
                        null
                ));
            }
            LocalDate start = from != null ? from : first;
            LocalDate end = to != null ? to : today;
            BigDecimal slice = average(movements, start, end);
            if (slice != null) {
                average = add(average, slice);
            }
        }
        BigDecimal profitability = null;
        if (profit != null && average != null && average.signum() > 0) {
            profitability = profit.multiply(BigDecimal.valueOf(100)).divide(average, 1, RoundingMode.HALF_UP);
        }
        List<FinanceView.RateMonth> rateMonths = new ArrayList<>();
        for (FinanceView.Month month : profitMonths) {
            if (month.profit() == null) {
                continue;
            }
            LocalDate start = LocalDate.parse(month.month() + "-01");
            BigDecimal monthAverage = monthAverage(contracts, client, objectFilter, all, start, start.plusMonths(1));
            if (monthAverage == null || monthAverage.signum() <= 0) {
                continue;
            }
            rateMonths.add(new FinanceView.RateMonth(
                    month.month(),
                    month.profit().multiply(BigDecimal.valueOf(100)).divide(monthAverage, 1, RoundingMode.HALF_UP)
            ));
        }
        return new Capital(invested, profitability, rows, rateMonths);
    }

    private BigDecimal monthAverage(List<ContractEntity> contracts, String client, String objectFilter, List<ExpenseEntity> all, LocalDate start, LocalDate end) {
        BigDecimal weighted = BigDecimal.ZERO;
        long days = Math.max(1, ChronoUnit.DAYS.between(start, end));
        boolean any = false;
        for (WorkObjectEntity site : objects.findAll()) {
            if (objectFilter != null && !objectFilter.isBlank() && !objectFilter.equals(site.getAddress())) {
                continue;
            }
            if (!inScope(site, contracts, client)) {
                continue;
            }
            BigDecimal slice = average(movements(site, contracts, all), start, end);
            if (slice != null) {
                any = true;
                weighted = weighted.add(slice);
            }
        }
        return any ? weighted : null;
    }

    private static List<Movement> movements(WorkObjectEntity site, List<ContractEntity> contracts, List<ExpenseEntity> all) {
        List<Movement> movements = new ArrayList<>();
        for (ExpenseEntity expense : all) {
            if (!"object".equals(expense.getKind()) || expense.getAmount() == null || expense.getSpentOn() == null) {
                continue;
            }
            if (site.getAddress() != null && site.getAddress().equals(expense.getObjectName())) {
                movements.add(new Movement(expense.getSpentOn(), expense.getAmount()));
            }
        }
        for (ContractEntity contract : contracts) {
            boolean same = site.getId().equals(contract.getObjectId())
                    || (site.getAddress() != null && site.getAddress().equals(contract.getObjectAddress()));
            if (!same) {
                continue;
            }
            for (ScheduleEntity step : contract.getSchedule()) {
                if (step.getPaidOn() != null) {
                    BigDecimal paid = step.getPaidAmount() == null ? step.getAmount() : step.getPaidAmount();
                    movements.add(new Movement(step.getPaidOn(), paid.negate()));
                }
            }
        }
        return movements;
    }

    private static BigDecimal average(List<Movement> movements, LocalDate start, LocalDate end) {
        if (movements.isEmpty() || start == null || end == null || end.isBefore(start)) {
            return null;
        }
        List<Movement> ordered = new ArrayList<>(movements);
        ordered.sort(Comparator.comparing(Movement::date));
        BigDecimal level = BigDecimal.ZERO;
        for (Movement movement : ordered) {
            if (movement.date().isBefore(start)) {
                level = level.add(movement.delta());
            }
        }
        BigDecimal area = BigDecimal.ZERO;
        LocalDate cursor = start;
        for (Movement movement : ordered) {
            if (movement.date().isBefore(start) || movement.date().isAfter(end)) {
                continue;
            }
            long span = ChronoUnit.DAYS.between(cursor, movement.date());
            if (span > 0 && level.signum() > 0) {
                area = area.add(level.multiply(BigDecimal.valueOf(span)));
            }
            level = level.add(movement.delta());
            cursor = movement.date();
        }
        long tail = ChronoUnit.DAYS.between(cursor, end);
        if (tail > 0 && level.signum() > 0) {
            area = area.add(level.multiply(BigDecimal.valueOf(tail)));
        }
        long total = ChronoUnit.DAYS.between(start, end);
        if (total <= 0) {
            return level.signum() > 0 ? level : null;
        }
        if (area.signum() == 0) {
            return null;
        }
        return area.divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    private static boolean inScope(WorkObjectEntity site, List<ContractEntity> contracts, String client) {
        if (client == null || client.isBlank()) {
            return true;
        }
        if (String.valueOf(site.getClientId()).equals(client)) {
            return true;
        }
        for (ContractEntity contract : contracts) {
            if (site.getId().equals(contract.getObjectId()) || (site.getAddress() != null && site.getAddress().equals(contract.getObjectAddress()))) {
                return true;
            }
        }
        return false;
    }

    public record Result(
            BigDecimal spent,
            BigDecimal profit,
            BigDecimal payable,
            BigDecimal invested,
            BigDecimal profitability,
            Map<String, BigDecimal> outs,
            List<FinanceView.Month> months,
            List<FinanceView.ProfitRow> cascade,
            List<FinanceView.ExpenseRow> expenses,
            List<FinanceView.CapitalRow> capital,
            List<FinanceView.RateMonth> profitabilityMonths
    ) {
    }

    public record Share(BigDecimal spent, BigDecimal profit, BigDecimal payable, BigDecimal invested, BigDecimal profitability) {
    }

    private record Capital(BigDecimal invested, BigDecimal profitability, List<FinanceView.CapitalRow> rows, List<FinanceView.RateMonth> months) {
    }

    private record Movement(LocalDate date, BigDecimal delta) {
    }
}
