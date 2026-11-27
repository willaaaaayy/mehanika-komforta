package ru.mehanikakomforta.finance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface PriceVersionLineRepository extends JpaRepository<PriceVersionLineEntity, Long> {

    List<PriceVersionLineEntity> findByVersionIdOrderByIdAsc(Long versionId);
}
