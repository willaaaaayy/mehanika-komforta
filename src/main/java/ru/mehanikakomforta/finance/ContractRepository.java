package ru.mehanikakomforta.finance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface ContractRepository extends JpaRepository<ContractEntity, Long> {

    Optional<ContractEntity> findByNumber(String number);

    List<ContractEntity> findByConfirmedTrueOrderByCreatedAtDesc();

    List<ContractEntity> findByClientIdAndConfirmedTrueOrderByCreatedAtDesc(Long clientId);

    long countByClientIdAndConfirmedTrue(Long clientId);
}
