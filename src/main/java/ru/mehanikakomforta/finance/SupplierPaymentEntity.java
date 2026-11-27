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
@Table(name = "supplier_payments")
class SupplierPaymentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    @Column(name = "paid_on", nullable = false)
    private LocalDate paidOn;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    protected SupplierPaymentEntity() {
    }

    SupplierPaymentEntity(Long supplierId, LocalDate paidOn, BigDecimal amount) {
        this.supplierId = supplierId;
        this.paidOn = paidOn;
        this.amount = amount;
    }

    LocalDate getPaidOn() {
        return paidOn;
    }

    BigDecimal getAmount() {
        return amount;
    }
}
