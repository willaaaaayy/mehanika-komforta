package ru.mehanikakomforta.finance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface WorkObjectRepository extends JpaRepository<WorkObjectEntity, Long> {

    List<WorkObjectEntity> findByClientIdOrderById(Long clientId);
}
