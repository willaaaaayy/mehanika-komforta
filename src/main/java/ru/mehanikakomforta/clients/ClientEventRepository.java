package ru.mehanikakomforta.clients;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClientEventRepository extends JpaRepository<ClientEventEntity, Long> {
    List<ClientEventEntity> findByClientIdOrderByAtDesc(Long clientId);
}
