package ru.mehanikakomforta.clients;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface ClientFieldValueRepository extends JpaRepository<ClientFieldValueEntity, ClientFieldValueEntity.Key> {

    List<ClientFieldValueEntity> findByClientId(Long clientId);
}
