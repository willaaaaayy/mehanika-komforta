package ru.mehanikakomforta.finance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "salary_months")
class SalaryMonthEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "month_start", nullable = false)
    private LocalDate monthStart;

    @Column(precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "expense_id")
    private Long expenseId;

    protected SalaryMonthEntity() {
    }

    SalaryMonthEntity(Long employeeId, LocalDate monthStart, BigDecimal amount) {
        this.employeeId = employeeId;
        this.monthStart = monthStart;
        this.amount = amount;
    }

    Long getEmployeeId() {
        return employeeId;
    }

    LocalDate getMonthStart() {
        return monthStart;
    }

    BigDecimal getAmount() {
        return amount;
    }

    Long getExpenseId() {
        return expenseId;
    }

    void expense(Long expenseId) {
        this.expenseId = expenseId;
    }
}
