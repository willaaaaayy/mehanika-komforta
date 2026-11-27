package ru.mehanikakomforta.clients;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface ClientFieldRepository extends JpaRepository<ClientFieldEntity, Long> {

    Optional<ClientFieldEntity> findByFieldKey(String fieldKey);
}
