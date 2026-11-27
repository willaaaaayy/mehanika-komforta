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
@Table(name = "internal_rates")
class InternalRateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "work_id", nullable = false, length = 64)
    private String workId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 16)
    private String unit;

    @Column(precision = 14, scale = 2)
    private BigDecimal labor;

    @Column(precision = 14, scale = 2)
    private BigDecimal material;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(nullable = false)
    private boolean teaching;

    protected InternalRateEntity() {
    }

    InternalRateEntity(String workId, String title, String unit, BigDecimal labor, BigDecimal material, LocalDate effectiveFrom, boolean teaching) {
        this.workId = workId;
        this.title = title;
        this.unit = unit;
        this.labor = labor;
        this.material = material;
        this.effectiveFrom = effectiveFrom;
        this.teaching = teaching;
    }

    Long getId() {
        return id;
    }

    String getWorkId() {
        return workId;
    }

    String getTitle() {
        return title;
    }

    String getUnit() {
        return unit;
    }

    BigDecimal getLabor() {
        return labor;
    }

    BigDecimal getMaterial() {
        return material;
    }

    LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    boolean isTeaching() {
        return teaching;
    }
}
