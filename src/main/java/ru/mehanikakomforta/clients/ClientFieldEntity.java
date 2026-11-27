package ru.mehanikakomforta.clients;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "client_fields")
class ClientFieldEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "field_key", nullable = false, length = 40)
    private String fieldKey;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(name = "field_type", nullable = false, length = 20)
    private String fieldType;

    @Column(length = 300)
    private String hint;

    @Column(name = "default_value", length = 500)
    private String defaultValue;

    @Column(nullable = false)
    private boolean required;

    @Column(length = 200)
    private String roles;

    @Column(name = "contract_key", length = 80)
    private String contractKey;

    @Column(name = "system_field", nullable = false)
    private boolean systemField;

    @Column(nullable = false)
    private boolean hidden;

    @Column(nullable = false)
    private boolean deleted;

    @Column(length = 500)
    private String options;

    protected ClientFieldEntity() {
    }

    ClientFieldEntity(String fieldKey, String title, String fieldType, boolean systemField) {
        this.fieldKey = fieldKey;
        this.title = title;
        this.fieldType = fieldType;
        this.systemField = systemField;
    }

    Long getId() {
        return id;
    }

    String getFieldKey() {
        return fieldKey;
    }

    String getTitle() {
        return title;
    }

    String getFieldType() {
        return fieldType;
    }

    String getHint() {
        return hint;
    }

    String getDefaultValue() {
        return defaultValue;
    }

    boolean isRequired() {
        return required;
    }

    String getRoles() {
        return roles;
    }

    String getContractKey() {
        return contractKey;
    }

    boolean isSystemField() {
        return systemField;
    }

    boolean isHidden() {
        return hidden;
    }

    boolean isDeleted() {
        return deleted;
    }

    String getOptions() {
        return options;
    }

    void update(String title, String fieldType, String hint, String defaultValue, boolean required, String roles, String contractKey, boolean hidden, boolean deleted, String options) {
        if (title != null && !title.isBlank()) {
            this.title = title.trim();
        }
        if (fieldType != null && !fieldType.isBlank()) {
            this.fieldType = fieldType;
        }
        this.hint = hint;
        this.defaultValue = defaultValue;
        this.required = required;
        this.roles = roles;
        this.contractKey = contractKey;
        this.hidden = hidden;
        this.deleted = deleted;
        this.options = options;
    }
}
