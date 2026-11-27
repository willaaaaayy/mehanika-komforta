package ru.mehanikakomforta.finance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "price_version_lines")
class PriceVersionLineEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_id", nullable = false)
    private Long versionId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "change_kind", length = 16)
    private String changeKind;

    @Column(precision = 14, scale = 3)
    private BigDecimal quantity;

    @Column(name = "plan_qty", precision = 14, scale = 3)
    private BigDecimal planQty;

    @Column(name = "fact_qty", precision = 14, scale = 3)
    private BigDecimal factQty;

    @Column(name = "client_price", precision = 14, scale = 2)
    private BigDecimal clientPrice;

    @Column(precision = 14, scale = 2)
    private BigDecimal cost;

    @Column(precision = 14, scale = 2)
    private BigDecimal margin;

    protected PriceVersionLineEntity() {
    }

    PriceVersionLineEntity(Long versionId, String title, String changeKind, BigDecimal quantity, BigDecimal planQty, BigDecimal factQty, BigDecimal clientPrice, BigDecimal cost, BigDecimal margin) {
        this.versionId = versionId;
        this.title = title;
        this.changeKind = changeKind;
        this.quantity = quantity;
        this.planQty = planQty;
        this.factQty = factQty;
        this.clientPrice = clientPrice;
        this.cost = cost;
        this.margin = margin;
    }

    String getTitle() {
        return title;
    }

    String getChangeKind() {
        return changeKind;
    }

    BigDecimal getQuantity() {
        return quantity;
    }

    BigDecimal getPlanQty() {
        return planQty;
    }

    BigDecimal getFactQty() {
        return factQty;
    }

    BigDecimal getClientPrice() {
        return clientPrice;
    }

    BigDecimal getCost() {
        return cost;
    }

    BigDecimal getMargin() {
        return margin;
    }
}
