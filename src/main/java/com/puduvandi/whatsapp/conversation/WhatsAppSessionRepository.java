package com.puduvandi.whatsapp.conversation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WhatsAppSessionRepository extends JpaRepository<WhatsAppSession, Long> {

    Optional<WhatsAppSession> findByWaId(String waId);
}
