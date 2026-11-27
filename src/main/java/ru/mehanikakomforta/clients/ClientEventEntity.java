package ru.mehanikakomforta.clients;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "client_events")
public class ClientEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Column(name = "lead_id")
    private Long leadId;

    @Column(nullable = false)
    private Instant at;

    @Column(nullable = false, length = 80)
    private String actor;

    @Column(nullable = false, length = 32)
    private String action;

    protected ClientEventEntity() {
    }

    public Instant getAt() {
        return at;
    }

    public String getAction() {
        return action;
    }

    public ClientEventEntity(Long clientId, Long leadId, Instant at, String actor, String action) {
        this.clientId = clientId;
        this.leadId = leadId;
        this.at = at;
        this.actor = actor;
        this.action = action;
    }
}
