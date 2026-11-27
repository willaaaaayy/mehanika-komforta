package ru.mehanikakomforta.clients;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;

@Entity
@Table(name = "client_field_values")
@IdClass(ClientFieldValueEntity.Key.class)
class ClientFieldValueEntity {

    @Id
    @Column(name = "client_id")
    private Long clientId;

    @Id
    @Column(name = "field_key", length = 40)
    private String fieldKey;

    @Column
    private String stored;

    protected ClientFieldValueEntity() {
    }

    ClientFieldValueEntity(Long clientId, String fieldKey, String stored) {
        this.clientId = clientId;
        this.fieldKey = fieldKey;
        this.stored = stored;
    }

    String getFieldKey() {
        return fieldKey;
    }

    String getStored() {
        return stored;
    }

    void stored(String stored) {
        this.stored = stored;
    }

    static class Key implements Serializable {
        private Long clientId;
        private String fieldKey;

        public Long getClientId() {
            return clientId;
        }

        public String getFieldKey() {
            return fieldKey;
        }

        public void setClientId(Long clientId) {
            this.clientId = clientId;
        }

        public void setFieldKey(String fieldKey) {
            this.fieldKey = fieldKey;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Key key)) {
                return false;
            }
            return clientId.equals(key.clientId) && fieldKey.equals(key.fieldKey);
        }

        @Override
        public int hashCode() {
            return clientId.hashCode() * 31 + fieldKey.hashCode();
        }
    }
}
