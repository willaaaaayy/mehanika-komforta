package ru.mehanikakomforta.finance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "suppliers")
class SupplierEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 20)
    private String inn;

    @Column(length = 40)
    private String phone;

    @Column(length = 500)
    private String address;

    @Column(name = "contract_name", length = 200)
    private String contractName;

    protected SupplierEntity() {
    }

    SupplierEntity(String name, String inn, String phone) {
        this.name = name;
        this.inn = inn;
        this.phone = phone;
    }

    Long getId() {
        return id;
    }

    String getName() {
        return name;
    }

    String getInn() {
        return inn;
    }

    String getPhone() {
        return phone;
    }

    String getAddress() {
        return address;
    }

    String getContractName() {
        return contractName;
    }

    void change(String name, String inn, String phone, String address, String contractName) {
        this.name = name;
        this.inn = inn;
        this.phone = phone;
        this.address = address;
        this.contractName = contractName;
    }
}
