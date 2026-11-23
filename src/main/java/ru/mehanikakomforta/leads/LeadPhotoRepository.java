package ru.mehanikakomforta.leads;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LeadPhotoRepository extends JpaRepository<LeadPhotoEntity, Long> {

    long countByLeadId(Long leadId);

    List<LeadPhotoEntity> findByLeadIdOrderByIdAsc(Long leadId);
}
