package ru.mehanikakomforta.finance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface SupplierDocumentRepository extends JpaRepository<SupplierDocumentEntity, Long> {

    List<SupplierDocumentEntity> findBySupplierIdOrderByIssuedOnAscIdAsc(Long supplierId);
}
