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
@Table(name = "expenses")
class ExpenseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "spent_on", nullable = false)
    private LocalDate spentOn;

    @Column(precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 16)
    private String kind;

    @Column(nullable = false, length = 32)
    private String category;

    @Column(name = "object_name", length = 500)
    private String objectName;

    @Column(length = 200)
    private String supplier;

    @Column(precision = 14, scale = 2)
    private BigDecimal principal;

    @Column(precision = 14, scale = 2)
    private BigDecimal interest;

    @Column(precision = 14, scale = 2)
    private BigDecimal owed;

    @Column(nullable = false)
    private boolean confirmed;

    @Column(name = "file_name", length = 300)
    private String fileName;

    @Column(nullable = false, length = 80)
    private String actor;

    protected ExpenseEntity() {
    }

    ExpenseEntity(LocalDate spentOn, String kind, String category, String actor) {
        this.spentOn = spentOn;
        this.kind = kind;
        this.category = category;
        this.actor = actor;
    }

    Long getId() {
        return id;
    }

    LocalDate getSpentOn() {
        return spentOn;
    }

    BigDecimal getAmount() {
        return amount;
    }

    String getKind() {
        return kind;
    }

    String getCategory() {
        return category;
    }

    String getObjectName() {
        return objectName;
    }

    String getSupplier() {
        return supplier;
    }

    BigDecimal getPrincipal() {
        return principal;
    }

    BigDecimal getInterest() {
        return interest;
    }

    BigDecimal getOwed() {
        return owed;
    }

    boolean isConfirmed() {
        return confirmed;
    }

    String getFileName() {
        return fileName;
    }

    void fill(LocalDate spentOn, BigDecimal amount, String kind, String category, String objectName, String supplier,
               BigDecimal principal, BigDecimal interest, BigDecimal owed, boolean confirmed) {
        this.spentOn = spentOn;
        this.amount = amount;
        this.kind = kind;
        this.category = category;
        this.objectName = objectName;
        this.supplier = supplier;
        this.principal = principal;
        this.interest = interest;
        this.owed = owed;
        this.confirmed = confirmed;
    }

    void file(String fileName) {
        this.fileName = fileName;
    }
}
