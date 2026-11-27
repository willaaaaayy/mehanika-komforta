package ru.mehanikakomforta.finance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface PayrollPaymentRepository extends JpaRepository<PayrollPaymentEntity, Long> {

    List<PayrollPaymentEntity> findByEmployeeIdOrderByPaidOnAsc(Long employeeId);
}
