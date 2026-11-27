package ru.mehanikakomforta.finance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "client_objects")
class WorkObjectEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Column(nullable = false, length = 500)
    private String address;

    @Column(nullable = false, length = 32)
    private String status;

    protected WorkObjectEntity() {
    }

    WorkObjectEntity(Long clientId, String address) {
        this.clientId = clientId;
        this.address = address;
        this.status = "подготовка";
    }

    Long getId() {
        return id;
    }

    Long getClientId() {
        return clientId;
    }

    String getAddress() {
        return address;
    }

    String getStatus() {
        return status;
    }
}
