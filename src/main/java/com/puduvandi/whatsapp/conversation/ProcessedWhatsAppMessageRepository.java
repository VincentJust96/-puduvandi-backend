package com.puduvandi.whatsapp.conversation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedWhatsAppMessageRepository extends JpaRepository<ProcessedWhatsAppMessage, Long> {
}
