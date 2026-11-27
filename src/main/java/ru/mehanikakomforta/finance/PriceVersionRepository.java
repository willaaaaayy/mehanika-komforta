package ru.mehanikakomforta.finance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface PriceVersionRepository extends JpaRepository<PriceVersionEntity, Long> {

    List<PriceVersionEntity> findByObjectIdOrderByVersionNoDesc(Long objectId);
}
