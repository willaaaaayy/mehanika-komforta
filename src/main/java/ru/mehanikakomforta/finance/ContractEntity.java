package ru.mehanikakomforta.finance;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "client_contracts")
class ContractEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(nullable = false, length = 40)
    private String number;

    @Column(name = "client_name", nullable = false, length = 300)
    private String clientName;

    @Column(name = "client_inn", length = 12)
    private String clientInn;

    @Column(name = "object_address", length = 500)
    private String objectAddress;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "original_name", nullable = false, length = 300)
    private String originalName;

    @Column(nullable = false)
    private boolean confirmed;

    @Column(name = "client_id")
    private Long clientId;

    @Column(name = "lead_id")
    private Long leadId;

    @Column(name = "object_id")
    private Long objectId;

    @Column(name = "signed_on")
    private java.time.LocalDate signedOn;

    @Column(length = 500)
    private String subject;

    @Column(precision = 14, scale = 2)
    private java.math.BigDecimal vat;

    @Column(name = "client_kpp", length = 12)
    private String clientKpp;

    @Column(name = "client_ogrn", length = 15)
    private String clientOgrn;

    @Column(name = "client_address", length = 300)
    private String clientAddress;

    @Column(length = 200)
    private String signatory;

    @OneToMany(mappedBy = "contract", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<ScheduleEntity> schedule = new ArrayList<>();

    protected ContractEntity() {
    }

    ContractEntity(Instant createdAt, String number, String originalName) {
        this.createdAt = createdAt;
        this.number = number;
        this.originalName = originalName;
        this.clientName = "";
        this.amount = BigDecimal.ZERO;
    }

    Long getId() {
        return id;
    }

    String getNumber() {
        return number;
    }

    String getClientName() {
        return clientName;
    }

    String getClientInn() {
        return clientInn;
    }

    String getObjectAddress() {
        return objectAddress;
    }

    BigDecimal getAmount() {
        return amount;
    }

    boolean isConfirmed() {
        return confirmed;
    }

    Long getClientId() {
        return clientId;
    }

    Long getLeadId() {
        return leadId;
    }

    Long getObjectId() {
        return objectId;
    }

    java.time.LocalDate getSignedOn() {
        return signedOn;
    }

    String getSubject() {
        return subject;
    }

    java.math.BigDecimal getVat() {
        return vat;
    }

    String getClientKpp() {
        return clientKpp;
    }

    String getClientOgrn() {
        return clientOgrn;
    }

    String getClientAddress() {
        return clientAddress;
    }

    String getSignatory() {
        return signatory;
    }

    void details(java.time.LocalDate signedOn, String subject, java.math.BigDecimal vat, String kpp, String ogrn, String address, String signatory) {
        this.signedOn = signedOn;
        this.subject = subject;
        this.vat = vat;
        this.clientKpp = kpp;
        this.clientOgrn = ogrn;
        this.clientAddress = address;
        this.signatory = signatory;
    }

    void linked(Long clientId, Long objectId, Long leadId) {
        this.clientId = clientId;
        this.objectId = objectId;
        if (leadId != null) {
            this.leadId = leadId;
        }
    }

    List<ScheduleEntity> getSchedule() {
        return schedule;
    }

    void fill(String number, String clientName, String clientInn, String objectAddress, BigDecimal amount, boolean confirmed) {
        this.number = number;
        this.clientName = clientName;
        this.clientInn = clientInn;
        this.objectAddress = objectAddress;
        this.amount = amount;
        this.confirmed = confirmed;
    }

    void replaceSchedule(List<ScheduleEntity> rows) {
        this.schedule.clear();
        this.schedule.addAll(rows);
    }
}
