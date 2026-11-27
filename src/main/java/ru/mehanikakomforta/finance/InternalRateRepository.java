package ru.mehanikakomforta.finance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface InternalRateRepository extends JpaRepository<InternalRateEntity, Long> {

    List<InternalRateEntity> findByWorkIdOrderByEffectiveFromDescIdDesc(String workId);

    List<InternalRateEntity> findAllByOrderByWorkIdAscEffectiveFromDescIdDesc();
}
