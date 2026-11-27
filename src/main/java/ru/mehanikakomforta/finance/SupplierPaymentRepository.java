package ru.mehanikakomforta.finance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface SupplierPaymentRepository extends JpaRepository<SupplierPaymentEntity, Long> {

    List<SupplierPaymentEntity> findBySupplierId(Long supplierId);
}
