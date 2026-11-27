package ru.mehanikakomforta.clients;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "client_layouts")
class ClientLayoutEntity {

    @Id
    @Column(name = "client_type", length = 16)
    private String clientType;

    @Column(nullable = false)
    private String document;

    protected ClientLayoutEntity() {
    }

    ClientLayoutEntity(String clientType, String document) {
        this.clientType = clientType;
        this.document = document;
    }

    String getClientType() {
        return clientType;
    }

    String getDocument() {
        return document;
    }

    void document(String document) {
        this.document = document;
    }
}
