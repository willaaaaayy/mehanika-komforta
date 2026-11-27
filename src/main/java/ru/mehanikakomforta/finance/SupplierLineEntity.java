package ru.mehanikakomforta.finance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "supplier_lines")
class SupplierLineEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "object_name", length = 500)
    private String objectName;

    @Column(name = "expense_id")
    private Long expenseId;

    protected SupplierLineEntity() {
    }

    SupplierLineEntity(Long documentId, String title, BigDecimal amount, String objectName) {
        this.documentId = documentId;
        this.title = title;
        this.amount = amount;
        this.objectName = objectName;
    }

    Long getId() {
        return id;
    }

    Long getDocumentId() {
        return documentId;
    }

    String getTitle() {
        return title;
    }

    BigDecimal getAmount() {
        return amount;
    }

    String getObjectName() {
        return objectName;
    }

    Long getExpenseId() {
        return expenseId;
    }

    void object(String objectName) {
        this.objectName = objectName;
    }

    void expense(Long expenseId) {
        this.expenseId = expenseId;
    }
}
