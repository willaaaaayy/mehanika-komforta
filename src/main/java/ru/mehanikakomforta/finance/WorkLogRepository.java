package ru.mehanikakomforta.finance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface WorkLogRepository extends JpaRepository<WorkLogEntity, Long> {

    List<WorkLogEntity> findAllByOrderByWorkedOnDesc();

    List<WorkLogEntity> findByEmployeeId(Long employeeId);

    List<WorkLogEntity> findByObjectName(String objectName);
}
