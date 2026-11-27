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
@Table(name = "work_logs")
class WorkLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "worked_on", nullable = false)
    private LocalDate workedOn;

    @Column(name = "object_name", length = 500)
    private String objectName;

    @Column(name = "work_id", length = 64)
    private String workId;

    @Column(precision = 14, scale = 3)
    private BigDecimal quantity;

    @Column(precision = 14, scale = 2)
    private BigDecimal rate;

    @Column(precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "expense_id")
    private Long expenseId;

    protected WorkLogEntity() {
    }

    WorkLogEntity(Long employeeId, LocalDate workedOn, String objectName, String workId, BigDecimal quantity, BigDecimal rate, BigDecimal amount) {
        this.employeeId = employeeId;
        this.workedOn = workedOn;
        this.objectName = objectName;
        this.workId = workId;
        this.quantity = quantity;
        this.rate = rate;
        this.amount = amount;
    }

    Long getId() {
        return id;
    }

    Long getEmployeeId() {
        return employeeId;
    }

    LocalDate getWorkedOn() {
        return workedOn;
    }

    String getObjectName() {
        return objectName;
    }

    String getWorkId() {
        return workId;
    }

    BigDecimal getQuantity() {
        return quantity;
    }

    BigDecimal getRate() {
        return rate;
    }

    BigDecimal getAmount() {
        return amount;
    }

    Long getExpenseId() {
        return expenseId;
    }

    void revise(LocalDate workedOn, String objectName, String workId, BigDecimal quantity, BigDecimal rate, BigDecimal amount) {
        this.workedOn = workedOn;
        this.objectName = objectName;
        this.workId = workId;
        this.quantity = quantity;
        this.rate = rate;
        this.amount = amount;
    }

    void expense(Long expenseId) {
        this.expenseId = expenseId;
    }
}
