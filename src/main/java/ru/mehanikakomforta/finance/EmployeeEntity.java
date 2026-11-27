package ru.mehanikakomforta.finance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "employees")
class EmployeeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "pay_type", nullable = false, length = 16)
    private String payType;

    @Column(precision = 14, scale = 2)
    private BigDecimal rate;

    protected EmployeeEntity() {
    }

    EmployeeEntity(String name, String payType, BigDecimal rate) {
        this.name = name;
        this.payType = payType;
        this.rate = rate;
    }

    Long getId() {
        return id;
    }

    String getName() {
        return name;
    }

    String getPayType() {
        return payType;
    }

    BigDecimal getRate() {
        return rate;
    }

    void change(String name, String payType, BigDecimal rate) {
        this.name = name;
        this.payType = payType;
        this.rate = rate;
    }
}
