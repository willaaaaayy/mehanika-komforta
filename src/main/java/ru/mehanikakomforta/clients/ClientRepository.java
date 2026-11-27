package ru.mehanikakomforta.clients;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClientRepository extends JpaRepository<ClientEntity, Long> {

    List<ClientEntity> findAllByOrderByCreatedAtDesc();
}
