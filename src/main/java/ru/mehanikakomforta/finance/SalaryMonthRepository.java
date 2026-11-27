package ru.mehanikakomforta.finance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

interface SalaryMonthRepository extends JpaRepository<SalaryMonthEntity, Long> {

    Optional<SalaryMonthEntity> findByEmployeeIdAndMonthStart(Long employeeId, LocalDate monthStart);

    List<SalaryMonthEntity> findByEmployeeId(Long employeeId);
}
