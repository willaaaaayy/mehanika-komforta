package ru.mehanikakomforta.finance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "contract_schedule")
class ScheduleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "contract_id")
    private ContractEntity contract;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "due_on")
    private LocalDate dueOn;

    @Column(nullable = false, length = 16)
    private String kind;

    @Column(name = "paid_on")
    private LocalDate paidOn;

    @Column(name = "paid_amount", precision = 14, scale = 2)
    private BigDecimal paidAmount;

    @Column(name = "pay_method", length = 16)
    private String payMethod;

    @Column(nullable = false)
    private int position;

    @Column(name = "due_basis", length = 16)
    private String dueBasis;

    @Column(name = "due_days")
    private Integer dueDays;

    protected ScheduleEntity() {
    }

    ScheduleEntity(ContractEntity contract, String title, BigDecimal amount, LocalDate dueOn, String kind, int position) {
        this.contract = contract;
        this.title = title;
        this.amount = amount;
        this.dueOn = dueOn;
        this.kind = kind;
        this.position = position;
    }

    Long getId() {
        return id;
    }

    ContractEntity getContract() {
        return contract;
    }

    String getTitle() {
        return title;
    }

    BigDecimal getAmount() {
        return amount;
    }

    LocalDate getDueOn() {
        return dueOn;
    }

    String getKind() {
        return kind;
    }

    LocalDate getPaidOn() {
        return paidOn;
    }

    BigDecimal getPaidAmount() {
        return paidAmount;
    }

    int getPosition() {
        return position;
    }

    String getDueBasis() {
        return dueBasis;
    }

    Integer getDueDays() {
        return dueDays;
    }

    void dueRule(String basis, Integer days) {
        this.dueBasis = basis;
        this.dueDays = days;
    }

    void replace(String title, BigDecimal amount, LocalDate dueOn, String kind, int position) {
        this.title = title;
        this.amount = amount;
        this.dueOn = dueOn;
        this.kind = kind;
        this.position = position;
    }

    void pay(BigDecimal amount, LocalDate date, String method) {
        this.paidAmount = amount;
        this.paidOn = date;
        this.payMethod = method;
    }
}
