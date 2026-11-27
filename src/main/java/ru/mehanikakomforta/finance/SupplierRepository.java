package ru.mehanikakomforta.finance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface SupplierRepository extends JpaRepository<SupplierEntity, Long> {

    List<SupplierEntity> findAllByOrderByNameAsc();
}
