package ru.mehanikakomforta.leads;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LeadRepository extends JpaRepository<LeadEntity, Long> {

    List<LeadEntity> findAllByOrderByCreatedAtDesc();
}
