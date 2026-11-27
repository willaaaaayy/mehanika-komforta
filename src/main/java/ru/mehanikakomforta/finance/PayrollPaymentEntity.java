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
@Table(name = "payroll_payments")
class PayrollPaymentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "paid_on", nullable = false)
    private LocalDate paidOn;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private boolean advance;

    protected PayrollPaymentEntity() {
    }

    PayrollPaymentEntity(Long employeeId, LocalDate paidOn, BigDecimal amount, boolean advance) {
        this.employeeId = employeeId;
        this.paidOn = paidOn;
        this.amount = amount;
        this.advance = advance;
    }

    Long getEmployeeId() {
        return employeeId;
    }

    LocalDate getPaidOn() {
        return paidOn;
    }

    BigDecimal getAmount() {
        return amount;
    }

    boolean isAdvance() {
        return advance;
    }
}
