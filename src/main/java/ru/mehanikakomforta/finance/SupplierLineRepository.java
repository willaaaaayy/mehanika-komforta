package ru.mehanikakomforta.finance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface SupplierLineRepository extends JpaRepository<SupplierLineEntity, Long> {

    List<SupplierLineEntity> findByDocumentIdOrderByIdAsc(Long documentId);

    List<SupplierLineEntity> findByObjectName(String objectName);
}
