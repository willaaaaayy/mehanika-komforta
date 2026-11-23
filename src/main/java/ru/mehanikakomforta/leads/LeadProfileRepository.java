package ru.mehanikakomforta.leads;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LeadProfileRepository extends JpaRepository<LeadProfileEntity, Long> {

    Optional<LeadProfileEntity> findByLeadId(Long leadId);

    boolean existsByLeadId(Long leadId);

    void deleteByLeadId(Long leadId);
}
