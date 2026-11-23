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

    @Column(name = "telegram_id")
    private Long telegramId;

    @Column(name = "telegram_username", length = 32)
    private String telegramUsername;

    @Column(name = "telegram_first_name", length = 64)
    private String telegramFirstName;

    @Column(name = "telegram_last_name", length = 64)
    private String telegramLastName;

    @Column(name = "telegram_bio", length = 70)
    private String telegramBio;

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

    public void telegram(Long id, String username, String firstName, String lastName, String bio) {
        this.telegramId = id;
        this.telegramUsername = username;
        this.telegramFirstName = firstName;
        this.telegramLastName = lastName;
        this.telegramBio = bio;
    }

    public Long getTelegramId() {
        return telegramId;
    }

    public String getTelegramUsername() {
        return telegramUsername;
    }

    public String getTelegramFirstName() {
        return telegramFirstName;
    }

    public String getTelegramLastName() {
        return telegramLastName;
    }

    public String getTelegramBio() {
        return telegramBio;
    }
}
