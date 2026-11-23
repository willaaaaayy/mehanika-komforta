package ru.mehanikakomforta.leads;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "leads")
public class LeadEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, length = 40)
    private String phone;

    @Column(name = "object_type", nullable = false, length = 80)
    private String objectType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private LeadSource source;

    @Convert(converter = LeadStageConverter.class)
    @Column(nullable = false, length = 16)
    private LeadStage stage;

    protected LeadEntity() {
    }

    public LeadEntity(Instant createdAt, String name, String phone, String objectType, LeadSource source) {
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
        this.name = name;
        this.phone = phone;
        this.objectType = objectType;
        this.source = source;
        this.stage = LeadStage.new_;
    }

    public Long getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public String getObjectType() {
        return objectType;
    }

    public LeadSource getSource() {
        return source;
    }

    public LeadStage getStage() {
        return stage;
    }

    public void changeStage(LeadStage stage, Instant updatedAt) {
        this.stage = stage;
        this.updatedAt = updatedAt;
    }
}
