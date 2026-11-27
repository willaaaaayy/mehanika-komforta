package ru.mehanikakomforta.finance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "supplier_documents")
class SupplierDocumentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    @Column(nullable = false, length = 16)
    private String kind;

    @Column(name = "doc_number", length = 80)
    private String number;

    @Column(name = "issued_on", nullable = false)
    private LocalDate issuedOn;

    @Column(name = "file_name", length = 300)
    private String fileName;

    @Column(nullable = false)
    private boolean cancelled;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    protected SupplierDocumentEntity() {
    }

    SupplierDocumentEntity(Long supplierId, String kind, String number, LocalDate issuedOn) {
        this.supplierId = supplierId;
        this.kind = kind;
        this.number = number;
        this.issuedOn = issuedOn;
    }

    Long getId() {
        return id;
    }

    Long getSupplierId() {
        return supplierId;
    }

    String getKind() {
        return kind;
    }

    String getNumber() {
        return number;
    }

    LocalDate getIssuedOn() {
        return issuedOn;
    }

    boolean isCancelled() {
        return cancelled;
    }

    String getCancelReason() {
        return cancelReason;
    }

    void file(String fileName) {
        this.fileName = fileName;
    }

    void cancel(String reason) {
        this.cancelled = true;
        this.cancelReason = reason;
    }
}
