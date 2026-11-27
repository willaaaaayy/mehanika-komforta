package ru.mehanikakomforta.clients;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "clients")
public class ClientEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(nullable = false, length = 16)
    private String type;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 40)
    private String phone;

    @Column(length = 120)
    private String email;

    @Column(length = 12)
    private String inn;

    @Column(length = 300)
    private String address;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "source_lead_id")
    private Long sourceLeadId;

    @Column(name = "telegram_id")
    private Long telegramId;

    @Column(name = "telegram_username", length = 64)
    private String telegramUsername;

    @Column(name = "telegram_first_name", length = 64)
    private String telegramFirstName;

    @Column(name = "telegram_last_name", length = 64)
    private String telegramLastName;

    protected ClientEntity() {
    }

    public ClientEntity(Instant createdAt, String type, String name, String status) {
        this.createdAt = createdAt;
        this.type = type;
        this.name = name;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public String getInn() {
        return inn;
    }

    public String getAddress() {
        return address;
    }

    public String getStatus() {
        return status;
    }

    public Long getSourceLeadId() {
        return sourceLeadId;
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

    public void fill(String type, String name, String phone, String email, String inn, String address, Long sourceLeadId) {
        if (type != null && !type.isBlank()) {
            this.type = type;
        }
        if (name != null && !name.isBlank()) {
            this.name = name;
        }
        if (phone != null) {
            this.phone = phone;
        }
        if (email != null) {
            this.email = email.isBlank() ? null : email.trim();
        }
        if (inn != null) {
            this.inn = inn.isBlank() ? null : inn.trim();
        }
        if (address != null) {
            this.address = address.isBlank() ? null : address.trim();
        }
        if (sourceLeadId != null && this.sourceLeadId == null) {
            this.sourceLeadId = sourceLeadId;
        }
        this.status = "current";
    }

    public void telegram(Long id, String username, String firstName, String lastName) {
        if (id != null) {
            this.telegramId = id;
        }
        if (username != null) {
            this.telegramUsername = username;
        }
        if (firstName != null) {
            this.telegramFirstName = firstName;
        }
        if (lastName != null) {
            this.telegramLastName = lastName;
        }
    }

    public void archive() {
        this.status = "archived";
    }

    public void status(String status) {
        if (status != null && !status.isBlank()) {
            this.status = status;
        }
    }
}
