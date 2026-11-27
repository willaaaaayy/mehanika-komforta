package ru.mehanikakomforta.finance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

record FinanceView(
        Totals totals,
        List<ClientRow> clients,
        List<Attention> attention,
        List<Week> weeks,
        List<ExpenseRow> expenses,
        List<Month> months,
        List<ProfitRow> profit,
        List<CapitalRow> capital,
        List<RateMonth> profitabilityMonths
) {
    record Totals(
            BigDecimal receivable,
            BigDecimal payable,
            BigDecimal spent,
            BigDecimal received,
            BigDecimal profit,
            BigDecimal invested,
            BigDecimal profitability
    ) {
    }

    record ClientRow(
            String id,
            String name,
            int contracts,
            BigDecimal amount,
            BigDecimal invoiced,
            BigDecimal paid,
            BigDecimal due,
            BigDecimal overdue,
            NextPayment nextPayment,
            List<Item> items
    ) {
    }

    record NextPayment(BigDecimal amount, LocalDate date) {
    }

    record Item(Long contractId, String number, String object, List<Step> schedule, List<WorkLine> works) {
    }

    record WorkLine(String title, BigDecimal clientPrice, BigDecimal cost, BigDecimal margin, boolean teaching) {
    }

    record CostLine(String title, BigDecimal plan, BigDecimal fact, BigDecimal deviation, boolean over) {
    }

    record Step(
            Long id,
            String title,
            BigDecimal amount,
            LocalDate due,
            String kind,
            String status,
            Integer overdueDays
    ) {
    }

    record Attention(String clientId, String title, BigDecimal amount, Integer days) {
    }

    record Week(String week, BigDecimal in, BigDecimal out) {
    }

    record Month(String month, BigDecimal profit) {
    }

    record ProfitRow(String title, BigDecimal amount, BigDecimal share, List<ProfitLine> lines) {
    }

    record ProfitLine(String title, BigDecimal amount) {
    }

    record CapitalRow(Long objectId, String address, BigDecimal invested, BigDecimal peak, Integer ageDays, LocalDate returnDate) {
    }

    record RateMonth(String month, BigDecimal value) {
    }

    record ExpenseRow(
            Long id,
            LocalDate date,
            BigDecimal amount,
            String kind,
            String category,
            String object,
            String supplier,
            BigDecimal principal,
            BigDecimal interest,
            BigDecimal owed,
            Boolean confirmed
    ) {
    }
}
