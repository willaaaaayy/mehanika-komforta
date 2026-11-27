package ru.mehanikakomforta.finance;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class FinanceService {

    private final ExpenseRepository expenses;

    public FinanceService(ExpenseRepository expenses) {
        this.expenses = expenses;
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
        return new Result(spent, profit, payable, outs, months, cascade, listed);
    }

    public Share share(Long clientId, List<ContractEntity> contracts) {
        Result result = assess(contracts, null, null, "cash", String.valueOf(clientId), null);
        return new Share(result.spent(), result.profit(), result.payable());
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

    private static boolean matches(ExpenseEntity expense, List<ContractEntity> contracts, String client, String object) {
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

    public record Result(
            BigDecimal spent,
            BigDecimal profit,
            BigDecimal payable,
            Map<String, BigDecimal> outs,
            List<FinanceView.Month> months,
            List<FinanceView.ProfitRow> cascade,
            List<FinanceView.ExpenseRow> expenses
    ) {
    }

    public record Share(BigDecimal spent, BigDecimal profit, BigDecimal payable) {
    }
}
