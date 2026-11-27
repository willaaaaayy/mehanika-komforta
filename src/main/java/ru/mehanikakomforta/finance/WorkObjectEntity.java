package ru.mehanikakomforta.finance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

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

    @Column(name = "object_type", length = 80)
    private String objectType;

    @Column(name = "area_m2", precision = 12, scale = 2)
    private BigDecimal area;

    @Column(name = "assigned_to", length = 80)
    private String assignedTo;

    protected WorkObjectEntity() {
    }

    WorkObjectEntity(Long clientId, String address) {
        this.clientId = clientId;
        this.address = address;
        this.status = "prep";
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

    String getObjectType() {
        return objectType;
    }

    BigDecimal getArea() {
        return area;
    }

    String getAssignedTo() {
        return assignedTo;
    }

    void change(String address, String objectType, BigDecimal area, String status, String assignedTo) {
        if (address != null && !address.isBlank()) {
            this.address = address.trim();
        }
        if (objectType != null) {
            this.objectType = objectType.isBlank() ? null : objectType.trim();
        }
        if (area != null) {
            this.area = area;
        }
        if (status != null && !status.isBlank()) {
            this.status = status;
        }
        if (assignedTo != null) {
            this.assignedTo = assignedTo.isBlank() ? null : assignedTo.trim();
        }
    }
}
