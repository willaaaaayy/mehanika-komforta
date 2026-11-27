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
@Table(name = "price_versions")
class PriceVersionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "object_id", nullable = false)
    private Long objectId;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Column(name = "created_on", nullable = false)
    private LocalDate createdOn;

    @Column(nullable = false, length = 80)
    private String author;

    @Column(length = 500)
    private String reason;

    @Column(precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private boolean approved;

    @Column(name = "addendum_number", length = 80)
    private String addendumNumber;

    @Column(name = "act_number", length = 80)
    private String actNumber;

    @Column(name = "invoice_amount", precision = 14, scale = 2)
    private BigDecimal invoiceAmount;

    protected PriceVersionEntity() {
    }

    PriceVersionEntity(Long objectId, int versionNo, LocalDate createdOn, String author, String reason, BigDecimal amount, boolean approved) {
        this.objectId = objectId;
        this.versionNo = versionNo;
        this.createdOn = createdOn;
        this.author = author;
        this.reason = reason;
        this.amount = amount;
        this.approved = approved;
    }

    Long getId() {
        return id;
    }

    Long getObjectId() {
        return objectId;
    }

    int getVersionNo() {
        return versionNo;
    }

    LocalDate getCreatedOn() {
        return createdOn;
    }

    String getAuthor() {
        return author;
    }

    String getReason() {
        return reason;
    }

    BigDecimal getAmount() {
        return amount;
    }

    boolean isApproved() {
        return approved;
    }

    String getAddendumNumber() {
        return addendumNumber;
    }

    String getActNumber() {
        return actNumber;
    }

    BigDecimal getInvoiceAmount() {
        return invoiceAmount;
    }

    void approve(String addendumNumber, String actNumber, BigDecimal invoiceAmount) {
        this.approved = true;
        this.addendumNumber = addendumNumber;
        this.actNumber = actNumber;
        this.invoiceAmount = invoiceAmount;
    }
}
